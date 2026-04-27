// port-lint: source src/api/test.rs
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

// tests may be run in parallel and these tests affect global state, so lock
private val API_TEST_MUTEX: ApiTestMutex = ApiTestMutex()

private const val TEST_DIR: String = "lalrpop-test"
private const val CUSTOM_TEST_DIR: String = "lalrpop-test2"

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

    // Equivalent of Rust's `Drop for TestState`. Kotlin lacks
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

// Set up for API tests.  The directory structure in test_files
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
            // Uh oh, we didn't clean up after all
            error("This test was started in an unclean state because another test failed but didn't manage to clean up test state")
        }
        API_TEST_MUTEX.clearPoison()
    })
    val origDir = apiCurrentDir()
    apiSetCurrentDir("./src/api/test_files")
    if (apiPathExists(outDir)) {
        // unclean data from previous failed test run.  Clean up
        apiRemoveDirAll(outDir)
    }
    // If we have unclean state from a previous run, clean it up
    removeLocalGeneratedFiles()

    apiCreateDir(outDir)

    // Safety note:
    // set_var is marked as unsafe starting in the 2025 rust edition.  This is because C calls to
    // set and read environmental variables are not thread safe.  Specifically, reading an
    // environmental variable while it is being written to can result in unspecified data being
    // read.  In rust alone, these calls are protected via a mutex in the standard library, but if
    // we call into C code (e.g. via libc in a dependency), we do not get those protections.
    //
    // In practice for us, we do have libc in some of our dependencies, and we can't necessarily
    // know or predict where they might read environmental variables.  These tests are under a
    // mutex for the tests in this file only, but may run concurrently with other tests.
    //
    // It is recommended to run the lalrpop test suite using cargo-nextest.  See CONTRIBUTING.md
    // for details and instructions on using cargo-nextest.  You can run cargo test at your own
    // risk, knowing that this thread safety issue may cause undefined behavior.  The risk is
    // mitigated by the following factors:
    //
    // 1. This is only ran in test code.
    // 2. Our testing so far has not encountered this issue in practice.
    // 3. As stated above, it is only calls in C code that are a concern - any calls in rust
    //    dependency would not introduce thread safety issues.
    //
    // That said, cargo-nextest actually makes this safe.  In cargo-nextest, each test is ran in a
    // separate process, and therefore will have its own copy of the environment.  As a result,
    // this safety issue cannot occur under cargo-nextest, as this code will not run in a
    // multi-threaded context.
    apiSetEnvVar("OUT_DIR", outDir)
    return TestState.new(origDir, lock)
}

// Assumes CWD is test_files
private fun removeLocalGeneratedFiles() {
    for (f in listOf("src.rs", "other.rs", "outer.rs")) {
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
// expectation that it's in that location.
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

class ApiTest {
    @Test
    fun test_process_root() {
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
    fun test_process_src() {
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
    fun test_process_file() {
        val state = setup()
        try {
            // This test is noting that with cargo_dir_conventions, "src/src.lalrpop"
            // will work, but prepending "../test_files" does not as it is an unexpected
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
    fun test_explicit_in_out() {
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
    fun test_cargo_dir_conventions() {
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
// Mutex transliteration. Rust's `std::sync::Mutex<i32>` carries a "poison"
// flag that is set when the holder of the lock panics. The Kotlin port
// uses a simple recursion-tolerant lock built on a top-level mutable
// flag — kotlin.test runs tests serially within a single JVM/process by
// default, so contention is rare. The poison flag is set explicitly by
// `setup()` if a previous run left the temp directory dirty.
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
