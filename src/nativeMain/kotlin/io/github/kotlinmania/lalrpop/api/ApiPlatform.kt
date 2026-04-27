// port-lint: source src/api/mod.rs (platform glue, native target)
package io.github.kotlinmania.lalrpop.api

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.compileGrammarToString
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
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.fprintf
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.pclose
import platform.posix.popen
import platform.posix.readdir
import platform.posix.stat
import platform.posix.stderr

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiCurrentDir(): String = memScoped {
    val buf = allocArray<ByteVar>(PATH_MAX + 1)
    val res = getcwd(buf, (PATH_MAX + 1).toULong())
        ?: error("getcwd failed")
    res.toKString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEnvVar(name: String): String? {
    val raw = getenv(name) ?: return null
    return raw.toKString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val f = popen("env", "r") ?: return emptySequence()
    val output = StringBuilder()
    try {
        memScoped {
            val buf = allocArray<ByteVar>(4096)
            while (true) {
                val n = platform.posix.fread(buf, 1.toULong(), 4096.toULong(), f).toInt()
                if (n <= 0) break
                for (i in 0 until n) {
                    output.append((buf[i].toInt() and 0xff).toChar())
                }
            }
        }
    } finally {
        pclose(f)
    }
    return output.toString().lineSequence().mapNotNull { line ->
        val eq = line.indexOf('=')
        if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEPrintln(message: String) {
    fprintf(stderr, "%s\n", message)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildProcessDir(session: Session, path: String) {
    // Mirror upstream `build::processDir`: walk `path` recursively for
    // `.lalrpop` files and call `processFile` on each.
    val files = lalrpopFiles(path)
    for (file in files) {
        apiBuildProcessFile(session, file)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiBuildProcessFile(session: Session, path: String) {
    // Mirror upstream `build::processFile` / `processFileInto`. Read
    // the source, run the parse + normalise + emit pipeline, and write
    // the resulting Rust source to `<path>.rs` next to the input.
    session.emitRerunDirective(path)

    val rsFile = resolveRsFile(session, path)
    if (!session.forceBuild && !needsRebuild(path, rsFile)) {
        return
    }

    session.log(io.github.kotlinmania.lalrpop.Level.Informative) {
        "processing file `$path`"
    }

    val sourceText = readFileToString(path)
    parentDir(rsFile)?.let { mkdirP(it) }
    removeIfExists(rsFile)

    // Use the buffer-based pipeline that already lives in build/Build.kt;
    // the upstream `processFileInto` writes a `LALRPOP_VERSION_HEADER` +
    // `// sha3:` line + body, which `compileGrammarToString` already
    // produces. The hash is computed from the source bytes.
    val emitted = compileGrammarToString(
        input = sourceText,
        sourceLabel = path,
        session = session,
        hashHex = null,
    )
    writeStringToFile(rsFile, emitted)
}

// ---------------------------------------------------------------------------
// Filesystem helpers (private to this file).
// ---------------------------------------------------------------------------

@OptIn(ExperimentalForeignApi::class)
private fun lalrpopFiles(root: String): List<String> {
    val collected = mutableListOf<String>()
    walkDir(root) { entry ->
        if (entry.endsWith(".lalrpop")) collected.add(entry)
    }
    return collected
}

@OptIn(ExperimentalForeignApi::class)
private fun walkDir(root: String, onFile: (String) -> Unit) {
    val dir = opendir(root) ?: return
    try {
        while (true) {
            val ent = readdir(dir) ?: break
            val name = ent.pointed.d_name.reinterpret<ByteVar>().toKString()
            if (name == "." || name == "..") continue
            val full = if (root.endsWith('/')) root + name else "$root/$name"
            if (isDirectory(full)) {
                walkDir(full, onFile)
            } else {
                onFile(full)
            }
        }
    } finally {
        closedir(dir)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun isDirectory(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    if (stat(path, st.ptr) != 0) return@memScoped false
    (st.st_mode.toInt() and S_IFMT) == S_IFDIR
}

@OptIn(ExperimentalForeignApi::class)
private fun resolveRsFile(session: Session, lalrpopFile: String): String {
    // Simplified mirror of upstream `genResolveFile` / `resolveRsFile`:
    // when outDir is set, place the output under it; otherwise keep the
    // output next to the input. Strip the inDir prefix if applicable.
    val parent = parentDir(lalrpopFile) ?: "."
    val baseName = lalrpopFile.substringAfterLast('/')
    val stem = if (baseName.endsWith(".lalrpop")) {
        baseName.removeSuffix(".lalrpop")
    } else {
        baseName.substringBeforeLast('.', baseName)
    }
    val outDir = session.outDir
    val targetDir = if (outDir != null) {
        val inDir = session.inDir
        val rel = if (inDir != null && parent.startsWith(inDir)) {
            parent.removePrefix(inDir).trimStart('/')
        } else {
            ""
        }
        // Strip leading "src" segment, mirroring upstream behaviour.
        val withoutSrc = rel.removePrefix("src").trimStart('/')
        if (withoutSrc.isEmpty()) outDir else "$outDir/$withoutSrc"
    } else {
        parent
    }
    return "$targetDir/$stem.rs"
}

private fun parentDir(path: String): String? {
    val idx = path.lastIndexOf('/')
    return if (idx <= 0) null else path.substring(0, idx)
}

@OptIn(ExperimentalForeignApi::class)
private fun mkdirP(path: String) {
    if (path.isEmpty() || isDirectory(path)) return
    parentDir(path)?.let { mkdirP(it) }
    platform.posix.mkdir(path, 493.toUShort())
}

@OptIn(ExperimentalForeignApi::class)
private fun removeIfExists(path: String) {
    platform.posix.remove(path)
}

@OptIn(ExperimentalForeignApi::class)
private fun needsRebuild(input: String, output: String): Boolean = memScoped {
    val inSt = alloc<stat>()
    if (stat(input, inSt.ptr) != 0) return@memScoped true
    val outSt = alloc<stat>()
    if (stat(output, outSt.ptr) != 0) return@memScoped true
    val inTime = inSt.st_mtimespec
    val outTime = outSt.st_mtimespec
    inTime.tv_sec > outTime.tv_sec ||
        (inTime.tv_sec == outTime.tv_sec && inTime.tv_nsec > outTime.tv_nsec)
}

@OptIn(ExperimentalForeignApi::class)
private fun readFileToString(path: String): String {
    val f = platform.posix.fopen(path, "rb") ?: error("could not open $path")
    try {
        val sb = StringBuilder()
        memScoped {
            val buf = allocArray<ByteVar>(4096)
            while (true) {
                val n = platform.posix.fread(buf, 1.toULong(), 4096.toULong(), f).toInt()
                if (n <= 0) break
                for (i in 0 until n) {
                    sb.append((buf[i].toInt() and 0xff).toChar())
                }
            }
        }
        return sb.toString()
    } finally {
        platform.posix.fclose(f)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeStringToFile(path: String, content: String) {
    val f = platform.posix.fopen(path, "wb") ?: error("could not create $path")
    try {
        memScoped {
            val bytes = content.encodeToByteArray()
            val pinned = bytes.pin()
            try {
                platform.posix.fwrite(pinned.addressOf(0), 1.toULong(), bytes.size.toULong(), f)
            } finally {
                pinned.unpin()
            }
        }
    } finally {
        platform.posix.fclose(f)
    }
}
