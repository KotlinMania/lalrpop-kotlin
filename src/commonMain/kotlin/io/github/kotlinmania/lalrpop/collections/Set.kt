// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/** As [Map], but for sets. */
class Set<K : Comparable<K>> private constructor(
    private val inner: BTreeSet<K>,
) {
    fun insert(key: K) {
        inner.insert(key)
    }

    fun contains(key: K): Boolean = inner.contains(key)

    fun remove(key: K): Boolean = inner.remove(key)

    fun isEmpty(): Boolean = inner.isEmpty()

    fun len(): Int = inner.len()

    fun iter(): Iterator<K> = inner.iter()

    companion object {
        fun <K : Comparable<K>> default(): Set<K> = Set(BTreeSet.default())
    }
}

fun <K : Comparable<K>> set(): Set<K> = Set.default()
