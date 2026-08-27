// Kotlin-only support: ordering wrappers needed because Kotlin does not
// auto-derive `Comparable<List<T>>` / `Comparable<Pair<A, B>>` the way
// Rust auto-derives `Ord` for `Vec<T: Ord>` and `(A: Ord, B: Ord)`.
//
// This file has no upstream Rust counterpart, so it carries no
// `port-lint: source` header.
package io.github.kotlinmania.lalrpop.collections

/**
 * Hand-rolled stand-in for the upstream auto-derived `Ord` on
 * `Vec<T: Ord>`: lexicographic compare with shorter-list-first when
 * one is a prefix of the other. Used as a key when the upstream Rust
 * code keys on `Vec<T>` directly.
 */
internal data class ComparableList<T : Comparable<T>>(
    val items: List<T>,
) : Comparable<ComparableList<T>> {
    override fun compareTo(other: ComparableList<T>): Int {
        val n = minOf(items.size, other.items.size)
        for (i in 0 until n) {
            val c = items[i].compareTo(other.items[i])
            if (c != 0) return c
        }
        return items.size.compareTo(other.items.size)
    }

    override fun toString(): String = items.toString()
}

/**
 * Hand-rolled stand-in for the upstream auto-derived `Ord` on
 * `(A: Ord, B: Ord)`: compare `first`, then `second`. Used as a key
 * when the upstream Rust code keys on a 2-tuple directly.
 */
internal data class ComparablePair<A : Comparable<A>, B : Comparable<B>>(
    val first: A,
    val second: B,
) : Comparable<ComparablePair<A, B>> {
    override fun compareTo(other: ComparablePair<A, B>): Int {
        val c = first.compareTo(other.first)
        if (c != 0) return c
        return second.compareTo(other.second)
    }

    override fun toString(): String = "($first, $second)"
}
