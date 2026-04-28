// port-lint: source api/test.rs (platform glue, native target)
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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.chdir
import platform.posix.closedir
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.setenv
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiTempDir(): String {
    // Mirror of std::env::tempDir(): consult TMPDIR / TMP / TEMP, else /tmp.
    for (name in listOf("TMPDIR", "TMP", "TEMP")) {
        val raw = getenv(name) ?: continue
        val s = raw.toKString()
        if (s.isNotEmpty()) return s.trimEnd('/').ifEmpty { "/" }
    }
    return "/tmp"
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiSetCurrentDir(path: String) {
    if (chdir(path) != 0) error("chdir($path) failed")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiSetEnvVar(name: String, value: String) {
    if (setenv(name, value, 1) != 0) error("setenv($name) failed")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiPathExists(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    stat(path, st.ptr) == 0
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiCreateDir(path: String) {
    if (mkdir(path, "0755".toUInt(8)) != 0) error("mkdir($path) failed")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiRemoveDirAll(path: String) {
    if (!apiPathExists(path)) return
    if (isDirectoryNative(path)) {
        val dir = opendir(path) ?: return
        try {
            while (true) {
                val ent = readdir(dir) ?: break
                val name = ent.pointed.d_name.reinterpret<ByteVar>().toKString()
                if (name == "." || name == "..") continue
                val full = if (path.endsWith('/')) path + name else "$path/$name"
                apiRemoveDirAll(full)
            }
        } finally {
            closedir(dir)
        }
        if (rmdir(path) != 0) error("rmdir($path) failed")
    } else {
        if (remove(path) != 0) error("remove($path) failed")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiRemoveFile(path: String) {
    if (remove(path) != 0) error("remove($path) failed")
}

@OptIn(ExperimentalForeignApi::class)
private fun isDirectoryNative(path: String): Boolean = memScoped {
    val st = alloc<stat>()
    if (stat(path, st.ptr) != 0) return@memScoped false
    (st.st_mode.toInt() and S_IFMT) == S_IFDIR
}
