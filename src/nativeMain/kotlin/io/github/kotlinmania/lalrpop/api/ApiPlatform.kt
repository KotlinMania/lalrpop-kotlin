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
import platform.posix.PATH_MAX
import platform.posix.fprintf
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.pclose
import platform.posix.popen
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

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    processDir(session, path)
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    processFile(session, path)
}
