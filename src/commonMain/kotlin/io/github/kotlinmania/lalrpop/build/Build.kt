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

internal const val LALRPOP_VERSION_HEADER: String = "// auto-generated: \"lalrpop 0.23.1\""

internal fun parseAndNormalizeGrammar(session: Session, fileText: FileText): Grammar {
    val ptGrammar = parseGrammar(fileText.text())
        .getOrElse { exception ->
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

internal fun processDir(session: Session, rootDir: String) {
    val files = lalrpopFiles(rootDir)
    for (lalrpopFile in files) {
        processFile(session, lalrpopFile)
    }
}

internal fun processFile(session: Session, lalrpopFile: String) {
    val rsFile = resolveRsFile(session, lalrpopFile)
    val reportFile = resolveReportFile(session, lalrpopFile)
    processFileInto(session, lalrpopFile, rsFile, reportFile)
}

internal fun hashFile(file: String): String {
    val hex = apiSha3Hex(file) ?: error("could not hash $file")
    return "// sha3: $hex"
}

internal fun resolveRsFile(session: Session, lalrpopFile: String): String =
    genResolveFile(session, lalrpopFile, "rs")

internal fun resolveReportFile(session: Session, lalrpopFile: String): String =
    genResolveFile(session, lalrpopFile, "report")

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

    // Ideally we validate that the file name is a Rust identifier.
    // But I do not think we want a full blown syn dependency unless fully converting to proc macros.
    val fileName = pathFileName(lalrpopFile)
        ?: error("LALRPOP could not extract a valid file name: $lalrpopFile")
    if (fileName.any { it.isWhitespace() }) {
        error("LALRPOP file names cannot contain whitespace: $lalrpopFile")
    }

    return pathWithExtension(pathJoin(outDir, fileName), ext)
}

internal fun processFileInto(
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

internal fun removeOldFile(rsFile: String) {
    apiBuildRemoveFileIgnoringMissing(rsFile)
}

internal fun needsRebuild(lalrpopFile: String, rsFile: String): Boolean {
    val lines = apiBuildReadFirstTwoLines(rsFile) ?: return true
    val (versionStr, hashStr) = lines
    return hashStr.trim() != hashFile(lalrpopFile) ||
        versionStr.trim() != LALRPOP_VERSION_HEADER
}

/**
 * Handles a [WalkDirError] if the root cause is a dangling symlink.
 *
 * Returns successfully if the error could be handled, otherwise throws.
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
 * Reports a parse error via a custom reporter.
 *
 * Maps [ParseError<Int, Tok, TokError>] to a custom error type [E]. The user of this
 * function can then handle the error as they see fit by passing a
 * callback that constructs [E] from the error message, source file
 * text, and span.
 */
internal fun <E> reportParseError(
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

internal fun reportError(fileText: FileText, span: Span, message: String): IllegalStateException {
    apiBuildPrintln("${fileText.spanStr(span)} error: $message")

    val out = StringBuilder()
    fileText.highlight(span, out)
    apiBuildEPrint(out.toString())

    return IllegalStateException(message)
}

internal fun reportMessage(message: Message): Result<Unit> {
    val content = InlineBuilder.new().push(message).end()
    val r = reportContent(content)
    if (r.isFailure) return r
    apiBuildPrintln("")
    return Result.success(Unit)
}

internal fun reportContent(content: Content): Result<Unit> {
    // Can we query the size of the terminal somehow?
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

    val sink = object : Output {
        private val buffer = StringBuilder()

        override fun append(value: CharSequence?): Appendable {
            buffer.append(value)
            return this
        }

        override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
            buffer.append(value, startIndex, endIndex)
            return this
        }

        override fun append(value: Char): Appendable {
            buffer.append(value)
            return this
        }

        override fun flush(): Result<Unit> {
            apiBuildPrint(buffer.toString())
            buffer.clear()
            return Result.success(Unit)
        }
    }
    val fake = FakeTerminal.new(sink)
    canvas.writeTo(fake)
    fake.flush().getOrElse { return Result.failure(it) }
    return Result.success(Unit)
}

internal expect fun pathParent(path: String): String?

internal expect fun pathFileName(path: String): String?

internal expect fun pathExtension(path: String): String?

internal expect fun pathJoin(parent: String, child: String): String

internal expect fun pathWithExtension(path: String, ext: String): String

internal expect fun pathStripPrefix(path: String, base: String): String?

internal expect fun apiSha3Hex(file: String): String?

internal expect fun apiBuildReadFileToString(path: String): String

internal expect fun apiBuildWriteFileBytes(path: String, content: String)

internal expect fun apiBuildCreateDirAll(path: String)

internal expect fun apiBuildRemoveFileIgnoringMissing(path: String)

internal expect fun apiBuildReadFirstTwoLines(path: String): Pair<String, String>?

internal expect fun apiBuildPathIsSymlink(path: String): Boolean

internal expect fun apiBuildWalkDir(root: String): Sequence<WalkEntry>

internal expect fun apiBuildEPrintln(message: String)

internal expect fun apiBuildEPrint(message: String)

internal expect fun apiBuildPrintln(message: String)

internal expect fun apiBuildPrint(message: String)

internal expect fun apiBuildIsStdoutTerminal(): Boolean

internal expect fun apiBuildOpenAnsiStdout(): Appendable?

internal data class WalkDirError(
    val message: String,
    val path: String?,
    val kindIsNotFound: Boolean,
)

internal sealed class WalkEntry {
    data class Ok(val path: String, val isFile: Boolean) : WalkEntry()
    data class Err(val error: WalkDirError) : WalkEntry()
}
