// port-lint: source src/collections/map.rs
package io.github.kotlinmania.lalrpop_kotlin.collections.map

/**
 * In general, we avoid coding directly against any particular map,
 * but rather build against `util::Map` (and `util::map` to construct
 * an instance). This should be a deterministic map, such that two
 * runs of LALRPOP produce the same output, but otherwise it doesn't
 * matter much. I'd probably prefer to use `HashMap` with an
 * alternative hasher, but that's not stable.
 */
typealias Map<K, V> = MutableMap<K, V>

fun <K, V> map(): Map<K, V> = linkedMapOf()
