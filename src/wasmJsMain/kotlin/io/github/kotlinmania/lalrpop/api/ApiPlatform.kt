@file:OptIn(ExperimentalWasmJsInterop::class)

// port-lint: source api/mod.rs (platform glue, wasmJs target)
package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile
import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@JsFun("() => globalThis.KotlinManiaLalrpopHost.currentDir()")
private external fun hostCurrentDir(): String

@JsFun("(name) => globalThis.KotlinManiaLalrpopHost.envVar(name)")
private external fun hostEnvVar(name: String): String?

@JsFun("() => globalThis.KotlinManiaLalrpopHost.envVars()")
private external fun hostEnvVars(): String

@JsFun("(message) => globalThis.KotlinManiaLalrpopHost.eprint(message)")
private external fun hostEPrint(message: String)

internal actual fun apiCurrentDir(): String = hostCurrentDir()

internal actual fun apiEnvVar(name: String): String? = hostEnvVar(name)

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val obj = Json.parseToJsonElement(hostEnvVars()).jsonObject
    return obj.asSequence().mapNotNull { (name, value) ->
        val stringValue = value.jsonPrimitive.content
        if (stringValue.isNotEmpty()) name to stringValue else null
    }
}

internal actual fun apiEPrintln(message: String) {
    hostEPrint("$message\n")
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    processDir(session, path)
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    processFile(session, path)
}
