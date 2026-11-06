@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile
import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

@JsFun(
    """() => {
        const host = globalThis.KotlinManiaLalrpopHost;
        if (host && typeof host.currentDir === 'function') return host.currentDir();
        const proc = globalThis.process;
        if (proc && typeof proc.cwd === 'function') return proc.cwd();
        return '/';
    }""",
)
private external fun hostCurrentDir(): String

@JsFun(
    """(name) => {
        const host = globalThis.KotlinManiaLalrpopHost;
        if (host && typeof host.envVar === 'function') return host.envVar(name);
        const proc = globalThis.process;
        if (proc && proc.env && Object.prototype.hasOwnProperty.call(proc.env, name)) {
            const v = proc.env[name];
            return v === undefined ? null : String(v);
        }
        return null;
    }""",
)
private external fun hostEnvVar(name: String): String?

@JsFun(
    """() => {
        const host = globalThis.KotlinManiaLalrpopHost;
        if (host && typeof host.envVars === 'function') return Object.entries(JSON.parse(host.envVars()));
        const proc = globalThis.process;
        if (proc && proc.env) return Object.entries(proc.env);
        return [];
    }""",
)
private external fun hostEnvEntries(): JsAny

@JsFun("(entries) => entries.length")
private external fun hostEntriesLength(entries: JsAny): Int

@JsFun("(entries, i) => String(entries[i][0])")
private external fun hostEntryKey(entries: JsAny, i: Int): String

@JsFun("(entries, i) => String(entries[i][1] ?? '')")
private external fun hostEntryValue(entries: JsAny, i: Int): String

@JsFun(
    """(message) => {
        const host = globalThis.KotlinManiaLalrpopHost;
        if (host && typeof host.eprint === 'function') { host.eprint(message); return; }
        const proc = globalThis.process;
        if (proc && proc.stderr && typeof proc.stderr.write === 'function') { proc.stderr.write(message); return; }
        console.error(message.endsWith('\n') ? message.slice(0, -1) : message);
    }""",
)
private external fun hostEPrint(message: String)

internal val wasmJsEnvOverrides: MutableMap<String, String> = mutableMapOf()

internal actual fun apiCurrentDir(): String = hostCurrentDir()

internal actual fun apiEnvVar(name: String): String? = wasmJsEnvOverrides[name] ?: hostEnvVar(name)

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val merged = LinkedHashMap<String, String>()
    val entries = hostEnvEntries()
    val count = hostEntriesLength(entries)
    for (i in 0 until count) {
        val value = hostEntryValue(entries, i)
        if (value.isNotEmpty()) {
            merged[hostEntryKey(entries, i)] = value
        }
    }
    for ((k, v) in wasmJsEnvOverrides) {
        if (v.isNotEmpty()) {
            merged[k] = v
        }
    }
    return merged.entries.asSequence().map { (k, v) -> k to v }
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
