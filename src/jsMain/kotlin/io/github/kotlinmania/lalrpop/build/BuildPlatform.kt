package io.github.kotlinmania.lalrpop.build

// Hiding require('fs') and require('crypto') from webpack via (new Function('return require'))()
// per AGENTS.md §10 to prevent webpack bundling failures in jsBrowserTest.
private fun nodeFs(): dynamic = js(
    "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); return rq ? rq('fs') : null; } catch (e) { return null; } })()",
)

private fun nodeCrypto(): dynamic = js(
    "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); return rq ? rq('crypto') : null; } catch (e) { return null; } })()",
)

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
    val fs = nodeFs() ?: return null
    val crypto = nodeCrypto() ?: return null
    if (!(fs.existsSync(file) as Boolean)) return null
    return crypto.createHash("sha3-256")
        .update(fs.readFileSync(file))
        .digest("hex") as String
}

internal actual fun apiBuildReadFileToString(path: String): String {
    val fs = nodeFs() ?: error("node:fs not available in this environment")
    return fs.readFileSync(path).toString()
}

internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    val fs = nodeFs() ?: error("node:fs not available in this environment")
    fs.writeFileSync(path, content)
}

internal actual fun apiBuildCreateDirAll(path: String) {
    if (path.isNotEmpty()) {
        val fs = nodeFs() ?: return
        fs.mkdirSync(path, js("({ recursive: true })"))
    }
}

internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    val fs = nodeFs() ?: return
    if (fs.existsSync(path) as Boolean) {
        fs.unlinkSync(path)
    }
}

internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    val fs = nodeFs() ?: return null
    if (!(fs.existsSync(path) as Boolean)) return null
    val text = apiBuildReadFileToString(path)
    val firstNl = text.indexOf('\n')
    if (firstNl < 0) return text to ""
    val first = text.substring(0, firstNl + 1)
    val rest = text.substring(firstNl + 1)
    val secondNl = rest.indexOf('\n')
    val second = if (secondNl < 0) rest else rest.substring(0, secondNl + 1)
    return first to second
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean {
    val fs = nodeFs() ?: return false
    return (fs.existsSync(path) as Boolean) && (fs.lstatSync(path).isSymbolicLink() as Boolean)
}

internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> = sequence {
    val fs = nodeFs()
    if (fs == null || !(fs.existsSync(root) as Boolean)) {
        yield(WalkEntry.Err(WalkDirError("no such file: $root", root, kindIsNotFound = true)))
        return@sequence
    }
    walkDir(root, fs).forEach { yield(it) }
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

private fun walkDir(path: String, fs: dynamic): List<WalkEntry> {
    val stat = fs.statSync(path)
    val isFile = stat.isFile() as Boolean
    if (isFile) return listOf(WalkEntry.Ok(path, isFile = true))

    val entries = mutableListOf<WalkEntry>(WalkEntry.Ok(path, isFile = false))
    val names = (fs.readdirSync(path) as Array<String>).sorted()
    for (name in names) {
        entries.addAll(walkDir(pathJoin(path, name), fs))
    }
    return entries
}
