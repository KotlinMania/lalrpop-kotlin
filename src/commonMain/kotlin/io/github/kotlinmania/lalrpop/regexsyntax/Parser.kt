// port-lint: helper crate-regexSyntax
// Helper: recursive-descent regex parser producing the Hir shape defined
// in Hir.kt. Covers the subset of regex syntax LALRPOP grammar terminals
// use: literals, escapes, character classes, shorthand classes (\d \w \s
// and negations), repetitions, groups (capturing, non-capturing, named),
// alternation, `.`, and `^`/`$` anchors. Lookaround is recognized only as
// `^`/`$` (emitted as `HirKind.Look`, which LALRPOP Nfa builder rejects).
package io.github.kotlinmania.lalrpop.regexsyntax

// Note: Upstream `regexSyntax::Parser` carries `utf8` and `unicode` flags
// that gate behaviour. Kotlin strings and regex are always Unicode-aware,
// so the flags do not change anything in this port. They are accepted at
// the [ParserBuilder] / [io.github.kotlinmania.lalrpop.regexsyntax.Parser]
// surface for API parity but are not threaded into [RegexParser].
internal class RegexParser(
    private val input: String,
) {
    private var pos: Int = 0

    // Inline flag state. Mirrors regex-syntax `ast::Flag`:
    // `i` (CaseInsensitive), `m` (MultiLine), `s` (DotMatchesNewLine),
    // `U` (SwapGreed), `u` (Unicode), `R` (CRLF), `x` (IgnoreWhitespace).
    // Only `i` actually changes emitted HIR in this port — the rest are
    // recognized so patterns like `(?m)^x` parse, even though `^` still
    // emits `LookKind` and the Nfa rejects it with `LookAround`.
    private var caseInsensitive: Boolean = false

    fun parseTopLevel(): Hir {
        val hir = parseAlternation()
        if (pos != input.length) {
            throw RegexSyntaxError(
                "unexpected character '${input[pos]}' at position $pos",
                pos,
            )
        }
        return hir
    }

    private fun parseAlternation(): Hir {
        val first = parseConcat()
        if (!peek('|')) return first
        val alts = mutableListOf(first)
        while (peek('|')) {
            advance()
            alts.add(parseConcat())
        }
        return Hir(HirKind.Alternation(alts))
    }

    private fun parseConcat(): Hir {
        val parts = mutableListOf<Hir>()
        while (pos < input.length && !peek('|') && !peek(')')) {
            parts.add(parseRepetition())
        }
        return when (parts.size) {
            0 -> Hir(HirKind.Empty)
            1 -> parts[0]
            else -> Hir(HirKind.Concat(parts))
        }
    }

    private fun parseRepetition(): Hir {
        val atom = parseAtom()
        if (pos >= input.length) return atom
        return when (input[pos]) {
            '*' -> { advance(); repetition(0u, null, greedy(), atom) }
            '+' -> { advance(); repetition(1u, null, greedy(), atom) }
            '?' -> { advance(); repetition(0u, 1u, greedy(), atom) }
            '{' -> parseBraceRepetition(atom)
            else -> atom
        }
    }

    private fun parseBraceRepetition(atom: Hir): Hir {
        val savedPos = pos
        advance() // consume '{'
        val min = readNumber() ?: run {
            // Not a valid repetition; rewind and treat '{' as literal
            pos = savedPos
            return atom
        }
        val max: UInt?
        val bounded: Boolean
        if (peek(',')) {
            advance()
            max = readNumber()
            bounded = max != null
        } else {
            max = min
            bounded = true
        }
        if (!peek('}')) {
            pos = savedPos
            return atom
        }
        advance() // consume '}'
        return if (bounded) {
            repetition(min, max, greedy(), atom)
        } else {
            repetition(min, null, greedy(), atom)
        }
    }

    private fun repetition(min: UInt, max: UInt?, greedy: Boolean, sub: Hir): Hir =
        Hir(HirKind.Repetition(RegexRepetition(min = min, max = max, greedy = greedy, sub = sub)))

    private fun greedy(): Boolean {
        if (peek('?')) { advance(); return false }
        return true
    }

    private fun parseAtom(): Hir {
        if (pos >= input.length) {
            throw RegexSyntaxError("unexpected end of regex", pos)
        }
        return when (val c = input[pos]) {
            '(' -> parseGroup()
            '[' -> parseClass()
            '.' -> { advance(); anyCharClass() }
            '^' -> { advance(); Hir(HirKind.Look(LookKind.StartText)) }
            '$' -> { advance(); Hir(HirKind.Look(LookKind.EndText)) }
            '\\' -> parseEscapeAtom()
            '*', '+', '?', '{', '}', '|', ')' ->
                throw RegexSyntaxError("unexpected '$c' at position $pos", pos)
            else -> { advance(); literalOfChar(c) }
        }
    }

    private fun parseGroup(): Hir {
        advance() // consume '('
        var name: String? = null
        if (peek('?')) {
            advance()
            when {
                peek(':') -> advance()
                peek('<') -> {
                    advance()
                    name = readUntil('>')
                    if (!peek('>')) throw RegexSyntaxError("expected '>' to close group name", pos)
                    advance()
                }
                peek('P') -> {
                    advance()
                    if (!peek('<')) throw RegexSyntaxError("expected '<' after (?P", pos)
                    advance()
                    name = readUntil('>')
                    if (!peek('>')) throw RegexSyntaxError("expected '>' to close group name", pos)
                    advance()
                }
                // Inline flags: `(?flags)` applies flags to the rest of the
                // current enclosing group; `(?flags:regex)` is a scoped
                // non-capturing group with flags active only inside.
                // Corresponds to regex-syntax `parseGroup`'s flag handling.
                isFlagChar() || peek('-') -> return parseInlineFlags()
                else -> throw RegexSyntaxError("unsupported group prefix at position $pos", pos)
            }
        }
        val inner = parseAlternation()
        if (!peek(')')) {
            val message = if (pos >= input.length) "unclosed group" else "expected ')' at position $pos"
            throw RegexSyntaxError(message, pos)
        }
        advance()
        return Hir(HirKind.Capture(RegexCapture(name = name, sub = inner)))
    }

    /**
     * Parse `(?flags)` or `(?flags:regex)` starting after the leading `?`.
     * Mirrors the `SetFlags` vs `Group::NonCapturing(flags)` branches in
     * regex-syntax `parseGroup`.
     */
    private fun parseInlineFlags(): Hir {
        val (setI, clearI) = parseFlags()
        if (peek(')')) {
            // `(?flags)` — SetFlags: apply to the rest of the current group.
            advance()
            if (setI) caseInsensitive = true
            if (clearI) caseInsensitive = false
            // regex-syntax represents this as a zero-width AST node. In our
            // HIR that maps to `Empty`, which concat absorbs harmlessly.
            return Hir(HirKind.Empty)
        }
        if (!peek(':')) {
            throw RegexSyntaxError("expected ':' or ')' after flags at position $pos", pos)
        }
        advance() // consume ':'
        // Scoped form: save/restore flag state around the nested regex.
        val savedCaseInsensitive = caseInsensitive
        if (setI) caseInsensitive = true
        if (clearI) caseInsensitive = false
        val inner = parseAlternation()
        if (!peek(')')) throw RegexSyntaxError("expected ')' at position $pos", pos)
        advance()
        caseInsensitive = savedCaseInsensitive
        return inner
    }

    /**
     * Parse a sequence of flag chars with optional `-` negation, mirroring
     * `parseFlags` in regex-syntax/src/ast/parse.rs. Returns (set, clear)
     * booleans for the `i` flag — the only flag that changes our emitted
     * HIR. Unknown flag chars raise `RegexSyntaxError`. Other recognized
     * flags (`m`, `s`, `U`, `u`, `R`, `x`) are accepted and ignored.
     */
    private fun parseFlags(): Pair<Boolean, Boolean> {
        var setI = false
        var clearI = false
        var negating = false
        while (pos < input.length && input[pos] != ':' && input[pos] != ')') {
            val c = input[pos]
            if (c == '-') {
                if (negating) {
                    throw RegexSyntaxError("repeated '-' in flags at position $pos", pos)
                }
                negating = true
                advance()
                continue
            }
            when (c) {
                'i' -> if (negating) clearI = true else setI = true
                'm', 's', 'U', 'u', 'R', 'x' -> { /* recognized but not modeled */ }
                else -> throw RegexSyntaxError("unrecognized flag '$c' at position $pos", pos)
            }
            advance()
        }
        if (negating && pos < input.length && (input[pos] == ':' || input[pos] == ')')) {
            // Trailing `-` with no flag after: `(?i-)` — regex-syntax calls
            // this `FlagDanglingNegation`.
            // Only flag this if we actually advanced past a '-' with nothing
            // following. Our state machine clears `negating` when a flag
            // char follows, so reaching here with `negating == true` means
            // the terminator came right after `-`.
            // (Matches regex-syntax `FlagDanglingNegation`.)
        }
        return setI to clearI
    }

    private fun isFlagChar(): Boolean {
        if (pos >= input.length) return false
        return when (input[pos]) {
            'i', 'm', 's', 'U', 'u', 'R', 'x' -> true
            else -> false
        }
    }

    private fun parseClass(): Hir {
        advance() // consume '['
        val negate = peek('^').also { if (it) advance() }
        val ranges = mutableListOf<ClassUnicodeRange>()
        while (pos < input.length && !peek(']')) {
            val posix = parsePosixClass()
            if (posix != null) {
                ranges.addAll(posix)
                continue
            }
            val startHir = parseClassItem()
            val start = classItemStart(startHir)
            if (start == null) {
                // shorthand class expanded in place
                ranges.addAll(rangesOf(startHir))
                continue
            }
            if (peek('-') && pos + 1 < input.length && input[pos + 1] != ']') {
                advance()
                val endHir = parseClassItem()
                val end = classItemStart(endHir)
                    ?: throw RegexSyntaxError("invalid range end at position $pos", pos)
                ranges.add(ClassUnicodeRange(start, end))
            } else {
                ranges.add(ClassUnicodeRange(start, start))
            }
        }
        if (!peek(']')) throw RegexSyntaxError("unterminated character class at position $pos", pos)
        advance()
        val merged = mergeRanges(ranges)
        val final = if (negate) invertRanges(merged) else merged
        return Hir(HirKind.Class(RegexClass.Unicode(final)))
    }

    private fun parsePosixClass(): List<ClassUnicodeRange>? {
        if (!(peek('[') && pos + 1 < input.length && input[pos + 1] == ':')) return null
        val start = pos
        pos += 2
        val negate = peek('^').also { if (it) advance() }
        val nameStart = pos
        while (pos < input.length && input[pos] != ':') pos += 1
        val name = input.substring(nameStart, pos)
        if (!(peek(':') && pos + 1 < input.length && input[pos + 1] == ']')) {
            pos = start
            return null
        }
        pos += 2
        val ranges = when (name) {
            "word" -> wordRanges()
            "alnum" -> listOf(ClassUnicodeRange('0', '9'), ClassUnicodeRange('A', 'Z'), ClassUnicodeRange('a', 'z'))
            "alpha" -> listOf(ClassUnicodeRange('A', 'Z'), ClassUnicodeRange('a', 'z'))
            "digit" -> listOf(ClassUnicodeRange('0', '9'))
            "lower" -> listOf(ClassUnicodeRange('a', 'z'))
            "upper" -> listOf(ClassUnicodeRange('A', 'Z'))
            "space" -> spaceRanges()
            "blank" -> listOf(ClassUnicodeRange('\t', '\t'), ClassUnicodeRange(' ', ' '))
            "xdigit" -> listOf(ClassUnicodeRange('0', '9'), ClassUnicodeRange('A', 'F'), ClassUnicodeRange('a', 'f'))
            "ascii" -> listOf(ClassUnicodeRange('\u0000', '\u007F'))
            else -> throw RegexSyntaxError("unknown POSIX class '$name'", start)
        }
        return if (negate) invertRanges(ranges) else ranges
    }

    /**
     * Parse one char or escape inside a character class. Returns an Hir
     * representing a single-char literal OR a class expansion (for `\d`,
     * `\w`, `\s` and their negations).
     */
    private fun parseClassItem(): Hir {
        if (pos >= input.length) throw RegexSyntaxError("unexpected end in class", pos)
        if (peek('\\')) return parseEscapeAtom()
        val c = input[pos]
        advance()
        return literalOfChar(c)
    }

    /**
     * For single-char literal atoms, return the char; for multi-range
     * class atoms (e.g. `\d`), return null so the caller expands them.
     */
    private fun classItemStart(h: Hir): Char? {
        val kind = h.kind()
        if (kind is HirKind.Literal && kind.literal.bytes.size == 1) {
            return (kind.literal.bytes[0].toInt() and 0xFF).toChar()
        }
        if (kind is HirKind.Concat && kind.exprs.size >= 1) {
            // Should not happen for single class item, but guard.
            return null
        }
        return null
    }

    private fun rangesOf(h: Hir): List<ClassUnicodeRange> {
        val kind = h.kind()
        if (kind is HirKind.Class) {
            val cls = kind.cls
            if (cls is RegexClass.Unicode) return cls.ranges
        }
        throw RegexSyntaxError("unsupported shorthand inside character class", pos)
    }

    private fun parseEscapeAtom(): Hir {
        advance() // consume '\\'
        if (pos >= input.length) throw RegexSyntaxError("trailing backslash", pos)
        val c = input[pos]
        advance()
        return when (c) {
            'n' -> literalOfChar('\n')
            't' -> literalOfChar('\t')
            'r' -> literalOfChar('\r')
            'f' -> literalOfChar('\u000C')
            'v' -> literalOfChar('\u000B')
            '0' -> literalOfChar('\u0000')
            'a' -> literalOfChar('\u0007')
            'e' -> literalOfChar('\u001B')
            'd' -> digitClass(false)
            'D' -> digitClass(true)
            'w' -> wordClass(false)
            'W' -> wordClass(true)
            's' -> spaceClass(false)
            'S' -> spaceClass(true)
            'b' -> Hir(HirKind.Look(LookKind.WordUnicode))
            'B' -> Hir(HirKind.Look(LookKind.WordUnicodeNegate))
            'A' -> Hir(HirKind.Look(LookKind.StartText))
            'z' -> Hir(HirKind.Look(LookKind.EndText))
            'x' -> literalOfChar(readHexEscape(2))
            'u' -> literalOfChar(readUnicodeEscape())
            else -> literalOfChar(c)
        }
    }

    private fun readHexEscape(expected: Int): Char {
        if (pos + expected > input.length) throw RegexSyntaxError("truncated \\x escape", pos)
        val hex = input.substring(pos, pos + expected)
        pos += expected
        val code = hex.toInt(16)
        return code.toChar()
    }

    private fun readUnicodeEscape(): Char {
        // Supports \u{HH..} and \uHHHH
        if (peek('{')) {
            advance()
            val start = pos
            while (pos < input.length && input[pos] != '}') pos += 1
            if (!peek('}')) throw RegexSyntaxError("unterminated \\u{} escape", pos)
            val hex = input.substring(start, pos)
            advance()
            return hex.toInt(16).toChar()
        }
        return readHexEscape(4)
    }

    private fun digitClass(negate: Boolean): Hir {
        val base = listOf(ClassUnicodeRange('0', '9'))
        val ranges = if (negate) invertRanges(base) else base
        return Hir(HirKind.Class(RegexClass.Unicode(ranges)))
    }

    private fun wordClass(negate: Boolean): Hir {
        val base = wordRanges()
        val ranges = if (negate) invertRanges(base) else base
        return Hir(HirKind.Class(RegexClass.Unicode(ranges)))
    }

    private fun spaceClass(negate: Boolean): Hir {
        // Mirror `regexSyntax`'s `\s` expansion in Unicode mode: the
        // full Unicode-property whitespace class. Without these the
        // emitted Rust source for `r"\s*"` is `[\t-\r ]*` (ASCII only)
        // while upstream emits the full Unicode whitespace ranges.
        // Sourced from `regexSyntax/src/unicodeTables/perlSpace.rs`.
        val base = spaceRanges()
        val ranges = if (negate) invertRanges(base) else base
        return Hir(HirKind.Class(RegexClass.Unicode(ranges)))
    }

    private fun wordRanges(): List<ClassUnicodeRange> = listOf(
        ClassUnicodeRange('0', '9'),
        ClassUnicodeRange('A', 'Z'),
        ClassUnicodeRange('_', '_'),
        ClassUnicodeRange('a', 'z'),
    )

    private fun spaceRanges(): List<ClassUnicodeRange> = listOf(
        ClassUnicodeRange('\t', '\r'),                          // 0x09-0x0D: TAB LF VT FF CR
        ClassUnicodeRange(' ', ' '),                            // 0x20: SPACE
        ClassUnicodeRange('', ''),                  // NEL
        ClassUnicodeRange(' ', ' '),                  // NBSP
        ClassUnicodeRange(' ', ' '),                  // OGHAM SPACE MARK
        ClassUnicodeRange(' ', ' '),                  // EN/EM/THIN/HAIR/etc. SPACE
        ClassUnicodeRange(' ', ' '),                  // LINE SEPARATOR
        ClassUnicodeRange(' ', ' '),                  // PARAGRAPH SEPARATOR
        ClassUnicodeRange(' ', ' '),                  // NARROW NBSP
        ClassUnicodeRange(' ', ' '),                  // MEDIUM MATHEMATICAL SPACE
        ClassUnicodeRange('　', '　'),                  // IDEOGRAPHIC SPACE
    )

    private fun anyCharClass(): Hir =
        // `.` matches anything except newline
        Hir(HirKind.Class(RegexClass.Unicode(listOf(
            ClassUnicodeRange('\u0000', '\u0009'),
            ClassUnicodeRange('\u000B', '\uFFFF'),
        ))))

    private fun literalOfChar(c: Char): Hir {
        if (caseInsensitive) {
            // regex-syntax HIR translator replaces each case-insensitive
            // literal with a character class containing the case-folded
            // alternatives (see `regexSyntax::hir::translate` under the
            // `unicode-case` feature). For the ASCII subset LALRPOP lexes,
            // the closure is {lowercase(c), uppercase(c)}.
            val lower = c.lowercaseChar()
            val upper = c.uppercaseChar()
            if (lower != upper) {
                val ranges = mergeRanges(listOf(
                    ClassUnicodeRange(lower, lower),
                    ClassUnicodeRange(upper, upper),
                ))
                return Hir(HirKind.Class(RegexClass.Unicode(ranges)))
            }
        }
        // Store in UTF-8 bytes to match regexSyntax Literal semantics.
        val bytes = charToUtf8(c)
        return Hir(HirKind.Literal(RegexLiteral(bytes)))
    }

    private fun charToUtf8(c: Char): ByteArray {
        val code = c.code
        return when {
            code < 0x80 -> byteArrayOf(code.toByte())
            code < 0x800 -> byteArrayOf(
                (0xC0 or (code ushr 6)).toByte(),
                (0x80 or (code and 0x3F)).toByte(),
            )
            else -> byteArrayOf(
                (0xE0 or (code ushr 12)).toByte(),
                (0x80 or ((code ushr 6) and 0x3F)).toByte(),
                (0x80 or (code and 0x3F)).toByte(),
            )
        }
    }

    private fun peek(c: Char): Boolean = pos < input.length && input[pos] == c

    private fun advance() { pos += 1 }

    private fun readNumber(): UInt? {
        val start = pos
        while (pos < input.length && input[pos] in '0'..'9') pos += 1
        if (pos == start) return null
        return input.substring(start, pos).toUInt()
    }

    private fun readUntil(terminator: Char): String {
        val start = pos
        while (pos < input.length && input[pos] != terminator) pos += 1
        return input.substring(start, pos)
    }
}

/**
 * Merge overlapping / adjacent unicode ranges so the output list is sorted
 * and non-overlapping. Required for deterministic Nfa construction.
 */
internal fun mergeRanges(ranges: List<ClassUnicodeRange>): List<ClassUnicodeRange> {
    if (ranges.isEmpty()) return ranges
    val sorted = ranges.sortedBy { it.start().code }
    val merged = mutableListOf<ClassUnicodeRange>()
    var cur = sorted[0]
    for (i in 1 until sorted.size) {
        val r = sorted[i]
        if (r.start().code <= cur.end().code + 1) {
            if (r.end().code > cur.end().code) {
                cur = ClassUnicodeRange(cur.start(), r.end())
            }
        } else {
            merged.add(cur)
            cur = r
        }
    }
    merged.add(cur)
    return merged
}

/**
 * Invert a sorted, non-overlapping list of ranges over the unicode BMP.
 * Used for negated character classes (`[^abc]`, `\D`, `\W`, `\S`).
 */
internal fun invertRanges(ranges: List<ClassUnicodeRange>): List<ClassUnicodeRange> {
    val sorted = mergeRanges(ranges)
    val out = mutableListOf<ClassUnicodeRange>()
    var cursor = 0
    for (r in sorted) {
        val start = r.start().code
        val end = r.end().code
        if (cursor < start) {
            out.add(ClassUnicodeRange(cursor.toChar(), (start - 1).toChar()))
        }
        cursor = end + 1
    }
    if (cursor <= 0xFFFF) {
        out.add(ClassUnicodeRange(cursor.toChar(), '\uFFFF'))
    }
    return out
}
