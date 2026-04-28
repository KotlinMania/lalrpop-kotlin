// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/**
 * As [Map], but for sets.
 */
class Set<K : Comparable<K>>(
    private val inner: BTreeSet<K> = BTreeSet(),
) : MutableSet<K> by inner {
    companion object {
        fun <K : Comparable<K>> default(): Set<K> = Set()
    }
}

fun <K : Comparable<K>> set(): Set<K> {
    return Set.default<K>()
}
