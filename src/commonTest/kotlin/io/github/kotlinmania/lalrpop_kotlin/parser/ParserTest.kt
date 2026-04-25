// port-lint: source src/parser/test.rs
package io.github.kotlinmania.lalrpop_kotlin.parser

import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.GrammarItem
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.MatchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ParserTest {
    @Test
    fun matchBlock() {
        val blocks = listOf(
            """grammar; match { _ }""",                     // Minimal
            """grammar; match { _ } else { _ }""",          // Doesn't really make sense, but should be allowed
            """grammar; match { "abc" }""",                 // Single token
            """grammar; match { "abc" => "QUOTED" }""",     // Single token with quoted alias
            """grammar; match { "abc" => UNQUOTED }""",     // Single token with unquoted alias
            """grammar; match { r"(?i)begin" => BEGIN }""", // Regex
            """grammar; match { "abc", "def" => "DEF", _ } else { "foo" => BAR, r"(?i)begin" => BEGIN, _ }""", // Complex
            """grammar; match { "abc" } else { "def" } else { _ }""", // Multi-chain
        )

        for (block in blocks) {
            val parsed = parseGrammar(block).getOrElse {
                fail("Invalid grammar; grammar=$block")
            }
            val firstItem = parsed.items.first()
            if (firstItem !is GrammarItem.MatchToken) {
                fail("expected MatchToken, but was $firstItem")
            }
        }
    }

    @Test
    fun matchComplex() {
        val parsed = parseGrammar(
            """
        grammar;
        match {
            r"(?i)begin" => "BEGIN",
            r"(?i)end" => "END",
        } else {
            r"[a-zA-Z_][a-zA-Z0-9_]*" => IDENTIFIER,
        } else {
            "other",
            _
        }
""",
        ).getOrThrow()

        val firstItem = parsed.items.first()
        val data = (firstItem as? GrammarItem.MatchToken)
            ?: fail("expected MatchToken, but was: $firstItem")
        val match = data.inner

        // match { ... }
        val contents0 = match.contents.first()
        // r"(?i)begin" => "BEGIN"
        val item00 = contents0.items.first()
        (item00 as? MatchItem.Mapped)?.let { m ->
            assertEquals("r#\"(?i)begin\"#", m.symbol.toString())
            assertEquals("\"BEGIN\"", m.mapping.toString())
        } ?: fail("expected MatchItem.Mapped, but was: $item00")
        // r"(?i)end" => "END",
        val item01 = contents0.items[1]
        (item01 as? MatchItem.Mapped)?.let { m ->
            assertEquals("r#\"(?i)end\"#", m.symbol.toString())
            assertEquals("\"END\"", m.mapping.toString())
        } ?: fail("expected MatchItem.Mapped, but was: $item01")

        // else { ... }
        val contents1 = match.contents[1]
        // r"[a-zA-Z_][a-zA-Z0-9_]*" => IDENTIFIER,
        val item10 = contents1.items.first()
        (item10 as? MatchItem.Mapped)?.let { m ->
            assertEquals("r#\"[a-zA-Z_][a-zA-Z0-9_]*\"#", m.symbol.toString())
            assertEquals("IDENTIFIER", m.mapping.toString())
        } ?: fail("expected MatchItem.Mapped, but was: $item10")

        // else { ... }
        val contents2 = match.contents[2]
        // "other",
        val item20 = contents2.items.first()
        (item20 as? MatchItem.Unmapped)?.let { u ->
            assertEquals("\"other\"", u.symbol.toString())
        } ?: fail("expected MatchItem.Unmapped, but was: $item20")
        // _
        val item21 = contents2.items[1]
        if (item21 !is MatchItem.CatchAll) {
            fail("expected MatchItem.CatchAll, but was: $item21")
        }
    }

    @Test
    fun whereClauses() {
        val clauses = listOf(
            "where T: Debug",
            "where T: Debug + Display",
            "where T: std::ops::Add<usize>",
            "where T: IntoIterator<Item = usize>",
            "where T: 'a",
            "where 'a: 'b",
            "where for<'a> &'a T: Debug",
            "where T: for<'a> Flobbles<'a>",
            "where T: FnMut(usize)",
            "where T: FnMut(usize, bool)",
            "where T: FnMut() -> bool",
            "where T: for<'a> FnMut(&'a usize)",
            "where T: Debug, U: Display",
        )

        for (santa in clauses) {
            assertTrue(
                parseWhereClauses(santa).isSuccess,
                "should parse where clauses: $santa",
            )
        }
    }

    @Test
    fun grammarsWithWhereClauses() {
        val grammars = listOf(
            """
grammar<T> where T: StaticMethods;
""",
            """
grammar<T>(methods: &mut T) where T: MutMethods;
""",
            """
grammar<'input, T>(methods: &mut T) where T: 'input + Debug + MutMethods;
""",
            """
grammar<F>(methods: &mut F) where F: for<'a> FnMut(&'a usize) -> bool;
""",
            """
grammar<F>(logger: &mut F) where F: for<'a> FnMut(&'a str);
""",
        )

        for (g in grammars) {
            assertTrue(parseGrammar(g).isSuccess)
        }
    }

    @Test
    fun optionalSemicolon() {
        // Semi after block is optional
        val gOk = """
grammar;
pub Foo: () = { Bar }
Bar: () = "bar";
"""
        assertTrue(parseGrammar(gOk).isSuccess)

        // Semi after "expression" is mandatory
        val gErr = """
grammar;
pub Foo: () = { Bar };
Bar: () = "bar"
"""
        assertTrue(parseGrammar(gErr).isFailure)
    }
}
