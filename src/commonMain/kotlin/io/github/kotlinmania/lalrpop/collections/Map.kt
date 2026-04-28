// port-lint: source collections/map.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeMap

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against [Map] (and [map] to construct
 * an instance). This should be a deterministic map, such that two
 * runs of LALRPOP produce the same output, but otherwise it does not
 * matter much. I would probably prefer to use [HashMap] with an
 * alternative hasher, but that is not stable.
 */
class Map<K : Comparable<K>, V> private constructor(
    private val inner: BTreeMap<K, V>,
) : MutableMap<K, V> by inner {
    companion object {
        fun <K : Comparable<K>, V> default(): Map<K, V> = Map(BTreeMap())
    }
}

fun <K : Comparable<K>, V> map(): Map<K, V> {
    return Map.default()
}
