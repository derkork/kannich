package dev.kannich.tools

import dev.kannich.stdlib.fail
import org.slf4j.LoggerFactory

/**
 * Represents a resolved APT package with its metadata.
 */
data class ResolvedPackage(
    val name: String,
    val version: String,
    val arch: String,
    val isVirtual: Boolean = false,
    val providedBy: String? = null
) {
    /**
     * Cache key for this package.
     * Format: apt/packages/{name}_{version}_{arch}.deb
     * Colons in version (epoch) are URL-encoded as %3a.
     */
    fun cacheKey(): String = "apt/packages/${name}_${version.replace(":", "%3a")}_${arch}.deb"
}

/**
 * Built-in tool for managing APT package installation with per-package caching.
 *
 * Unlike typical apt caching solutions that hash the entire package list (invalidating
 * cache on any change), this implementation caches each .deb file separately. This means
 * adding or removing one package doesn't invalidate the cache for others.
 *
 * Cache structure: /kannich/cache/apt/packages/{name}_{version}_{arch}.deb
 *
 * Usage:
 * ```kotlin
 * job("build") {
 *     Apt.install("gcc", "make", "curl")
 *     Apt.install("gcc=4:11.2.0-1ubuntu1", "cmake=3.22.1-1ubuntu1")  // with versions
 * }
 * ```
 */
object Apt {
    private val logger = LoggerFactory.getLogger(Apt::class.java)
    private const val CACHE_KEY_PREFIX = "apt/packages"

    /**
     * Installs the specified packages, using cache when available.
     * Accepts packages with optional version constraints.
     *
     * This method automatically runs `apt-get update` to ensure package lists are fresh.
     *
     * @param packages Package specifications (e.g., "gcc=11.2.0", "vim", "build-essential")
     */
    suspend fun install(vararg packages: String) {
        if (packages.isEmpty()) return

        logger.info("Installing packages: ${packages.joinToString(", ")}")

        // Always update package lists first to ensure we have fresh metadata
        update()

        // 1. Parse package specifications
        val packageSpecs = packages.map { parsePackageSpec(it) }

        // 2. Get system architecture
        val arch = getSystemArchitecture()

        // 3. Resolve all packages to concrete packages with versions
        val requestedPackages = mutableListOf<ResolvedPackage>()
        for ((name, version) in packageSpecs) {
            val pkg = resolveToConcretePackage(name, version, arch)
            requestedPackages.add(pkg)
        }

        // 4. Collect all dependencies
        val allPackages = mutableSetOf<ResolvedPackage>()
        for (pkg in requestedPackages) {
            collectDependencies(pkg, allPackages, arch)
        }

        logger.info("Resolved ${allPackages.size} packages (including dependencies)")

        // 5. Filter out already installed packages
        val packagesToInstall = allPackages.filter { pkg ->
            !isPackageInstalled(pkg.name, pkg.version)
        }

        if (packagesToInstall.isEmpty()) {
            logger.info("All packages are already installed")
            return
        }

        logger.info("${packagesToInstall.size} packages need to be installed")

        // 6. Partition into cached and uncached packages
        val (cached, uncached) = packagesToInstall.partition { pkg ->
            Cache.exists(pkg.cacheKey())
        }

        logger.info("Found ${cached.size} packages in cache, ${uncached.size} need download")

        // 7. Ensure cache directory exists
        Cache.ensureDir(CACHE_KEY_PREFIX)

        // 8. Download uncached packages
        val tempDir = if (uncached.isNotEmpty()) Fs.mktemp("apt-download") else null

        try {
            val downloadedPaths = mutableListOf<String>()
            for (pkg in uncached) {
                val debPath = downloadPackage(pkg, tempDir!!)
                downloadedPaths.add(debPath)

                // Cache the downloaded .deb file
                Fs.copy(debPath, Cache.path(pkg.cacheKey()))
                logger.debug("Cached: ${pkg.name}=${pkg.version}")
            }

            // 9. Collect all .deb paths (cached + newly downloaded)
            val allDebPaths = cached.map { Cache.path(it.cacheKey()) } + downloadedPaths

            // 10. Install all packages using dpkg
            installDebFiles(allDebPaths)

            logger.info("Successfully installed ${packages.size} packages")

        } finally {
            // 11. Cleanup temp directory
            if (tempDir != null) {
                Fs.delete(tempDir)
            }
        }
    }

    /**
     * Clears the apt package Cache.
     *
     * @param packageName If specified, clears only cached versions of this package.
     *                    If null, clears all cached apt packages.
     */
    suspend fun clearCache(packageName: String? = null) {
        if (packageName != null) {
            // Clear all versions of this package
            val pattern = "${CACHE_KEY_PREFIX}/${packageName}_*"
            Shell.execShell("rm -f '${Cache.path(pattern)}'")
            logger.info("Cleared cache for package: $packageName")
        } else {
            // Clear entire apt cache
            Cache.clear(CACHE_KEY_PREFIX)
            logger.info("Cleared all apt package cache")
        }
    }

    /**
     * Parses a package specification into name and optional version.
     * Examples: "gcc=11.2.0" -> ("gcc", "11.2.0"), "vim" -> ("vim", null)
     */
    private fun parsePackageSpec(spec: String): Pair<String, String?> {
        val parts = spec.split("=", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            Pair(parts[0].trim(), null)
        }
    }

    /**
     * Gets the system's dpkg architecture.
     * Returns: "amd64", "arm64", "i386", etc.
     */
    private suspend fun getSystemArchitecture(): String {
        val result = Shell.exec("dpkg", "--print-architecture")
        if (!result.success) {
            fail("Failed to determine system architecture: ${result.stderr}")
        }
        return result.stdout.trim()
    }

    /**
     * Resolves a package name to a concrete package, handling virtual packages.
     */
    private suspend fun resolveToConcretePackage(name: String, version: String?, arch: String): ResolvedPackage {
        val pkgSpec = if (version != null) "$name=$version" else name

        // Try to get package info directly
        val showResult = Shell.execShell(
            "apt-cache show --quiet=0 --no-all-versions '$pkgSpec' 2>&1"
        )

        val output = showResult.stdout + showResult.stderr

        // Check for virtual package indicators
        if (output.contains("is a virtual package") ||
            output.contains("has no installation candidate") ||
            output.contains("Unable to locate package")
        ) {
            // This might be a virtual package - try to resolve
            return resolveVirtualPackage(name, arch)
        }

        if (!showResult.success) {
            fail("Failed to resolve package '$pkgSpec': ${showResult.stderr}")
        }

        // Parse the output to get Package, Version, Architecture
        return parsePackageShow(showResult.stdout, name)
    }

    /**
     * Resolves a virtual package to its concrete provider.
     * Uses apt-cache showpkg to find "Reverse Provides" section.
     */
    private suspend fun resolveVirtualPackage(name: String, defaultArch: String): ResolvedPackage {
        logger.debug("Resolving virtual package: $name")

        val result = Shell.exec("apt-cache", "showpkg", name)
        if (!result.success) {
            fail("Failed to resolve virtual package '$name': ${result.stderr}")
        }

        // Parse Reverse Provides section
        val lines = result.stdout.lines()
        val providesIndex = lines.indexOfFirst { it.trim() == "Reverse Provides:" }

        if (providesIndex == -1 || providesIndex >= lines.size - 1) {
            fail("Virtual package '$name' has no providers")
        }

        // Get the first provider (typically the default)
        val providerLine = lines.getOrNull(providesIndex + 1)?.trim()
        if (providerLine.isNullOrBlank()) {
            fail("Virtual package '$name' has no providers")
        }

        // Parse "package-name version" format
        val parts = providerLine.split(" ", limit = 2)
        val providerName = parts[0]

        logger.info("Virtual package '$name' resolved to '$providerName'")

        // Now get full info on the concrete provider
        val providerPkg = resolveToConcretePackage(providerName, null, defaultArch)
        return providerPkg.copy(
            isVirtual = true,
            providedBy = providerName
        )
    }

    /**
     * Parses apt-cache show output to extract package metadata.
     */
    internal fun parsePackageShow(output: String, requestedName: String): ResolvedPackage {
        var name: String? = null
        var version: String? = null
        var architecture: String? = null

        for (line in output.lines()) {
            when {
                line.startsWith("Package:") -> name = line.substringAfter(":").trim()
                line.startsWith("Version:") -> version = line.substringAfter(":").trim()
                line.startsWith("Architecture:") -> architecture = line.substringAfter(":").trim()
            }
            // Stop after finding all three to avoid parsing multiple stanzas
            if (name != null && version != null && architecture != null) break
        }

        if (name == null || version == null || architecture == null) {
            fail("Failed to parse package info for '$requestedName': missing required fields")
        }

        return ResolvedPackage(
            name = name,
            version = version,
            arch = architecture
        )
    }

    /**
     * Collects all dependencies for a package recursively.
     */
    private suspend fun collectDependencies(
        pkg: ResolvedPackage,
        collected: MutableSet<ResolvedPackage>,
        defaultArch: String
    ) {
        // Add the package itself
        collected.add(pkg)

        // Get recursive dependencies
        // Filter to only hard dependencies (Pre-Depends and Depends)
        val result = Shell.execShell(
            "apt-cache depends --recurse --no-recommends --no-suggests " +
                "--no-conflicts --no-breaks --no-replaces --no-enhances " +
                "'${pkg.name}' 2>/dev/null | grep '^\\w' | sort -u"
        )

        if (!result.success) {
            // Some packages might not have dependencies, that's OK
            logger.debug("Could not get dependencies for '${pkg.name}': ${result.stderr}")
            return
        }

        for (line in result.stdout.lines()) {
            val depName = line.trim()
            if (depName.isBlank()) continue

            // Skip if already collected
            if (collected.any { it.name == depName }) continue

            // Resolve this dependency
            try {
                val depPkg = resolveToConcretePackage(depName, null, defaultArch)
                collected.add(depPkg)
            } catch (e: Exception) {
                // Some dependencies may be virtual or optional - log and continue
                logger.debug("Could not resolve dependency '$depName': ${e.message}")
            }
        }
    }

    /**
     * Downloads a single package using apt-get download.
     * Returns the path to the downloaded .deb file.
     */
    private suspend fun downloadPackage(pkg: ResolvedPackage, targetDir: String): String {
        logger.debug("Downloading: ${pkg.name}=${pkg.version}")

        // apt-get download puts .deb in current directory
        val result = Shell.execShell(
            "cd '$targetDir' && apt-get download '${pkg.name}=${pkg.version}' 2>&1"
        )

        if (!result.success) {
            fail("Failed to download package '${pkg.name}=${pkg.version}': ${result.stderr}")
        }

        // Find the downloaded .deb file
        // Filename format: {name}_{version}_{arch}.deb
        val findResult = Shell.execShell("ls '$targetDir'/${pkg.name}_*.deb 2>/dev/null | head -1")

        val debPath = findResult.stdout.trim()
        if (debPath.isBlank() || !Fs.exists(debPath)) {
            fail("Downloaded .deb file not found for '${pkg.name}'")
        }

        return debPath
    }

    /**
     * Installs .deb files using dpkg.
     */
    private suspend fun installDebFiles(debPaths: List<String>) {
        if (debPaths.isEmpty()) {
            logger.info("No packages to install")
            return
        }

        logger.info("Installing ${debPaths.size} .deb files")

        // Install all .deb files at once for proper dependency ordering
        val debList = debPaths.joinToString(" ") { "'$it'" }
        val result = Shell.execShell("dpkg -i $debList 2>&1")

        if (!result.success) {
            // dpkg may fail due to missing dependencies - try to fix
            logger.warn("dpkg install had issues, attempting to fix dependencies")
            fixBrokenDependencies()
        }
    }

    /**
     * Updates the APT package lists.
     */
    private suspend fun update() {
        logger.info("Updating APT package lists")
        val result = Shell.execShell("apt-get update")
        if (!result.success) {
            fail("Failed to update APT package lists: ${result.stderr}")
        }
    }

    /**
     * Fixes broken package dependencies.
     */
    private suspend fun fixBrokenDependencies() {
        val result = Shell.execShell("apt-get --fix-broken install -y 2>&1")
        if (!result.success) {
            fail("Failed to fix broken dependencies: ${result.stderr}")
        }
    }

    /**
     * Checks if a specific version of a package is already installed.
     */
    private suspend fun isPackageInstalled(name: String, version: String): Boolean {
        val result = Shell.execShell(
            "dpkg-query -W -f='\${Version}' '$name' 2>/dev/null"
        )
        return result.success && result.stdout.trim() == version
    }
}
