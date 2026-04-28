// port-lint: source tok/mod.rs
//! A tokenizer for use in LALRPOP itself.
package io.github.kotlinmania.lalrpop.tok
import io.github.kotlinmania.lalrpop.lr1.Lookahead
import io.github.kotlinmania.lalrpop.normalize.normUtil.Symbols

data class Error(
    val location: Int,
    val code: ErrorCode,
)

enum class ErrorCode {
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

internal class TokError(val err: Error) : RuntimeException()

private fun <T> error(c: ErrorCode, l: Int): Result<T> =
    Result.failure(TokError(Error(location = l, code = c)))

sealed class Tok {
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
    object Type : Tok()
    object Where : Tok()
    object For : Tok()
    object Dyn : Tok()

    // Special keywords: these are accompanied by a series of
    // uninterpreted strings representing imports and stuff.
    data class Use(val s: String) : Tok()

    // Identifiers of various kinds:
    data class Escape(val s: String) : Tok()
    data class Id(val s: String) : Tok()
    data class MacroId(val s: String) : Tok()       // identifier followed immediately by `<`
    data class Lifetime(val s: String) : Tok()      // includes the `'`
    data class StringLiteral(val s: String) : Tok() // excludes the `"`
    data class CharLiteral(val s: String) : Tok()   // excludes the `'`
    data class RegexLiteral(val s: String) : Tok()  // excludes the `r"` and `"`

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
    data class EqualsGreaterThanCode(val s: String) : Tok()
    data class EqualsGreaterThanQuestionCode(val s: String) : Tok()
    object EqualsGreaterThanLookahead : Tok()
    object EqualsGreaterThanLookbehind : Tok()
    object Hash : Tok()
    object GreaterThan : Tok()
    object LeftBrace : Tok()
    object LeftBracket : Tok()
    object LeftParen : Tok()
    object LessThan : Tok()
    object Lookahead : Tok()  // @L
    object Lookbehind : Tok() // @R
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
    data class ShebangAttribute(val s: String) : Tok() // #![...]

    // Dummy tokens for parser sharing
    object StartGrammar : Tok()
    object StartPattern : Tok()
    object StartMatchMapping : Tok()
    object StartGrammarWhereClauses : Tok()
    object StartTypeRef : Tok()
}

class Tokenizer(val text: String, val shift: Int) : Iterator<Spanned<Tok>> {
    var chars: Int = 0
    var lookahead: Pair<Int, Char>? = null

    init {
        bump()
    }

    companion object {
        fun new(text: String, shift: Int): Tokenizer = Tokenizer(text, shift)

        private val KEYWORDS: List<Pair<String, Tok>> = listOf(
            "enum" to Tok.Enum,
            "extern" to Tok.Extern,
            "grammar" to Tok.Grammar,
            "match" to Tok.Match,
            "else" to Tok.Else,
            "if" to Tok.If,
            "mut" to Tok.Mut,
            "pub" to Tok.Pub,
            "in" to Tok.In,
            "type" to Tok.Type,
            "where" to Tok.Where,
            "for" to Tok.For,
            "dyn" to Tok.Dyn,
        )
    }

    private var pending: Result<Spanned<Tok>>? = null

    override fun hasNext(): Boolean {
        if (pending != null) return true
        pending = next_()
        return pending != null
    }

    override fun next(): Spanned<Tok> {
        val p = pending ?: next_() ?: throw NoSuchElementException()
        pending = null
        return p.getOrThrow()
    }

    internal fun nextResult(): Result<Spanned<Tok>>? {
        val p = pending
        if (p != null) { pending = null; return p }
        return next_()
    }

    private fun next_(): Result<Spanned<Tok>>? {
        return when (val r = nextUnshifted()) {
            null -> null
            else -> r.map { (l, t, rr) -> Spanned(l + shift, t, rr + shift) }.recoverCatching {
                val err = (it as TokError).err
                throw TokError(Error(location = err.location + shift, code = err.code))
            }
        }
    }

    fun bump(): Pair<Int, Char>? {
        lookahead = if (chars < text.length) {
            val p = chars to text[chars]
            chars += 1
            p
        } else {
            null
        }
        return lookahead
    }

    fun shebangAttribute(idx0: Int): Result<Spanned<Tok>> {
        expectChar('!')?.let {
            val inner = it.exceptionOrNull()
            if (inner != null) return error(ErrorCode.UnrecognizedToken, idx0)
        } ?: return error(ErrorCode.UnrecognizedToken, idx0)
        expectChar('[')?.let {
            val inner = it.exceptionOrNull()
            if (inner != null) return error(ErrorCode.UnterminatedAttribute, idx0)
        } ?: return error(ErrorCode.UnterminatedAttribute, idx0)
        var sqBracketCounter = 1
        while (true) {
            val (idx1, c) = lookahead ?: break
            when (c) {
                '[' -> {
                    bump()
                    sqBracketCounter += 1
                }
                ']' -> {
                    bump()
                    sqBracketCounter -= 1
                    when {
                        sqBracketCounter == 0 -> {
                            val idx2 = idx1 + 1
                            val data = text.substring(idx0, idx2)
                            bump()
                            return Result.success(Spanned(idx0, Tok.ShebangAttribute(data), idx2))
                        }
                        sqBracketCounter < 0 -> return error(ErrorCode.UnrecognizedToken, idx0)
                        else -> {}
                    }
                }
                '"' -> {
                    bump()
                    val r = stringLiteral(idx1)
                    if (r.isFailure) return r
                }
                '\n' -> return error(ErrorCode.UnrecognizedToken, idx0)
                else -> {
                    bump()
                }
            }
        }
        return error(ErrorCode.UnrecognizedToken, idx0)
    }

    fun nextUnshifted(): Result<Spanned<Tok>>? {
        while (true) {
            val (idx0, c) = lookahead ?: return null

            return when (c) {
                '&' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Ampersand, idx0 + 1))
                }
                '!' -> when (val n = bump()) {
                    null -> Result.success(Spanned(idx0, Tok.Bang, idx0 + 1))
                    else -> when (n.second) {
                        '=' -> {
                            bump()
                            Result.success(Spanned(idx0, Tok.BangEquals, n.first + 1))
                        }
                        '~' -> {
                            bump()
                            Result.success(Spanned(idx0, Tok.BangTilde, n.first + 1))
                        }
                        else -> Result.success(Spanned(idx0, Tok.Bang, idx0 + 1))
                    }
                }
                ':' -> when (val n = bump()) {
                    null -> Result.success(Spanned(idx0, Tok.Colon, idx0 + 1))
                    else -> if (n.second == ':') {
                        bump()
                        Result.success(Spanned(idx0, Tok.ColonColon, n.first + 1))
                    } else {
                        Result.success(Spanned(idx0, Tok.Colon, idx0 + 1))
                    }
                }
                ',' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Comma, idx0 + 1))
                }
                '.' -> when (val n = bump()) {
                    null -> error(ErrorCode.UnrecognizedToken, idx0)
                    else -> if (n.second == '.') {
                        bump()
                        Result.success(Spanned(idx0, Tok.DotDot, n.first + 1))
                    } else {
                        error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }
                '=' -> when (val n = bump()) {
                    null -> Result.success(Spanned(idx0, Tok.Equals, idx0 + 1))
                    else -> when (n.second) {
                        '=' -> {
                            bump()
                            Result.success(Spanned(idx0, Tok.EqualsEquals, n.first + 1))
                        }
                        '>' -> {
                            bump()
                            rightArrow(idx0)
                        }
                        else -> Result.success(Spanned(idx0, Tok.Equals, idx0 + 1))
                    }
                }
                '#' -> {
                    bump()
                    // first!: try shebangAttribute, fall back to Hash on failure
                    val fallbackChars = chars
                    val fallbackLookahead = lookahead
                    val result = shebangAttribute(idx0)
                    if (result.isSuccess) {
                        result
                    } else {
                        chars = fallbackChars
                        lookahead = fallbackLookahead
                        Result.success(Spanned(idx0, Tok.Hash, idx0 + 1))
                    }
                }
                '>' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.GreaterThan, idx0 + 1))
                }
                '{' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.LeftBrace, idx0 + 1))
                }
                '[' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.LeftBracket, idx0 + 1))
                }
                '(' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.LeftParen, idx0 + 1))
                }
                '<' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.LessThan, idx0 + 1))
                }
                '@' -> when (val n = bump()) {
                    null -> error(ErrorCode.UnrecognizedToken, idx0)
                    else -> when (n.second) {
                        'L' -> {
                            bump()
                            Result.success(Spanned(idx0, Tok.Lookahead, n.first + 1))
                        }
                        'R' -> {
                            bump()
                            Result.success(Spanned(idx0, Tok.Lookbehind, n.first + 1))
                        }
                        else -> error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }
                '+' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Plus, idx0 + 1))
                }
                '?' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Question, idx0 + 1))
                }
                '}' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.RightBrace, idx0 + 1))
                }
                ']' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.RightBracket, idx0 + 1))
                }
                ')' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.RightParen, idx0 + 1))
                }
                ';' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Semi, idx0 + 1))
                }
                '*' -> {
                    bump()
                    Result.success(Spanned(idx0, Tok.Star, idx0 + 1))
                }
                '~' -> when (val n = bump()) {
                    null -> error(ErrorCode.UnrecognizedToken, idx0)
                    else -> if (n.second == '~') {
                        bump()
                        Result.success(Spanned(idx0, Tok.TildeTilde, n.first + 1))
                    } else {
                        error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }
                '`' -> {
                    bump()
                    escape(idx0)
                }
                '\'' -> {
                    bump()
                    lifetimeish(idx0)
                }
                '"' -> {
                    bump()
                    stringLiteral(idx0)
                }
                '/' -> {
                    when (val n = bump()) {
                        null -> error(ErrorCode.UnrecognizedToken, idx0)
                        else -> when (n.second) {
                            '/' -> {
                                takeUntil { ch -> ch == '\n' }
                                continue
                            }
                            '*' -> {
                                bump() // Skip over the *
                                val r = blockComment(idx0)
                                if (r.isFailure) Result.failure(r.exceptionOrNull()!!)
                                else continue
                            }
                            else -> error(ErrorCode.UnrecognizedToken, idx0)
                        }
                    }
                }
                '-' -> when (val n = bump()) {
                    null -> error(ErrorCode.UnrecognizedToken, idx0)
                    else -> if (n.second == '>') {
                        bump()
                        Result.success(Spanned(idx0, Tok.MinusGreaterThan, n.first + 1))
                    } else {
                        error(ErrorCode.UnrecognizedToken, idx0)
                    }
                }
                else -> if (isIdentifierStart(c)) {
                    if (c == 'r') {
                        // watch out for r"..." or r#"..."# strings
                        bump()
                        val la = lookahead
                        if (la != null && (la.second == '#' || la.second == '"')) {
                            regexLiteral(idx0)
                        } else {
                            identifierish(idx0)
                        }
                    } else {
                        identifierish(idx0)
                    }
                } else if (c.isWhitespace()) {
                    bump()
                    continue
                } else {
                    error(ErrorCode.UnrecognizedToken, idx0)
                }
            }
        }
    }

    fun rightArrow(idx0: Int): Result<Spanned<Tok>> {
        // we have seen =>, now we have to choose between:
        //
        // => code
        // =>? code
        // =>@L
        // =>@R
        val la = lookahead ?: return Result.failure(TokError(Error(idx0, ErrorCode.UnterminatedCode)))
        val (idx1, c) = la

        return when (c) {
            '@' -> when (val n = bump()) {
                null -> error(ErrorCode.UnrecognizedToken, idx0)
                else -> when (n.second) {
                    'L' -> {
                        bump()
                        Result.success(Spanned(idx0, Tok.EqualsGreaterThanLookahead, n.first + 1))
                    }
                    'R' -> {
                        bump()
                        Result.success(Spanned(idx0, Tok.EqualsGreaterThanLookbehind, n.first + 1))
                    }
                    else -> error(ErrorCode.UnrecognizedToken, idx0)
                }
            }
            '?' -> {
                bump()
                val idx2 = code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
                val codeStr = text.substring(idx1 + 1, idx2)
                Result.success(Spanned(idx0, Tok.EqualsGreaterThanQuestionCode(codeStr), idx2))
            }
            else -> {
                val idx2 = code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
                val codeStr = text.substring(idx1, idx2)
                Result.success(Spanned(idx0, Tok.EqualsGreaterThanCode(codeStr), idx2))
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
            val la = lookahead
            if (la != null) {
                val (idx, c) = la
                when {
                    c == '"' -> {
                        bump()
                        val r = stringLiteral(idx)
                        if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
                        continue
                    }
                    c == '\'' -> {
                        bump()
                        if (takeLifetimeOrCharacterLiteral() == null) {
                            return error(ErrorCode.UnterminatedCharacterLiteral, idx)
                        }
                        continue
                    }
                    c == 'r' -> {
                        bump()
                        val la2 = lookahead
                        if (la2 != null && la2.second == '#') {
                            val r = regexLiteral(la2.first)
                            if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
                        }
                        continue
                    }
                    c == '/' -> {
                        bump()
                        val la2 = lookahead
                        when {
                            la2 != null && la2.second == '/' -> {
                                takeUntil { ch -> ch == '\n' }
                            }
                            la2 != null && la2.second == '*' -> {
                                bump() // Skip over the *
                                val r = blockComment(idx)
                                if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
                            }
                            else -> {}
                        }
                        continue
                    }
                    openDelims.indexOf(c) >= 0 -> {
                        balance += 1
                    }
                    balance > 0 -> {
                        if (closeDelims.indexOf(c) >= 0) {
                            balance -= 1
                        }
                    }
                    else -> {
                        // balance == 0
                        if (c == ',' || c == ';' || closeDelims.indexOf(c) >= 0) {
                            // Note: we do not consume the
                            // terminator. The code is everything *up
                            // to but not including* the terminating
                            // `,`, `;`, etc.
                            return Result.success(idx)
                        }
                    }
                }
            } else if (balance > 0) {
                // the input should not end with an
                // unbalanced number of `{` etc!
                return error(ErrorCode.UnterminatedCode, idx0)
            } else {
                // balance == 0
                return Result.success(text.length)
            }

            bump()
        }
    }

    fun escape(idx0: Int): Result<Spanned<Tok>> {
        return when (val idx1 = takeUntil { c -> c == '`' }) {
            null -> error(ErrorCode.UnterminatedEscape, idx0)
            else -> {
                bump() // consume the '`'
                val txt: String = text.substring(idx0 + 1, idx1) // do not include the `` in the str
                Result.success(Spanned(idx0, Tok.Escape(txt), idx1 + 1))
            }
        }
    }

    fun takeLifetimeOrCharacterLiteral(): Int? {
        // Try to decide whether `'` is the start of a lifetime or a character literal.
        val la = lookahead ?: return null
        val c = la.second

        return if (c == '\\') {
            // escape after `'` => it had to be character literal token, consume
            // the backslash and escaped character, then consume until `'`
            bump()
            bump()
            takeUntilAndConsumeTerminatingCharacter { cc -> cc == '\'' }
        } else {
            // no escape, then we require to see next `'` or we assume it was lifetime
            val n = bump() ?: return null
            val (idx, cc) = n
            if (cc == '\'') {
                bump()?.first
            } else {
                idx
            }
        }
    }

    fun stringOrCharLiteral(
        idx0: Int,
        quote: Char,
        variant: (String) -> Tok,
    ): Spanned<Tok>? {
        var escape = false
        val terminate = { c: Char ->
            if (escape) {
                escape = false
                false
            } else if (c == '\\') {
                escape = true
                false
            } else {
                c == quote
            }
        }
        return when (val idx1 = takeUntil(terminate)) {
            null -> null
            else -> {
                bump() // consume the closing quote
                val txt = text.substring(idx0 + 1, idx1) // do not include quotes in the str
                Spanned(idx0, variant(txt), idx1 + 1)
            }
        }
    }

    fun stringLiteral(idx0: Int): Result<Spanned<Tok>> {
        val r = stringOrCharLiteral(idx0, '"') { s -> Tok.StringLiteral(s) }
        return if (r != null) Result.success(r)
        else error(ErrorCode.UnterminatedStringLiteral, idx0)
    }

    fun blockComment(idx0: Int): Result<Unit> {
        var state = BlockCommentState.Initial

        var depth = 1

        val endOfComment = { c: Char ->
            state = when {
                (state == BlockCommentState.Initial || state == BlockCommentState.Star) && c == '*' -> BlockCommentState.Star
                (state == BlockCommentState.Initial || state == BlockCommentState.Slash) && c == '/' -> BlockCommentState.Slash
                state == BlockCommentState.Slash && c == '*' -> {
                    depth += 1
                    BlockCommentState.Initial
                }
                state == BlockCommentState.Star && c == '/' -> {
                    depth -= 1
                    if (depth == 0) BlockCommentState.Complete else BlockCommentState.Initial
                }
                else -> BlockCommentState.Initial
            }

            state == BlockCommentState.Complete
        }

        return when (takeUntil(endOfComment)) {
            null -> error(ErrorCode.UnterminatedBlockComment, idx0)
            else -> {
                bump()
                Result.success(Unit)
            }
        }
    }

    // parses `r#"..."#` (for some number of #), starts after the `r`
    // has been consumed; idx0 points at the `r`
    fun regexLiteral(idx0: Int): Result<Spanned<Tok>> {
        val idx1 = takeWhile { c -> c == '#' }
        return when {
            idx1 != null && lookahead == (idx1 to '"') -> {
                bump()
                val hashes = idx1 - idx0 - 1
                var state = 0
                val endOfRegex = { c: Char ->
                    if (state > 0) {
                        // state N>0 means: observed n-1 hashes
                        if (c == '#') {
                            state += 1
                        } else {
                            state = 0
                        }
                    }

                    // state 0 means: not yet seen the `"`
                    if (state == 0 && c == '"') {
                        state = 1
                    }

                    state == (hashes + 1)
                }
                when (val idxEnd = takeUntil(endOfRegex)) {
                    null -> error(ErrorCode.UnterminatedStringLiteral, idx0)
                    else -> {
                        // idxEnd is the closing quote
                        bump()
                        val start = idx0 + 2 + hashes // skip the `r###"`
                        val end = idxEnd - hashes // skip the `###`.
                        Result.success(Spanned(idx0, Tok.RegexLiteral(text.substring(start, end)), idxEnd + 1))
                    }
                }
            }
            idx1 != null && run {
                val la = lookahead
                la != null && idx1 == la.first && isIdentifierStart(la.second)
            } -> {
                bump()
                identifierish(idx0)
            }
            idx1 != null -> error(ErrorCode.ExpectedStringLiteral, idx1)
            else -> error(ErrorCode.UnterminatedStringLiteral, idx0)
        }
    }

    // Saw a `'`, could either be: `'a` or `'a'`.
    fun lifetimeish(idx0: Int): Result<Spanned<Tok>> {
        val la = lookahead ?: return Result.failure(TokError(Error(idx0, ErrorCode.UnterminatedCharacterLiteral)))
        val c = la.second

        return if (isIdentifierStart(c)) {
            val (start, word, end) = word(idx0)
            val la2 = lookahead
            if (la2 != null && la2.second == '\'') {
                bump()
                val txt = text.substring(idx0 + 1, la2.first)
                Result.success(Spanned(idx0, Tok.CharLiteral(txt), la2.first + 1))
            } else {
                Result.success(Spanned(start, Tok.Lifetime(word), end))
            }
        } else {
            val r = stringOrCharLiteral(idx0, '\'') { s -> Tok.CharLiteral(s) }
            if (r != null) Result.success(r)
            else error(ErrorCode.UnterminatedCharacterLiteral, idx0)
        }
    }

    fun identifierish(idx0: Int): Result<Spanned<Tok>> {
        val (start, word, end) = word(idx0)

        if (word == "_") {
            return Result.success(Spanned(idx0, Tok.Underscore, idx0 + 1))
        }

        if (word == "r#_") {
            return Result.success(Spanned(idx0, Tok.Underscore, idx0 + 3))
        }

        if (word == "use") {
            val codeEnd = code(idx0, "([{", "}])").getOrElse { return Result.failure(it) }
            val codeStr = text.substring(end, codeEnd)
            return Result.success(Spanned(start, Tok.Use(codeStr), codeEnd))
        }

        val tok: Tok =
            // search for a keyword first; if none are found, this is
            // either a MacroId or an Id, depending on whether there
            // is a `<` immediately afterwards
            KEYWORDS.asSequence()
                .filter { (w, _) -> w == word }
                .map { (_, t) -> t }
                .firstOrNull()
                ?: run {
                    val la = lookahead
                    if (la != null && la.second == '<') Tok.MacroId(word)
                    else Tok.Id(word)
                }

        return Result.success(Spanned(start, tok, end))
    }

    fun word(idx0: Int): Spanned<String> {
        return when (val end = takeWhile(::isIdentifierContinue)) {
            null -> Spanned(idx0, text.substring(idx0), text.length)
            else -> Spanned(idx0, text.substring(idx0, end), end)
        }
    }

    fun takeWhile(keepGoing: (Char) -> Boolean): Int? {
        return takeUntil { c -> !keepGoing(c) }
    }

    fun takeUntil(terminate: (Char) -> Boolean): Int? {
        while (true) {
            val (idx1, c) = lookahead ?: return null
            if (terminate(c)) {
                return idx1
            } else {
                bump()
            }
        }
    }

    fun takeUntilAndConsumeTerminatingCharacter(terminate: (Char) -> Boolean): Int? {
        return takeUntil(terminate)?.let { bump()?.first }
    }

    fun expectChar(c: Char): Result<Int>? {
        val la = lookahead ?: return null
        val (idx0, cc) = la

        bump()
        return if (c == cc) {
            Result.success(idx0)
        } else {
            error(ErrorCode.UnrecognizedToken, idx0)
        }
    }
}

private enum class BlockCommentState {
    Initial,
    Slash,
    Star,
    Complete,
}

data class Spanned<T>(val start: Int, val value: T, val end: Int)

fun isIdentifierStart(c: Char): Boolean =
    isXidStart(c) || c == '_'

fun isIdentifierContinue(c: Char): Boolean =
    isXidContinue(c) || c == '_'

// Approximation of unicodeXid::UnicodeXID; LALRPOP grammar is predominantly ASCII.
private fun isXidStart(c: Char): Boolean =
    c.isLetter()

private fun isXidContinue(c: Char): Boolean =
    c.isLetterOrDigit()

private fun applyAsciiCharEscape(
    code: CharIndicesIter,
    idx0: Int,
    offset: Int,
): Result<Char> {
    val first = code.next() ?: return error(ErrorCode.UnterminatedAsciiEscape, idx0 + offset)
    val octalOffset = first.first
    val octal = first.second

    val second = code.next() ?: return error(ErrorCode.UnterminatedAsciiEscape, idx0 + octalOffset)
    val hex = second.second

    val err = Error(location = idx0 + offset, code = ErrorCode.UnrecognizedEscape)

    val high = Character.digitOrNull(octal, 8) ?: return Result.failure(TokError(err))
    val low = Character.digitOrNull(hex, 16) ?: return Result.failure(TokError(err))

    return Result.success(((high.toInt() shl 4) or low.toInt()).toChar())
}

private object Character {
    fun digitOrNull(c: Char, radix: Int): UByte? {
        val d = c.digitToIntOrNull(radix) ?: return null
        return d.toUByte()
    }
}

/**
 * Port of the upstream `str::charIndices()`. Yields `(byteOffset, char)` pairs,
 * where `byteOffset` is the position of the start of each character in the
 * string UTF-8 encoding — matching the upstream indexing, not Kotlin native
 * UTF-16 code-unit indexing. Surrogate pairs are collapsed so one `next()`
 * call produces one code point; the emitted `Char` loses any supplementary
 * plane info but the byte offset still advances by 4 bytes.
 */
private class CharIndicesIter(private val s: String) {
    private var charPos: Int = 0
    private var bytePos: Int = 0

    fun next(): Pair<Int, Char>? {
        if (charPos >= s.length) return null
        val startByte = bytePos
        val c = s[charPos]
        val codePoint: Int = if (c.isHighSurrogate() && charPos + 1 < s.length) {
            val low = s[charPos + 1]
            charPos += 2
            ((c.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
        } else {
            charPos += 1
            c.code
        }
        bytePos += when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        return startByte to c
    }
}

/** Expand escape characters in a string literal, converting the source code
 * representation to the text it represents. The `idx0` argument should be the
 * position in the input stream of the first character of `text`, the position
 * after the opening double-quote. */
fun applyStringEscapes(code: String, idx0: Int): Result<String> {
    return if (!code.contains('\\')) {
        Result.success(code)
    } else {
        val iter = CharIndicesIter(code)
        val text = StringBuilder()
        while (true) {
            val pair = iter.next() ?: break
            var ch = pair.second
            if (ch == '\\') {
                // The parser should never have accepted an ill-formed string
                // literal, so we know it cannot end in a backslash.
                val (offset, nextCh) = iter.next()!!
                ch = when (nextCh) {
                    '\\', '"' -> nextCh
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '0' -> '\u0000'
                    'x' -> applyAsciiCharEscape(iter, idx0, offset).getOrElse { return Result.failure(it) }
                    else -> {
                        return error(ErrorCode.UnrecognizedEscape, idx0 + offset)
                    }
                }
            }
            text.append(ch)
        }
        Result.success(text.toString())
    }
}
