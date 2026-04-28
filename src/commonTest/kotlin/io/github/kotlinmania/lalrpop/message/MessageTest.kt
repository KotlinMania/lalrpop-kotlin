// port-lint: source message/test.rs
package io.github.kotlinmania.lalrpop.message

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.expectDebug
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.message.builder.MessageBuilder
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test

private fun installTls(): Tls = Tls.testString(
    """foo
bar
baz
""",
)

class MessageTest {
    @Test
    fun helloWorld() {
        installTls().use {
            val msg = MessageBuilder.new(Span(0, 2))
                .heading()
                .text("Hello, world!")
                .end()
                .body()
                .beginWrap()
                .text(
                    "This is a very, very, very, very long sentence. " +
                        "OK, not THAT long!",
                )
                .end()
                .indentedBy(4)
                .end()
                .end()
            val minWidth = msg.minWidth()
            val canvas = AsciiCanvas.new(0, minWidth)
            msg.emit(canvas)
            expectDebug(
                canvas.toStrings(),
                """
[
    "tmp.txt:1:1: 1:2: Hello, world!",
    "",
    "      This is a very, very,",
    "      very, very long sentence.",
    "      OK, not THAT long!"
]
""".trim(),
            )
        }
    }

    /**
     * Test a case where the body in the message is longer than the
     * header (which used to mess up the `minWidth` computation).
     */
    @Test
    fun longBody() {
        installTls().use {
            val msg = MessageBuilder.new(Span(0, 2))
                .heading()
                .text("Hello, world!")
                .end()
                .body()
                .text(
                    "This is a very, very, very, very long sentence. " +
                        "OK, not THAT long!",
                )
                .end()
                .end()
            val minWidth = msg.minWidth()
            val canvas = AsciiCanvas.new(0, minWidth)
            msg.emit(canvas)
            expectDebug(
                canvas.toStrings(),
                """
[
    "tmp.txt:1:1: 1:2: Hello, world!",
    "",
    "  This is a very, very, very, very long sentence. OK, not THAT long!"
]
""".trim(),
            )
        }
    }
}
