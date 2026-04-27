// port-lint: source src/api/test.rs (platform glue, JS target)
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

private fun unsupported(): Nothing =
    throw UnsupportedOperationException("filesystem access is not available on the JS target")

internal actual fun apiTempDir(): String = unsupported()
internal actual fun apiSetCurrentDir(path: String): Unit = unsupported()
internal actual fun apiSetEnvVar(name: String, value: String): Unit = unsupported()
internal actual fun apiPathExists(path: String): Boolean = unsupported()
internal actual fun apiCreateDir(path: String): Unit = unsupported()
internal actual fun apiRemoveDirAll(path: String): Unit = unsupported()
internal actual fun apiRemoveFile(path: String): Unit = unsupported()
