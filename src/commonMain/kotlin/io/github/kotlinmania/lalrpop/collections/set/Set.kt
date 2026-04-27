// port-lint: source src/collections/set.rs
package io.github.kotlinmania.lalrpop.collections.set

import io.github.kotlinmania.btree.BTreeSet

/**
 * `public type Set<K> = BTreeSet<K>;` — direct port. As `Map`, but for
 * sets.
 *
 * The typealias remains at the wider [MutableSet] interface (rather
 * than the narrower [BTreeSet]) so Kotlin-side code that builds via
 * `mutableSetOf()` / `linkedSetOf()` does not need a `Comparable`
 * bound it does not actually use. [set] returns a real [BTreeSet]
 * from `io.github.kotlinmania:btree-kotlin` — the line-by-line port
 * of `std::collections::BTreeSet`. Iteration order matches upstream
 * `BTreeSet` byte-for-byte for any `Comparable` element type.
 */
typealias Set<K> = MutableSet<K>

/**
 * `fun set<K: Ord>() -> Set<K>` — direct port. The
 * `K : Comparable<K>` bound mirrors the upstream `K: Ord`.
 */
fun <K : Comparable<K>> set(): Set<K> = BTreeSet()
