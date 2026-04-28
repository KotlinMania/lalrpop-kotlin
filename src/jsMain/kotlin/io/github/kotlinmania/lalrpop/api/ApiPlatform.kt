// port-lint: source api/mod.rs (platform glue, JS target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session

// JS does not have a portable filesystem in the lalrpop build-script
// sense (the API entry points are intended for `build.rs` scripts that
// scan a Cargo workspace). Surface this as `UnsupportedOperationException`
// so callers get a clear failure rather than a silent no-op.

internal actual fun apiCurrentDir(): String =
    throw UnsupportedOperationException("filesystem access is not available on the JS target")

internal actual fun apiEnvVar(name: String): String? = null

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> = emptySequence()

internal actual fun apiEPrintln(message: String) {
    console.error(message)
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    throw UnsupportedOperationException("filesystem access is not available on the JS target")
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    throw UnsupportedOperationException("filesystem access is not available on the JS target")
}
