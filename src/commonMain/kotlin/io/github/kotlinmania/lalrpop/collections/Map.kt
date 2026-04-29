// port-lint: source collections/map.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeMap

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against [Map] (and [map] to construct
 * an instance). This should be a deterministic map, such that two
 * runs of LALRPOP produce the same output, but otherwise it doesn't
 * matter much. I would probably prefer to use [HashMap] with an
 * alternative hasher, but that's not stable.
 */
class Map<K : Comparable<K>, V> private constructor(
    private val inner: BTreeMap<K, V>,
) {
    fun insert(key: K, value: V) {
        inner.insert(key, value)
    }

    fun get(key: K): V? = inner.get(key)

    fun remove(key: K): V? = inner.remove(key)

    fun isEmpty(): Boolean = inner.isEmpty()

    fun len(): Int = inner.len()

    fun iter(): Iterator<Pair<K, V>> = inner.iter()

    companion object {
        fun <K : Comparable<K>, V> default(): Map<K, V> = Map(BTreeMap.default())
    }
}

fun <K : Comparable<K>, V> map(): Map<K, V> = Map.default()
