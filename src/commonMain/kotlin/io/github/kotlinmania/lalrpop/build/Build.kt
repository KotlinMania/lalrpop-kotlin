// transliterated from upstream module root
/**
 * Top-level orchestrator entry points ported from upstream
 * `src/build/mod.rs`. The `mod.rs` itself is not translated as a
 * single `Mod.kt` per project rules; its functions are split between
 * [EmitRecursiveAscent.kt] (the emit pipeline) and this file
 * (the parse-and-normalise driver and the file-IO orchestrators).
 */
package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.ColorConfig
import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.message.Message
import io.github.kotlinmania.lalrpop.message.builder.InlineBuilder
import io.github.kotlinmania.lalrpop.message.message.Content
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.normalize.normalize
import io.github.kotlinmania.lalrpop.parser.LrParseErrorException
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import io.github.kotlinmania.lalrpop.runtime.ParseError
import io.github.kotlinmania.lalrpop.tls.Tls
import io.github.kotlinmania.lalrpop.tok.ErrorCode
import io.github.kotlinmania.lalrpop.tok.Tok
import io.github.kotlinmania.lalrpop.tok.Error as TokError

/**
 * The header upstream emits at the top of every generated file.
 *
 * Direct port of upstream `LALRPOP_VERSION_HEADER` constant, expanded
 * via `concat("// auto-generated: \"", env("CARGO_PKG_NAME"), " ",
 * env("CARGO_PKG_VERSION"), "\"")`. The version string is captured at
 * upstream build time.
 */
const val LALRPOP_VERSION_HEADER: String = "// auto-generated: \"lalrpop 0.23.1\""

/**
 * Direct port of upstream `parseAndNormalizeGrammar`. Parses the
 * given grammar source text and runs the normalisation passes
 * (resolve, prevalidate, macroExpand, tokenCheck, …) against it,
 * yielding the [Grammar] that the Rust-emission back-end consumes.
 *
 * @throws IllegalStateException with the upstream parse/normalisation
 *   error message and span, mirroring the `reportError` path in
 *   upstream `mod.rs`.
 */
fun parseAndNormalizeGrammar(session: Session, fileText: FileText): Grammar {
    val ptGrammar = parseGrammar(fileText.text())
        .getOrElse { exception ->
            // Mirror upstream:
            //   parser::parseGrammar(fileText.text())
            //       .mapErr(|error| reportParseError(fileText, error, reportError))?;
            val parseError: ParseError<Int, Tok, TokError> = (exception as? LrParseErrorException)?.parseError
                ?: throw IllegalStateException("parse error: ${exception.message}", exception)
            throw reportParseError(fileText, parseError) { ft, span, message ->
                reportError(ft, span, message)
            }
        }
    return try {
        normalize(session, ptGrammar)
    } catch (e: NormErrorException) {
        throw reportError(fileText, e.err.span, e.err.message)
    }
}

// ---------------------------------------------------------------------------
// Direct ports of the remaining `src/build/mod.rs` functions. Filesystem
// I/O is delegated to per-platform `actual fun` declarations (see the
// `apiBuild*` expects at the bottom of this file). The control flow,
// formatting strings, and error messages mirror upstream verbatim.
// ---------------------------------------------------------------------------

/**
 * Direct port of upstream `processDir(session, rootDir)` from
 * `src/build/mod.rs:53`. Walks [rootDir] via [lalrpopFiles] and runs
 * [processFile] against each grammar.
 */
fun processDir(session: Session, rootDir: String) {
    val files = lalrpopFiles(rootDir)
    for (lalrpopFile in files) {
        processFile(session, lalrpopFile)
    }
}

/**
 * Direct port of upstream `processFile(session, lalrpopFile)` from
 * `src/build/mod.rs:61`. Resolves the matching `.rs` and `.report` paths
 * and dispatches into [processFileInto].
 */
fun processFile(session: Session, lalrpopFile: String) {
    val rsFile = resolveRsFile(session, lalrpopFile)
    val reportFile = resolveReportFile(session, lalrpopFile)
    processFileInto(session, lalrpopFile, rsFile, reportFile)
}

/**
 * Direct port of upstream `hashFile(file)` from `src/build/mod.rs:40`.
 * Computes the SHA3-256 of [file]s contents and returns the
 * `// sha3: <hex>` line that upstream embeds in generated `.rs` files.
 */
internal fun hashFile(file: String): String {
    val hex = apiSha3Hex(file) ?: error("could not hash $file")
    return "// sha3: $hex"
}

/**
 * Direct port of upstream `resolveRsFile` from `src/build/mod.rs:68`.
 */
internal fun resolveRsFile(session: Session, lalrpopFile: String): String =
    genResolveFile(session, lalrpopFile, "rs")

/**
 * Direct port of upstream `resolveReportFile` from `src/build/mod.rs:72`.
 */
internal fun resolveReportFile(session: Session, lalrpopFile: String): String =
    genResolveFile(session, lalrpopFile, "report")

/**
 * Direct port of upstream `genResolveFile` from `src/build/mod.rs:76`.
 *
 * Computes the output path for [lalrpopFile] under the session
 * `outDir`/`inDir` configuration, mirroring the upstream "strip inDir
 * prefix, then strip leading `src` segment" logic.
 */
internal fun genResolveFile(session: Session, lalrpopFile: String, ext: String): String {
    val outDir: String = if (session.outDir != null) {
        val d = session.outDir!!
        // If there is an outDirectory, we still expect it to mirror the
        // directory structure of where we found the lalrpop file relative to
        // the starting point.
        val parent = pathParent(lalrpopFile)
        val maybeP: String? = if (parent != null) {
            val inDir = session.inDir
            if (inDir != null) {
                // We need to strip the inDir from the path, if it exists.
                // If this file was from the inDirectory, then it was
                // necessarily a prefix of the path?
                val pathFromIn = pathStripPrefix(parent, inDir)
                    ?: error("expected $parent to be a child of $inDir")

                // Strip the src directory if we can?
                // Is this only for maintaining the old behavior of starting
                // from the root instead of starting in source?
                pathStripPrefix(pathFromIn, "src") ?: pathFromIn
            } else {
                null
            }
        } else {
            null
        }
        if (maybeP != null && maybeP != "") {
            // There is some additional path structure, so add it
            pathJoin(d, maybeP)
        } else {
            d
        }
    } else {
        pathParent(lalrpopFile) ?: "."
    }

    // Ideally we do something like syn::parseStr::<syn::Ident>(lalrpopFile.fileName())?;
    // But I do not think we want a full blown syn dependency unless fully converting to proc macros.
    val fileName = pathFileName(lalrpopFile)
        ?: error("LALRPOP could not extract a valid file name: $lalrpopFile")
    if (fileName.any { it.isWhitespace() }) {
        error("LALRPOP file names cannot contain whitespace: $lalrpopFile")
    }

    return pathWithExtension(pathJoin(outDir, fileName), ext)
}

/**
 * Direct port of upstream `processFileInto` from `src/build/mod.rs:148`.
 *
 * The orchestrator: emits a Cargo `rerun-if-changed` directive, decides
 * whether the `.rs` is up-to-date, and on rebuild loads the source,
 * runs parse → normalize → emit, prepends the version+sha3 header, and
 * writes the result to [rsFile]. Throws on I/O or codegen failure.
 */
fun processFileInto(
    session: Session,
    lalrpopFile: String,
    rsFile: String,
    reportFile: String,
) {
    session.emitRerunDirective(lalrpopFile)
    if (session.forceBuild || needsRebuild(lalrpopFile, rsFile)) {
        session.log.log(Level.Informative) {
            "processing file `$lalrpopFile`"
        }

        // Load the LALRPOP source text for this file:
        val sourceText = apiBuildReadFileToString(lalrpopFile)
        val fileText = FileText.new(lalrpopFile, sourceText)

        pathParent(rsFile)?.let { apiBuildCreateDirAll(it) }
        removeOldFile(rsFile)

        // Store the session and file-text in TLS -- this is not
        // intended to be used in this high-level code, but it gives
        // easy access to this information pervasively in the
        // low-level LR(1) and grammar normalization code. This is
        // particularly useful for error-reporting.
        val tls = Tls.install(session, fileText)
        try {
            // Do the LALRPOP processing itself and write the resulting
            // buffer into a file. We use a buffer so that if LR(1)
            // generation fails at some point, we do not leave a partial
            // file behind.
            val grammar = parseAndNormalizeGrammar(session, fileText)
            val reportBuffer = if (session.emitReport) StringBuilder() else null
            val buffer = emitRecursiveAscent(session, grammar, reportBuffer)
            // writeln(outputFile, "{LALRPOP_VERSION_HEADER}")
            // writeln(outputFile, "{}", hashFile(lalrpopFile)?)
            // outputFile.writeAll(&buffer)
            val out = StringBuilder()
            out.append(LALRPOP_VERSION_HEADER)
            out.append('\n')
            out.append(hashFile(lalrpopFile))
            out.append('\n')
            out.append(buffer)
            apiBuildWriteFileBytes(rsFile, out.toString())
            if (reportBuffer != null) {
                pathParent(reportFile)?.let { apiBuildCreateDirAll(it) }
                apiBuildWriteFileBytes(reportFile, reportBuffer.toString())
            }
        } finally {
            tls.close()
        }
    }
}

/**
 * Direct port of upstream `removeOldFile` from `src/build/mod.rs:194`.
 *
 * Removes [rsFile] if present, ignoring NotFound / PermissionDenied
 * (the latter is what Windows reports for a non-existent file).
 */
internal fun removeOldFile(rsFile: String) {
    // when fs::removeFile(rsFile) {
    //     Ok(()) => Ok(()),
    //     Err(e) => when e.kind() {
    //         NotFound | PermissionDenied => Ok(()),
    //         _ => Err(e),
    //     }
    // }
    apiBuildRemoveFileIgnoringMissing(rsFile)
}

/**
 * Direct port of upstream `needsRebuild` from `src/build/mod.rs:207`.
 *
 * Compares the `// sha3: ...` and version-header lines at the top of
 * [rsFile] against the current source hash and version constant,
 * returning `true` if a rebuild is needed.
 */
internal fun needsRebuild(lalrpopFile: String, rsFile: String): Boolean {
    val lines = apiBuildReadFirstTwoLines(rsFile) ?: return true
    val (versionStr, hashStr) = lines
    return hashStr.trim() != hashFile(lalrpopFile) ||
        versionStr.trim() != LALRPOP_VERSION_HEADER
}

/**
 * Handles a [walkdir error][apiBuildWalkErrorKind] if the root cause
 * is a dangling symlink. Direct port of upstream
 * `handleDanglingSymlinkError` from `src/build/mod.rs:231`.
 *
 * the upstream `walkdir::Error` packages an inner `io::Error` plus the
 * offending path. The Kotlin port lifts those two pieces of state into
 * the [WalkDirError] data class so the platform actuals can build it
 * from whatever scanner they use.
 *
 * Returns successfully if the error could be handled (i.e. it pointed
 * to a dangling symlink, in which case a warning is printed to stderr);
 * otherwise rethrows the original error wrapped in [IllegalStateException].
 */
internal fun handleDanglingSymlinkError(err: WalkDirError) {
    val isNotFound = err.kindIsNotFound
    if (!isNotFound) {
        throw IllegalStateException(err.message)
    }

    // As of now on Linux, this is the path of the symlink (not where it points to) in case of a
    // dangling symlink:
    val path = err.path ?: throw IllegalStateException(err.message)

    if (!apiBuildPathIsSymlink(path)) {
        throw IllegalStateException(err.message)
    }

    apiBuildEPrintln("Warning: ignoring dangling/erroneous symlink $path")
}

/**
 * Direct port of upstream `lalrpopFiles` from `src/build/mod.rs:257`.
 *
 * Walks [rootDir] (following symlinks, deterministic file-name order)
 * and returns every regular file whose extension is `.lalrpop`.
 */
internal fun lalrpopFiles(rootDir: String): List<String> {
    val result = mutableListOf<String>()

    val walkdir = apiBuildWalkDir(rootDir)
    for (entry in walkdir) {
        val resolved = when (entry) {
            is WalkEntry.Ok -> entry
            is WalkEntry.Err -> {
                handleDanglingSymlinkError(entry.error)
                continue
            }
        }

        // `fileType` follows symlinks, so if `entry` points to a symlink to a file, then
        // `isFile` returns true.
        if (!resolved.isFile) {
            continue
        }

        val path = resolved.path
        if (pathExtension(path) != "lalrpop") {
            continue
        }

        result.add(path)
    }

    return result
}

/**
 * Reports a parse error via a custom reporter. Direct port of upstream
 * `reportParseError` from `src/build/mod.rs:305`.
 *
 * Maps [ParseError<Int, Tok, TokError>] to a custom error type [E]. The user of this
 * function can then handle the error as they see fit by passing a
 * callback that constructs [E] from the error message, source file
 * text, and span.
 */
fun <E> reportParseError(
    fileText: FileText,
    error: ParseError<Int, Tok, TokError>,
    reporter: (FileText, Span, String) -> E,
): E {
    return when (error) {
        is ParseError.InvalidToken -> {
            val location = error.location
            val ch = fileText.text().substring(location).first()
            reporter(
                fileText,
                Span(location, location),
                "invalid character `$ch`",
            )
        }

        is ParseError.UnrecognizedEof -> {
            val location = error.location
            reporter(
                fileText,
                Span(location, location),
                "unexpected end of file",
            )
        }

        is ParseError.UnrecognizedToken -> {
            val (lo, _, hi) = error.token
            val expected = error.expected // did not implement this yet :)
            val text = fileText.text().substring(lo, hi)
            reporter(
                fileText,
                Span(lo, hi),
                "unexpected token: `$text`",
            )
        }

        is ParseError.ExtraToken -> {
            val (lo, _, hi) = error.token
            val text = fileText.text().substring(lo, hi)
            reporter(
                fileText,
                Span(lo, hi),
                "extra token at end of input: `$text`",
            )
        }

        is ParseError.User -> {
            val tokError = error.error
            val string = when (tokError.code) {
                ErrorCode.UnrecognizedToken -> "unrecognized token"
                ErrorCode.UnterminatedEscape -> "unterminated escape; missing '`'?"
                ErrorCode.UnterminatedAsciiEscape ->
                    "unterminated ascii escape; missing second digit?"
                ErrorCode.UnrecognizedEscape ->
                    "unrecognized escape; only \\n, \\r, \\t, \\0, \\\", \\\\, and \\x## are recognized"
                ErrorCode.UnterminatedStringLiteral ->
                    "unterminated string literal; missing `\"`?"
                ErrorCode.UnterminatedCharacterLiteral ->
                    "unterminated character literal; missing `'`?"
                ErrorCode.UnterminatedAttribute -> "unterminated #! attribute; missing `]`?"
                ErrorCode.ExpectedStringLiteral -> "expected string literal; missing `\"`?"
                ErrorCode.UnterminatedCode ->
                    "unterminated code block; perhaps a missing `;`, `)`, `]` or `}`?"
                ErrorCode.UnterminatedBlockComment ->
                    "unterminated block comment; missing `*/`?"
            }

            reporter(
                fileText,
                Span(tokError.location, tokError.location + 1),
                string,
            )
        }
    }
}

/**
 * Direct port of upstream `reportError` from `src/build/mod.rs:383`.
 *
 * Prints `<span>: error: <message>` to stdout, the highlighted source
 * span to stderr, and yields an [IllegalStateException] (the Kotlin
 * port stand-in for `io::Error::new(InvalidData, message)`).
 */
internal fun reportError(fileText: FileText, span: Span, message: String): IllegalStateException {
    apiBuildPrintln("${fileText.spanStr(span)} error: $message")

    val out = StringBuilder()
    fileText.highlight(span, out)
    apiBuildEPrint(out.toString())

    return IllegalStateException(message)
}

/**
 * Direct port of upstream `reportMessage` from `src/build/mod.rs:393`.
 *
 * Wraps [message] in an [InlineBuilder] and ships it through
 * [reportContent], appending a trailing blank line to mirror upstream
 * `printlnCall()`.
 */
internal fun reportMessage(message: Message): Result<Unit> {
    val content = InlineBuilder.new().push(message).end()
    val r = reportContent(content)
    if (r.isFailure) return r
    apiBuildPrintln("")
    return Result.success(Unit)
}

/**
 * Direct port of upstream `reportContent` from `src/build/mod.rs:400`.
 *
 * Renders [content] onto an 80-column [io.github.kotlinmania.lalrpop.message.AsciiCanvas]
 * and writes it to stdout, picking between a real ANSI terminal (when
 * the session colour-config and TTY check both allow it) and a
 * [FakeTerminal] otherwise.
 */
internal fun reportContent(content: Content): Result<Unit> {
    // Upstream asks: can we query the size of the terminal somehow?
    // For now, it renders diagnostics at a fixed 80-column width.
    val canvas = content.emitToCanvas(80)

    val tryColors = when (Tls.session().colorConfig) {
        ColorConfig.Yes -> true
        ColorConfig.No -> false
        ColorConfig.IfTty -> apiBuildIsStdoutTerminal()
    }

    if (tryColors) {
        val stdout = apiBuildOpenAnsiStdout()
        if (stdout != null) {
            canvas.writeTo(stdout)
            return Result.success(Unit)
        }
    }

    val sink = StringBuilder()
    val fake = FakeTerminal.new(sink)
    canvas.writeTo(sink)
    // FakeTerminal is the same shape as upstream `&mut FakeTerminal::new(stdout.lock())`;
    // we drain the buffer through stdout once the canvas has finished writing.
    apiBuildPrint(fake.intoInner().toString())
    return Result.success(Unit)
}

// ---------------------------------------------------------------------------
// Path helpers (commonMain). the upstream `std::path::Path` does these
// inline; Kotlin Multiplatform has no portable Path, so the platform
// actuals supply the small subset upstream actually uses.
// ---------------------------------------------------------------------------

/** Mirror of `Path::parent()` returning `None` for "no parent". */
internal expect fun pathParent(path: String): String?

/** Mirror of `Path::fileName()` returning `None` for an empty / `..` path. */
internal expect fun pathFileName(path: String): String?

/** Mirror of `Path::extension()` returning `None` if there no dot suffix. */
internal expect fun pathExtension(path: String): String?

/** Mirror of `Path::join(child)`. */
internal expect fun pathJoin(parent: String, child: String): String

/** Mirror of `Path::withExtension(ext)`. */
internal expect fun pathWithExtension(path: String, ext: String): String

/**
 * Mirror of `Path::stripPrefix(base)` returning `None` when [path]
 * does not start with [base].
 */
internal expect fun pathStripPrefix(path: String, base: String): String?

// ---------------------------------------------------------------------------
// Platform glue: filesystem / IO / hashing / terminal probing. Mirrors
// the same pattern used in `api/Api.kt` for `apiCurrentDir` etc.
// ---------------------------------------------------------------------------

/**
 * Computes the SHA3-256 of [file]s contents and returns it as a
 * lower-case hex string (no `// sha3:` prefix). Returns `null` when
 * the file cannot be read.
 *
 * Upstream uses the `sha3` crate `Sha3256` here. There is no
 * commonMain SHA3 in the Kotlin stdlib, so each platform supplies its
 * own implementation or reports that hashing is unavailable.
 */
internal expect fun apiSha3Hex(file: String): String?

/** Mirror of `std::fs::readToString(path)`. */
internal expect fun apiBuildReadFileToString(path: String): String

/**
 * Mirror of `std::fs::File::create(path)` followed by
 * `outputFile.writeAll(&buffer)`. Writes [content] to [path] in
 * UTF-8, replacing any existing file.
 */
internal expect fun apiBuildWriteFileBytes(path: String, content: String)

/** Mirror of `std::fs::createDirAll(path)`. */
internal expect fun apiBuildCreateDirAll(path: String)

/**
 * Mirror of `std::fs::removeFile(path)` that swallows
 * `NotFound`/`PermissionDenied` errors (the upstream
 * `removeOldFile` semantics).
 */
internal expect fun apiBuildRemoveFileIgnoringMissing(path: String)

/**
 * Reads the first two lines of [path] (each terminated with `\n`)
 * and returns them as `Pair(version, hash)`. Returns `null` if the
 * file does not exist.
 */
internal expect fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>?

/** Direct mirror of `std::path::Path::isSymlink`. */
internal expect fun apiBuildPathIsSymlink(path: String): Boolean

/**
 * Mirror of `WalkDir::new(root).followLinks(true).sortByFileName()`
 * iterator. Yields a [WalkEntry] per visited file/directory; `Err`
 * variants carry the original error so [handleDanglingSymlinkError]
 * can decide whether to ignore them.
 */
internal expect fun apiBuildWalkDir(root: String): Sequence<WalkEntry>

/** Mirror of `eprintln!`. */
internal expect fun apiBuildEPrintln(message: String)

/** Mirror of `eprint!`. */
internal expect fun apiBuildEPrint(message: String)

/** Mirror of `println!`. */
internal expect fun apiBuildPrintln(message: String)

/** Mirror of `print!`. */
internal expect fun apiBuildPrint(message: String)

/** Mirror of `io::stdout().isTerminal()`. */
internal expect fun apiBuildIsStdoutTerminal(): Boolean

/**
 * Mirror of `term::stdout()`: returns an ANSI-capable [Appendable]
 * representing standard output, or `null` if the terminfo for the
 * current terminal cannot be loaded. The Kotlin port treats every
 * platform as "no terminfo available" and always returns `null` —
 * the [reportContent] fallback through [FakeTerminal] is the path
 * upstream takes on those same platforms anyway.
 */
internal expect fun apiBuildOpenAnsiStdout(): Appendable?

// ---------------------------------------------------------------------------
// Walkdir support types — small data classes that stand in for
// `walkdir::DirEntry` and `walkdir::Error` since neither exists in
// commonMain.
// ---------------------------------------------------------------------------

/** Mirror of `walkdir::Error`s relevant projection. */
internal data class WalkDirError(
    val message: String,
    val path: String?,
    val kindIsNotFound: Boolean,
)

/** One entry yielded by [apiBuildWalkDir]. */
internal sealed class WalkEntry {
    data class Ok(val path: String, val isFile: Boolean) : WalkEntry()
    data class Err(val error: WalkDirError) : WalkEntry()
}
