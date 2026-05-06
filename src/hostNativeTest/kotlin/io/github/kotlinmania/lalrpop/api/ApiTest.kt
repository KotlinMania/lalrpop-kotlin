// port-lint: source api/test.rs
package io.github.kotlinmania.lalrpop.api

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.build.apiBuildReadFileToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

// tests may be run in parallel and these tests affect global state, so lock
private val API_TEST_MUTEX: ApiTestMutex = ApiTestMutex()

private const val TEST_DIR: String = "lalrpop-test"
private const val CUSTOM_TEST_DIR: String = "lalrpop-test2"
private const val EXPECTED_FULL_OUTPUT_DIR: String = "expected/full"

private enum class GenFileLoc {
    Src,
    Other,
    Root,
    OutDir,
    OutDirSlashOther,
    CustomOut,
    DoesntExist,
}

// This holds state during the test to be cleaned up on drop
private class TestState(
    val origDir: String,
    val lock: ApiTestMutexGuard,
) {
    companion object {
        fun new(origDir: String, lock: ApiTestMutexGuard): TestState =
            TestState(origDir, lock)
    }

    // Equivalent of the upstream `Drop for TestState`. Kotlin lacks
    // deterministic destructors, so each test calls [drop] from a
    // `try { ... } finally { ... }` block.
    fun drop() {
        removeLocalGeneratedFiles()
        apiSetCurrentDir(origDir)
        val outDir = apiPathJoin(apiTempDir(), TEST_DIR)
        apiRemoveDirAll(outDir)
        // the lock is automatically released when it goes out of scope
        lock.unlock()
    }
}

// Set up for API tests.  The directory structure in testFiles
// is:
//
// outer.lalrpop
// other
//   - other.lalrpop
// src
//   - src.lalrpop
//
// So we want to set CWD to directly above that, and OUT_DIR to a temp directory
private fun setup(): TestState {
    val outDir = apiPathJoin(apiTempDir(), TEST_DIR)

    // lock() can return an error if another thread panicked while holding the mutex.  In our case,
    // that represents a test failure.  If another test failed, state was already cleaned up on
    // drop.  So we check that and clear the mutex poison to resume processing
    val lock = API_TEST_MUTEX.lock(onPoisoned = {
        if (apiPathExists(outDir)) {
            // Uh oh, we did not clean up after all
            error("This test was started in an unclean state because another test failed but didn't manage to clean up test state")
        }
        API_TEST_MUTEX.clearPoison()
    })
    val origDir = apiCurrentDir()
    val testRoot = apiEnvVar("LALRPOP_TEST_ROOT") ?: origDir
    apiSetCurrentDir(apiPathJoin(testRoot, "src/api/test_files"))
    if (apiPathExists(outDir)) {
        // unclean data from previous failed test run.  Clean up
        apiRemoveDirAll(outDir)
    }
    // If we have unclean state from a previous run, clean it up
    removeLocalGeneratedFiles()

    apiCreateDir(outDir)

    // Safety note:
    // Setting process environment variables is not thread safe on most platforms,
    // because the underlying syscalls to set and read environment variables are not
    // synchronized.  Specifically, reading an environment variable while it is being
    // written can result in unspecified data being read.  These tests are under a
    // mutex for the tests in this file only, but may run concurrently with other tests.
    //
    // It is recommended to run the lalrpop test suite with each test in its own process.
    // You can run the test suite in-process at your own risk, knowing that this thread
    // safety issue may cause undefined behavior.  The risk is mitigated by the following
    // factors:
    //
    // 1. This is only invoked in test code.
    // 2. Our testing so far has not encountered this issue in practice.
    //
    // Running each test in its own process actually makes this safe.  Each test will then
    // have its own copy of the environment, so this safety issue cannot occur because the
    // code will not run in a multi-threaded context.
    apiSetEnvVar("OUT_DIR", outDir)
    return TestState.new(origDir, lock)
}

// Assumes CWD is testFiles
private fun removeLocalGeneratedFiles() {
    val generatedFiles = listOf(
        "src.rs",
        "src.report",
        "other.rs",
        "other.report",
        "outer.rs",
        "outer.report",
    )
    for (f in generatedFiles) {
        for (loc in listOf(".", "src", "other")) {
            val filePath = apiPathJoin(loc, f)
            if (apiPathExists(filePath)) {
                apiRemoveFile(filePath)
            }
        }
    }
    val customDir = apiPathJoin(apiTempDir(), CUSTOM_TEST_DIR)
    if (apiPathExists(customDir)) {
        apiRemoveDirAll(customDir)
    }
}

// This is maybe a little nonintuitive at first.  We verify that the file exists where we expect
// it, and nowhere else.  So fs::exists().unwrap() for a given location must be equal to our
// expectation that it is in that location.
private fun verifyFile(filename: String, expectedLocation: GenFileLoc) {
    println("Checking the location of $filename")
    assertEquals(
        expectedLocation == GenFileLoc.Src,
        apiPathExists(apiPathJoin("src", filename)),
    )
    assertEquals(
        expectedLocation == GenFileLoc.Other,
        apiPathExists(apiPathJoin("other", filename)),
    )
    assertEquals(
        expectedLocation == GenFileLoc.Root,
        apiPathExists(filename),
    )
    if (apiPathExists(apiPathJoin(apiTempDir(), CUSTOM_TEST_DIR))) {
        // Some tests create a custom output directory here.  We only check for contents if it
        // exists
        assertEquals(
            expectedLocation == GenFileLoc.CustomOut,
            apiPathExists(apiPathJoin(apiPathJoin(apiTempDir(), CUSTOM_TEST_DIR), filename)),
        )
    }
    assertEquals(
        expectedLocation == GenFileLoc.OutDir,
        apiPathExists(apiPathJoin(apiPathJoin(apiTempDir(), TEST_DIR), filename)),
    )
    assertEquals(
        expectedLocation == GenFileLoc.OutDirSlashOther,
        apiPathExists(
            apiPathJoin(
                apiPathJoin(
                    apiPathJoin(apiTempDir(), TEST_DIR),
                    "other",
                ),
                filename,
            ),
        ),
    )
    // For GenFileLoc::DoesntExist, we should have returned false for all others.  There is nothing
    // to positive test
}

private fun generatedFilePath(filename: String, expectedLocation: GenFileLoc): String =
    when (expectedLocation) {
        GenFileLoc.Src -> apiPathJoin("src", filename)
        GenFileLoc.Other -> apiPathJoin("other", filename)
        GenFileLoc.Root -> filename
        GenFileLoc.OutDir -> apiPathJoin(apiPathJoin(apiTempDir(), TEST_DIR), filename)
        GenFileLoc.OutDirSlashOther ->
            apiPathJoin(
                apiPathJoin(
                    apiPathJoin(apiTempDir(), TEST_DIR),
                    "other",
                ),
                filename,
            )
        GenFileLoc.CustomOut -> apiPathJoin(apiPathJoin(apiTempDir(), CUSTOM_TEST_DIR), filename)
        GenFileLoc.DoesntExist -> error("cannot read a file that should not exist")
    }

private fun verifyGeneratedOutput(filename: String, expectedLocation: GenFileLoc) {
    assertEquals(
        apiBuildReadFileToString(apiPathJoin(EXPECTED_FULL_OUTPUT_DIR, filename)),
        apiBuildReadFileToString(generatedFilePath(filename, expectedLocation)),
        "$filename generated output diverged from upstream oracle",
    )
}

class ApiTest {
    @Test
    fun testProcessRoot() {
        val state = setup()
        try {
            processRoot()

            verifyFile("src.rs", GenFileLoc.OutDir)
            verifyFile("other.rs", GenFileLoc.OutDirSlashOther)
            verifyFile("outer.rs", GenFileLoc.OutDir)
        } finally {
            state.drop()
        }
    }

    @Test
    fun testProcessSrc() {
        val state = setup()
        try {
            processSrc()

            verifyFile("src.rs", GenFileLoc.OutDir)
            verifyFile("other.rs", GenFileLoc.DoesntExist)
            verifyFile("outer.rs", GenFileLoc.DoesntExist)
        } finally {
            state.drop()
        }
    }

    @Test
    fun testProcessFileFullOutputMatchesUpstream() {
        val state = setup()
        try {
            Configuration.new()
                .forceBuild(true)
                .emitComments(true)
                .emitReport(true)
                .processFile("src/src.lalrpop")

            verifyFile("src.rs", GenFileLoc.Src)
            verifyFile("src.report", GenFileLoc.Src)
            verifyGeneratedOutput("src.rs", GenFileLoc.Src)
            verifyGeneratedOutput("src.report", GenFileLoc.Src)
        } finally {
            state.drop()
        }
    }

    @Test
    fun testProcessFile() {
        val state = setup()
        try {
            // This test is noting that with cargoDirConventions, "src/src.lalrpop"
            // will work, but prepending "../testFiles" does not as it is an unexpected
            // file prefix.
            assertFails {
                Configuration.new()
                    .useCargoDirConventions()
                    .processFile("../test_files/src/src.lalrpop")
            }

            verifyFile("src.rs", GenFileLoc.DoesntExist)
            verifyFile("other.rs", GenFileLoc.DoesntExist)
            verifyFile("outer.rs", GenFileLoc.DoesntExist)
        } finally {
            state.drop()
        }
    }

    @Test
    fun testExplicitInOut() {
        val state = setup()
        try {
            val customDir = apiPathJoin(apiTempDir(), CUSTOM_TEST_DIR)
            apiCreateDir(customDir)

            Configuration.new()
                .setInDir("other")
                .setOutDir(customDir)
                .process()

            verifyFile("src.rs", GenFileLoc.DoesntExist)
            verifyFile("other.rs", GenFileLoc.CustomOut)
            verifyFile("outer.rs", GenFileLoc.DoesntExist)

            apiRemoveDirAll(customDir)
        } finally {
            state.drop()
        }
    }

    @Test
    fun testCargoDirConventions() {
        val state = setup()
        try {
            Configuration.new()
                .useCargoDirConventions()
                .process()

            verifyFile("src.rs", GenFileLoc.OutDir)
            verifyFile("other.rs", GenFileLoc.DoesntExist)
            verifyFile("outer.rs", GenFileLoc.DoesntExist)
        } finally {
            state.drop()
        }
    }
}

// ---------------------------------------------------------------------------
// Mutex transliteration. the upstream `std::sync::Mutex<i32>` carries a "poison"
// flag that is set when the holder of the lock panics. The Kotlin port
// uses a simple recursion-tolerant lock built on a top-level mutable
// flag. These host-native API tests mutate process-global state (current
// directory and environment), so the lock keeps this file's tests serialized.
// The poison flag is set explicitly by `setup()` if a previous run left the
// temp directory dirty.
// ---------------------------------------------------------------------------

private class ApiTestMutex {
    private var locked: Boolean = false
    private var poisoned: Boolean = false

    fun lock(onPoisoned: () -> Unit): ApiTestMutexGuard {
        if (poisoned) {
            onPoisoned()
        }
        // Spin until the lock is released; kotlin.test runs serially in
        // common configurations, so this loop terminates immediately.
        while (locked) {
            // intentionally empty — see header comment
        }
        locked = true
        return ApiTestMutexGuard(this)
    }

    fun clearPoison() {
        poisoned = false
    }

    fun unlock(guard: ApiTestMutexGuard) {
        if (guard.owner !== this) error("ApiTestMutexGuard.unlock: foreign guard")
        locked = false
    }
}

private class ApiTestMutexGuard(internal val owner: ApiTestMutex) {
    fun unlock() {
        owner.unlock(this)
    }
}
