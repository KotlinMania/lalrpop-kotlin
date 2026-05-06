// port-lint: source api/mod.rs (platform glue, JS target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile

@JsModule("node:process")
@JsNonModule
private external object NodeProcess {
    val env: dynamic
    fun cwd(): String
}

internal actual fun apiCurrentDir(): String = NodeProcess.cwd()

internal actual fun apiEnvVar(name: String): String? =
    NodeProcess.env[name] as String?

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val names = js("Object.keys(process.env)") as Array<String>
    return names.asSequence().mapNotNull { name ->
        val value = NodeProcess.env[name] as String?
        if (value != null) name to value else null
    }
}

internal actual fun apiEPrintln(message: String) {
    console.error(message)
}

internal actual fun apiBuildProcessDir(
    session: Session,
    path: String,
) {
    processDir(session, path)
}

internal actual fun apiBuildProcessFile(
    session: Session,
    path: String,
) {
    processFile(session, path)
}
