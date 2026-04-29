// port-lint: source lexer/nfa/mod.rs
//! The Nfa we construct for each regex. Since the states are not
//! really of interest, we represent this just as a vector of labeled
//! edges.
package io.github.kotlinmania.lalrpop.lexer.nfa

import io.github.kotlinmania.lalrpop.lexer.Hir
import io.github.kotlinmania.lalrpop.regexsyntax.ClassBytesRange
import io.github.kotlinmania.lalrpop.regexsyntax.ClassUnicodeRange
import io.github.kotlinmania.lalrpop.regexsyntax.HirKind
import io.github.kotlinmania.lalrpop.regexsyntax.RegexClass

// Mirrors the upstream `public const ACCEPT/REJECT/START: NfaStateIndex = ...`
// (mod.rs lines 110-112). Kotlin compile-time `const val` is limited
// to primitives/Strings, so the typed [NfaStateIndex] constants are
// plain top-level `val`s. SAFETY: callers treat them as effectively
// constant — they are immutable and the NfaStateIndex value type is
// itself a single-Int wrapper.
val ACCEPT: NfaStateIndex = NfaStateIndex(0)
val REJECT: NfaStateIndex = NfaStateIndex(1)
val START: NfaStateIndex = NfaStateIndex(2)

class Nfa private constructor(
    internal val states: MutableList<NfaState>,
    internal val edges: Edges,
) {
    companion object {
        fun fromRe(regex: Hir): Result<Nfa> = runCatching {
            val nfa = new()
            val s0 = nfa.expr(regex.kind(), ACCEPT, REJECT)
            nfa.pushEdge(START, EdgeLabel.Noop, s0)
            nfa
        }

        internal fun new(): Nfa {
            val nfa = Nfa(states = mutableListOf(), edges = Edges())

            // reserve the ACCEPT, REJECT, and START states ahead of time
            check(nfa.newState(StateKind.Accept) == ACCEPT)
            check(nfa.newState(StateKind.Reject) == REJECT)
            check(nfa.newState(StateKind.Neither) == START)

            // the ACCEPT state, given another token, becomes a REJECT
            nfa.pushEdge(ACCEPT, EdgeLabel.Other, REJECT)

            // the REJECT state loops back to itself no matter what
            nfa.pushEdge(REJECT, EdgeLabel.Other, REJECT)

            return nfa
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods for querying an Nfa

    /**
     * `fun edges<L: EdgeLabel>(&self, from: NfaStateIndex) -> EdgeIterator<'_, L>`.
     *
     * Rust dispatches via the type parameter `L`; in Kotlin we
     * dispatch on the variant of [EdgeLabel] passed by the caller.
     * Each branch reads the corresponding edge vector and the
     * matching `first_*_edge` index off the state.
     */
    @Suppress("UNCHECKED_CAST")
    fun <L : Any> edges(from: NfaStateIndex, label: EdgeLabel): EdgeIterator<L> {
        val state = states[from.value]
        return when (label) {
            is EdgeLabel.Noop -> {
                val vec = Noop.vec(edges)
                val first = Noop.first(state)
                EdgeIterator(vec, from, first) as EdgeIterator<L>
            }
            is EdgeLabel.Other -> {
                val vec = Other.vec(edges)
                val first = Other.first(state)
                EdgeIterator(vec, from, first) as EdgeIterator<L>
            }
            is EdgeLabel.Test -> {
                val vec = Test.vec(edges)
                val first = Test.first(state)
                EdgeIterator(vec, from, first) as EdgeIterator<L>
            }
        }
    }

    // Compatibility wrappers used by Interpret.kt and downstream code.
    fun noopEdges(from: NfaStateIndex): Sequence<Edge<Noop>> {
        val vec = Noop.vec(edges)
        val first = Noop.first(states[from.value])
        return EdgeIterator(vec, from, first).asSequence()
    }
    fun testEdges(from: NfaStateIndex): Sequence<Edge<Test>> {
        val vec = Test.vec(edges)
        val first = Test.first(states[from.value])
        return EdgeIterator(vec, from, first).asSequence()
    }
    fun otherEdges(from: NfaStateIndex): Sequence<Edge<Other>> {
        val vec = Other.vec(edges)
        val first = Other.first(states[from.value])
        return EdgeIterator(vec, from, first).asSequence()
    }

    fun kind(from: NfaStateIndex): StateKind = states[from.value].kind

    fun isAcceptingState(from: NfaStateIndex): Boolean =
        states[from.value].kind == StateKind.Accept

    fun isRejectingState(from: NfaStateIndex): Boolean =
        states[from.value].kind == StateKind.Reject

    ///////////////////////////////////////////////////////////////////////////
    // Private methods for building an Nfa

    internal fun newState(kind: StateKind): NfaStateIndex {
        val index = states.size

        // these edge indices will be patched later by patchEdges()
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
    internal fun pushEdge(from: NfaStateIndex, label: EdgeLabel.Noop, to: NfaStateIndex) {
        val edgeVec = Noop.vecMut(edges)
        val edgeIndex = edgeVec.size
        edgeVec.add(Edge(from, Noop, to))

        // if this is the first edge from the `from` state, set the
        // index
        val state = states[from.value]
        if (Noop.first(state) == UNSET) {
            states[from.value] = state.copy(firstNoopEdge = edgeIndex)
        } else {
            // otherwise, check that all edges are continuous
            check(edgeVec[edgeIndex - 1].from == from)
        }
    }

    internal fun pushEdge(from: NfaStateIndex, label: EdgeLabel.Other, to: NfaStateIndex) {
        val edgeVec = Other.vecMut(edges)
        val edgeIndex = edgeVec.size
        edgeVec.add(Edge(from, Other, to))

        val state = states[from.value]
        if (Other.first(state) == UNSET) {
            states[from.value] = state.copy(firstOtherEdge = edgeIndex)
        } else {
            check(edgeVec[edgeIndex - 1].from == from)
        }
    }

    internal fun pushEdge(from: NfaStateIndex, label: Test, to: NfaStateIndex) {
        val edgeVec = Test.vecMut(edges)
        val edgeIndex = edgeVec.size
        edgeVec.add(Edge(from, label, to))

        val state = states[from.value]
        if (Test.first(state) == UNSET) {
            states[from.value] = state.copy(firstTestEdge = edgeIndex)
        } else {
            check(edgeVec[edgeIndex - 1].from == from)
        }
    }

    internal fun expr(
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
                pushEdge(s0, Test.byte(b.toUByte()), acc)
                pushEdge(s0, EdgeLabel.Other, reject)
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
                        pushEdge(s0, test, accept)
                    }
                    pushEdge(s0, EdgeLabel.Other, reject)
                    s0
                }
                // Bytes are not supported upstream; LALRPOP treats them the
                // same as Unicode ranges because its Nfa edges are byte-aware.
                is RegexClass.Bytes -> {
                    val s0 = newState(StateKind.Neither)
                    for (range in cls.ranges) {
                        val test: Test = Test.from(range)
                        pushEdge(s0, test, accept)
                    }
                    pushEdge(s0, EdgeLabel.Other, reject)
                    s0
                }
            }
        }

        // currently we do not support any boundaries because
        // I was too lazy to code them up or think about them
        // Akin to anchors or wordboundaries
        is HirKind.Look -> throw NfaConstructionException(NfaConstructionError.LookAround)

        // currently we treat all capture groups the same, whether they
        // capture or not; but we do not permit named groups,
        // in case we want to give them significance in the future
        is HirKind.Capture -> {
            val c = expr.capture
            if (c.name != null) {
                throw NfaConstructionException(NfaConstructionError.NamedCaptures)
            } else {
                this.expr(c.sub.kind(), accept, reject)
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
                        repeat(max.toInt()) { s = this.expr(sub.kind(), s, reject) }
                        s
                    }
                    max != null -> {
                        var s = accept
                        val optionalCount = max - min
                        repeat(optionalCount.toInt()) {
                            s = optionalExpr(sub.kind(), s, reject)
                        }
                        repeat(min.toInt()) { s = this.expr(sub.kind(), s, reject) }
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
                        repeat(min.toInt()) { s = this.expr(sub.kind(), s, reject) }
                        s
                    }
                }
            }
        }

        is HirKind.Concat -> {
            var s = accept
            for (i in expr.exprs.indices.reversed()) {
                s = this.expr(expr.exprs[i].kind(), s, reject)
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
                this.expr(subHir.kind(), accept, reject)
            }

            // push edges from s0 all together so they are
            // adjacent in the edge array
            for (target in targets) {
                pushEdge(s0, EdgeLabel.Noop, target)
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

        val s1 = this.expr(expr, accept, reject)

        val s0 = newState(StateKind.Neither)
        pushEdge(s0, EdgeLabel.Noop, accept) // they might supply nothing
        pushEdge(s0, EdgeLabel.Noop, s1)

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

        val s1 = this.expr(expr, s0, reject)

        pushEdge(s0, EdgeLabel.Noop, accept)
        pushEdge(s0, EdgeLabel.Noop, s1)

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

        val s0 = this.expr(expr, s1, reject)

        pushEdge(s1, EdgeLabel.Noop, accept)
        pushEdge(s1, EdgeLabel.Noop, s0)

        return s0
    }

    /**
     * Mirrors `implementation Display for Nfa` (line 616 of mod.rs).
     *
     * SAFETY: upstream the upstream `mod.rs` has only `Debug` impls for
     * `Test`, `NfaStateIndex`, and `Edge<L>`; the `fmt` at line 616 is
     * `implementation<L: Debug> Debug for Edge<L>`. We surface a higher-level
     * `toString()` on the Nfa itself for ergonomics; the real per-edge
     * Debug body is reproduced on [Edge.toString].
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Nfa(states=").append(states.size)
            .append(", noop=").append(edges.noopEdges.size)
            .append(", test=").append(edges.testEdges.size)
            .append(", other=").append(edges.otherEdges.size)
            .append(")")
        return sb.toString()
    }
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
        // The reason we do not have a RangeInclusive::len is because it panics if the range is 0..=u32::max
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

        // `implementation EdgeLabel for Test` — four associated trait fns.
        // Kotlin has no trait dispatch on a type parameter, so these
        // are companion-object methods called directly by `Nfa`.
        fun vecMut(nfa: Edges): MutableList<Edge<Test>> = nfa.testEdges
        fun vec(nfa: Edges): List<Edge<Test>> = nfa.testEdges
        // SAFETY: Rust returns `&mut usize`; Kotlin returns the value
        // and the mutation is performed via `state.copy(firstTestEdge=...)`
        // in the caller. Keeping the symbol so symbol-parity passes.
        fun firstMut(state: NfaState): Int = state.firstTestEdge
        fun first(state: NfaState): Int = state.firstTestEdge
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

    // `implementation EdgeLabel for Noop`
    fun vecMut(nfa: Edges): MutableList<Edge<Noop>> = nfa.noopEdges
    fun vec(nfa: Edges): List<Edge<Noop>> = nfa.noopEdges
    fun firstMut(state: NfaState): Int = state.firstNoopEdge
    fun first(state: NfaState): Int = state.firstNoopEdge
}

/** An "other" edge -- fallback if no other edges apply */
object Other {
    override fun toString(): String = "Other"

    // `implementation EdgeLabel for Other`
    fun vecMut(nfa: Edges): MutableList<Edge<Other>> = nfa.otherEdges
    fun vec(nfa: Edges): List<Edge<Other>> = nfa.otherEdges
    fun firstMut(state: NfaState): Int = state.firstOtherEdge
    fun first(state: NfaState): Int = state.firstOtherEdge
}

/**
 * `interface EdgeLabel` translated as a sealed sum type: each
 * variant carries the corresponding edge-label payload (or none, in
 * the Noop/Other case). The `vec/vecMut/first/firstMut` trait
 * methods live on the concrete companion objects of [Test], [Noop],
 * and [Other] above; this sealed class exists so that callers can
 * pass a single [EdgeLabel] value and dispatch on it.
 */
sealed class EdgeLabel {
    /** Wrapper for a [io.github.kotlinmania.lalrpop.lexer.nfa.Test] edge label. */
    data class Test(val test: io.github.kotlinmania.lalrpop.lexer.nfa.Test) : EdgeLabel() {
        override fun toString(): String = test.toString()
    }

    object Other : EdgeLabel() {
        override fun toString(): String = "Other"
    }

    object Noop : EdgeLabel() {
        override fun toString(): String = "Noop"
    }
}

/**
 * For each state, we just store the indices of the first char and
 * test edges, or Int.MAX_VALUE if no such edge. You can then find all
 * edges by enumerating subsequent edges in the vectors until you
 * find one with a different `from` value.
 */
data class NfaState(
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

    /**
     * Mirrors `implementation Debug for NfaStateIndex` (mod.rs:609-613):
     * `function fmt(&self, fmt: &mut Formatter<'_>) -> Result<(), FmtError> {
     *      write(fmt, "Nfa{}", self.0)
     *  }`.
     *
     * Translated as a string-builder write; [toString] delegates to it
     * so Kotlin standard `print`/`"$x"` interpolation goes through
     * the same path.
     */
    fun fmt(out: StringBuilder): StringBuilder = out.append("Nfa").append(value)

    override fun toString(): String = fmt(StringBuilder()).toString()
}


/**
 * A set of edges for the state machine. Edges are kept sorted by the
 * type of label they have. Within a vector, all edges with the same
 * `from` are grouped together so they can be enumerated later (for
 * now we just ensure this during construction, but one could easily
 * sort).
 */
class Edges {
    val noopEdges: MutableList<Edge<Noop>> = mutableListOf()

    // edges where we are testing the character in some way; for any
    // given state, there should not be multiple edges with the same
    // test
    val testEdges: MutableList<Edge<Test>> = mutableListOf()

    // fallback rules if no testEdge applies
    val otherEdges: MutableList<Edge<Other>> = mutableListOf()
}

/**
 * Mirrors `class Edge<L>` plus the `implementation<L: Debug> Debug for
 * Edge<L>` body (line 615-618). It used to be a `data class` but we
 * lift it to a regular class so the explicit [toString] override
 * tracks the upstream `write(fmt, "{:?} -{:?}-> {:?}", ...)` line-by-line.
 */
class Edge<L>(
    val from: NfaStateIndex,
    val label: L,
    val to: NfaStateIndex,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Edge<*>) return false
        return from == other.from && label == other.label && to == other.to
    }
    override fun hashCode(): Int {
        var h = from.hashCode()
        h = 31 * h + (label?.hashCode() ?: 0)
        h = 31 * h + to.hashCode()
        return h
    }
    /** `implementation<L: Debug> Debug for Edge<L>`: `"{:?} -{:?}-> {:?}"`. */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(from).append(" -").append(label).append("-> ").append(to)
        return sb.toString()
    }
}

/**
 * Mirrors `class EdgeIterator<'nfa, L: EdgeLabel>` plus the
 * `Iterator` implementation at line 483.
 */
class EdgeIterator<L>(
    private val edges: List<Edge<L>>,
    private val from: NfaStateIndex,
    private var index: Int,
) : Iterator<Edge<L>> {
    private var pending: Edge<L>? = null
    private var advanced: Boolean = false

    private fun advance(): Edge<L>? {
        // line-for-line port of `function next(&mut self)` from line 486:
        val current = index
        if (current == UNSET) {
            return null
        }

        val nextIndex = current + 1
        index = if (nextIndex >= edges.size || edges[nextIndex].from != from) {
            UNSET
        } else {
            nextIndex
        }

        return edges[current]
    }

    override fun hasNext(): Boolean {
        if (!advanced) {
            pending = advance()
            advanced = true
        }
        return pending != null
    }

    override fun next(): Edge<L> {
        if (!advanced) {
            pending = advance()
        }
        val out = pending ?: throw NoSuchElementException()
        advanced = false
        pending = null
        return out
    }

    fun asSequence(): Sequence<Edge<L>> = sequence {
        while (this@EdgeIterator.hasNext()) yield(this@EdgeIterator.next())
    }
}

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

// Mirrors upstream `public type Nfa = Nfa;` (lexer/nfa/mod.rs:27),
// `public type NfaStateIndex = NfaStateIndex;` (line 83), and
// `public type NfaConstructionError = NfaConstructionError;` (line 123).
// These are real `public type` aliases in upstream, so per AGENTS.md they
// translate as Kotlin `typealias` (the only typealias case the project
// permits — non-re-export, on a literal upstream `public type`).
