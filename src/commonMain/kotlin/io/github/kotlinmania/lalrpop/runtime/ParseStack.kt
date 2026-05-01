package io.github.kotlinmania.lalrpop.runtime

/**
 * Typed parse stack used by data-driven generated parsers.
 *
 * Each generated parser declares its own `Symbol` sealed class, with one variant per
 * distinct stack-element type. `ParseStack<S>` stores those variants and lets reduction
 * lambdas pop typed payloads back out.
 *
 * The stack stores `Located<S>` triples (start location, symbol, end location) to match
 * the existing [ParserDefinition.reduce] contract — every push carries the source span
 * the symbol came from, and reductions can compute the span of the produced symbol from
 * the spans of its constituents.
 *
 * Pops are checked against the variant type at runtime via a sealed-class cast. If the
 * generated tables disagree with the lambdas (i.e. a codegen bug), the cast fails fast
 * with a typed exception rather than corrupting the parse silently.
 */
class ParseStack<S, L>(
    initialCapacity: Int = 32,
) {
    private val storage: ArrayDeque<Located<S, L>> = ArrayDeque(initialCapacity)

    /** Number of elements on the stack. */
    val size: Int get() = storage.size

    /** True if the stack is empty. */
    fun isEmpty(): Boolean = storage.isEmpty()

    /** Push a symbol with its source span onto the top of the stack. */
    fun push(start: L, symbol: S, end: L) {
        storage.addLast(Located(start, symbol, end))
    }

    /** Push a pre-located symbol onto the top of the stack. */
    fun push(located: Located<S, L>) {
        storage.addLast(located)
    }

    /** Pop the top entry. Throws [NoSuchElementException] if the stack is empty. */
    fun popLocated(): Located<S, L> = storage.removeLast()

    /**
     * Pop the top entry and return its symbol payload as the reified variant type [V].
     *
     * Type-checked at runtime against the sealed `Symbol` hierarchy. A mismatch indicates
     * a codegen bug (the parse table disagrees with the production's declared RHS shape),
     * not a user error, and surfaces immediately as a [ParseStackTypeException].
     */
    inline fun <reified V : S> pop(): V {
        val top = popLocated()
        val payload = top.symbol
        if (payload !is V) {
            throw ParseStackTypeException(
                expected = V::class.simpleName ?: "<anonymous>",
                actual = payload?.let { it::class.simpleName ?: "<anonymous>" } ?: "null",
            )
        }
        return payload
    }

    /**
     * Pop [n] entries and return them in **stack order** — index 0 is the value pushed
     * earliest among the popped run, index `n - 1` is the value popped first (the original
     * top of stack).
     *
     * Production action lambdas receive their inputs in the order the rule's RHS lists
     * them, which is the same as stack order. Returning the run already in that order
     * means lambdas don't have to reverse anything.
     */
    fun popLocatedRun(n: Int): List<Located<S, L>> {
        require(n >= 0) { "cannot pop a negative count: $n" }
        if (n == 0) return emptyList()
        check(storage.size >= n) {
            "stack underflow: tried to pop $n, have ${storage.size}"
        }
        val out = ArrayList<Located<S, L>>(n)
        val base = storage.size - n
        for (i in 0 until n) {
            out.add(storage[base + i])
        }
        repeat(n) { storage.removeLast() }
        return out
    }

    /** Peek at the top entry without popping. */
    fun peek(): Located<S, L> = storage.last()

    /** Peek at the entry [depth] from the top (0 = top). */
    fun peek(depth: Int): Located<S, L> {
        require(depth >= 0) { "negative depth: $depth" }
        check(depth < storage.size) { "peek depth $depth >= size ${storage.size}" }
        return storage[storage.size - 1 - depth]
    }
}

/**
 * A symbol paired with the source span it covers.
 *
 * Equivalent to the upstream Rust `(L, Symbol, L)` triple but as a named type so the
 * span endpoints can't be silently swapped at the call site.
 */
data class Located<out S, out L>(
    val start: L,
    val symbol: S,
    val end: L,
)

/**
 * Thrown by [ParseStack.pop] when the popped symbol's runtime variant disagrees with the
 * type the caller asked for.
 *
 * This indicates a codegen bug — the parse table told the driver to reduce a production
 * whose action lambda expects variants the stack doesn't actually contain. Generated code
 * should never trigger this in practice; if a user sees it, the generator has shipped a
 * miscompiled grammar.
 */
class ParseStackTypeException(
    val expected: String,
    val actual: String,
) : IllegalStateException("parse stack type mismatch: expected $expected, got $actual")
