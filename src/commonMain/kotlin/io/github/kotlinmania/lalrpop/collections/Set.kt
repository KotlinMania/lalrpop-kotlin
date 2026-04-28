// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/** As [io.github.kotlinmania.lalrpop.collections.map], but for sets. */
fun <K : Comparable<K>> set(): BTreeSet<K> {
    return BTreeSet()
}
