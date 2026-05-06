@file:OptIn(ExperimentalWasmJsInterop::class)

// port-lint: source api/test.rs (platform glue, wasmJs target)
package io.github.kotlinmania.lalrpop.api

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

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

@JsFun("() => globalThis.KotlinManiaLalrpopHost.tempDir()")
private external fun hostTempDir(): String

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.setCurrentDir(path)")
private external fun hostSetCurrentDir(path: String)

@JsFun("(name, value) => globalThis.KotlinManiaLalrpopHost.setEnvVar(name, value)")
private external fun hostSetEnvVar(name: String, value: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.pathExists(path)")
private external fun hostPathExists(path: String): Boolean

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.createDir(path)")
private external fun hostCreateDir(path: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.removeDirAll(path)")
private external fun hostRemoveDirAll(path: String)

@JsFun("(path) => globalThis.KotlinManiaLalrpopHost.removeFile(path)")
private external fun hostRemoveFile(path: String)

internal actual fun apiTempDir(): String = hostTempDir().trimEnd('/').ifEmpty { "/" }

internal actual fun apiSetCurrentDir(path: String) {
    hostSetCurrentDir(path)
}

internal actual fun apiSetEnvVar(name: String, value: String) {
    hostSetEnvVar(name, value)
}

internal actual fun apiPathExists(path: String): Boolean = hostPathExists(path)

internal actual fun apiCreateDir(path: String) {
    hostCreateDir(path)
}

internal actual fun apiRemoveDirAll(path: String) {
    hostRemoveDirAll(path)
}

internal actual fun apiRemoveFile(path: String) {
    hostRemoveFile(path)
}
