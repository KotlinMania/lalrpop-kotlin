// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/**
 * As [Map], but for sets.
 */
class Set<K : Comparable<K>>(
    private val inner: BTreeSet<K> = BTreeSet(),
) : MutableSet<K> by inner {
    fun asBTreeSet(): BTreeSet<K> = inner
}

fun <K : Comparable<K>> set(): Set<K> {
    return Set()
}
