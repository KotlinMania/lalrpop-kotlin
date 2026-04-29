// port-lint: source collections/set.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeSet

/** As [Map], but for sets. */
typealias Set<K> = BTreeSet<K>

fun <K : Comparable<K>> set(): Set<K> = BTreeSet.default()
