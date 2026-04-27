// port-lint: source src/api/test.rs (platform glue, JVM/Android target)
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

import java.io.File

internal actual fun apiTempDir(): String =
    System.getProperty("java.io.tmpdir")?.trimEnd(File.separatorChar)?.ifEmpty { "/" } ?: "/tmp"

internal actual fun apiSetCurrentDir(path: String) {
    // The JVM does not support changing the working directory. Set
    // `user.dir` so subsequent `apiCurrentDir()` calls reflect the
    // change; relative File operations on the JVM resolve against the
    // process CWD captured at startup, so the api/test.rs port should
    // import absolute paths derived from this property where possible.
    System.setProperty("user.dir", path)
}

internal actual fun apiSetEnvVar(name: String, value: String) {
    // The JVM does not expose a portable setter for the process
    // environment.  System properties are a reasonable surrogate for
    // the `OUT_DIR` import case in the api/test.rs port: production code
    // reads via `apiEnvVar`, which on JVM consults `System.getenv`.
    // We additionally publish to system properties so test fixtures
    // that need to override `OUT_DIR` have a hook.
    System.setProperty(name, value)
}

internal actual fun apiPathExists(path: String): Boolean = File(path).exists()

internal actual fun apiCreateDir(path: String) {
    if (!File(path).mkdir()) error("create_dir($path) failed")
}

internal actual fun apiRemoveDirAll(path: String) {
    File(path).deleteRecursively()
}

internal actual fun apiRemoveFile(path: String) {
    if (!File(path).delete()) error("remove_file($path) failed")
}
