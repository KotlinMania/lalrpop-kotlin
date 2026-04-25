// port-lint: source src/lr1/tls.rs
package io.github.kotlinmania.lalrpop_kotlin.lr1.tls

/*
 * Copyright 2019 The Starlark in Rust Authors.
 * Copyright (c) Facebook, Inc. and its affiliates.
 * Copyright (c) 2025 Sydney Renee, The Solace Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.TerminalSet

/** Thread-local data specific to LR(1) processing. */
private object Terminals {
    // Kotlin Multiplatform commonMain has no thread-local primitive;
    // a process-global slot mirrors the single-threaded use in the
    // Rust original (each thread would scope its own install/drop).
    var value: TerminalSet? = null
}

class Lr1Tls private constructor(
    private val oldValue: TerminalSet?,
) : AutoCloseable {
    private var closed: Boolean = false

    fun drop() {
        close()
    }

    override fun close() {
        if (!closed) {
            Terminals.value = oldValue
            closed = true
        }
    }

    companion object {
        fun install(terminals: TerminalSet): Lr1Tls {
            val oldValue = Terminals.value
            Terminals.value = terminals
            return Lr1Tls(oldValue)
        }

        fun <RET> with(op: (TerminalSet) -> RET): RET {
            val t = Terminals.value ?: error("LR1 TLS not installed")
            return op(t)
        }
    }
}
