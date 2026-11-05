@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile
import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

@JsFun("() => globalThis.KotlinManiaLalrpopHost.currentDir()")
private external fun hostCurrentDir(): String

@JsFun("(name) => globalThis.KotlinManiaLalrpopHost.envVar(name)")
private external fun hostEnvVar(name: String): String?

@JsFun("() => Object.entries(JSON.parse(globalThis.KotlinManiaLalrpopHost.envVars()))")
private external fun hostEnvEntries(): JsAny

@JsFun("(entries) => entries.length")
private external fun hostEntriesLength(entries: JsAny): Int

@JsFun("(entries, i) => String(entries[i][0])")
private external fun hostEntryKey(entries: JsAny, i: Int): String

@JsFun("(entries, i) => String(entries[i][1] ?? '')")
private external fun hostEntryValue(entries: JsAny, i: Int): String

@JsFun("(message) => globalThis.KotlinManiaLalrpopHost.eprint(message)")
private external fun hostEPrint(message: String)

internal actual fun apiCurrentDir(): String = hostCurrentDir()

internal actual fun apiEnvVar(name: String): String? = hostEnvVar(name)

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val entries = hostEnvEntries()
    val count = hostEntriesLength(entries)
    val pairs = mutableListOf<Pair<String, String>>()
    for (i in 0 until count) {
        val value = hostEntryValue(entries, i)
        if (value.isNotEmpty()) {
            pairs.add(hostEntryKey(entries, i) to value)
        }
    }
    return pairs.asSequence()
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
