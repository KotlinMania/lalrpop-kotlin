// port-lint: source lexer/re/test.rs
package io.github.kotlinmania.lalrpop.lexer.re

import kotlin.test.Test
import kotlin.test.assertTrue

class ReTest {
    @Test
    fun parse_unclosed_group() {
        assertTrue(parseRegex("(123").isFailure)
    }

    @Test
    fun alt_oom() {
        parseRegex("(%%|[^%])+").getOrThrow()
    }
}
