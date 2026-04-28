// port-lint: source collections/map.rs
package io.github.kotlinmania.lalrpop.collections.map

import io.github.kotlinmania.btree.BTreeMap

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against [BTreeMap][io.github.kotlinmania.btree.BTreeMap]
 * (and [map] to construct an instance). This should be a deterministic
 * map, such that two runs of LALRPOP produce the same output, but
 * otherwise it does not matter much. I would probably prefer to use
 * [HashMap][kotlin.collections.HashMap] with an alternative hasher,
 * but that is not stable.
 */
fun <K : Comparable<K>, V> map(): BTreeMap<K, V> {
    return BTreeMap<K, V>()
}
