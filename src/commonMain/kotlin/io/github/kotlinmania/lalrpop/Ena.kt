// port-lint: source external/ena/unify
// Minimal port of `ena::unify::InPlaceUnificationTable` — a union-find
// (a.k.a. disjoint-set) structure with per-key values that are merged
// via user-supplied `unifyValues`. Only the surface used by the
// lane-table construction is modeled.
package io.github.kotlinmania.lalrpop

interface UnifyKey<V> {
    fun index(): Int

    companion object {
        fun tag(): String = "UnifyKey"
    }
}

interface UnifyValue<Self> {
    /**
     * Merge two values. Returns `null` if the merge is not possible;
     * callers treat that as a unification failure.
     */
    fun unifyValues(other: Self): Self?
}

class InPlaceUnificationTable<K : UnifyKey<V>, V>(
    private val keyFromIndex: (Int) -> K,
    private val unifyValues: (V, V) -> V?,
) {
    private val parent: MutableList<Int> = mutableListOf()
    private val values: MutableList<V> = mutableListOf()
    private val rank: MutableList<Int> = mutableListOf()

    fun newKey(value: V): K {
        val idx = parent.size
        parent.add(idx)
        values.add(value)
        rank.add(0)
        return keyFromIndex(idx)
    }

    fun probeValue(key: K): V = values[find(key.index())]

    fun unifyVarVar(a: K, b: K): Boolean {
        val ra = find(a.index())
        val rb = find(b.index())
        if (ra == rb) return true

        val merged = unifyValues(values[ra], values[rb]) ?: return false

        val (winner, loser) = when {
            rank[ra] < rank[rb] -> Pair(rb, ra)
            rank[ra] > rank[rb] -> Pair(ra, rb)
            else -> {
                rank[ra] = rank[ra] + 1
                Pair(ra, rb)
            }
        }
        parent[loser] = winner
        values[winner] = merged
        return true
    }

    private fun find(i: Int): Int {
        var current = i
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }
}
