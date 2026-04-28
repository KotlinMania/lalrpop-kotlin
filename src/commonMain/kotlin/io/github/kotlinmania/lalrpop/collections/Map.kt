// port-lint: source collections/map.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeMap

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against [BTreeMap] (and [map] to construct
 * an instance). This should be a deterministic map, such that two
 * runs of LALRPOP produce the same output, but otherwise it doesn't
 * matter much. I would probably prefer to use [HashMap] with an
 * alternative hasher, but that's not stable.
 */
fun <K : Comparable<K>, V> map(): BTreeMap<K, V> {
    return BTreeMap()
}
