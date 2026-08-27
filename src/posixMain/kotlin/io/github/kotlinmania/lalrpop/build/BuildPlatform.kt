// port-lint: source build/mod.rs (platform glue, native target)
package io.github.kotlinmania.lalrpop.build

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.S_IFDIR
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fprintf
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.stat
import platform.posix.stderr

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

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiSha3Hex(file: String): String? {
    val bytes = readFileBytes(file) ?: return null
    val digest = Sha3_256().digest(bytes)
    return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
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
                fwrite(pinned.addressOf(0), 1u.convert(), bytes.size.toUInt().convert(), f)
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
    mkdir(path, "0755".toUInt(8).convert())
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
                val n = fread(buf, 1u.convert(), 4096u.convert(), f).toInt()
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

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildPathIsSymlink(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    if (lstat(path, st.ptr) != 0) return@memScoped false
    (st.st_mode.toInt() and S_IFMT) == S_IFLNK
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
            val full = if (root.endsWith('/')) root + name else "$root/$name"
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
                val n = fread(buf, 1u.convert(), 4096u.convert(), f).toInt()
                if (n <= 0) break
                for (i in 0 until n) out.add(buf[i])
            }
        }
        return out.toByteArray()
    } finally {
        fclose(f)
    }
}

// ---------------------------------------------------------------------------
// Pure-Kotlin SHA3-256 implementation. Vendored here because the
// commonMain stdlib has no SHA3 and Kotlin/Native has no portable
// MessageDigest analogue. Based on the FIPS 202 specification of
// Keccak-f[1600] with a 256-bit output.
// ---------------------------------------------------------------------------

private class Sha3_256 {
    private val state = LongArray(25)

    fun digest(input: ByteArray): ByteArray {
        // SHA3-256: rate = 1088 bits = 136 bytes, capacity = 512 bits.
        val rate = 136
        var offset = 0
        // Absorb full blocks.
        while (offset + rate <= input.size) {
            absorbBlock(input, offset, rate)
            keccakF()
            offset += rate
        }
        // Pad final block: append 0x06 (SHA-3 domain separator + first pad bit),
        // then zeros, then 0x80 at last byte.
        val tail = ByteArray(rate)
        val remaining = input.size - offset
        for (i in 0 until remaining) tail[i] = input[offset + i]
        tail[remaining] = 0x06
        tail[rate - 1] = (tail[rate - 1].toInt() or 0x80).toByte()
        absorbBlock(tail, 0, rate)
        keccakF()

        // Squeeze 32 bytes (256 bits) out of the state.
        val out = ByteArray(32)
        for (i in 0 until 32) {
            val laneIndex = i / 8
            val byteIndex = i % 8
            out[i] = ((state[laneIndex] ushr (8 * byteIndex)) and 0xff).toByte()
        }
        return out
    }

    private fun absorbBlock(block: ByteArray, offset: Int, length: Int) {
        var i = 0
        while (i < length) {
            var lane = 0L
            for (j in 0 until 8) {
                if (i + j < length) {
                    lane = lane or ((block[offset + i + j].toLong() and 0xff) shl (8 * j))
                }
            }
            state[i / 8] = state[i / 8] xor lane
            i += 8
        }
    }

    private fun keccakF() {
        for (round in 0 until 24) {
            // Theta
            val c = LongArray(5)
            for (x in 0 until 5) {
                c[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            }
            val d = LongArray(5)
            for (x in 0 until 5) {
                d[x] = c[(x + 4) % 5] xor c[(x + 1) % 5].rotl(1)
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    state[x + 5 * y] = state[x + 5 * y] xor d[x]
                }
            }
            // Rho + Pi
            val b = LongArray(25)
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    val nx = y
                    val ny = (2 * x + 3 * y) % 5
                    b[nx + 5 * ny] = state[x + 5 * y].rotl(R[x + 5 * y])
                }
            }
            // Chi
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    state[x + 5 * y] = b[x + 5 * y] xor (b[((x + 1) % 5) + 5 * y].inv() and b[((x + 2) % 5) + 5 * y])
                }
            }
            // Iota
            state[0] = state[0] xor RC[round]
        }
    }

    private fun Long.rotl(n: Int): Long {
        val nn = n % 64
        return if (nn == 0) this else (this shl nn) or (this ushr (64 - nn))
    }

    companion object {
        private val RC = longArrayOf(
            0x0000000000000001uL.toLong(),
            0x0000000000008082uL.toLong(),
            0x800000000000808auL.toLong(),
            0x8000000080008000uL.toLong(),
            0x000000000000808buL.toLong(),
            0x0000000080000001uL.toLong(),
            0x8000000080008081uL.toLong(),
            0x8000000000008009uL.toLong(),
            0x000000000000008auL.toLong(),
            0x0000000000000088uL.toLong(),
            0x0000000080008009uL.toLong(),
            0x000000008000000auL.toLong(),
            0x000000008000808buL.toLong(),
            0x800000000000008buL.toLong(),
            0x8000000000008089uL.toLong(),
            0x8000000000008003uL.toLong(),
            0x8000000000008002uL.toLong(),
            0x8000000000000080uL.toLong(),
            0x000000000000800auL.toLong(),
            0x800000008000000auL.toLong(),
            0x8000000080008081uL.toLong(),
            0x8000000000008080uL.toLong(),
            0x0000000080000001uL.toLong(),
            0x8000000080008008uL.toLong(),
        )

        private val R = intArrayOf(
            0, 1, 62, 28, 27,
            36, 44, 6, 55, 20,
            3, 10, 43, 25, 39,
            41, 45, 15, 21, 8,
            18, 2, 61, 56, 14,
        )
    }
}
