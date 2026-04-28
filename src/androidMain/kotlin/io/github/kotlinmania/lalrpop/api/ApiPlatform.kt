// port-lint: source api/mod.rs (platform glue, JVM/Android target)
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

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.build.compileGrammarToString
import java.io.File

internal actual fun apiCurrentDir(): String = System.getProperty("user.dir") ?: "."

// On the JVM, the process environment is read-only at runtime; tests
// override variables (e.g. `OUT_DIR`) via system properties because
// `setenv` is not portable here. Consult system properties first so
// `apiSetEnvVar` (test support) round-trips through `apiEnvVar`.
internal actual fun apiEnvVar(name: String): String? =
    System.getProperty(name) ?: System.getenv(name)

internal actual fun apiEnvVars(): Sequence<Pair<String, String>> =
    System.getenv().entries.asSequence().map { (k, v) -> k to v }

internal actual fun apiEPrintln(message: String) {
    System.err.println(message)
}

internal actual fun apiBuildProcessDir(session: Session, path: String) {
    val root = File(path)
    val files = root.walkTopDown().filter { it.isFile && it.extension == "lalrpop" }
    for (file in files) {
        apiBuildProcessFile(session, file.absolutePath)
    }
}

internal actual fun apiBuildProcessFile(session: Session, path: String) {
    session.emitRerunDirective(path)

    val lalrpopFile = File(path)
    val rsFile = resolveRsFile(session, lalrpopFile)

    if (!session.forceBuild && rsFile.exists() && rsFile.lastModified() >= lalrpopFile.lastModified()) {
        return
    }

    session.log(Level.Informative) { "processing file `$path`" }

    rsFile.parentFile?.mkdirs()
    if (rsFile.exists()) rsFile.delete()

    val source = lalrpopFile.readText()
    val emitted = compileGrammarToString(
        input = source,
        sourceLabel = lalrpopFile.absolutePath,
        session = session,
        hashHex = null,
    )
    rsFile.writeText(emitted)
}

private fun resolveRsFile(session: Session, lalrpopFile: File): File {
    val outDir = session.outDir
    val parentDir = if (outDir != null) {
        val inDir = session.inDir
        val parent = lalrpopFile.parentFile?.absolutePath ?: "."
        val rel = if (inDir != null) {
            File(inDir).absoluteFile.toPath().toAbsolutePath().let { abs ->
                runCatching { File(parent).toPath().toAbsolutePath().relativize(abs).toString() }
                    .getOrDefault("")
            }
        } else {
            ""
        }
        val withoutSrc = rel.removePrefix("src").trimStart(File.separatorChar)
        if (withoutSrc.isEmpty()) File(outDir) else File(outDir, withoutSrc)
    } else {
        lalrpopFile.parentFile ?: File(".")
    }
    return File(parentDir, lalrpopFile.nameWithoutExtension + ".rs")
}
