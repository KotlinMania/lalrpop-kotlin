// port-lint: source lr1/tls.rs
package io.github.kotlinmania.lalrpop.lr1

/**
 * Thread-local data specific to LR(1) processing.
 */

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

private object TERMINALS {
    var value: TerminalSet? = null
}

class Lr1Tls private constructor(
    private var oldValue: TerminalSet?,
) : AutoCloseable {

    private fun drop() {
        TERMINALS.value = oldValue
        oldValue = null
    }

    override fun close() = drop()

    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = TERMINALS.value
            TERMINALS.value = terminals
            return Lr1Tls(oldValue)
        }

        fun <RET> with(
            op: (TerminalSet) -> RET,
        ): RET {
            return op(TERMINALS.value ?: error("LR1 TLS not installed"))
        }
    }
}
