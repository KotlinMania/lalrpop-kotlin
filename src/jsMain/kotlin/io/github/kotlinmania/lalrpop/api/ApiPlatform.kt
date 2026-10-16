// port-lint: source api/mod.rs (platform glue, JS target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile

// Hiding require('process') from webpack via (new Function('return require'))()
// per AGENTS.md §10 to prevent webpack bundling failures in jsBrowserTest.
private fun nodeProcess(): dynamic =
    js(
        "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); if (rq) return rq('process'); if (typeof process !== 'undefined') return process; return null; } catch (e) { return null; } })()",
    )

internal actual fun apiCurrentDir(): String {
    val proc = nodeProcess()
    return if (proc != null && proc.cwd != null) (proc.cwd() as String) else "/"
}

internal actual fun apiEnvVar(name: String): String? {
    val proc = nodeProcess() ?: return null
    val env = proc.env ?: return null
    return env[name] as String?
}

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val proc = nodeProcess() ?: return emptySequence()
    val env = proc.env ?: return emptySequence()
    val names = js("Object.keys(env)") as Array<String>
    return names.asSequence().mapNotNull { name ->
        val value = env[name] as String?
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
