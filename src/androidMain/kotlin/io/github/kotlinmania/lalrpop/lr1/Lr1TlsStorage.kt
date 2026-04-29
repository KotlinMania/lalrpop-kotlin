// port-lint: source lr1/tls.rs (thread_local! definition, JVM target)
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

internal actual object Lr1TlsStorage {
    private val tl: ThreadLocal<TerminalSet?> = ThreadLocal()

    actual var terminals: TerminalSet?
        get() = tl.get()
        set(value) {
            if (value == null) tl.remove() else tl.set(value)
        }
}

