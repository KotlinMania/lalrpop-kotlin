package io.github.kotlinmania.lalrpop.build

@JsModule("node:fs")
@JsNonModule
private external object NodeFs {
    fun readFileSync(path: String): dynamic
    fun writeFileSync(path: String, content: String)
    fun mkdirSync(path: String, options: dynamic = definedExternally)
    fun unlinkSync(path: String)
    fun existsSync(path: String): Boolean
    fun lstatSync(path: String): dynamic
    fun statSync(path: String): dynamic
    fun readdirSync(path: String): dynamic
}

@JsModule("node:crypto")
@JsNonModule
private external object NodeCrypto {
    fun createHash(algorithm: String): dynamic
}

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

internal actual fun apiSha3Hex(file: String): String? {
    if (!NodeFs.existsSync(file)) return null
    return NodeCrypto.createHash("sha3-256")
        .update(NodeFs.readFileSync(file))
        .digest("hex") as String
}

internal actual fun apiBuildReadFileToString(path: String): String =
    NodeFs.readFileSync(path).toString()

internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    NodeFs.writeFileSync(path, content)
}

internal actual fun apiBuildCreateDirAll(path: String) {
    if (path.isNotEmpty()) {
        NodeFs.mkdirSync(path, js("({ recursive: true })"))
    }
}

internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    if (NodeFs.existsSync(path)) {
        NodeFs.unlinkSync(path)
    }
}

internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    if (!NodeFs.existsSync(path)) return null
    val text = apiBuildReadFileToString(path)
    val firstNl = text.indexOf('\n')
    if (firstNl < 0) return text to ""
    val first = text.substring(0, firstNl + 1)
    val rest = text.substring(firstNl + 1)
    val secondNl = rest.indexOf('\n')
    val second = if (secondNl < 0) rest else rest.substring(0, secondNl + 1)
    return first to second
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean =
    NodeFs.existsSync(path) && (NodeFs.lstatSync(path).isSymbolicLink() as Boolean)

internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> = sequence {
    if (!NodeFs.existsSync(root)) {
        yield(WalkEntry.Err(WalkDirError("no such file: $root", root, kindIsNotFound = true)))
        return@sequence
    }
    walkDir(root).forEach { yield(it) }
}

internal actual fun apiBuildEPrintln(message: String) {
    console.error(message)
}

internal actual fun apiBuildEPrint(message: String) {
    console.error(message)
}

internal actual fun apiBuildPrintln(message: String) {
    console.log(message)
}

internal actual fun apiBuildPrint(message: String) {
    console.log(message)
}

internal actual fun apiBuildIsStdoutTerminal(): Boolean = false

internal actual fun apiBuildOpenAnsiStdout(): Appendable? = null

private fun walkDir(path: String): List<WalkEntry> {
    val stat = NodeFs.statSync(path)
    val isFile = stat.isFile() as Boolean
    if (isFile) return listOf(WalkEntry.Ok(path, isFile = true))

    val entries = mutableListOf<WalkEntry>(WalkEntry.Ok(path, isFile = false))
    val names = (NodeFs.readdirSync(path) as Array<String>).sorted()
    for (name in names) {
        entries.addAll(walkDir(pathJoin(path, name)))
    }
    return entries
}
