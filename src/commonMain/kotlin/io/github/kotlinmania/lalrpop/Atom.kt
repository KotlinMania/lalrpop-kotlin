// port-lint: source external/stringCache/DefaultAtom
// Minimal port of `stringCache::DefaultAtom` — an interned string.
// Interning is not implemented here; this is a plain string wrapper.
package io.github.kotlinmania.lalrpop

class Atom private constructor(private val value: String) : Comparable<Atom> {
    fun len(): Int = value.length
    fun asRef(): String = value

    override fun toString(): String = value
    override fun hashCode(): Int = value.hashCode()
    override fun equals(other: Any?): Boolean = other is Atom && other.value == value
    override fun compareTo(other: Atom): Int = value.compareTo(other.value)

    companion object {
        fun from(s: String): Atom = Atom(s)
    }
}
