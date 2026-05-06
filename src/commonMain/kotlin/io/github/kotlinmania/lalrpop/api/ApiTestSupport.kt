// port-lint: source api/test.rs (platform glue, test support)
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
// Filesystem and environment glue used by the api/test.rs port. Upstream
// uses `std::env::{tempDir, setCurrentDir, setVar}` and
// `std::fs::{exists, createDir, createDirAll, removeDirAll,
// removeFile}`. Kotlin Multiplatform commonMain has no portable
// filesystem; the actuals live alongside the production Api.kt actuals
// per-platform.
// ---------------------------------------------------------------------------

/** Mirror of `std::env::tempDir()`. Returns the absolute path to the system temp dir. */
internal expect fun apiTempDir(): String

/** Mirror of `std::env::setCurrentDir(path)`. */
internal expect fun apiSetCurrentDir(path: String)

/** Mirror of `std::env::setVar(name, value)`. */
internal expect fun apiSetEnvVar(name: String, value: String)

/** Mirror of `std::fs::exists(path).unwrap()`. */
internal expect fun apiPathExists(path: String): Boolean

/** Mirror of `std::fs::createDir(path).unwrap()`. */
internal expect fun apiCreateDir(path: String)

/** Mirror of `std::fs::removeDirAll(path).unwrap()`. */
internal expect fun apiRemoveDirAll(path: String)

/** Mirror of `std::fs::removeFile(path).unwrap()`. */
internal expect fun apiRemoveFile(path: String)

/**
 * Mirror of `path::Path::new(a).join(b)`. Portable, pure-string path
 * join: uses `/` as the separator (the upstream tests run on Unix).
 */
internal fun apiPathJoin(a: String, b: String): String =
    when {
        a.isEmpty() -> b
        a.endsWith('/') -> a + b
        else -> "$a/$b"
    }
