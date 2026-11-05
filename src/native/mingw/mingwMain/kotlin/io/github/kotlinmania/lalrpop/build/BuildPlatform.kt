package io.github.kotlinmania.lalrpop.build

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fprintf
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.stat
import platform.posix.stderr
import platform.windows.CreateDirectoryA
import platform.windows.FILE_ATTRIBUTE_REPARSE_POINT
import platform.windows.GetFileAttributesA
import platform.windows.INVALID_FILE_ATTRIBUTES

private fun lastSepIndex(path: String): Int =
    maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))

internal actual fun pathParent(path: String): String? {
    val idx = lastSepIndex(path)
    return if (idx <= 0) null else path.substring(0, idx)
}

internal actual fun pathFileName(path: String): String? {
    if (path.isEmpty()) return null
    val idx = lastSepIndex(path)
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
    if (parent.endsWith('/') || parent.endsWith('\\')) parent + child else "$parent/$child"

internal actual fun pathWithExtension(path: String, ext: String): String {
    val parent = pathParent(path)
    val name = pathFileName(path) ?: path
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val newName = if (ext.isEmpty()) stem else "$stem.$ext"
    return if (parent != null) "$parent/$newName" else newName
}

internal actual fun pathStripPrefix(path: String, base: String): String? {
    val normPath = path.replace('\\', '/').trimEnd('/')
    val normBase = base.replace('\\', '/').trimEnd('/')
    if (normPath == normBase) return ""
    val withSep = "$normBase/"
    return if (normPath.startsWith(withSep)) normPath.removePrefix(withSep) else null
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiSha3Hex(file: String): String? {
    val bytes = readFileBytes(file) ?: return null
    return Sha3256().digestHex(bytes)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildReadFileToString(path: String): String {
    val bytes = readFileBytes(path) ?: error("could not open $path")
    return bytes.decodeToString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildWriteFileBytes(path: String, content: String) {
    val f = fopen(path, "wb") ?: error("could not create $path")
    try {
        val bytes = content.encodeToByteArray()
        if (bytes.isNotEmpty()) {
            val pinned = bytes.pin()
            try {
                fwrite(pinned.addressOf(0), 1.toULong(), bytes.size.toULong(), f)
            } finally {
                pinned.unpin()
            }
        }
    } finally {
        fclose(f)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildCreateDirAll(path: String) {
    if (path.isEmpty() || isDirectory(path)) return
    pathParent(path)?.let { apiBuildCreateDirAll(it) }
    CreateDirectoryA(path, null)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildRemoveFileIgnoringMissing(path: String) {
    // Best-effort: posix `remove` returns 0 on success. We ignore failure
    // here because upstream `removeOldFile` already swallows
    // NotFound and PermissionDenied.
    remove(path)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>? {
    val f = fopen(path, "rb") ?: return null
    try {
        val sb = StringBuilder()
        memScoped {
            val buf = allocArray<ByteVar>(4096)
            // We only need the first two lines, so read enough then split.
            var newlines = 0
            while (newlines < 2) {
                val n = fread(buf, 1.toULong(), 4096.toULong(), f).toInt()
                if (n <= 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt()
                    sb.append(b.toChar())
                    if (b == '\n'.code) {
                        newlines++
                        if (newlines >= 2) break
                    }
                }
            }
        }
        val text = sb.toString()
        val firstNl = text.indexOf('\n')
        if (firstNl < 0) return Pair(text, "")
        val first = text.substring(0, firstNl + 1)
        val rest = text.substring(firstNl + 1)
        val secondNl = rest.indexOf('\n')
        val second = if (secondNl < 0) rest else rest.substring(0, secondNl + 1)
        return Pair(first, second)
    } finally {
        fclose(f)
    }
}

internal actual fun apiBuildPathIsSymlink(path: String): Boolean {
    val attrs = GetFileAttributesA(path)
    return attrs != INVALID_FILE_ATTRIBUTES && (attrs and FILE_ATTRIBUTE_REPARSE_POINT.toUInt()) != 0u
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildWalkDir(root: String): Sequence<WalkEntry> {
    if (!exists(root)) {
        return sequenceOf(
            WalkEntry.Err(
                WalkDirError(
                    message = "no such file: $root",
                    path = root,
                    kindIsNotFound = true,
                ),
            ),
        )
    }
    val collected = mutableListOf<WalkEntry>()
    walkDirInto(root, collected)
    return collected.asSequence()
}

@OptIn(ExperimentalForeignApi::class)
private fun walkDirInto(root: String, out: MutableList<WalkEntry>) {
    if (isDirectory(root)) {
        out.add(WalkEntry.Ok(path = root, isFile = false))
        val dir = opendir(root) ?: return
        val children = mutableListOf<String>()
        try {
            while (true) {
                val ent = readdir(dir) ?: break
                val name = ent.pointed.d_name.reinterpret<ByteVar>().toKString()
                if (name == "." || name == "..") continue
                children.add(name)
            }
        } finally {
            closedir(dir)
        }
        children.sort()
        for (name in children) {
            val full = if (root.endsWith('/') || root.endsWith('\\')) root + name else "$root/$name"
            walkDirInto(full, out)
        }
    } else {
        out.add(WalkEntry.Ok(path = root, isFile = true))
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildEPrintln(message: String) {
    fprintf(stderr, "%s\n", message)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildEPrint(message: String) {
    fprintf(stderr, "%s", message)
}

internal actual fun apiBuildPrintln(message: String) {
    println(message)
}

internal actual fun apiBuildPrint(message: String) {
    print(message)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildIsStdoutTerminal(): Boolean {
    return platform.posix.isatty(platform.posix.STDOUT_FILENO) != 0
}

internal actual fun apiBuildOpenAnsiStdout(): Appendable? {
    // No terminfo binding in commonMain/native; always fall through to FakeTerminal.
    return null
}

// ---------------------------------------------------------------------------
// Local helpers.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private fun isDirectory(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    if (stat(path, st.ptr) != 0) return@memScoped false
    (st.st_mode.toInt() and S_IFMT) == S_IFDIR
}

@OptIn(ExperimentalForeignApi::class)
private fun exists(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    stat(path, st.ptr) == 0
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileBytes(path: String): ByteArray? {
    val f = fopen(path, "rb") ?: return null
    try {
        val out = mutableListOf<Byte>()
        memScoped {
            val buf = allocArray<ByteVar>(4096)
            while (true) {
                val n = fread(buf, 1.toULong(), 4096.toULong(), f).toInt()
                if (n <= 0) break
                for (i in 0 until n) out.add(buf[i])
            }
        }
        return out.toByteArray()
    } finally {
        fclose(f)
    }
}
