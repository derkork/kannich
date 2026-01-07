import dev.kannich.stdlib.JobContext
import dev.kannich.stdlib.fail

interface Env {
    suspend fun getEnv(name: String): String?
    suspend fun requireEnv(name: String): String =
        getEnv(name) ?: fail("Please specify $name in environment variables.")

    suspend fun hasEnv(name: String): Boolean = getEnv(name) != null
}


class EnvImpl() : Env {
    /**
     * Return the environment variable from the most appropriate execution context.
     */
    override suspend fun getEnv(name: String): String? {
        if (JobContext.exists()) {
            return JobContext.current().env[name]
        }
        return DefaultEnv.env[name]
    }
}

/**
 * Holds environment variables during pipeline definition time.
 *
 * Users pass `-e KEY=VALUE` arguments to configure builds. During job execution,
 * these are available via JobScope.getEnv(). But pipelines are built during script
 * evaluation, BEFORE job execution. This singleton bridges that gap by allowing
 * Main.kt to set the extra env vars before script evaluation, so getEnv() calls
 * in PipelineBuilder/ExecutionBuilder/etc. can see them.
 */
object DefaultEnv {
    var env: Map<String, String> = emptyMap()
}