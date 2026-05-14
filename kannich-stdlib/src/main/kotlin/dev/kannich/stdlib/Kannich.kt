package dev.kannich.stdlib

object Kannich {
    val CacheDir get() = DefaultEnv.env["KANNICH_CACHE_DIR"] ?: "/kannich/cache"
    val WorkspaceDir get() = DefaultEnv.env["KANNICH_PROJECT_DIR"] ?: "/workspace"
}