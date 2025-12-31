package dev.kannich.core.util

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

    /**
     * Checks if the given path matches the pattern.
     *
     * @param pattern The ant-style glob pattern
     * @param path The path to match (should use `/` as separator)
     * @return true if the path matches the pattern
     */
    fun matches(pattern: String, path: String): Boolean {
        val regex = patternToRegex(pattern)
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
                // Handle **/ at start of pattern - matches zero or more directories
                i == 0 && pattern.startsWith("**/") -> {
                    result.append("(.*/)?")
                    i += 3
                }
                // Handle /**/ in middle - matches zero or more directories
                pattern[i] == '/' && i + 3 <= pattern.length && pattern.substring(i, minOf(i + 4, pattern.length)).startsWith("/**/") -> {
                    result.append("/(.*/)?")
                    i += 4
                }
                // Handle /** at end - matches everything including subdirectories
                pattern[i] == '/' && i + 2 < pattern.length && pattern.substring(i).startsWith("/**") && (i + 3 >= pattern.length || pattern[i + 3] == '/') -> {
                    result.append("/.*")
                    i += 3
                }
                // Handle ** (must check before single *)
                i + 1 < pattern.length && pattern[i] == '*' && pattern[i + 1] == '*' -> {
                    result.append(".*")
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
}
