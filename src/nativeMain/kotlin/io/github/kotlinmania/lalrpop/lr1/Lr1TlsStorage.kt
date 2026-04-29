// port-lint: source lr1/tls.rs (thread_local! definition, native target)
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual object Lr1TlsStorage {
    actual var terminals: TerminalSet? = null
}

