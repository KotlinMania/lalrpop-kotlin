// port-lint: source src/collections/set.rs
package io.github.kotlinmania.lalrpop_kotlin.collections.set

/** As `Map`, but for sets. */
typealias Set<K> = MutableSet<K>

fun <K> set(): Set<K> = linkedSetOf()
