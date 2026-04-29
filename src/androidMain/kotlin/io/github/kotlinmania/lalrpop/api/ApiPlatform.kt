// port-lint: source api/mod.rs (platform glue, JVM/Android target)
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

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile

internal actual fun apiCurrentDir(): String = System.getProperty("user.dir") ?: "."

// On the JVM, the process environment is read-only at runtime; tests
// override variables (e.g. `OUT_DIR`) via system properties because
// `setenv` is not portable here. Consult system properties first so
// `apiSetEnvVar` (test support) round-trips through `apiEnvVar`.
internal actual fun apiEnvVar(name: String): String? =
    System.getProperty(name) ?: System.getenv(name)

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> =
    System.getenv().entries.asSequence().map { (k, v) -> k to v }

internal actual fun apiEPrintln(message: String) {
    System.err.println(message)
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    processDir(session, path)
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    processFile(session, path)
}
