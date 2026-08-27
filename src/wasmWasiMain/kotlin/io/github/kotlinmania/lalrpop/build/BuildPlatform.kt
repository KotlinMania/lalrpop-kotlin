// port-lint: source build/mod.rs (platform glue, wasmWasi target)
package io.github.kotlinmania.lalrpop.build

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
    if (parent.isEmpty() || parent == ".") child else if (child.isEmpty()) parent else "${parent.trimEnd('/')}/$child"

internal actual fun pathWithExtension(path: String, ext: String): String {
    val dot = path.lastIndexOf('.')
    val base = if (dot > path.lastIndexOf('/')) path.substring(0, dot) else path
    return if (ext.isEmpty()) base else "$base.$ext"
}

internal actual fun pathStripPrefix(path: String, base: String): String? {
    val normalizedPath = path.trimEnd('/')
    val normalizedBase = base.trimEnd('/')
    if (normalizedBase == "." || normalizedBase.isEmpty()) return normalizedPath.trimStart('/')
    if (normalizedPath == normalizedBase) return ""
    val prefix = "$normalizedBase/"
    return if (normalizedPath.startsWith(prefix)) normalizedPath.removePrefix(prefix) else null
}

internal actual fun apiSha3Hex(file: String): String? = null
internal actual fun apiBuildReadFileToString(path: String): String = ""
internal actual fun apiBuildWriteFileBytes(path: String, content: String) {}
internal actual fun apiBuildCreateDirAll(path: String) {}
internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {}
internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? = null
internal actual fun apiBuildPathIsSymlink(path: String): Boolean = false
internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> = emptySequence()
internal actual fun apiBuildEPrintln(message: String) { println(message) }
internal actual fun apiBuildEPrint(message: String) { print(message) }
internal actual fun apiBuildPrintln(message: String) { println(message) }
internal actual fun apiBuildPrint(message: String) { print(message) }
internal actual fun apiBuildIsStdoutTerminal(): Boolean = false
internal actual fun apiBuildOpenAnsiStdout(): Appendable? = null
