// port-lint: source collections/map.rs
package io.github.kotlinmania.lalrpop.collections

import io.github.kotlinmania.btree.BTreeMap

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against [Map] (and [map] to construct
 * an instance). This should be a deterministic map, such that two
 * runs of LALRPOP produce the same output, but otherwise it doesn't
 * matter much. I'd probably prefer to use a hash-based map with an
 * alternative hasher, but that's not stable.
 */
internal typealias Map<K, V> = BTreeMap<K, V>

internal fun <K : Comparable<K>, V> map(): Map<K, V> = BTreeMap()
