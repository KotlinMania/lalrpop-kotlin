// port-lint: source api/test.rs (platform glue, JS target)
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

@JsModule("node:fs")
@JsNonModule
private external object NodeFs {
    fun existsSync(path: String): Boolean
    fun mkdirSync(path: String, options: dynamic = definedExternally)
    fun rmSync(path: String, options: dynamic = definedExternally)
    fun unlinkSync(path: String)
}

@JsModule("node:os")
@JsNonModule
private external object NodeOs {
    fun tmpdir(): String
}

@JsModule("node:process")
@JsNonModule
private external object NodeTestProcess {
    val env: dynamic
    fun chdir(path: String)
}

internal actual fun apiTempDir(): String = NodeOs.tmpdir().trimEnd('/').ifEmpty { "/" }

internal actual fun apiSetCurrentDir(path: String) {
    NodeTestProcess.chdir(path)
}

internal actual fun apiSetEnvVar(
    name: String,
    value: String,
) {
    NodeTestProcess.env[name] = value
}

internal actual fun apiPathExists(path: String): Boolean = NodeFs.existsSync(path)

internal actual fun apiCreateDir(path: String) {
    NodeFs.mkdirSync(path)
}

internal actual fun apiRemoveDirAll(path: String) {
    if (NodeFs.existsSync(path)) {
        NodeFs.rmSync(path, js("({ recursive: true, force: true })"))
    }
}

internal actual fun apiRemoveFile(path: String) {
    NodeFs.unlinkSync(path)
}
