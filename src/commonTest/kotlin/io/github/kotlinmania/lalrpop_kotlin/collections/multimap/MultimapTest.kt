// port-lint: source src/collections/multimap.rs
package io.github.kotlinmania.lalrpop_kotlin.collections.multimap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultimapTest {
    @Test
    fun push() {
        val m: Multimap<UInt, SetCollection<Char>, Char> = Multimap.new(::SetCollection)
        assertTrue(m.push(0u, 'a'))
        assertTrue(m.push(0u, 'b'))
        assertFalse(m.push(0u, 'b'))
        assertTrue(m.push(1u, 'a'))
    }

    @Test
    fun push_nil() {
        val m: Multimap<UInt, UnitCollection, Unit> = Multimap.new(::UnitCollection)
        assertTrue(m.push(0u, Unit))
        assertFalse(m.push(0u, Unit))
        assertTrue(m.push(1u, Unit))
        assertFalse(m.push(0u, Unit))
    }
}
