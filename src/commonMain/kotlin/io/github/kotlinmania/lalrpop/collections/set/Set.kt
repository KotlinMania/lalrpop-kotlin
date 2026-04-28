// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections.set

import io.github.kotlinmania.btree.BTreeSet

/** As [BTreeMap][io.github.kotlinmania.btree.BTreeMap], but for sets. */
fun <K : Comparable<K>> set(): BTreeSet<K> {
    return BTreeSet<K>()
}
