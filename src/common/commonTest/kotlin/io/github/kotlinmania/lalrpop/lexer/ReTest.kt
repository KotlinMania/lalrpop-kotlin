// port-lint: source lexer/re/test.rs
package io.github.kotlinmania.lalrpop.lexer

import kotlin.test.Test
import kotlin.test.assertTrue

class ReTest {
    @Test
    fun parseUnclosedGroup() {
        assertTrue(parseRegex("(123").isFailure)
    }

    @Test
    fun altOom() {
        parseRegex("(%%|[^%])+").getOrThrow()
    }
}
