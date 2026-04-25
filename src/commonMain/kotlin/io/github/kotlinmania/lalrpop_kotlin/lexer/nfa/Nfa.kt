// port-lint: source src/lexer/nfa/mod.rs
//! The Nfa we construct for each regex. Since the states are not
//! really of interest, we represent this just as a vector of labeled
//! edges.
package io.github.kotlinmania.lalrpop_kotlin.lexer.nfa

import io.github.kotlinmania.lalrpop_kotlin.lexer.re.Regex
import io.github.kotlinmania.lalrpop_kotlin.regexSyntax.ClassBytesRange
import io.github.kotlinmania.lalrpop_kotlin.regexSyntax.ClassUnicodeRange
import io.github.kotlinmania.lalrpop_kotlin.regexSyntax.HirKind
import io.github.kotlinmania.lalrpop_kotlin.regexSyntax.RegexClass

class Nfa private constructor(
    internal val states: MutableList<NfaState>,
    internal val edges: Edges,
) {
    companion object {
        fun fromRe(regex: Regex): Result<Nfa> = runCatching {
            val nfa = new()
            val s0 = nfa.buildExpr(regex.kind(), ACCEPT, REJECT)
            nfa.pushEdgeNoop(START, s0)
            nfa
        }

        internal fun new(): Nfa {
            val nfa = Nfa(states = mutableListOf(), edges = Edges())

            // reserve the ACCEPT, REJECT, and START states ahead of time
            check(nfa.newState(StateKind.Accept) == ACCEPT)
            check(nfa.newState(StateKind.Reject) == REJECT)
            check(nfa.newState(StateKind.Neither) == START)

            // the ACCEPT state, given another token, becomes a REJECT
            nfa.pushEdgeOther(ACCEPT, REJECT)

            // the REJECT state loops back to itself no matter what
            nfa.pushEdgeOther(REJECT, REJECT)

            return nfa
        }
    }

    // ///////////////////////////////////////////////////////////////////////
    // Public methods for querying an Nfa

    fun noopEdges(from: NfaStateIndex): Sequence<Edge<Noop>> =
        edgeSequence(edges.noopEdges, from, states[from.value].firstNoopEdge)

    fun testEdges(from: NfaStateIndex): Sequence<Edge<Test>> =
        edgeSequence(edges.testEdges, from, states[from.value].firstTestEdge)

    fun otherEdges(from: NfaStateIndex): Sequence<Edge<Other>> =
        edgeSequence(edges.otherEdges, from, states[from.value].firstOtherEdge)

    fun kind(from: NfaStateIndex): StateKind = states[from.value].kind

    fun isAcceptingState(from: NfaStateIndex): Boolean =
        states[from.value].kind == StateKind.Accept

    fun isRejectingState(from: NfaStateIndex): Boolean =
        states[from.value].kind == StateKind.Reject

    // ///////////////////////////////////////////////////////////////////////
    // Private methods for building an Nfa

    internal fun newState(kind: StateKind): NfaStateIndex {
        val index = states.size

        // these edge indices will be patched later by patch_edges()
        states.add(NfaState(
            kind = kind,
            firstNoopEdge = UNSET,
            firstTestEdge = UNSET,
            firstOtherEdge = UNSET,
        ))

        return NfaStateIndex(index)
    }

    // pushes an edge: note that all outgoing edges from a particular
    // state should be pushed together, so that the edge vectors are
    // suitably sorted
    internal fun pushEdgeNoop(from: NfaStateIndex, to: NfaStateIndex) {
        val vec = edges.noopEdges
        val edgeIndex = vec.size
        vec.add(Edge(from, Noop, to))
        val state = states[from.value]
        if (state.firstNoopEdge == UNSET) {
            states[from.value] = state.copy(firstNoopEdge = edgeIndex)
        } else {
            check(vec[edgeIndex - 1].from == from)
        }
    }

    internal fun pushEdgeTest(from: NfaStateIndex, label: Test, to: NfaStateIndex) {
        val vec = edges.testEdges
        val edgeIndex = vec.size
        vec.add(Edge(from, label, to))
        val state = states[from.value]
        if (state.firstTestEdge == UNSET) {
            states[from.value] = state.copy(firstTestEdge = edgeIndex)
        } else {
            check(vec[edgeIndex - 1].from == from)
        }
    }

    internal fun pushEdgeOther(from: NfaStateIndex, to: NfaStateIndex) {
        val vec = edges.otherEdges
        val edgeIndex = vec.size
        vec.add(Edge(from, Other, to))
        val state = states[from.value]
        if (state.firstOtherEdge == UNSET) {
            states[from.value] = state.copy(firstOtherEdge = edgeIndex)
        } else {
            check(vec[edgeIndex - 1].from == from)
        }
    }

    private fun buildExpr(
        expr: HirKind,
        accept: NfaStateIndex,
        reject: NfaStateIndex,
    ): NfaStateIndex = when (expr) {
        HirKind.Empty -> accept

        is HirKind.Literal -> {
            var acc = accept
            for (i in expr.literal.bytes.indices.reversed()) {
                val b = expr.literal.bytes[i]
                val s0 = newState(StateKind.Neither)
                pushEdgeTest(s0, Test.byte(b.toUByte()), acc)
                pushEdgeOther(s0, reject)
                acc = s0
            }
            acc
        }

        is HirKind.Class -> {
            when (val cls = expr.cls) {
                is RegexClass.Unicode -> {
                    // [s0] --c0--> [accept]
                    //  | |            ^
                    //  | |   ...      |
                    //  | |            |
                    //  | +---cn-------+
                    //  +---------------> [reject]
                    val s0 = newState(StateKind.Neither)
                    for (range in cls.ranges) {
                        val test: Test = Test.from(range)
                        pushEdgeTest(s0, test, accept)
                    }
                    pushEdgeOther(s0, reject)
                    s0
                }
                // Bytes are not supported upstream; LALRPOP treats them the
                // same as Unicode ranges because its NFA edges are byte-aware.
                is RegexClass.Bytes -> {
                    val s0 = newState(StateKind.Neither)
                    for (range in cls.ranges) {
                        val test: Test = Test.from(range)
                        pushEdgeTest(s0, test, accept)
                    }
                    pushEdgeOther(s0, reject)
                    s0
                }
            }
        }

        // currently we don't support any boundaries because
        // I was too lazy to code them up or think about them
        // Akin to anchors or wordboundaries
        is HirKind.Look -> throw NfaConstructionException(NfaConstructionError.LookAround)

        // currently we treat all capture groups the same, whether they
        // capture or not; but we don't permit named groups,
        // in case we want to give them significance in the future
        is HirKind.Capture -> {
            val c = expr.capture
            if (c.name != null) {
                throw NfaConstructionException(NfaConstructionError.NamedCaptures)
            } else {
                buildExpr(c.sub.kind(), accept, reject)
            }
        }

        is HirKind.Repetition -> {
            val rep = expr.repetition
            val min = rep.min
            val max = rep.max
            val greedy = rep.greedy
            val sub = rep.sub
            if (!greedy) {
                // currently we always report the longest match possible
                throw NfaConstructionException(NfaConstructionError.NonGreedy)
            } else {
                when {
                    min == 0u && max == 1u -> optionalExpr(sub.kind(), accept, reject)
                    min == 0u && max == null -> starExpr(sub.kind(), accept, reject)
                    min == 1u && max == null -> plusExpr(sub.kind(), accept, reject)
                    max != null && min == max -> {
                        var s = accept
                        repeat(max.toInt()) { s = buildExpr(sub.kind(), s, reject) }
                        s
                    }
                    max != null -> {
                        var s = accept
                        val optionalCount = max - min
                        repeat(optionalCount.toInt()) {
                            s = optionalExpr(sub.kind(), s, reject)
                        }
                        repeat(min.toInt()) { s = buildExpr(sub.kind(), s, reject) }
                        s
                    }
                    else -> {
                        // +---min times----+
                        // |                |
                        //
                        // [s0] --..e..-- [s1] --..e*..--> [accept]
                        //          |      |
                        //          |      v
                        //          +-> [reject]
                        var s = starExpr(sub.kind(), accept, reject)
                        repeat(min.toInt()) { s = buildExpr(sub.kind(), s, reject) }
                        s
                    }
                }
            }
        }

        is HirKind.Concat -> {
            var s = accept
            for (i in expr.exprs.indices.reversed()) {
                s = buildExpr(expr.exprs[i].kind(), s, reject)
            }
            s
        }

        is HirKind.Alternation -> {
            // [s0] --exprs[0]--> [accept/reject]
            //   |                   ^
            //   |                   |
            //   +----exprs[..]------+
            //   |                   |
            //   |                   |
            //   +----exprs[n-1]-----+

            val s0 = newState(StateKind.Neither)
            val targets: List<NfaStateIndex> = expr.exprs.map { subHir ->
                buildExpr(subHir.kind(), accept, reject)
            }

            // push edges from s0 all together so they are
            // adjacent in the edge array
            for (target in targets) {
                pushEdgeNoop(s0, target)
            }
            s0
        }
    }

    private fun optionalExpr(
        expr: HirKind,
        accept: NfaStateIndex,
        reject: NfaStateIndex,
    ): NfaStateIndex {
        // [s0] ----> [accept]
        //   |           ^
        //   v           |
        // [s1] --...----+
        //         |
        //         v
        //      [reject]

        val s1 = buildExpr(expr, accept, reject)

        val s0 = newState(StateKind.Neither)
        pushEdgeNoop(s0, accept) // they might supply nothing
        pushEdgeNoop(s0, s1)

        return s0
    }

    private fun starExpr(
        expr: HirKind,
        accept: NfaStateIndex,
        reject: NfaStateIndex,
    ): NfaStateIndex {
        // [s0] ----> [accept]
        //  | ^
        //  | |
        //  | +----------+
        //  v            |
        // [s1] --...----+
        //         |
        //         v
        //      [reject]

        val s0 = newState(StateKind.Neither)

        val s1 = buildExpr(expr, s0, reject)

        pushEdgeNoop(s0, accept)
        pushEdgeNoop(s0, s1)

        return s0
    }

    private fun plusExpr(
        expr: HirKind,
        accept: NfaStateIndex,
        reject: NfaStateIndex,
    ): NfaStateIndex {
        //            [accept]
        //               ^
        //               |
        //    +----------+
        //    v          |
        // [s0] --...--[s1]
        //         |
        //         v
        //      [reject]

        val s1 = newState(StateKind.Neither)

        val s0 = buildExpr(expr, s1, reject)

        pushEdgeNoop(s1, accept)
        pushEdgeNoop(s1, s0)

        return s0
    }

    override fun toString(): String =
        "Nfa(states=${states.size}, noop=${edges.noopEdges.size}, test=${edges.testEdges.size}, other=${edges.otherEdges.size})"
}

private const val UNSET: Int = Int.MAX_VALUE

/**
 * An edge label representing a range of characters, inclusive. Note
 * that this range may contain some endpoints that are not valid
 * unicode, hence we store u32.
 */
data class Test(val rangeStart: UInt, val rangeEnd: UInt) : Comparable<Test> {
    fun start(): UInt = rangeStart
    fun end(): UInt = rangeEnd

    override fun compareTo(other: Test): Int {
        val byStart = start().compareTo(other.start())
        return if (byStart != 0) byStart else end().compareTo(other.end())
    }

    fun isChar(): Boolean = length() == 1u

    fun length(): UInt {
        // The reason we don't have a RangeInclusive::len is because it panics if the range is 0..=u32::max
        // Akin to https://github.com/rust-lang/rust/issues/36386
        // Plus one because the range is inclusive
        return end() + 1u - start()
    }

    fun containsU32(c: UInt): Boolean = c in rangeStart..rangeEnd

    fun containsChar(c: Char): Boolean = containsU32(c.code.toUInt())

    fun intersects(r: Test): Boolean =
        !isEmpty() &&
            !r.isEmpty() &&
            (containsU32(r.start()) || r.containsU32(start()))

    fun isDisjoint(r: Test): Boolean = !intersects(r)

    fun isEmpty(): Boolean = rangeStart > rangeEnd

    companion object {
        fun new(start: UInt, end: UInt): Test = Test(start, end)

        fun char(c: Char): Test {
            val u = c.code.toUInt()
            return Test(u, u)
        }

        fun byte(b: UByte): Test {
            val u = b.toUInt()
            return Test(u, u)
        }

        fun inclusiveRange(s: Char, e: Char): Test =
            Test(s.code.toUInt(), e.code.toUInt())

        fun inclusiveByteRange(s: UByte, e: UByte): Test =
            Test(s.toUInt(), e.toUInt())

        fun exclusiveRange(s: Char, e: Char): Test =
            Test(s.code.toUInt(), e.code.toUInt() - 1u)

        fun from(range: ClassUnicodeRange): Test =
            inclusiveRange(range.start(), range.end())

        fun from(range: ClassBytesRange): Test =
            inclusiveByteRange(range.start(), range.end())
    }

    override fun toString(): String {
        val start = start()
        val end = end()
        val startChar = if (start <= 0x10FFFFu) start.toInt().toChar() else null
        val endChar = if (end <= 0x10FFFFu) end.toInt().toChar() else null
        if (startChar != null && endChar != null) {
            return if (isChar()) {
                if (".[]()?+*!".contains(startChar)) "\\$startChar" else "$startChar"
            } else {
                "[${debugChar(startChar)}..=${debugChar(endChar)}]"
            }
        }
        return "[${start}..]${end}]"
    }

    private fun debugChar(c: Char): String =
        if (c.isISOControl()) "\\u{${c.code.toString(16)}}" else "'$c'"
}

/** An "epsilon" edge -- no input */
object Noop {
    override fun toString(): String = "Noop"
}

/** An "other" edge -- fallback if no other edges apply */
object Other {
    override fun toString(): String = "Other"
}

/**
 * For each state, we just store the indices of the first char and
 * test edges, or Int.MAX_VALUE if no such edge. You can then find all
 * edges by enumerating subsequent edges in the vectors until you
 * find one with a different `from` value.
 */
internal data class NfaState(
    val kind: StateKind,
    val firstNoopEdge: Int,
    val firstTestEdge: Int,
    val firstOtherEdge: Int,
)

enum class StateKind : Comparable<StateKind> {
    Accept,
    Reject,
    Neither,
}

data class NfaStateIndex(val value: Int) : Comparable<NfaStateIndex> {
    override fun compareTo(other: NfaStateIndex): Int = value.compareTo(other.value)
    override fun toString(): String = "Nfa$value"
}

/**
 * A set of edges for the state machine. Edges are kept sorted by the
 * type of label they have. Within a vector, all edges with the same
 * `from` are grouped together so they can be enumerated later (for
 * now we just ensure this during construction, but one could easily
 * sort).
 */
internal class Edges {
    val noopEdges: MutableList<Edge<Noop>> = mutableListOf()

    // edges where we are testing the character in some way; for any
    // given state, there should not be multiple edges with the same
    // test
    val testEdges: MutableList<Edge<Test>> = mutableListOf()

    // fallback rules if no test_edge applies
    val otherEdges: MutableList<Edge<Other>> = mutableListOf()
}

data class Edge<L>(
    val from: NfaStateIndex,
    val label: L,
    val to: NfaStateIndex,
)

val ACCEPT: NfaStateIndex = NfaStateIndex(0)
val REJECT: NfaStateIndex = NfaStateIndex(1)
val START: NfaStateIndex = NfaStateIndex(2)

/**
 * Raised (via [NfaConstructionException]) when LALRPOP encounters a
 * regex feature it does not support.
 */
enum class NfaConstructionError {
    NamedCaptures,
    NonGreedy,
    LookAround,
    ByteRegex,
}

/** Exception carrier for [NfaConstructionError] — thrown across the expr walk. */
class NfaConstructionException(val error: NfaConstructionError) : RuntimeException(error.name)

private fun <L> edgeSequence(
    vec: List<Edge<L>>,
    from: NfaStateIndex,
    firstIndex: Int,
): Sequence<Edge<L>> = sequence {
    var index = firstIndex
    while (index != UNSET) {
        yield(vec[index])
        val next = index + 1
        index = if (next >= vec.size || vec[next].from != from) UNSET else next
    }
}

// Compat/idiomatic wrappers matching the Rust `Debug for Edge<L>`:
// not needed by the NFA builder but kept so downstream debug prints work.
fun <L> Edge<L>.debugString(): String = "${from} -${label}-> ${to}"
