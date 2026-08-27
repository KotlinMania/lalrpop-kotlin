// port-lint: source build/mod.rs (platform glue, JVM/Android target)
package io.github.kotlinmania.lalrpop.build

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.security.MessageDigest

internal actual fun pathParent(path: String): String? {
    val parent = File(path).parent ?: return null
    return parent
}

internal actual fun pathFileName(path: String): String? {
    val name = File(path).name
    return if (name.isEmpty() || name == "..") null else name
}

internal actual fun pathExtension(path: String): String? {
    val name = File(path).name
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return null
    return name.substring(dot + 1)
}

internal actual fun pathJoin(parent: String, child: String): String =
    File(parent, child).path

internal actual fun pathWithExtension(path: String, ext: String): String {
    val f = File(path)
    val parent = f.parent
    val baseName = f.name
    val dot = baseName.lastIndexOf('.')
    val stem = if (dot > 0) baseName.substring(0, dot) else baseName
    val newName = if (ext.isEmpty()) stem else "$stem.$ext"
    return if (parent != null) File(parent, newName).path else newName
}

internal actual fun pathStripPrefix(path: String, base: String): String? {
    val pathFile = File(path).normalize()
    val baseFile = File(base).normalize()
    val rel = try {
        baseFile.toPath().toAbsolutePath().relativize(pathFile.toPath().toAbsolutePath()).toString()
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (rel.startsWith("..")) return null
    return rel
}

internal actual fun apiSha3Hex(file: String): String? {
    return try {
        val md = MessageDigest.getInstance("SHA3-256")
        val bytes = File(file).readBytes()
        md.update(bytes)
        md.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    } catch (_: IOException) {
        null
    }
}

internal actual fun apiBuildReadFileToString(path: String): String =
    File(path).readText()

internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    File(path).writeText(content)
}

internal actual fun apiBuildCreateDirAll(path: String) {
    File(path).mkdirs()
}

internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    val f = File(path)
    try {
        Files.delete(f.toPath())
    } catch (_: NoSuchFileException) {
        // Unix reports NotFound, Windows PermissionDenied!
    } catch (_: java.nio.file.AccessDeniedException) {
        // PermissionDenied
    } catch (_: IOException) {
        // Other IO: rethrow as in upstream
        throw IOException("could not remove $path")
    }
}

internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    val f = File(path)
    if (!f.exists()) return null
    return f.bufferedReader().use { reader ->
        val v = reader.readLine() ?: ""
        val h = reader.readLine() ?: ""
        // Mirror BufRead::readLine which retains the trailing '\n'.
        Pair(v + "\n", h + "\n")
    }
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean =
    Files.isSymbolicLink(Paths.get(path))

internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> = sequence {
    val rootFile = File(root)
    if (!rootFile.exists()) {
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
    // walkdir followLinks(true) + sortByFileName(): DFS with sorted children.
    val stack = ArrayDeque<File>()
    stack.addLast(rootFile)
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        if (cur.isDirectory) {
            yield(WalkEntry.Ok(path = cur.path, isFile = false))
            val children = cur.listFiles()?.sortedBy { it.name } ?: emptyList()
            // Push in reverse so traversal yields in sorted order.
            for (c in children.asReversed()) stack.addLast(c)
        } else {
            // isFile follows symlinks per upstream comment.
            val isFile = cur.isFile
            yield(WalkEntry.Ok(path = cur.path, isFile = isFile))
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

internal actual fun apiBuildIsStdoutTerminal(): Boolean {
    // Java 17+ provides System.console() != null; rough but matches the
    // upstream `isTerminal` import case for the Kotlin port.
    return System.console() != null
}

internal actual fun apiBuildOpenAnsiStdout(): Appendable? {
    // Upstream `term::stdout()` returns a terminfo-backed terminal. The
    // Kotlin port has no terminfo dependency, so we always fall back to
    // the FakeTerminal path the way upstream does on non-terminfo
    // platforms.
    return null
}
