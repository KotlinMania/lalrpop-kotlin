@file:OptIn(ExperimentalWasmJsInterop::class)

// port-lint: source build/mod.rs (platform glue, wasmJs target)
package io.github.kotlinmania.lalrpop.build

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.sha3Hex(path)")
private external fun hostSha3Hex(path: String): String?

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.readFileToString(path)")
private external fun hostReadFileToString(path: String): String

@JsFun("(path, content) => globalThis.KotlinManiaLalrpopHost.writeFileBytes(path, content)")
private external fun hostWriteFileBytes(path: String, content: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.createDirAll(path)")
private external fun hostCreateDirAll(path: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.removeFileIgnoringMissing(path)")
private external fun hostRemoveFileIgnoringMissing(path: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.pathExists(path)")
private external fun hostPathExists(path: String): Boolean

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.pathIsSymlink(path)")
private external fun hostPathIsSymlink(path: String): Boolean

@JsFun("(root) => globalThis.KotlinManiaLalrpopHost.walkDir(root)")
private external fun hostWalkDir(root: String): String

@JsFun("(message) => globalThis.KotlinManiaLalrpopHost.eprint(message)")
private external fun hostEPrint(message: String)

@JsFun("(message) => globalThis.KotlinManiaLalrpopHost.print(message)")
private external fun hostPrint(message: String)

internal actual fun pathParent(path: String): String? {
    val idx = path.lastIndexOf('/')
    return if (idx <= 0) null else path.substring(0, idx)
}

internal actual fun pathFileName(path: String): String? {
    if (path.isEmpty()) return null
    val idx = path.lastIndexOf('/')
    val name = if (idx < 0) path else path.substring(idx + 1)
    return if (name.isEmpty() || name == "..") null else name
}

internal actual fun pathExtension(path: String): String? {
    val name = pathFileName(path) ?: return null
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return null
    return name.substring(dot + 1)
}

internal actual fun pathJoin(parent: String, child: String): String =
    if (parent.endsWith('/')) parent + child else "$parent/$child"

internal actual fun pathWithExtension(path: String, ext: String): String {
    val parent = pathParent(path)
    val name = pathFileName(path) ?: path
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val newName = if (ext.isEmpty()) stem else "$stem.$ext"
    return if (parent != null) "$parent/$newName" else newName
}

internal actual fun pathStripPrefix(path: String, base: String): String? {
    val normBase = base.trimEnd('/')
    if (path == normBase) return ""
    val withSep = "$normBase/"
    return if (path.startsWith(withSep)) path.removePrefix(withSep) else null
}

internal actual fun apiSha3Hex(file: String): String? = hostSha3Hex(file)

internal actual fun apiBuildReadFileToString(path: String): String = hostReadFileToString(path)

internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    hostWriteFileBytes(path, content)
}

internal actual fun apiBuildCreateDirAll(path: String) {
    hostCreateDirAll(path)
}

internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    hostRemoveFileIgnoringMissing(path)
}

internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    if (!hostPathExists(path)) return null
    val text = hostReadFileToString(path)
    val firstNl = text.indexOf('\n')
    if (firstNl < 0) return text to ""
    val first = text.substring(0, firstNl + 1)
    val rest = text.substring(firstNl + 1)
    val secondNl = rest.indexOf('\n')
    val second = if (secondNl < 0) rest else rest.substring(0, secondNl + 1)
    return first to second
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean = hostPathIsSymlink(path)

internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> {
    if (!hostPathExists(root)) {
        return sequenceOf(
            WalkEntry.Err(
                WalkDirError("no such file: $root", root, kindIsNotFound = true),
            ),
        )
    }
    return parseWalkEntries(hostWalkDir(root)).asSequence()
}

internal actual fun apiBuildEPrintln(message: String) {
    hostEPrint("$message\n")
}

internal actual fun apiBuildEPrint(message: String) {
    hostEPrint(message)
}

internal actual fun apiBuildPrintln(message: String) {
    hostPrint("$message\n")
}

internal actual fun apiBuildPrint(message: String) {
    hostPrint(message)
}

internal actual fun apiBuildIsStdoutTerminal(): Boolean = false
internal actual fun apiBuildOpenAnsiStdout(): Appendable? = null

private fun parseWalkEntries(json: String): List<WalkEntry> {
    val array = Json.parseToJsonElement(json) as JsonArray
    return array.map { element ->
        val obj = element.jsonObject
        when (obj.requiredString("kind")) {
            "ok" -> WalkEntry.Ok(
                path = obj.requiredString("path"),
                isFile = obj.requiredBoolean("isFile"),
            )
            "err" -> WalkEntry.Err(
                WalkDirError(
                    message = obj.requiredString("message"),
                    path = obj.requiredString("path"),
                    kindIsNotFound = obj["kindIsNotFound"]?.jsonPrimitive?.boolean ?: false,
                ),
            )
            else -> error("unknown walk entry kind")
        }
    }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content ?: error("missing walk entry field: $name")

private fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.boolean ?: error("missing walk entry field: $name")
