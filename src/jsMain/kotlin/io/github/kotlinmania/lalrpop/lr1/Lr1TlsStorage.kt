// port-lint: source lr1/tls.rs (thread_local! definition, JS target)
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

internal actual object Lr1TlsStorage {
    actual var terminals: TerminalSet? = null
}

