// port-lint: source collections/multimap.rs
package io.github.kotlinmania.lalrpop.collections.multimap

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.btree.BTreeSet
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.set

class Multimap<K : Comparable<K>, C : Collection<Item>, Item>(
    private val collectionFactory: () -> C,
) : Iterable<Pair<K, C>> {
    // The K : Comparable<K> bound mirrors upstream
    // `implementation<K: Ord, C: Collection> Multimap<K, C>` block — Rust hangs
    // the bound on the implementation rather than the struct, but Kotlin field
    // initializers cannot carry their own bound, so we lift it onto the
    // class. Every Multimap instantiated by the rest of the codebase
    // already keys on a Comparable type (Production, Item<Nil>,
    // StateIndex, Symbol, Atom, NonterminalString).
    private val map: Map<K, C> = map()

    companion object {
        fun <K : Comparable<K>, C : Collection<Item>, Item> new(
            collectionFactory: () -> C,
        ): Multimap<K, C, Item> = Multimap(collectionFactory)
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

fun <K : Comparable<K>, C : Collection<Item>, Item> fromIter(
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

class SetCollection<T : Comparable<T>> : Collection<T> {
    // Mirrors `implementation<T: Ord> Default for BTreeSet<T>` — Rust adds the bound
    // implicitly via `BTreeSet<T>: Default` requiring `T: Ord`.
    private val inner: Set<T> = set()

    override fun push(item: T): Boolean = inner.add(item)

    fun asSet(): Set<T> = inner
}

class MultimapCollection<K : Comparable<K>, C : Collection<Item>, Item>(
    private val inner: Multimap<K, C, Item>,
) : Collection<Pair<K, Item>> {
    override fun push(item: Pair<K, Item>): Boolean {
        val (key, value) = item
        return inner.push(key, value)
    }
}
