// port-lint: source parser/mod.rs
package io.github.kotlinmania.lalrpop.parser

import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeRef
import io.github.kotlinmania.lalrpop.grammar.parsetree.WhereClause
import io.github.kotlinmania.lalrpop.grammar.Pattern
import io.github.kotlinmania.lalrpop.runtime.ParseError
import io.github.kotlinmania.lalrpop.runtime.ParseResult
import io.github.kotlinmania.lalrpop.tok.Tok
import io.github.kotlinmania.lalrpop.tok.Tokenizer
import io.github.kotlinmania.lalrpop.tok.Error as TokError

// The TypeRef and GrammarWhereClauses variants have data that is only read by
// the parseTypeRef() and parseWhereClauses() functions lower in this file.
// Those functions use the parser() helper, which expects all variants to have
// a single data field. They are set in the parser. So to have those fields
// only in the test configuration would require changes at multiple code
// points across several files to define both a test variant and a non-test
// variant, reducing readability.
sealed class Top {
    data class Grammar(val grammar: io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar) : Top()
    data class Pattern(val pattern: io.github.kotlinmania.lalrpop.grammar.Pattern<TypeRef>) : Top()
    data class MatchMapping(val matchMapping: io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping) : Top()
    data class TypeRefTop(val typeRef: TypeRef) : Top()
    data class GrammarWhereClauses(val whereClauses: List<WhereClause<TypeRef>>) : Top()
}

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

fun parseGrammar(
    input: String,
): Result<io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar> {
    val tokens = startPrefixed(Tok.StartGrammar, 0, input)
    val grammar = when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> (r.value as Top.Grammar).grammar
        is ParseResult.Failure -> return Result.failure(LrParseErrorException(r.error))
    }
    // find a unique prefix that does not appear anywhere in the input
    while (input.contains(grammar.prefix)) {
        grammar.prefix += "_"
    }
    return Result.success(grammar)
}

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

fun parseTypeRef(input: String): Result<TypeRef> {
    val tokens = startPrefixed(Tok.StartTypeRef, 0, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.TypeRefTop).typeRef)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}

fun parseWhereClauses(input: String): Result<List<WhereClause<TypeRef>>> {
    val tokens = startPrefixed(Tok.StartGrammarWhereClauses, 0, input)
    return when (val r = TopParser.new().parse(input, tokens)) {
        is ParseResult.Success -> Result.success((r.value as Top.GrammarWhereClauses).whereClauses)
        is ParseResult.Failure -> Result.failure(LrParseErrorException(r.error))
    }
}
