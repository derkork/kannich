package dev.kannich.stdlib

import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for matching file paths against ant-style glob patterns.
 *
 * Pattern syntax:
 * - `*` matches 0 or more characters except the directory separator (`/`)
 * - `**` matches 0 or more characters including the directory separator (`/`)
 * - `?` matches exactly one character (except `/`)
 *
 * No implicit shorthands - patterns ending in `/` do NOT automatically append `**`.
 */
object AntPathMatcher {
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Checks if the given path matches the pattern.
     *
     * @param pattern The ant-style glob pattern
     * @param path The path to match (should use `/` as separator)
     * @return true if the path matches the pattern
     */
    fun matches(pattern: String, path: String): Boolean {
        val regex = regexCache.getOrPut(pattern) { patternToRegex(pattern) }
        return regex.matches(path)
    }

    /**
     * Converts an ant-style glob pattern to a regex.
     */
    fun patternToRegex(pattern: String): Regex {
        val result = StringBuilder()
        var i = 0

        while (i < pattern.length) {
            when {
                // Handle ** (must check before single *)
                i + 1 < pattern.length && pattern[i] == '*' && pattern[i + 1] == '*' -> {
                    result.append(".*?")
                    i += 2
                }
                // Handle single *
                pattern[i] == '*' -> {
                    result.append("[^/]*")
                    i++
                }
                // Handle ?
                pattern[i] == '?' -> {
                    result.append("[^/]")
                    i++
                }
                // Escape regex special characters
                pattern[i] in "\\^$.|+()[]{}".toCharArray() -> {
                    result.append("\\")
                    result.append(pattern[i])
                    i++
                }
                // Regular character
                else -> {
                    result.append(pattern[i])
                    i++
                }
            }
        }

        return Regex("^${result}$")
    }

    /**
     * Checks if the given path matches any of the patterns.
     *
     * @param patterns The list of ant-style glob patterns
     * @param path The path to match
     * @return true if the path matches any pattern
     */
    fun matchesAny(patterns: List<String>, path: String): Boolean {
        return patterns.any { matches(it, path) }
    }

    /**
     * Checks if a pattern is a literal path (no wildcards).
     */
    fun isLiteralPath(pattern: String): Boolean {
        return !pattern.contains("*") && !pattern.contains("?")
    }

    /**
     * Extracts the static base path from a pattern (the part before any wildcards).
     * This can be used to limit directory traversal to specific subtrees.
     *
     * Examples:
     * - `target/**/*.jar` → `target`
     * - `build/libs/*.jar` → `build/libs`
     * - `**/*.jar` → `` (empty, must search from root)
     * - `*.txt` → `` (empty, must search from root)
     *
     * Note: For literal paths (no wildcards), use isLiteralPath() instead and
     * check the file directly without find.
     *
     * @param pattern The ant-style glob pattern
     * @return The static base path, or empty string if pattern starts with wildcard
     */
    fun getBasePath(pattern: String): String {
        val parts = pattern.split("/")
        val staticParts = mutableListOf<String>()

        for (part in parts) {
            if (part.contains("*") || part.contains("?")) {
                break
            }
            staticParts.add(part)
        }

        return staticParts.joinToString("/")
    }

    /**
     * Gets unique base paths from multiple patterns.
     * Deduplicates and removes paths that are subpaths of others.
     *
     * @param patterns List of ant-style glob patterns
     * @return Set of base paths to search
     */
    fun getBasePaths(patterns: List<String>): Set<String> {
        val basePaths = patterns.map { getBasePath(it) }.toMutableSet()

        // If any pattern requires searching from root, just return root
        if (basePaths.contains("")) {
            return setOf("")
        }

        // Remove paths that are subpaths of others (keep only the shortest prefixes)
        return basePaths.filter { path ->
            basePaths.none { other ->
                other != path && path.startsWith("$other/")
            }
        }.toSet()
    }
}
