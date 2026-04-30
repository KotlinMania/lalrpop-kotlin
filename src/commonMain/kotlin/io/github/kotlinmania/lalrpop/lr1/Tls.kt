// port-lint: source lr1/tls.rs
/** Thread-local data specific to LR(1) processing. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

internal expect object Lr1TlsStorage {
    var terminals: TerminalSet?
}

class Lr1Tls private constructor(
    private var oldValue: TerminalSet?,
) {
    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = Lr1TlsStorage.terminals
            Lr1TlsStorage.terminals = terminals
            return Lr1Tls(oldValue)
        }

        fun <RET> with(op: (TerminalSet) -> RET): RET {
            val terminals = Lr1TlsStorage.terminals
            check(terminals != null) { "LR1 TLS not installed" }
            return op(terminals)
        }
    }

    fun drop() {
        Lr1TlsStorage.terminals = oldValue.also { oldValue = null }
    }
}
