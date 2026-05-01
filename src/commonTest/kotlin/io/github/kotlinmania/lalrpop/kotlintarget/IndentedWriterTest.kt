package io.github.kotlinmania.lalrpop.kotlintarget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IndentedWriterTest {

    @Test
    fun lineWritesAtCurrentDepth() {
        val out = IndentedWriter()
        out.line("a")
        out.indented { line("b") }
        out.line("c")

        assertEquals("a\n    b\nc\n", out.toString())
    }

    @Test
    fun emptyLineHasNoIndent() {
        val out = IndentedWriter()
        out.indented {
            line("x")
            line()
            line("y")
        }

        assertEquals("    x\n\n    y\n", out.toString())
    }

    @Test
    fun blockEmitsHeaderIndentedBodyAndFooter() {
        val out = IndentedWriter()
        out.block("class Foo {") {
            block("fun greet() {") {
                line("println(\"hi\")")
            }
        }

        val expected =
            "class Foo {\n" +
            "    fun greet() {\n" +
            "        println(\"hi\")\n" +
            "    }\n" +
            "}\n"
        assertEquals(expected, out.toString())
    }

    @Test
    fun customFooterIsRespected() {
        val out = IndentedWriter()
        out.block("val xs = listOf(", footer = ")") {
            line("1,")
            line("2,")
        }

        assertEquals("val xs = listOf(\n    1,\n    2,\n)\n", out.toString())
    }

    @Test
    fun depthRestoresAfterException() {
        val out = IndentedWriter()
        assertFailsWith<RuntimeException> {
            out.indented { throw RuntimeException("boom") }
        }
        out.line("recovered")

        assertEquals("recovered\n", out.toString())
    }

    @Test
    fun customIndentString() {
        val out = IndentedWriter(indent = "\t")
        out.indented { line("tabbed") }

        assertEquals("\ttabbed\n", out.toString())
    }
}
