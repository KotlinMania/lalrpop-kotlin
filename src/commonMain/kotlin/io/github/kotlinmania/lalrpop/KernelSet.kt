// port-lint: source kernel_set.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map

class KernelSet<K : Kernel<K, Index>, Index> {
    private var counter: Int = 0
    private val kernels: ArrayDeque<K> = ArrayDeque()
    private val map: Map<K, Index> = map()

    companion object {
        fun <K : Kernel<K, Index>, Index> new(): KernelSet<K, Index> = KernelSet()
    }

    fun addState(kernel: K): Index {
        val kernels = this.kernels
        return map.getOrPut(kernel) {
            val index = counter
            counter += 1
            kernels.addLast(kernel)
            kernel.index(index)
        }
    }

    fun next(): K? = kernels.removeFirstOrNull()
}

interface Kernel<K : Kernel<K, Index>, Index> : Comparable<K> {
    fun index(c: Int): Index
}
