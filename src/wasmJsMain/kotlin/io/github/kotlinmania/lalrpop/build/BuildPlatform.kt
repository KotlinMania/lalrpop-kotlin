// port-lint: source build/mod.rs (platform glue, wasmJs target)
package io.github.kotlinmania.lalrpop.build

private fun nope(): Nothing =
    throw UnsupportedOperationException("filesystem access is not available on the wasmJs target")

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

internal actual fun apiSha3Hex(file: String): String? = nope()
internal actual fun apiBuildReadFileToString(path: String): String = nope()
internal actual fun apiBuildWriteFileBytes(path: String, content: String): Unit = nope()
internal actual fun apiBuildCreateDirAll(path: String): Unit = nope()
internal actual fun apiBuildRemoveFileIgnoringMissing(path: String): Unit = nope()
internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? = nope()
internal actual fun apiBuildPathIsSymlink(path: String): Boolean = nope()
internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> = nope()

internal actual fun apiBuildEPrintln(message: String) { /* no console available in wasmJs */ }
internal actual fun apiBuildEPrint(message: String) { /* no console available in wasmJs */ }
internal actual fun apiBuildPrintln(message: String) { println(message) }
internal actual fun apiBuildPrint(message: String) { print(message) }
internal actual fun apiBuildIsStdoutTerminal(): Boolean = false
internal actual fun apiBuildOpenAnsiStdout(): Appendable? = null
