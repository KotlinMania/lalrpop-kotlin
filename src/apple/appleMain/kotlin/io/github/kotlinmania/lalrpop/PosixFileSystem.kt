package io.github.kotlinmania.lalrpop

/*
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
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.S_IFMT
import platform.posix.closedir
import platform.posix.lstat
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
internal actual fun posixListDirectory(path: String): List<String>? {
    val dir = opendir(path) ?: return null
    val names = mutableListOf<String>()
    try {
        while (true) {
            val ent = readdir(dir) ?: break
            val name =
                ent.pointed.d_name
                    .reinterpret<ByteVar>()
                    .toKString()
            if (name != "." && name != "..") names.add(name)
        }
    } finally {
        closedir(dir)
    }
    return names
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun posixFileType(path: String, followLinks: Boolean): Int? =
    memScoped {
        val st = alloc<stat>()
        val rc = if (followLinks) stat(path, st.ptr) else lstat(path, st.ptr)
        if (rc != 0) null else st.st_mode.toInt() and S_IFMT
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun posixMakeDirectory(path: String): Boolean = mkdir(path, "0755".toUInt(8).convert()) == 0
