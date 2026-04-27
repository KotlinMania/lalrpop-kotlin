// port-lint: source src/lr1/tls.rs
package io.github.kotlinmania.lalrpop.lr1.tls

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 *  at your option.
 */

/** Thread-local data specific to LR(1) processing. */

import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet

private class RefCell<T>(private var value: T) {
    fun borrow(): T = value
    fun borrowMut(): RefCell<T> = this
    fun replace(other: T): T {
        val old = value
        value = other
        return old
    }
    fun take(default: T): T {
        val old = value
        value = default
        return old
    }
}

private object TERMINALS {
    private val cell: RefCell<TerminalSet?> = RefCell(null)
    fun <R> with(op: (RefCell<TerminalSet?>) -> R): R = op(cell)
}

class Lr1Tls private constructor(
    private var oldValue: TerminalSet?,
) : AutoCloseable {

    fun drop() {
        TERMINALS.with { s -> s.borrowMut().replace(oldValue) }
    }

    override fun close() = drop()

    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = TERMINALS.with { s -> s.borrowMut().replace(terminals) }
            return Lr1Tls(oldValue)
        }

        fun <RET> with(op: (TerminalSet) -> RET): RET {
            return TERMINALS.with { s -> op(s.borrow() ?: error("LR1 TLS not installed")) }
        }
    }
}
