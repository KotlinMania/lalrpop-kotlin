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

// ---------------------------------------------------------------------------
// Filesystem and environment glue used by the API test suite.
// Kotlin Multiplatform commonMain delegates filesystem and process environment
// operations to per-platform actuals alongside Api.kt.
// ---------------------------------------------------------------------------

/** Returns the absolute path to the system temporary directory. */
internal expect fun apiTempDir(): String

/** Changes the process current working directory to [path]. */
internal expect fun apiSetCurrentDir(path: String)

/** Sets the environment variable [name] to [value] for the current process. */
internal expect fun apiSetEnvVar(name: String, value: String)

/** Returns true when [path] exists on the filesystem. */
internal expect fun apiPathExists(path: String): Boolean

/** Creates the directory [path]. */
internal expect fun apiCreateDir(path: String)

/** Recursively removes [path] and its contents. */
internal expect fun apiRemoveDirAll(path: String)

/** Removes the file at [path]. */
internal expect fun apiRemoveFile(path: String)

/**
 * Portable string path join using `/` as the separator.
 */
internal fun apiPathJoin(a: String, b: String): String =
    when {
        a.isEmpty() -> b
        a.endsWith('/') -> a + b
        else -> "$a/$b"
    }
