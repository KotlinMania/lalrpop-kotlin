// port-lint: source api/mod.rs (platform glue, wasmJs target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session

// wasmJs (browser/node) has no portable filesystem in the lalrpop
// build-script sense. The API entry points are intended for
// build-script use; on wasmJs they raise UnsupportedOperationException.

internal actual fun apiCurrentDir(): String =
    throw UnsupportedOperationException("filesystem access is not available on the wasmJs target")

internal actual fun apiEnvVar(name: String): String? = null

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> = emptySequence()

internal actual fun apiEPrintln(message: String) {
    println(message)
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    throw UnsupportedOperationException("filesystem access is not available on the wasmJs target")
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    throw UnsupportedOperationException("filesystem access is not available on the wasmJs target")
}
