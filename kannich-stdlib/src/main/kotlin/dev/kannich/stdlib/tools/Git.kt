package dev.kannich.stdlib.tools

import dev.kannich.stdlib.context.ExecResult
import dev.kannich.stdlib.fail
import org.slf4j.LoggerFactory

/**
 * Tool for executing Git commands.
 * Provides a simple wrapper around the git CLI.
 *
 * Usage in job blocks:
 * ```kotlin
 * job("Git Clone") {
 *     Git.exec("clone", "https://github.com/user/repo.git")
 * }
 * ```
 */
object Git {
    private val logger = LoggerFactory.getLogger(Git::class.java)

    /**
     * Executes a git command with the given arguments.
     * Fails if the command exits with non-zero status.
     *
     * @param args Arguments to pass to git
     * @param silent If true, suppresses output to the console
     * @return The execution result
     * @throws dev.kannich.stdlib.JobFailedException if the command fails
     */
    suspend fun exec(vararg args: String, silent: Boolean = false): ExecResult {
        val result = Shell.exec("git", *args, silent = silent)
        if (!result.success) {
            val errorMessage = result.stderr.ifBlank { "Exit code: ${result.exitCode}" }
            fail("Git command failed: $errorMessage")
        }
        return result
    }

    /**
     * Returns the current commit SHA of the checkout in the current directory.
     *
     * @return The commit SHA
     * @throws dev.kannich.stdlib.JobFailedException if git command fails (e.g. not a git repository)
     */
    suspend fun currentCommitSha(): String {
        val result = exec("rev-parse", "HEAD", silent = true)
        return result.stdout.trim()
    }

    /**
     * Returns a list of branches that know about the currently checked out commit.
     *
     * @return List of branch names
     */
    suspend fun currentBranches(): List<String> {
        val result = exec("branch", "--contains", "HEAD", "--format=%(refname:short)", silent = true)
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Returns a list of all tags that point to the currently checked out commit.
     *
     * @return List of tag names
     */
    suspend fun currentTags(): List<String> {
        val result = exec("tag", "--points-at", "HEAD", silent = true)
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Switches the working copy to the given ref and resets all changes.
     *
     * @param ref The ref to switch to (branch name, tag name, or commit SHA)
     */
    suspend fun switch(ref: String) {
        exec("checkout", "-f", ref)
    }
}
