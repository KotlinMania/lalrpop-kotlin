#!/usr/bin/env python
"""Append the `___reduce` dispatcher + fallible forwarders + ParserDefinition + TopParser to LrGrammar.kt.

The dispatcher is a 535-way when-expression delegating to `reduceN(...)` for
every regular case and inlining five fallible cases (205, 206, 386, 446, 447)
that short-circuit on error, plus case 535 which returns the accepted Top.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KT = ROOT / 'src' / 'commonMain' / 'kotlin' / 'io' / 'github' / 'kotlinmania' / 'lalrpop_kotlin' / 'parser' / 'LrGrammar.kt'

SENTINEL = '// === lrgrammar.rs:6614-8366 — fallible forwarders + `___reduce` dispatcher + `ParserDefinition` + `TopParser` ==='

FALLIBLE_CASES = {205, 206, 386, 446, 447}

DISPATCHER_LINES = []
for i in range(535):
    if i in FALLIBLE_CASES:
        continue
    DISPATCHER_LINES.append(
        f'        {i}.toShort() -> reduce{i}(text, lookaheadStart, symbols)'
    )

INLINE_205 = """        205.toShort() -> {
            // Conversion = Terminal, "=>" => ActionFn(501);
            check(symbols.size >= 2)
            val sym1 = popVariant1(symbols)
            val sym0 = popVariant89(symbols)
            val start = sym0.first
            val end = sym1.third
            val nt = action501(text, sym0, sym1).getOrElse {
                val pe = (it as? LrParseErrorException)?.parseError
                    ?: return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(
                        io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = (it as io.github.kotlinmania.lalrpop_kotlin.tok.TokError).err),
                    )
                return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(pe)
            }
            symbols.add(Triple(start, LrSymbol.Variant14(nt), end))
            Pair(2, 97)
        }
"""

INLINE_206 = """        206.toShort() -> {
            // Conversion = Attribute+, Terminal, "=>" => ActionFn(502);
            check(symbols.size >= 3)
            val sym2 = popVariant1(symbols)
            val sym1 = popVariant89(symbols)
            val sym0 = popVariant13(symbols)
            val start = sym0.first
            val end = sym2.third
            val nt = action502(text, sym0, sym1, sym2).getOrElse {
                val pe = (it as? LrParseErrorException)?.parseError
                    ?: return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(
                        io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = (it as io.github.kotlinmania.lalrpop_kotlin.tok.TokError).err),
                    )
                return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(pe)
            }
            symbols.add(Triple(start, LrSymbol.Variant14(nt), end))
            Pair(3, 97)
        }
"""

INLINE_386 = """        386.toShort() -> {
            // MatchItem = MatchSymbol, "=>" => ActionFn(467);
            check(symbols.size >= 2)
            val sym1 = popVariant1(symbols)
            val sym0 = popVariant80(symbols)
            val start = sym0.first
            val end = sym1.third
            val nt = action467(text, sym0, sym1).getOrElse {
                val pe = (it as? LrParseErrorException)?.parseError
                    ?: return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(
                        io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = (it as io.github.kotlinmania.lalrpop_kotlin.tok.TokError).err),
                    )
                return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(pe)
            }
            symbols.add(Triple(start, LrSymbol.Variant26(nt), end))
            Pair(2, 125)
        }
"""

INLINE_446 = """        446.toShort() -> {
            // StringConstant = "StringLiteral" => ActionFn(444);
            val sym0 = popVariant1(symbols)
            val start = sym0.first
            val end = sym0.third
            val nt = action444(text, sym0).getOrElse {
                val pe = (it as? LrParseErrorException)?.parseError
                    ?: return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(
                        io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = (it as io.github.kotlinmania.lalrpop_kotlin.tok.TokError).err),
                    )
                return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(pe)
            }
            symbols.add(Triple(start, LrSymbol.Variant91(nt), end))
            Pair(1, 148)
        }
"""

INLINE_447 = """        447.toShort() -> {
            // StringLiteral = "StringLiteral" => ActionFn(445);
            val sym0 = popVariant1(symbols)
            val start = sym0.first
            val end = sym0.third
            val nt = action445(text, sym0).getOrElse {
                val pe = (it as? LrParseErrorException)?.parseError
                    ?: return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(
                        io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = (it as io.github.kotlinmania.lalrpop_kotlin.tok.TokError).err),
                    )
                return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Failure(pe)
            }
            symbols.add(Triple(start, LrSymbol.Variant22(nt), end))
            Pair(1, 149)
        }
"""

INLINE_535 = """        535.toShort() -> {
            // ___Top = Top => ActionFn(0);
            val sym0 = popVariant95(symbols)
            val nt = action0(text, sym0)
            return io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult.Success(nt)
        }
"""

FORWARDERS = """// === lrgrammar.rs:26967-29220 — fallible forwarder actions ===
// These forwarders splice in `___action197`/`___action198` placeholder
// sub-results around a call into one of the fallible base actions
// (`action96`, `action102`, `action126`, `action127`). Any failure from
// the base action is propagated through `Result` unchanged.

/** `___action431` — forward to `___action102` with empty leading attrs/params. */
internal fun action431(
    text: String,
    sym0: Triple<Int, List<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Attribute>, Int>,
    sym1: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalString, Int>,
    sym2: Triple<Int, String, Int>,
    sym3: Triple<Int, Int, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Conversion> {
    val start0 = sym0.first
    val end0 = sym0.first
    val start1 = sym1.third
    val end1 = sym2.first
    val temp0 = action198(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    val temp1 = action198(text, start1, end1)
    val temp1Triple = Triple(start1, temp1, end1)
    return action102(text, temp0Triple, sym0, sym1, temp1Triple, sym2, sym3)
}

/** `___action439` — forward to `___action96` with empty leading slots. */
internal fun action439(
    text: String,
    sym0: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalLiteral, Int>,
    sym1: Triple<Int, String, Int>,
    sym2: Triple<Int, Int, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.MatchItem> {
    val start0 = sym0.first
    val end0 = sym0.first
    val start1 = sym0.third
    val end1 = sym1.first
    val temp0 = action198(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    val temp1 = action198(text, start1, end1)
    val temp1Triple = Triple(start1, temp1, end1)
    return action96(text, temp0Triple, sym0, temp1Triple, sym1, sym2)
}

/** `___action444` — forward to `___action127` with empty leading slot. */
internal fun action444(
    text: String,
    sym0: Triple<Int, String, Int>,
): Result<String> {
    val start0 = sym0.first
    val end0 = sym0.first
    val temp0 = action198(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action127(text, temp0Triple, sym0)
}

/** `___action445` — forward to `___action126` with empty leading slot. */
internal fun action445(
    text: String,
    sym0: Triple<Int, String, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.Atom> {
    val start0 = sym0.first
    val end0 = sym0.first
    val temp0 = action198(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action126(text, temp0Triple, sym0)
}

/** `___action459` — forward to `___action431` with trailing lookbehind slot. */
internal fun action459(
    text: String,
    sym0: Triple<Int, List<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Attribute>, Int>,
    sym1: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalString, Int>,
    sym2: Triple<Int, String, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Conversion> {
    val start0 = sym2.third
    val end0 = sym2.third
    val temp0 = action197(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action431(text, sym0, sym1, sym2, temp0Triple)
}

/** `___action467` — forward to `___action439` with trailing lookbehind slot. */
internal fun action467(
    text: String,
    sym0: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalLiteral, Int>,
    sym1: Triple<Int, String, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.MatchItem> {
    val start0 = sym1.third
    val end0 = sym1.third
    val temp0 = action197(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action439(text, sym0, sym1, temp0Triple)
}

/** `___action501` — forward to `___action459` with empty leading attrs. */
internal fun action501(
    text: String,
    sym0: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalString, Int>,
    sym1: Triple<Int, String, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Conversion> {
    val start0 = sym0.first
    val end0 = sym0.first
    val temp0 = action199(text, start0, end0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action459(text, temp0Triple, sym0, sym1)
}

/** `___action502` — forward to `___action459` with the attrs from sym0. */
internal fun action502(
    text: String,
    sym0: Triple<Int, List<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Attribute>, Int>,
    sym1: Triple<Int, io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalString, Int>,
    sym2: Triple<Int, String, Int>,
): Result<io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Conversion> {
    val start0 = sym0.first
    val end0 = sym0.third
    val temp0 = action200(text, sym0)
    val temp0Triple = Triple(start0, temp0, end0)
    return action459(text, temp0Triple, sym1, sym2)
}

"""

DISPATCHER_HEAD = """// === lrgrammar.rs:6681-8366 — `___reduce` dispatcher ===
/**
 * Translation of `fn ___reduce<'input>(...)` from lrgrammar.rs:6681. Looks up
 * the i16 action code, delegates to the matching infallible `reduceN`, or
 * inlines the five fallible cases that must short-circuit on error. Returns
 * a non-null [ParseResult] only when the parse is complete (case 535) or when
 * a fallible action reported a user error.
 */
internal fun reduce(
    text: String,
    action: Short,
    lookaheadStart: Int?,
    states: MutableList<Short>,
    symbols: MutableList<io.github.kotlinmania.lalrpop_kotlin.runtime.SymbolTriple<Int, LrSymbol>>,
): io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult<Top, Int, io.github.kotlinmania.lalrpop_kotlin.tok.Tok, io.github.kotlinmania.lalrpop_kotlin.tok.Error>? {
    val (popStates, nonterminal) = when (action) {
"""

DISPATCHER_TAIL = """        else -> error("invalid action code $action")
    }
    repeat(popStates) { states.removeAt(states.size - 1) }
    val state = states.last()
    val nextState = goto(state, nonterminal)
    states.add(nextState)
    return null
}
"""

PARSER_DEF = """
// === lrgrammar.rs:3209-3303 — `ParserDefinition` impl on `StateMachine` ===
/**
 * Translation of `impl ___state_machine::ParserDefinition for ___StateMachine`
 * (lrgrammar.rs:3209). Each Rust associated-type binding becomes a concrete
 * generic argument on the Kotlin interface. Every method forwards to the
 * existing top-level helper that Rust's impl delegates to.
 */
internal class StateMachineDefinition(
    private val stateMachine: StateMachine,
) : io.github.kotlinmania.lalrpop_kotlin.runtime.ParserDefinition<
    Int,
    io.github.kotlinmania.lalrpop_kotlin.tok.Error,
    io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
    Int,
    LrSymbol,
    Top,
    Short,
    io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction,
    Short,
    Int,
> {
    private val states: MutableList<Short> = mutableListOf()

    override fun startLocation(): Int = 0

    override fun startState(): Short = 0

    override fun tokenToIndex(token: io.github.kotlinmania.lalrpop_kotlin.tok.Tok): Int? =
        tokenToInteger(token)

    override fun action(state: Short, tokenIndex: Int): io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction =
        io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction(
            io.github.kotlinmania.lalrpop_kotlin.parser.action(state, tokenIndex),
        )

    override fun errorAction(state: Short): io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction =
        io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction(
            io.github.kotlinmania.lalrpop_kotlin.parser.action(state, 58),
        )

    override fun eofAction(state: Short): io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction =
        io.github.kotlinmania.lalrpop_kotlin.runtime.ShortAction(EOF_ACTION[state.toInt()])

    override fun goto(state: Short, nt: Int): Short =
        io.github.kotlinmania.lalrpop_kotlin.parser.goto(state, nt)

    override fun tokenToSymbol(tokenIndex: Int, token: io.github.kotlinmania.lalrpop_kotlin.tok.Tok): LrSymbol =
        io.github.kotlinmania.lalrpop_kotlin.parser.tokenToSymbol(tokenIndex, token)

    override fun expectedTokens(state: Short): List<String> =
        io.github.kotlinmania.lalrpop_kotlin.parser.expectedTokens(state)

    override fun expectedTokensFromStates(states: List<Short>): List<String> =
        io.github.kotlinmania.lalrpop_kotlin.parser.expectedTokensFromStates(states)

    override fun usesErrorRecovery(): Boolean = false

    override fun errorRecoverySymbol(
        recovery: io.github.kotlinmania.lalrpop_kotlin.runtime.ErrorRecovery<
            Int,
            io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
            io.github.kotlinmania.lalrpop_kotlin.tok.Error,
        >,
    ): LrSymbol = error("error recovery not enabled for this grammar")

    override fun reduce(
        reduceIndex: Short,
        startLocation: Int?,
        states: MutableList<Short>,
        symbols: MutableList<io.github.kotlinmania.lalrpop_kotlin.runtime.SymbolTriple<Int, LrSymbol>>,
    ): io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult<
        Top,
        Int,
        io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
        io.github.kotlinmania.lalrpop_kotlin.tok.Error,
    >? = reduce(stateMachine.text, reduceIndex, startLocation, states, symbols)

    override fun simulateReduce(action: Short): io.github.kotlinmania.lalrpop_kotlin.runtime.SimulatedReduce<Int> =
        simulateReduce(action)
}

// === lrgrammar.rs:6614-6647 — `pub struct TopParser` + `parse()` ===
/**
 * Translation of `pub struct TopParser` and its `parse()` entry point
 * (lrgrammar.rs:6614). The Rust `___TOKEN: ___ToTriple` bound is realised
 * on the Kotlin side by accepting any [Iterator] of either bare
 * [LrTriple] values or `Result<LrTriple>` values, adapted through the
 * [toTriple] extension functions.
 */
class TopParser {
    companion object {
        fun new(): TopParser = TopParser()
    }

    /** Parse using a pre-shifted [LrTriple] iterator (the Rust infallible case). */
    fun parseTriples(
        text: String,
        tokens: Iterator<LrTriple>,
    ): io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult<
        Top,
        Int,
        io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
        io.github.kotlinmania.lalrpop_kotlin.tok.Error,
    > {
        val adapted = object : Iterator<io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult<
            Int,
            io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
            io.github.kotlinmania.lalrpop_kotlin.tok.Error,
        >> {
            override fun hasNext(): Boolean = tokens.hasNext()
            override fun next() = io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult.Ok<
                Int,
                io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
                io.github.kotlinmania.lalrpop_kotlin.tok.Error,
            >(tokens.next())
        }
        return io.github.kotlinmania.lalrpop_kotlin.runtime.Parser.drive(
            StateMachineDefinition(StateMachine(text)),
            adapted,
        )
    }

    /** Parse using a fallible iterator that may carry [LrParseErrorException] failures. */
    fun parseResults(
        text: String,
        tokens: Iterator<Result<LrTriple>>,
    ): io.github.kotlinmania.lalrpop_kotlin.runtime.ParseResult<
        Top,
        Int,
        io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
        io.github.kotlinmania.lalrpop_kotlin.tok.Error,
    > {
        val adapted = object : Iterator<io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult<
            Int,
            io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
            io.github.kotlinmania.lalrpop_kotlin.tok.Error,
        >> {
            override fun hasNext(): Boolean = tokens.hasNext()
            override fun next(): io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult<
                Int,
                io.github.kotlinmania.lalrpop_kotlin.tok.Tok,
                io.github.kotlinmania.lalrpop_kotlin.tok.Error,
            > = tokens.next().fold(
                onSuccess = {
                    io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult.Ok(it)
                },
                onFailure = { err ->
                    val pe = when (err) {
                        is LrParseErrorException -> err.parseError
                        is io.github.kotlinmania.lalrpop_kotlin.tok.TokError ->
                            io.github.kotlinmania.lalrpop_kotlin.runtime.ParseError.User(error = err.err)
                        else -> throw err
                    }
                    io.github.kotlinmania.lalrpop_kotlin.runtime.TokResult.Err(pe)
                },
            )
        }
        return io.github.kotlinmania.lalrpop_kotlin.runtime.Parser.drive(
            StateMachineDefinition(StateMachine(text)),
            adapted,
        )
    }
}
"""


def main() -> int:
    existing = KT.read_text()
    if SENTINEL in existing:
        print('Dispatcher section already present — aborting.', file=sys.stderr)
        return 1

    parts: list[str] = []
    parts.append('\n\n')
    parts.append(SENTINEL + '\n\n')
    parts.append(FORWARDERS)
    parts.append(DISPATCHER_HEAD)
    parts.extend(l + '\n' for l in DISPATCHER_LINES)
    parts.append(INLINE_205)
    parts.append(INLINE_206)
    parts.append(INLINE_386)
    parts.append(INLINE_446)
    parts.append(INLINE_447)
    parts.append(INLINE_535)
    parts.append(DISPATCHER_TAIL)
    parts.append(PARSER_DEF)

    KT.write_text(existing + ''.join(parts))
    print('Appended dispatcher + forwarders + ParserDefinition + TopParser.', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
