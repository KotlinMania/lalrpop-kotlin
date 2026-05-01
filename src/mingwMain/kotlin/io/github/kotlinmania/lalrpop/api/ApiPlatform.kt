// port-lint: source api/mod.rs (platform glue, native target)
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

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.processDir
import io.github.kotlinmania.lalrpop.build.processFile
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fprintf
import platform.posix.getenv
import platform.posix.stderr
import platform.windows.FreeEnvironmentStringsA
import platform.windows.GetCurrentDirectoryA
import platform.windows.GetEnvironmentStrings
import platform.windows.MAX_PATH

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiCurrentDir(): String = memScoped {
    val buf = allocArray<ByteVar>(MAX_PATH + 1)
    if (GetCurrentDirectoryA((MAX_PATH + 1).toUInt(), buf) == 0u) {
        error("GetCurrentDirectoryA failed")
    }
    buf.toKString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEnvVar(name: String): String? {
    val raw = getenv(name) ?: return null
    return raw.toKString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEnvVars(): Sequence<Pair<String, String>> {
    val block = GetEnvironmentStrings() ?: return emptySequence()
    try {
        val vars = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var i = 0
        while (true) {
            val b = block[i++]
            if (b == 0.toByte()) {
                if (current.isEmpty()) break
                val line = current.toString()
                val eq = line.indexOf('=')
                if (eq > 0) vars.add(line.substring(0, eq) to line.substring(eq + 1))
                current.clear()
            } else {
                current.append((b.toInt() and 0xff).toChar())
            }
        }
        return vars.asSequence()
    } finally {
        FreeEnvironmentStringsA(block)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun apiEPrintln(message: String) {
    fprintf(stderr, "%s\n", message)
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    processDir(session, path)
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    processFile(session, path)
}
