// transliterated from upstream module root
/** A tokenizer for use in LALRPOP itself. */
package io.github.kotlinmania.lalrpop.tok

import io.github.kotlinmania.regexsyntax.hir.Class
import io.github.kotlinmania.regexsyntax.hir.ClassUnicodeRange
import io.github.kotlinmania.regexsyntax.hir.HirKind
import io.github.kotlinmania.regexsyntax.parser.ParserBuilder

internal data class Error(
    val location: Int,
    val code: ErrorCode,
)

internal enum class ErrorCode {
    UnrecognizedToken,
    UnterminatedEscape,
    UnterminatedAsciiEscape,
    UnrecognizedEscape,
    UnterminatedStringLiteral,
    UnterminatedCharacterLiteral,
    UnterminatedAttribute,
    UnterminatedCode,
    ExpectedStringLiteral,
    UnterminatedBlockComment,
}

internal class TokError(
    val err: Error,
) : RuntimeException()

private fun <T> error(c: ErrorCode, l: Int): Result<T> =
    Result.failure(TokError(Error(location = l, code = c)))

internal sealed class Tok {
    // Keywords;
    object Enum : Tok()

    object Extern : Tok()

    object Grammar : Tok()

    object Match : Tok()

    object Else : Tok()

    object If : Tok()

    object Mut : Tok()

    object Pub : Tok()

    object In : Tok()

    object KwType : Tok()

    object Where : Tok()

    object For : Tok()

    object Dyn : Tok()

    // Special keywords: these are accompanied by a series of
    // uninterpreted strings representing imports and stuff.
    data class Use(
        val s: String,
    ) : Tok()

    // Identifiers of various kinds:
    data class Escape(
        val s: String,
    ) : Tok()

    data class Id(
        val s: String,
    ) : Tok()

    // identifier followed immediately by `<`
    data class MacroId(
        val s: String,
    ) : Tok()

    // includes the `'`
    data class Lifetime(
        val s: String,
    ) : Tok()

    // excludes the `"`
    data class StringLiteral(
        val s: String,
    ) : Tok()

    // excludes the `'`
    data class CharLiteral(
        val s: String,
    ) : Tok()

    // excludes the `r"` and `"`
    data class RegexLiteral(
        val s: String,
    ) : Tok()

    // Symbols:
    object Ampersand : Tok()

    object BangEquals : Tok()

    object BangTilde : Tok()

    object Colon : Tok()

    object ColonColon : Tok()

    object Comma : Tok()

    object DotDot : Tok()

    object Equals : Tok()

    object EqualsEquals : Tok()

    data class EqualsGreaterThanCode(
        val s: String,
    ) : Tok()

    data class EqualsGreaterThanQuestionCode(
        val s: String,
    ) : Tok()

    object EqualsGreaterThanLookahead : Tok()

    object EqualsGreaterThanLookbehind : Tok()

    object Hash : Tok()

    object GreaterThan : Tok()

    object LeftBrace : Tok()

    object LeftBracket : Tok()

    object LeftParen : Tok()

    object LessThan : Tok()

    // @L
    object Lookahead : Tok()

    // @R
    object Lookbehind : Tok()

    object MinusGreaterThan : Tok()

    object Plus : Tok()

    object Question : Tok()

    object RightBrace : Tok()

    object RightBracket : Tok()

    object RightParen : Tok()

    object Semi : Tok()

    object Star : Tok()

    object TildeTilde : Tok()

    object Underscore : Tok()

    object Bang : Tok()

    // #![...]
    data class ShebangAttribute(
        val s: String,
    ) : Tok()

    // Dummy tokens for parser sharing
    object StartGrammar : Tok()

    object StartPattern : Tok()

    object StartMatchMapping : Tok()

    object StartGrammarWhereClauses : Tok()

    object StartTypeRef : Tok()
}

private val KEYWORDS: List<Pair<String, Tok>> =
    listOf(
        "enum" to Tok.Enum,
        "extern" to Tok.Extern,
        "grammar" to Tok.Grammar,
        "match" to Tok.Match,
        "else" to Tok.Else,
        "if" to Tok.If,
        "mut" to Tok.Mut,
        "pub" to Tok.Pub,
        "in" to Tok.In,
        "type" to Tok.KwType,
        "where" to Tok.Where,
        "for" to Tok.For,
        "dyn" to Tok.Dyn,
    )

internal class Tokenizer private constructor(
    val text: String,
    var chars: CharIndices,
    var lookahead: Pair<Int, Int>?,
    val shift: Int,
    private val textBytes: ByteArray,
) : Iterator<Spanned<Tok>> {
    constructor(
        text: String,
        shift: Int,
    ) : this(
        text = text,
        chars = text.charIndices(),
        lookahead = null,
        shift = shift,
        textBytes = text.encodeToByteArray(throwOnInvalidSequence = true),
    ) {
        this.bump()
    }

    private var pending: Result<Spanned<Tok>>? = null

    override fun hasNext(): Boolean {
        if (this.pending != null) return true
        this.pending = this.nextShifted()
        return this.pending != null
    }

    override fun next(): Spanned<Tok> {
        val p = this.pending ?: this.nextShifted() ?: throw NoSuchElementException()
        this.pending = null
        return p.getOrThrow()
    }

    internal fun nextResult(): Result<Spanned<Tok>>? {
        val p = this.pending
        if (p != null) {
            this.pending = null
            return p
        }
        return this.nextShifted()
    }

    fun shebangAttribute(idx0: Int): Result<Spanned<Tok>> {
        when (val r = this.expectChar('!'.code)) {
            null -> return error(ErrorCode.UnrecognizedToken, idx0)
            else -> {
                val err = r.exceptionOrNull()
                if (err != null) return Result.failure(err)
            }
        }
        when (val r1 = this.expectChar('['.code)) {
            null -> return error(ErrorCode.UnterminatedAttribute, idx0)
            else -> {
                val err1 = r1.exceptionOrNull()
                if (err1 != null) return Result.failure(err1)
            }
        }
        var sqBracketCounter = 1
        while (true) {
            val (idx1, c) = this.lookahead ?: break
            when (c) {
                '['.code -> {
                    this.bump()
                    sqBracketCounter += 1
                }

                ']'.code -> {
                    this.bump()
                    sqBracketCounter -= 1
                    when {
                        sqBracketCounter == 0 -> {
                            val idx2 = idx1 + 1
                            val data = this.sliceBytes(idx0, idx2)
                            this.bump()
                            return Result.success(Spanned(idx0, Tok.ShebangAttribute(data), idx2))
                        }

                        sqBracketCounter < 0 -> {
                            return error(ErrorCode.UnrecognizedToken, idx0)
                        }

                        else -> Unit
                    }
                }

                '"'.code -> {
                    this.bump()
                    this.stringLiteral(idx1).getOrElse { return Result.failure(it) }
                }

                '\n'.code -> {
                    return error(ErrorCode.UnrecognizedToken, idx0)
                }

                else -> {
                    this.bump()
                }
            }
        }
        return error(ErrorCode.UnrecognizedToken, idx0)
    }

    fun nextUnshifted(): Result<Spanned<Tok>>? {
        while (true) {
            val (idx0, c) = this.lookahead ?: return null

            return when {
                c == '&'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Ampersand, idx0 + 1))
                }

                c == '!'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '='.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.BangEquals, idx1 + 1))
                        }

                        matchVal != null && matchVal.second == '~'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.BangTilde, idx1 + 1))
                        }

                        else -> Result.success(Spanned(idx0, Tok.Bang, idx0 + 1))
                    }
                }

                c == ':'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == ':'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.ColonColon, idx1 + 1))
                        }

                        else -> Result.success(Spanned(idx0, Tok.Colon, idx0 + 1))
                    }
                }

                c == ','.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Comma, idx0 + 1))
                }

                c == '.'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '.'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.DotDot, idx1 + 1))
                        }

                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }

                c == '='.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '='.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.EqualsEquals, idx1 + 1))
                        }

                        matchVal != null && matchVal.second == '>'.code -> {
                            this.bump()
                            this.rightArrow(idx0)
                        }

                        else -> Result.success(Spanned(idx0, Tok.Equals, idx0 + 1))
                    }
                }

                c == '#'.code -> {
                    this.bump()
                    // Helper for backtracking.
                    val fallbackState = Pair(this.chars.clone(), this.lookahead)
                    val result = this.shebangAttribute(idx0)
                    if (result.isSuccess) {
                        result
                    } else {
                        this.chars = fallbackState.first
                        this.lookahead = fallbackState.second
                        Result.success(Spanned(idx0, Tok.Hash, idx0 + 1))
                    }
                }

                c == '>'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.GreaterThan, idx0 + 1))
                }

                c == '{'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.LeftBrace, idx0 + 1))
                }

                c == '['.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.LeftBracket, idx0 + 1))
                }

                c == '('.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.LeftParen, idx0 + 1))
                }

                c == '<'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.LessThan, idx0 + 1))
                }

                c == '@'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == 'L'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.Lookahead, idx1 + 1))
                        }

                        matchVal != null && matchVal.second == 'R'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.Lookbehind, idx1 + 1))
                        }

                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }

                c == '+'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Plus, idx0 + 1))
                }

                c == '?'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Question, idx0 + 1))
                }

                c == '}'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.RightBrace, idx0 + 1))
                }

                c == ']'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.RightBracket, idx0 + 1))
                }

                c == ')'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.RightParen, idx0 + 1))
                }

                c == ';'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Semi, idx0 + 1))
                }

                c == '*'.code -> {
                    this.bump()
                    Result.success(Spanned(idx0, Tok.Star, idx0 + 1))
                }

                c == '~'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '~'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.TildeTilde, idx1 + 1))
                        }

                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }

                c == '`'.code -> {
                    this.bump()
                    this.escape(idx0)
                }

                c == '\''.code -> {
                    this.bump()
                    this.lifetimeish(idx0)
                }

                c == '"'.code -> {
                    this.bump()
                    this.stringLiteral(idx0)
                }

                c == '/'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '/'.code -> {
                            this.takeUntil { ch -> ch == '\n'.code }
                            continue
                        }

                        matchVal != null && matchVal.second == '*'.code -> {
                            this.bump() // Skip over the *
                            val r = this.blockComment(idx0)
                            if (r.isFailure) {
                                Result.failure(r.exceptionOrNull()!!)
                            } else {
                                continue
                            }
                        }

                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }

                c == '-'.code -> {
                    val matchVal = this.bump()
                    when {
                        matchVal != null && matchVal.second == '>'.code -> {
                            val idx1 = matchVal.first
                            this.bump()
                            Result.success(Spanned(idx0, Tok.MinusGreaterThan, idx1 + 1))
                        }

                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }

                isIdentifierStart(c) -> {
                    if (c == 'r'.code) {
                        // watch out for r"..." or r#"..."# strings
                        this.bump()
                        val matchVal = this.lookahead
                        when {
                            matchVal != null && (matchVal.second == '#'.code || matchVal.second == '"'.code) ->
                                this.regexLiteral(idx0)

                            else -> {
                                // due to the particulars of how identifierish works,
                                // it's ok that we already consumed the `r`, because the
                                // identifier will run from idx0 (the `r`) to the end
                                this.identifierish(idx0)
                            }
                        }
                    } else {
                        this.identifierish(idx0)
                    }
                }

                isUnicodeWhitespace(c) -> {
                    this.bump()
                    continue
                }

                else -> error(ErrorCode.UnrecognizedToken, idx0)
            }
        }
    }

    fun bump(): Pair<Int, Int>? {
        val next = this.chars.next()
        this.lookahead = next
        return next
    }

    fun rightArrow(idx0: Int): Result<Spanned<Tok>> {
        // we've seen =>, now we have to choose between:
        //
        // => code
        // =>? code
        // =>@L
        // =>@R
        val (idx1, c) =
            this.lookahead
                ?: return Result.failure(TokError(Error(location = idx0, code = ErrorCode.UnterminatedCode)))

        return when (c) {
            '@'.code -> {
                val matchVal = this.bump()
                when {
                    matchVal != null && matchVal.second == 'L'.code -> {
                        val idx2 = matchVal.first
                        this.bump()
                        Result.success(Spanned(idx0, Tok.EqualsGreaterThanLookahead, idx2 + 1))
                    }

                    matchVal != null && matchVal.second == 'R'.code -> {
                        val idx2 = matchVal.first
                        this.bump()
                        Result.success(Spanned(idx0, Tok.EqualsGreaterThanLookbehind, idx2 + 1))
                    }

                    else -> error(ErrorCode.UnrecognizedToken, idx0)
                }
            }

            '?'.code -> {
                this.bump()
                val idx2 = this.code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
                val code = this.sliceBytes(idx1 + 1, idx2)
                Result.success(Spanned(idx0, Tok.EqualsGreaterThanQuestionCode(code), idx2))
            }

            else -> {
                val idx2 = this.code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
                val code = this.sliceBytes(idx1, idx2)
                Result.success(Spanned(idx0, Tok.EqualsGreaterThanCode(code), idx2))
            }
        }
    }

    fun code(idx0: Int, openDelims: String, closeDelims: String): Result<Int> {
        // This is the interesting case. To find the end of the code,
        // we have to scan ahead, matching (), [], and {}, and looking
        // for a suitable terminator: `,`, `;`, `]`, `}`, or `)`.
        // Additionally we had to take into account that we can encounter an character literal
        // equal to one of delimiters.
        var balance = 0 // number of unclosed `(` etc
        while (true) {
            val matchVal = this.lookahead
            if (matchVal != null) {
                val (idx, c) = matchVal
                if (c == '"'.code) {
                    this.bump()
                    this.stringLiteral(idx).getOrElse { return Result.failure(it) } // discard the produced token
                    continue
                } else if (c == '\''.code) {
                    this.bump()
                    if (this.takeLifetimeOrCharacterLiteral() == null) {
                        return error(ErrorCode.UnterminatedCharacterLiteral, idx)
                    }
                    continue
                } else if (c == 'r'.code) {
                    this.bump()
                    val matchVal1 = this.lookahead
                    if (matchVal1 != null && matchVal1.second == '#'.code) {
                        this.regexLiteral(matchVal1.first).getOrElse { return Result.failure(it) }
                    }
                    continue
                } else if (c == '/'.code) {
                    this.bump()
                    val matchVal2 = this.lookahead
                    when {
                        matchVal2 != null && matchVal2.second == '/'.code -> {
                            this.takeUntil { ch -> ch == '\n'.code }
                        }

                        matchVal2 != null && matchVal2.second == '*'.code -> {
                            this.bump() // Skip over the *
                            this.blockComment(idx).getOrElse { return Result.failure(it) }
                        }

                        else -> Unit
                    }
                    continue
                } else if (containsAsciiScalar(openDelims, c)) {
                    balance += 1
                } else if (balance > 0) {
                    if (containsAsciiScalar(closeDelims, c)) {
                        balance -= 1
                    }
                } else {
                    check(balance == 0)

                    if (c == ','.code || c == ';'.code || containsAsciiScalar(closeDelims, c)) {
                        // Note: we do not consume the
                        // terminator. The code is everything *up
                        // to but not including* the terminating
                        // `,`, `;`, etc.
                        return Result.success(idx)
                    }
                }
            } else if (balance > 0) {
                // the input should not end with an
                // unbalanced number of `{` etc!
                return error(ErrorCode.UnterminatedCode, idx0)
            } else {
                check(balance == 0)
                return Result.success(this.textBytes.size)
            }

            this.bump()
        }
    }

    fun escape(idx0: Int): Result<Spanned<Tok>> =
        when (val idx1 = this.takeUntil { c -> c == '`'.code }) {
            null -> error(ErrorCode.UnterminatedEscape, idx0)
            else -> {
                this.bump() // consume the '`'
                val text: String = this.sliceBytes(idx0 + 1, idx1) // do not include the `` in the str
                Result.success(Spanned(idx0, Tok.Escape(text), idx1 + 1))
            }
        }

    fun takeLifetimeOrCharacterLiteral(): Int? {
        // Try to decide whether `'` is the start of a scope or a character literal.
        val forgetCharacter = { p: Pair<Int, Int> -> p.first }

        val matchVal = this.lookahead ?: return null
        val c = matchVal.second
        return if (c == '\\'.code) {
            // escape after `'` => it had to be character literal token, consume
            // the backslash and escaped character, then consume until `'`
            this.bump()
            this.bump()
            this.takeUntilAndConsumeTerminatingCharacter { ch -> ch == '\''.code }
        } else {
            // no escape, then we require to see next `'` or we assume it was scope
            this.bump()?.let { (idx, ch) ->
                if (ch == '\''.code) {
                    this.bump()?.let(forgetCharacter)
                } else {
                    idx
                }
            }
        }
    }

    fun stringOrCharLiteral(
        idx0: Int,
        quote: Int,
        variant: (String) -> Tok,
    ): Spanned<Tok>? {
        var escape = false
        val terminate = { c: Int ->
            if (escape) {
                escape = false
                false
            } else if (c == '\\'.code) {
                escape = true
                false
            } else {
                c == quote
            }
        }
        return when (val idx1 = this.takeUntil(terminate)) {
            null -> null
            else -> {
                this.bump() // consume the closing quote
                val text = this.sliceBytes(idx0 + 1, idx1) // do not include quotes in the str
                Spanned(idx0, variant(text), idx1 + 1)
            }
        }
    }

    fun stringLiteral(idx0: Int): Result<Spanned<Tok>> =
        when (val x = this.stringOrCharLiteral(idx0, '"'.code, Tok::StringLiteral)) {
            null -> error(ErrorCode.UnterminatedStringLiteral, idx0)
            else -> Result.success(x)
        }

    fun blockComment(idx0: Int): Result<Unit> {
        var depth = 1
        var state = BlockCommentState.Initial

        val endOfComment = { c: Int ->
            state =
                when {
                    (state == BlockCommentState.Initial || state == BlockCommentState.Star) && c == '*'.code ->
                        BlockCommentState.Star

                    (state == BlockCommentState.Initial || state == BlockCommentState.Slash) && c == '/'.code ->
                        BlockCommentState.Slash

                    state == BlockCommentState.Slash && c == '*'.code -> {
                        depth += 1
                        BlockCommentState.Initial
                    }

                    state == BlockCommentState.Star && c == '/'.code -> {
                        depth -= 1
                        if (depth == 0) {
                            BlockCommentState.Complete
                        } else {
                            BlockCommentState.Initial
                        }
                    }

                    else -> BlockCommentState.Initial
                }

            state == BlockCommentState.Complete
        }

        return when (this.takeUntil(endOfComment)) {
            null -> error(ErrorCode.UnterminatedBlockComment, idx0)
            else -> {
                this.bump()
                Result.success(Unit)
            }
        }
    }

    // parses `r#"..."#` (for some number of #), starts after the `r`
    // has been consumed; idx0 points at the `r`
    fun regexLiteral(idx0: Int): Result<Spanned<Tok>> {
        val idx1 = this.takeWhile { c -> c == '#'.code }
        val currentLookahead = this.lookahead
        return when {
            idx1 != null && currentLookahead == (idx1 to '"'.code) -> {
                this.bump()
                val hashes = idx1 - idx0 - 1
                var state = 0
                val endOfRegex = { c: Int ->
                    if (state > 0) {
                        // state N>0 means: observed n-1 hashes
                        if (c == '#'.code) {
                            state += 1
                        } else {
                            state = 0
                        }
                    }

                    // state 0 means: not yet seen the `"`
                    if (state == 0 && c == '"'.code) {
                        state = 1
                    }

                    state == (hashes + 1)
                }
                when (val idxEnd = this.takeUntil(endOfRegex)) {
                    null -> error(ErrorCode.UnterminatedStringLiteral, idx0)
                    else -> {
                        // idxEnd is the closing quote
                        this.bump()
                        val start = idx0 + 2 + hashes // skip the `r###"`
                        val end = idxEnd - hashes // skip the `###`.
                        Result.success(Spanned(idx0, Tok.RegexLiteral(this.sliceBytes(start, end)), idxEnd + 1))
                    }
                }
            }

            idx1 != null && currentLookahead != null && idx1 == currentLookahead.first &&
                isIdentifierStart(currentLookahead.second)
            -> {
                this.bump()
                this.identifierish(idx0)
            }

            idx1 != null -> error(ErrorCode.ExpectedStringLiteral, idx1)

            else -> error(ErrorCode.UnterminatedStringLiteral, idx0)
        }
    }

    // Saw a `'`, could either be: `'a` or `'a'`.
    fun lifetimeish(idx0: Int): Result<Spanned<Tok>> {
        val (_, c) =
            this.lookahead
                ?: return Result.failure(
                    TokError(Error(location = idx0, code = ErrorCode.UnterminatedCharacterLiteral)),
                )

        return if (isIdentifierStart(c)) {
            val (start, word, end) = this.word(idx0)
            val matchVal = this.lookahead
            when {
                matchVal != null && matchVal.second == '\''.code -> {
                    val idx2 = matchVal.first
                    this.bump()
                    val text = this.sliceBytes(idx0 + 1, idx2)
                    Result.success(Spanned(idx0, Tok.CharLiteral(text), idx2 + 1))
                }

                else -> Result.success(Spanned(start, Tok.Lifetime(word), end))
            }
        } else {
            when (val x = this.stringOrCharLiteral(idx0, '\''.code, Tok::CharLiteral)) {
                null -> error(ErrorCode.UnterminatedCharacterLiteral, idx0)
                else -> Result.success(x)
            }
        }
    }

    fun identifierish(idx0: Int): Result<Spanned<Tok>> {
        val (start, word, end) = this.word(idx0)

        if (word == "_") {
            return Result.success(Spanned(idx0, Tok.Underscore, idx0 + 1))
        }

        if (word == "r#_") {
            return Result.success(Spanned(idx0, Tok.Underscore, idx0 + 3))
        }

        if (word == "use") {
            val codeEnd = this.code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
            val code = this.sliceBytes(end, codeEnd)
            return Result.success(Spanned(start, Tok.Use(code), codeEnd))
        }

        val tok: Tok =
            // search for a keyword first; if none are found, this is
            // either a MacroId or an Id, depending on whether there
            // is a `<` immediately afterwards
            KEYWORDS
                .asSequence()
                .filter { (w, _) -> w == word }
                .map { (_, t) -> t }
                .firstOrNull()
                ?: run {
                    val matchVal = this.lookahead
                    if (matchVal != null && matchVal.second == '<'.code) {
                        Tok.MacroId(word)
                    } else {
                        Tok.Id(word)
                    }
                }

        return Result.success(Spanned(start, tok, end))
    }

    fun word(idx0: Int): Spanned<String> =
        when (val end = this.takeWhile(::isIdentifierContinue)) {
            null -> Spanned(idx0, this.sliceBytes(idx0, this.textBytes.size), this.textBytes.size)
            else -> Spanned(idx0, this.sliceBytes(idx0, end), end)
        }

    fun takeWhile(keepGoing: (Int) -> Boolean): Int? = this.takeUntil { c -> !keepGoing(c) }

    fun takeUntil(terminate: (Int) -> Boolean): Int? {
        while (true) {
            val (idx1, c) = this.lookahead ?: return null
            if (terminate(c)) {
                return idx1
            } else {
                this.bump()
            }
        }
    }

    fun takeUntilAndConsumeTerminatingCharacter(terminate: (Int) -> Boolean): Int? =
        this.takeUntil(terminate)?.let { this.bump()?.first }

    fun expectChar(c: Int): Result<Int>? {
        val (idx0, cc) = this.lookahead ?: return null

        this.bump()
        return if (c == cc) {
            Result.success(idx0)
        } else {
            error(ErrorCode.UnrecognizedToken, idx0)
        }
    }

    private fun nextShifted(): Result<Spanned<Tok>>? =
        when (val matchVal = this.nextUnshifted()) {
            null -> null
            else ->
                matchVal
                    .map { (l, t, r) -> Spanned(l + this.shift, t, r + this.shift) }
                    .recoverCatching { ex ->
                        val err = (ex as TokError).err
                        throw TokError(Error(location = err.location + this.shift, code = err.code))
                    }
        }

    private fun sliceBytes(start: Int, end: Int): String {
        if (start < 0 || end < start || end > this.textBytes.size) {
            throw IndexOutOfBoundsException(
                "UTF-8 byte span $start..$end out of bounds for length ${this.textBytes.size}",
            )
        }
        val validStart =
            start == 0 ||
                start == this.textBytes.size ||
                (this.textBytes[start].toInt() and 0xC0) != 0x80
        val validEnd =
            end == 0 ||
                end == this.textBytes.size ||
                (this.textBytes[end].toInt() and 0xC0) != 0x80
        if (!validStart || !validEnd) {
            throw IllegalArgumentException("UTF-8 byte span $start..$end is not on a char boundary")
        }
        return this.textBytes.decodeToString(
            startIndex = start,
            endIndex = end,
            throwOnInvalidSequence = true,
        )
    }

    companion object {
        fun new(text: String, shift: Int): Tokenizer = Tokenizer(text, shift)
    }
}

private enum class BlockCommentState {
    Initial,
    Slash,
    Star,
    Complete,
}

internal data class Spanned<T>(
    val start: Int,
    val value: T,
    val end: Int,
)

/**
 * Yields `(byteOffset, scalar)` pairs where `byteOffset` is the start offset in
 * UTF-8 bytes and `scalar` is the full 32-bit Unicode scalar value. Unpaired
 * UTF-16 surrogates throw [CharacterCodingException] rather than performing
 * lossy substitution.
 */
internal class CharIndices private constructor(
    private val s: String,
    private var charPos: Int,
    private var bytePos: Int,
) {
    constructor(s: String) : this(s = s, charPos = 0, bytePos = 0)

    fun clone(): CharIndices = CharIndices(s = this.s, charPos = this.charPos, bytePos = this.bytePos)

    fun next(): Pair<Int, Int>? {
        if (this.charPos >= this.s.length) return null
        val startByte = this.bytePos
        val c = this.s[this.charPos]
        val scalar: Int =
            if (c.isHighSurrogate()) {
                if (this.charPos + 1 >= this.s.length || !this.s[this.charPos + 1].isLowSurrogate()) {
                    throw CharacterCodingException()
                }
                val low = this.s[this.charPos + 1]
                this.charPos += 2
                ((c.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
            } else if (c.isLowSurrogate()) {
                throw CharacterCodingException()
            } else {
                this.charPos += 1
                c.code
            }
        this.bytePos +=
            when {
                scalar < 0x80 -> 1
                scalar < 0x800 -> 2
                scalar < 0x10000 -> 3
                else -> 4
            }
        return startByte to scalar
    }
}

private fun String.charIndices(): CharIndices = CharIndices(this)

private fun containsAsciiScalar(asciiDelims: String, scalar: Int): Boolean =
    scalar in 0..0x7F && asciiDelims.indexOf(scalar.toChar()) >= 0

private val XID_START_RANGES: List<ClassUnicodeRange> by lazy {
    parseUnicodeClassRanges("\\p{XID_Start}")
}

private val XID_CONTINUE_RANGES: List<ClassUnicodeRange> by lazy {
    parseUnicodeClassRanges("\\p{XID_Continue}")
}

private val WHITE_SPACE_RANGES: List<ClassUnicodeRange> by lazy {
    parseUnicodeClassRanges("\\p{White_Space}")
}

private fun parseUnicodeClassRanges(pattern: String): List<ClassUnicodeRange> {
    val hir =
        ParserBuilder
            .new()
            .build()
            .parse(pattern)
            .getOrThrow()
    val kind = hir.kind() as HirKind.Class
    val unicode = kind.value as Class.Unicode
    return unicode.value.ranges()
}

private fun containsUnicodeScalar(ranges: List<ClassUnicodeRange>, scalar: Int): Boolean {
    var lo = 0
    var hi = ranges.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val range = ranges[mid]
        val start = range.start()
        val end = range.end()
        when {
            scalar < start -> hi = mid - 1
            scalar > end -> lo = mid + 1
            else -> return true
        }
    }
    return false
}

internal fun isIdentifierStart(c: Int): Boolean =
    if (c < 0x80) {
        (c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) || c == '_'.code
    } else {
        containsUnicodeScalar(XID_START_RANGES, c)
    }

internal fun isIdentifierStart(c: Char): Boolean = isIdentifierStart(c.code)

internal fun isIdentifierContinue(c: Int): Boolean =
    if (c < 0x80) {
        (c in 'A'.code..'Z'.code) ||
            (c in 'a'.code..'z'.code) ||
            (c in '0'.code..'9'.code) ||
            c == '_'.code
    } else {
        containsUnicodeScalar(XID_CONTINUE_RANGES, c)
    }

internal fun isIdentifierContinue(c: Char): Boolean = isIdentifierContinue(c.code)

private fun isUnicodeWhitespace(c: Int): Boolean =
    if (c < 0x80) {
        c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code || c == 0x0B || c == 0x0C
    } else {
        containsUnicodeScalar(WHITE_SPACE_RANGES, c)
    }

private fun asciiDigitToIntOrNull(scalar: Int, radix: Int): Int? {
    val value =
        when (scalar) {
            in '0'.code..'9'.code -> scalar - '0'.code
            in 'a'.code..'z'.code -> scalar - 'a'.code + 10
            in 'A'.code..'Z'.code -> scalar - 'A'.code + 10
            else -> return null
        }
    return if (value < radix) value else null
}

private fun applyAsciiCharEscape(
    code: CharIndices,
    idx0: Int,
    offset: Int,
): Result<Int> {
    val (octalOffset, octal) =
        code.next() ?: return error(ErrorCode.UnterminatedAsciiEscape, idx0 + offset)

    val (_, hex) =
        code.next() ?: return error(ErrorCode.UnterminatedAsciiEscape, idx0 + octalOffset)

    val err = Error(location = idx0 + offset, code = ErrorCode.UnrecognizedEscape)

    val high = asciiDigitToIntOrNull(octal, 8) ?: return Result.failure(TokError(err))
    val low = asciiDigitToIntOrNull(hex, 16) ?: return Result.failure(TokError(err))

    return Result.success((high shl 4) or low)
}

private fun StringBuilder.appendScalar(scalar: Int) {
    if (scalar < 0x10000) {
        append(scalar.toChar())
    } else {
        val u = scalar - 0x10000
        append((0xD800 + (u ushr 10)).toChar())
        append((0xDC00 + (u and 0x3FF)).toChar())
    }
}

/**
 * Expand escape characters in a string literal, converting the source code
 * representation to the text it represents. The `idx0` argument should be the
 * position in the input stream of the first character of `text`, the position
 * after the opening double-quote.
 */
internal fun applyStringEscapes(code: String, idx0: Int): Result<String> {
    return if (!code.contains('\\')) {
        // Validate UTF-16 surrogate integrity even when no backslash is present.
        code.encodeToByteArray(throwOnInvalidSequence = true)
        Result.success(code)
    } else {
        val iter = code.charIndices()
        val text = StringBuilder()
        while (true) {
            val matchVal = iter.next() ?: break
            var ch = matchVal.second
            if (ch == '\\'.code) {
                // The parser should never have accepted an ill-formed string
                // literal, so we know it can't end in a backslash.
                val (offset, nextCh) = iter.next()!!
                ch =
                    when (nextCh) {
                        '\\'.code, '"'.code -> nextCh
                        'n'.code -> '\n'.code
                        'r'.code -> '\r'.code
                        't'.code -> '\t'.code
                        '0'.code -> 0
                        'x'.code ->
                            applyAsciiCharEscape(iter, idx0, offset)
                                .getOrElse { return Result.failure(it) }
                        else -> {
                            return error(ErrorCode.UnrecognizedEscape, idx0 + offset)
                        }
                    }
            }
            text.appendScalar(ch)
        }
        Result.success(text.toString())
    }
}
