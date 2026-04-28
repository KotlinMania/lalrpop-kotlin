// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/** As [Map], but for sets. */
class Set<K : Comparable<K>> private constructor(
    private val inner: BTreeSet<K>,
) : MutableSet<K> by inner {
    fun iter(): Iterator<K> = inner.iterator()

    fun len(): Int = inner.size

    fun insert(value: K): Boolean = inner.insert(value)

    companion object {
        fun <K : Comparable<K>> default(): Set<K> = Set(BTreeSet())
    }
}

fun <K : Comparable<K>> set(): Set<K> {
    return Set.default<K>()
}
