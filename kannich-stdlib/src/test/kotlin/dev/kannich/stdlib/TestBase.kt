package dev.kannich.stdlib

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

open class TestBase {
    fun withJobContext(workingDir: String, block: suspend () -> Unit) = runBlocking {
        withContext(JobContext(workingDir = workingDir)) {
            block()
        }
    }
}
