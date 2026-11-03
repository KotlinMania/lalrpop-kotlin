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

import io.github.kotlinmania.lalrpop.posixFileType
import io.github.kotlinmania.lalrpop.posixListDirectory
import io.github.kotlinmania.lalrpop.posixMakeDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.S_IFDIR
import platform.posix.chdir
import platform.posix.getenv
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.setenv

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
internal actual fun apiPathExists(path: String): Boolean = posixFileType(path, followLinks = true) != null

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiCreateDir(path: String) {
    if (!posixMakeDirectory(path)) error("mkdir($path) failed")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiRemoveDirAll(path: String) {
    if (!apiPathExists(path)) return
    if (isDirectoryNative(path)) {
        val names = posixListDirectory(path) ?: return
        for (name in names) {
            val full = if (path.endsWith('/')) path + name else "$path/$name"
            apiRemoveDirAll(full)
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
private fun isDirectoryNative(path: String): Boolean = posixFileType(path, followLinks = true) == S_IFDIR
