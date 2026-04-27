// port-lint: source src/collections/set.rs
package io.github.kotlinmania.lalrpop.collections.set

import io.github.kotlinmania.btree.BTreeSet

/**
 * `pub type Set<K> = BTreeSet<K>;` — direct port. As `Map`, but for
 * sets.
 *
 * The typealias remains at the wider [MutableSet] interface (rather
 * than the narrower [BTreeSet]) so Kotlin-side code that builds via
 * `mutableSetOf()` / `linkedSetOf()` doesn't need a `Comparable`
 * bound it doesn't actually use. [set] returns a real [BTreeSet]
 * from `io.github.kotlinmania:btree-kotlin` — the line-by-line port
 * of `std::collections::BTreeSet`. Iteration order matches upstream
 * `BTreeSet` byte-for-byte for any `Comparable` element type.
 */
typealias Set<K> = MutableSet<K>

/**
 * `pub fn set<K: Ord>() -> Set<K>` — direct port. The
 * `K : Comparable<K>` bound mirrors Rust's `K: Ord`.
 */
fun <K : Comparable<K>> set(): Set<K> = BTreeSet()
