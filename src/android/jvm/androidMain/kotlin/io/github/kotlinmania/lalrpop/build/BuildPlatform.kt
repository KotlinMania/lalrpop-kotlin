package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.files.SystemPathSeparator
import io.github.kotlinmania.io.files.isDirectory
import io.github.kotlinmania.io.files.readBytes
import io.github.kotlinmania.io.files.readString
import io.github.kotlinmania.io.files.relativize
import io.github.kotlinmania.io.files.writeString
import io.github.kotlinmania.io.readLine

internal actual fun pathParent(path: String): String? =
    Path(path).parent?.toString()

internal actual fun pathFileName(path: String): String? {
    val name = Path(path).name
    return if (name.isEmpty() || name == "..") null else name
}

internal actual fun pathExtension(path: String): String? {
    val name = Path(path).name
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return null
    return name.substring(dot + 1)
}

internal actual fun pathJoin(parent: String, child: String): String =
    Path(parent, child).toString()

internal actual fun pathWithExtension(path: String, ext: String): String {
    val p = Path(path)
    val parent = p.parent
    val baseName = p.name
    val dot = baseName.lastIndexOf('.')
    val stem = if (dot > 0) baseName.substring(0, dot) else baseName
    val newName = if (ext.isEmpty()) stem else "$stem.$ext"
    return if (parent != null) Path(parent, newName).toString() else newName
}

internal actual fun pathStripPrefix(path: String, base: String): String? {
    val rel = Path(base).relativize(Path(path)).toString()
    if (rel == ".") return ""
    if (rel == ".." || rel.startsWith("..$SystemPathSeparator") || rel.startsWith("../")) {
        return null
    }
    return rel
}

internal actual fun apiSha3Hex(file: String): String? {
    val p = Path(file)
    if (SystemFileSystem.metadataOrNull(p)?.isRegularFile != true) return null
    return Sha3256().digestHex(SystemFileSystem.readBytes(p))
}

internal actual fun apiBuildReadFileToString(path: String): String =
    SystemFileSystem.readString(Path(path))

internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    SystemFileSystem.writeString(Path(path), content)
}

internal actual fun apiBuildCreateDirAll(path: String) {
    if (path.isNotEmpty()) {
        SystemFileSystem.createDirectories(Path(path), mustCreate = false)
    }
}

internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    SystemFileSystem.delete(Path(path), mustExist = false)
}

internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    val p = Path(path)
    if (SystemFileSystem.metadataOrNull(p)?.isRegularFile != true) return null
    val raw = SystemFileSystem.source(p)
    try {
        val reader = raw.buffered()
        val v = reader.readLine().orEmpty()
        val h = reader.readLine().orEmpty()
        return Pair("$v\n", "$h\n")
    } finally {
        raw.close()
    }
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean {
    val p = Path(path)
    val parent = p.parent ?: return false
    if (!SystemFileSystem.isDirectory(parent)) return false
    val listedInParent = SystemFileSystem.list(parent).any { it.name == p.name }
    if (!listedInParent) return false
    if (!SystemFileSystem.exists(p)) return true
    val canonicalParent = SystemFileSystem.resolve(parent)
    val canonicalTarget = SystemFileSystem.resolve(p)
    return canonicalTarget != Path(canonicalParent, p.name)
}

internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> =
    sequence {
        val rootPath = Path(root)
        if (!SystemFileSystem.exists(rootPath)) {
            yield(
                WalkEntry.Err(
                    WalkDirError(
                        message = "no such file: $root",
                        path = root,
                        kindIsNotFound = true,
                    ),
                ),
            )
            return@sequence
        }
        val stack = ArrayDeque<Path>()
        stack.addLast(rootPath)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val meta = SystemFileSystem.metadataOrNull(cur)
            if (meta == null) {
                yield(
                    WalkEntry.Err(
                        WalkDirError(
                            message = "no such file: $cur",
                            path = cur.toString(),
                            kindIsNotFound = true,
                        ),
                    ),
                )
            } else if (meta.isDirectory) {
                yield(WalkEntry.Ok(path = cur.toString(), isFile = false))
                val children = SystemFileSystem.list(cur).sortedBy { it.name }
                for (c in children.asReversed()) {
                    stack.addLast(c)
                }
            } else {
                yield(WalkEntry.Ok(path = cur.toString(), isFile = meta.isRegularFile))
            }
        }
    }

internal actual fun apiBuildEPrintln(message: String) {
    System.err.println(message)
}

internal actual fun apiBuildEPrint(message: String) {
    System.err.print(message)
}

internal actual fun apiBuildPrintln(message: String) {
    println(message)
}

internal actual fun apiBuildPrint(message: String) {
    print(message)
}

internal actual fun apiBuildIsStdoutTerminal(): Boolean =
    System.console() != null

internal actual fun apiBuildOpenAnsiStdout(): Appendable? = null
