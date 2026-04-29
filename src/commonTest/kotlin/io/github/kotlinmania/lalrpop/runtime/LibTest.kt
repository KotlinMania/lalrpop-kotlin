// port-lint: source lib.rs
package io.github.kotlinmania.lalrpop.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class LibTest {
    @Test
    fun test() {
        val err: ParseError<Int, String, String> = ParseError.UnrecognizedToken(
            token = Triple(1, "t0", 2),
            expected = listOf("t1", "t2", "t3"),
        )
        assertEquals(
            "Unrecognized token `t0` found at 1:2\n" +
                "Expected one of t1, t2 or t3",
            err.toString(),
        )
    }
}
