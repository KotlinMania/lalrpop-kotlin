// port-lint: source src/collections/multimap.rs
package io.github.kotlinmania.lalrpop_kotlin.collections.multimap

import io.github.kotlinmania.lalrpop_kotlin.collections.map.Map
import io.github.kotlinmania.lalrpop_kotlin.collections.map.map
import io.github.kotlinmania.lalrpop_kotlin.collections.set.Set
import io.github.kotlinmania.lalrpop_kotlin.collections.set.set

class Multimap<K, C : Collection<Item>, Item>(
    private val collectionFactory: () -> C,
) : Iterable<Pair<K, C>> {
    private val map: Map<K, C> = map()

    companion object {
        fun <K, C : Collection<Item>, Item> new(collectionFactory: () -> C): Multimap<K, C, Item> =
            Multimap(collectionFactory)
    }

    fun isEmpty(): Boolean = map.isEmpty()

    /**
     * Push `value` to the collection associated with `key`. Returns
     * true if the collection was changed from the default.
     */
    fun push(key: K, value: Item): Boolean {
        var inserted = false
        val pushed = map.getOrPut(key) {
            inserted = true
            collectionFactory()
        }.push(value)
        return inserted || pushed
    }

    fun get(key: K): C? = map[key]

    fun iter(): Iterator<Pair<K, C>> = map.entries.asSequence().map { it.key to it.value }.iterator()

    fun intoIter(): Iterator<Pair<K, C>> = iter()

    override fun iterator(): Iterator<Pair<K, C>> = iter()

    fun default(): Multimap<K, C, Item> = Multimap(collectionFactory)
}

fun <K, C : Collection<Item>, Item> fromIter(
    collectionFactory: () -> C,
    iterator: Iterable<Pair<K, Item>>,
): Multimap<K, C, Item> {
    val mm = Multimap<K, C, Item>(collectionFactory)
    for ((key, value) in iterator) {
        mm.push(key, value)
    }
    return mm
}

interface Collection<Item> {
    /**
     * Push `item` into the collection and return `true` if
     * collection changed.
     */
    fun push(item: Item): Boolean
}

class UnitCollection : Collection<Unit> {
    override fun push(item: Unit): Boolean = false
}

class VecCollection<T> : Collection<T> {
    private val inner: MutableList<T> = mutableListOf()

    override fun push(item: T): Boolean {
        inner.add(item)
        return true // always changes
    }

    fun asList(): List<T> = inner
}

class SetCollection<T> : Collection<T> {
    private val inner: Set<T> = set()

    override fun push(item: T): Boolean = inner.add(item)

    fun asSet(): Set<T> = inner
}

class MultimapCollection<K, C : Collection<Item>, Item>(
    private val inner: Multimap<K, C, Item>,
) : Collection<Pair<K, Item>> {
    override fun push(item: Pair<K, Item>): Boolean {
        val (key, value) = item
        return inner.push(key, value)
    }
}
