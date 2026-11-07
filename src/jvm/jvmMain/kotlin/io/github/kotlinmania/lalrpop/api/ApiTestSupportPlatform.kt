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

import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.files.SystemPathSeparator
import io.github.kotlinmania.io.files.SystemTemporaryDirectory
import io.github.kotlinmania.io.files.createDirectory
import io.github.kotlinmania.io.files.isDirectory

internal actual fun apiTempDir(): String =
    SystemTemporaryDirectory
        .toString()
        .trimEnd(SystemPathSeparator)
        .ifEmpty { "/" }

internal actual fun apiSetCurrentDir(path: String) {
    System.setProperty("user.dir", path)
}

internal actual fun apiSetEnvVar(name: String, value: String) {
    System.setProperty(name, value)
}

internal actual fun apiPathExists(path: String): Boolean =
    SystemFileSystem.exists(Path(path))

internal actual fun apiCreateDir(path: String) {
    SystemFileSystem.createDirectory(Path(path))
}

internal actual fun apiRemoveDirAll(path: String) {
    val root = Path(path)
    if (!SystemFileSystem.exists(root)) return
    if (SystemFileSystem.isDirectory(root)) {
        for (child in SystemFileSystem.list(root)) {
            apiRemoveDirAll(child.toString())
        }
    }
    SystemFileSystem.delete(root, mustExist = false)
}

internal actual fun apiRemoveFile(path: String) {
    SystemFileSystem.delete(Path(path), mustExist = true)
}
