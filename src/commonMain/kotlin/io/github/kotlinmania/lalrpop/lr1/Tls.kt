// port-lint: source lr1/tls.rs
package io.github.kotlinmania.lalrpop.lr1

/**
 * Thread-local data specific to LR(1) processing.
 */

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

private object TERMINALS {
    private val cell: RefCell<TerminalSet?> = RefCell(null)
    fun <R> with(op: (RefCell<TerminalSet?>) -> R): R = op(cell)
}

private class RefCell<T>(private var value: T) {
    fun borrow(): T = value
    fun borrowMut(): RefCell<T> = this
    fun set(other: T) {
        value = other
    }
    fun replace(other: T): T {
        val old = value
        value = other
        return old
    }
}

private fun <T> RefCell<T?>.take(): T? = this.replace(null)

class Lr1Tls private constructor(
    private val oldValue: RefCell<TerminalSet?>,
) : AutoCloseable {

    fun drop() {
        TERMINALS.with { s -> s.borrowMut().set(this.oldValue.take()) }
    }

    override fun close() = drop()

    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = TERMINALS.with { s -> s.borrowMut().replace(terminals) }
            return Lr1Tls(RefCell(oldValue))
        }

        fun <RET> with(
            op: (TerminalSet) -> RET,
        ): RET {
            return TERMINALS.with { s ->
                val terminals = s.borrow()
                op(terminals ?: error("LR1 TLS not installed"))
            }
        }
    }
}
