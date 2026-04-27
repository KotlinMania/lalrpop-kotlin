// port-lint: source src/parser/mod.rs
package io.github.kotlinmania.lalrpop.parser

import io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parseTree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeRef
import io.github.kotlinmania.lalrpop.grammar.parseTree.WhereClause
import io.github.kotlinmania.lalrpop.grammar.pattern.Pattern
import io.github.kotlinmania.lalrpop.runtime.ParseError
import io.github.kotlinmania.lalrpop.runtime.ParseResult
import io.github.kotlinmania.lalrpop.tok.Tok
import io.github.kotlinmania.lalrpop.tok.Tokenizer
import io.github.kotlinmania.lalrpop.tok.Error as TokError

// `mod lrgrammar;` and `mod test;` from the Rust file are realised by the
// neighbouring `LrGrammar.kt` and `ParserTest.kt` files in this package.

/**
 * Translation of `enum Top` from `parser/mod.rs:23`.
 *
 * Doc comment from the Rust source:
 * > The TypeRef and GrammarWhereClauses variants have data that is only
 * > read under `cfg(test)` (the `parseTypeRef()` and
 * > `parseWhereClauses()` functions lower in this file). Those
 * > functions import the `parser()` macro, which expects all variants to
 * > have a single data field. They are set in the parser. So to have
 * > those fields only in the test configuration requires changes at
 * > multiple code points across several files to define both a
 * > `cfg(test)` variant and a `cfg(not(test))` variant, reducing
 * > readability.
 */
sealed class Top {
    data class Grammar(val grammar: io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar) : Top()
    data class Pattern(val pattern: io.github.kotlinmania.lalrpop.grammar.pattern.Pattern<TypeRef>) : Top()
    data class MatchMapping(val matchMapping: io.github.kotlinmania.lalrpop.grammar.parseTree.MatchMapping) : Top()
    data class TypeRefTop(val typeRef: TypeRef) : Top()
    data class GrammarWhereClauses(val whereClauses: List<WhereClause<TypeRef>>) : Top()
}

/** `public type ParseError<'input> = lalrpopUtil::ParseError<usize, tok::Tok<'input>, tok::Error>;` */
typealias LrParseError = ParseError<Int, Tok, TokError>

/**
 * Builds the iterator of `Result<(Int, Tok, Int)>` that the upstream `parser!` macro
 * constructs by chaining a synthetic start sentinel with the real tokenizer
 * output. Mirrors `iter::once(Ok((0, tok::Tok::$tok, 0))).chain(...)` from
 * `parser/mod.rs:36`.
 */
private fun startPrefixed(start: Tok, offset: Int, input: String): Iterator<Result<Triple<Int, Tok, Int>>> {
    val tokenizer = Tokenizer.new(input, offset)
    return object : Iterator<Result<Triple<Int, Tok, Int>>> {
        private var emittedStart = false

        override fun hasNext(): Boolean = !emittedStart || tokenizer.hasNext()

        override fun next(): Result<Triple<Int, Tok, Int>> {
            if (!emittedStart) {
                emittedStart = true
                return Result.success(Triple(0, start, 0))
            }
            return runCatching { tokenizer.next() }.map {
                Triple(it.start, it.value, it.end)
            }
        }
    }
}

/**
 * Translation of `fun parseGrammar(input: &str)` from `parser/mod.rs:48`.
 * Parses a full `.lalrpop` file. After a successful parse, extends the
 * grammar `prefix` until it is unique within the input — matching the Rust
 * loop at `mod.rs:51-53`.
 */
fun parseGrammar(
    input: String,
): Result<io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar> {
    val tokens = startPrefixed(Tok.StartGrammar, 0, input)
    val grammar = when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> (r.value as Top.Grammar).grammar
        is ParseResult.Failure -> return Result.failure(LrParseErrorException(r.error))
    }
    while (input.contains(grammar.prefix)) {
        grammar.prefix += "_"
    }
    return Result.success(grammar)
}

/**
 * Translation of `function parsePattern(input: &str, offset: usize)` from
 * `parser/mod.rs:58`. Used by `___action102` to parse the `=> pattern` body of
 * a Conversion.
 */
internal fun parsePattern(
    input: String,
    offset: Int,
): Result<Pattern<TypeRef>> {
    val tokens = startPrefixed(Tok.StartPattern, offset, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.Pattern).pattern)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}

/**
 * Translation of `function parseMatchMapping(input: &str, offset: usize)` from
 * `parser/mod.rs:62`. Used by `___action96` to parse the `=> mapping` body of
 * a MatchItem.
 */
internal fun parseMatchMapping(
    input: String,
    offset: Int,
): Result<MatchMapping> {
    val tokens = startPrefixed(Tok.StartMatchMapping, offset, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.MatchMapping).matchMapping)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}

/**
 * Translation of `fun parseTypeRef(input: &str)` from `parser/mod.rs:68`.
 * Marked `cfg(test)` in Rust because only the test harness invokes it; in the
 * Kotlin port we leave it accessible to the same tests.
 */
fun parseTypeRef(input: String): Result<TypeRef> {
    val tokens = startPrefixed(Tok.StartTypeRef, 0, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.TypeRefTop).typeRef)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}

/**
 * Translation of `fun parseWhereClauses(input: &str)` from
 * `parser/mod.rs:72`. `cfg(test)` in Rust; see [parseTypeRef] for the porting
 * rationale.
 */
fun parseWhereClauses(input: String): Result<List<WhereClause<TypeRef>>> {
    val tokens = startPrefixed(Tok.StartGrammarWhereClauses, 0, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.GrammarWhereClauses).whereClauses)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}
