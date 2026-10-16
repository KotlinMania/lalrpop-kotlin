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

// Hiding require('fs'), require('os'), and require('process') from webpack via (new Function('return require'))()
// per AGENTS.md §10 to prevent webpack bundling failures in jsBrowserTest.
private fun nodeFs(): dynamic =
    js(
        "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); return rq ? rq('fs') : null; } catch (e) { return null; } })()",
    )

private fun nodeOs(): dynamic =
    js(
        "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); return rq ? rq('os') : null; } catch (e) { return null; } })()",
    )

private fun nodeProcess(): dynamic =
    js(
        "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); if (rq) return rq('process'); if (typeof process !== 'undefined') return process; return null; } catch (e) { return null; } })()",
    )

internal actual fun apiTempDir(): String {
    val os = nodeOs()
    return if (os != null) (os.tmpdir() as String).trimEnd('/').ifEmpty { "/" } else "/tmp"
}

internal actual fun apiSetCurrentDir(path: String) {
    nodeProcess()?.chdir(path)
}

internal actual fun apiSetEnvVar(
    name: String,
    value: String,
) {
    val proc = nodeProcess() ?: return
    if (proc.env != null) {
        proc.env[name] = value
    }
}

internal actual fun apiPathExists(path: String): Boolean {
    val fs = nodeFs() ?: return false
    return fs.existsSync(path) as Boolean
}

internal actual fun apiCreateDir(path: String) {
    nodeFs()?.mkdirSync(path)
}

internal actual fun apiRemoveDirAll(path: String) {
    val fs = nodeFs() ?: return
    if (fs.existsSync(path) as Boolean) {
        fs.rmSync(path, js("({ recursive: true, force: true })"))
    }
}

internal actual fun apiRemoveFile(path: String) {
    nodeFs()?.unlinkSync(path)
}
