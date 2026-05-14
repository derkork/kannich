package dev.kannich.test

import dev.kannich.stdlib.JobFailedException
import dev.kannich.stdlib.JobScope
import dev.kannich.stdlib.Pipeline
import dev.kannich.stdlib.fail
import dev.kannich.stdlib.pipeline
import dev.kannich.tools.Cache
import kotlin.math.max

private fun JobScope.logSection(name: String) = log("=".repeat(60) + "\n$name\n" + "=".repeat(60))
private fun JobScope.logSubsection(name: String) =
    "---[ $name ]-".apply { log(this + ("-".repeat(max(1, 60 - length)))) }


suspend fun JobScope.clearCaches(vararg names: String) {
    if (getEnvFlag("KANNICH_TEST_CLEAR") ?: false) {
        log("Clearing caches ${names.joinToString()}")
        for (name in names) {
            Cache.clear(name)
        }
    } else {
        log("Skipping cache clear")
    }
}

suspend fun JobScope.verify(condition: Boolean, message: String = "Verification failed") {
    if (!condition) {
        fail(message)
    }
}

suspend fun JobScope.verifyFail(expectedMessage: String? = null, block: suspend () -> Unit) {
    var caught: JobFailedException? = null
    try {
        block()
    } catch (e: JobFailedException) {
        caught = e
    }
    if (caught == null) {
        fail("Expected a failure but the block succeeded")
    }
    if (expectedMessage != null && !caught.message.orEmpty().contains(expectedMessage)) {
        fail("Expected failure message to contain \"$expectedMessage\" but was: \"${caught.message}\"")
    }
}

private const val DEFAULT_GROUP = "default"

fun testSuite(block: suspend TestSuiteBuilder.() -> Unit): Pipeline =
    pipeline {
        execution("test") {
            job {
                log("Executing test")
                log("Architecture is ${System.getProperty("os.arch")}")

                val builder = TestSuiteBuilder()
                builder.block()

                val testSuite = builder.build()

                val activeGroup = getEnv("KANNICH_TEST_GROUP")
                val testsToRun = if (activeGroup != null) {
                    log("Filtering tests to group '$activeGroup'")
                    testSuite.testBlocks.filter { it.group == activeGroup }
                } else {
                    testSuite.testBlocks
                }

                logSection("TestSuite of ${testsToRun.size} tests")
                if (!testSuite.beforeAllBlocks.isEmpty()) {
                    logSubsection("beforeAll")
                    testSuite.beforeAllBlocks.forEach { it() }
                }
                testsToRun.forEach { entry ->
                    if (!testSuite.beforeEachBlocks.isEmpty()) {
                        logSubsection("beforeEach")
                        testSuite.beforeEachBlocks.forEach { it() }
                    }
                    logSubsection("Test: ${entry.name}")
                    entry.block.invoke(this)
                    if (!testSuite.afterEachBlocks.isEmpty()) {
                        logSubsection("afterEach")
                        testSuite.afterEachBlocks.forEach { it() }
                    }
                }
                if (!testSuite.afterAllBlocks.isEmpty()) {
                    logSubsection("afterAll")
                    testSuite.afterAllBlocks.forEach { it() }
                }
            }
        }
    }


class TestSuiteBuilder {
    private val beforeEachBlocks = mutableListOf<suspend JobScope.() -> Unit>()
    private val afterEachBlocks = mutableListOf<suspend JobScope.() -> Unit>()
    private val beforeAllBlocks = mutableListOf<suspend JobScope.() -> Unit>()
    private val afterAllBlocks = mutableListOf<suspend JobScope.() -> Unit>()
    private val testBlocks = mutableListOf<TestEntry>()

    fun beforeEach(block: suspend JobScope.() -> Unit) {
        beforeEachBlocks.add(block)
    }

    fun afterEach(block: suspend JobScope.() -> Unit) {
        afterEachBlocks.add(block)
    }

    fun beforeAll(block: suspend JobScope.() -> Unit) {
        beforeAllBlocks.add(block)
    }

    fun afterAll(block: suspend JobScope.() -> Unit) {
        afterAllBlocks.add(block)
    }

    fun test(name: String, block: suspend JobScope.() -> Unit) {
        testBlocks.add(TestEntry(DEFAULT_GROUP, name, block))
    }

    fun group(name: String, block: GroupBuilder.() -> Unit) {
        GroupBuilder(name).apply(block).tests.forEach { testBlocks.add(it) }
    }

    internal fun build() = TestSuite(
        beforeEachBlocks.toList(),
        afterEachBlocks.toList(),
        beforeAllBlocks.toList(),
        afterAllBlocks.toList(),
        testBlocks.toList()
    )
}

class GroupBuilder(private val groupName: String) {
    internal val tests = mutableListOf<TestEntry>()

    fun test(name: String, block: suspend JobScope.() -> Unit) {
        tests.add(TestEntry(groupName, name, block))
    }
}

data class TestEntry(
    val group: String,
    val name: String,
    val block: suspend JobScope.() -> Unit
)

class TestSuite(
    val beforeEachBlocks: List<suspend JobScope.() -> Unit>,
    val afterEachBlocks: List<suspend JobScope.() -> Unit>,
    val beforeAllBlocks: List<suspend JobScope.() -> Unit>,
    val afterAllBlocks: List<suspend JobScope.() -> Unit>,
    val testBlocks: List<TestEntry>
)
