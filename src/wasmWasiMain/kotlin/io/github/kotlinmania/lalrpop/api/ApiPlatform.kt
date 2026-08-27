// port-lint: source api/mod.rs (platform glue, wasmWasi target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile

internal actual fun apiCurrentDir(): String = "."
internal actual fun apiEnvVar(name: String): String? = null
internal actual fun apiEnvVars(): Sequence<Pair<String, String>> = emptySequence()
internal actual fun apiEPrintln(message: String) {
    println(message)
}
internal actual fun apiBuildProcessDir(session: Session, path: String) {
    processDir(session, path)
}
internal actual fun apiBuildProcessFile(session: Session, path: String) {
    processFile(session, path)
}
