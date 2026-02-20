package dev.kannich.stdlib

interface Tool {
    /**
     * Returns the paths of the directories where the tool executables are located.
     * Most tools will likely have only one executable, but some may have multiple.
     */
    suspend fun getToolPaths() : List<String>

    /**
     * Ensures that the tool is installed and ready to use.
     */
    suspend fun ensureInstalled()
}