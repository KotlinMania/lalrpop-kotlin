// port-lint: source lr1/tls.rs
/** Thread-local data specific to LR(1) processing. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

private var TERMINALS: TerminalSet? = null

class Lr1Tls private constructor(
    private var oldValue: TerminalSet?,
) {
    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = TERMINALS
            TERMINALS = terminals
            return Lr1Tls(oldValue)
        }

        fun <RET> with(op: (TerminalSet) -> RET): RET {
            val terminals = TERMINALS ?: error("LR1 TLS not installed")
            return op(terminals)
        }
    }

    fun drop() {
        val taken = oldValue
        oldValue = null
        TERMINALS = taken
    }
}
