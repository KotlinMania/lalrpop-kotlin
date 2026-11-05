package io.github.kotlinmania.lalrpop.lr1.lanetable.table.contextset

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.ConflictIndex
import io.github.kotlinmania.lalrpop.normalizedGrammar
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun lookahead(literal: String): TokenSet =
    TokenSet.from(Token.Terminal(TerminalString.quoted(Atom.from(literal))))

class ContextSetBehaviorTest {
    @Test
    fun disjointInsertAndClone() {
        val grammar =
            normalizedGrammar(
                """
                grammar;
                pub S = { "a", "b", "c" };
                """.trimIndent(),
            )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val set = ContextSet.new(2)
            assertTrue(set.insert(ConflictIndex.new(0), lookahead("a")))
            assertFalse(set.insert(ConflictIndex.new(0), lookahead("a")))
            assertTrue(set.insert(ConflictIndex.new(1), lookahead("b")))

            val cloned = set.clone()
            assertTrue(cloned.insert(ConflictIndex.new(0), lookahead("c")))
            assertTrue(set.insert(ConflictIndex.new(1), lookahead("c")))
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun overlappingInsertAndUnionRejectConflict() {
        val grammar =
            normalizedGrammar(
                """
                grammar;
                pub S = { "x", "y" };
                """.trimIndent(),
            )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val left = ContextSet.new(2)
            assertTrue(left.insert(ConflictIndex.new(0), lookahead("x")))
            assertFailsWith<OverlappingLookahead> {
                left.insert(ConflictIndex.new(1), lookahead("x"))
            }

            val rightDisjoint = ContextSet.new(2)
            assertTrue(rightDisjoint.insert(ConflictIndex.new(1), lookahead("y")))
            val merged = ContextSet.union(left, rightDisjoint)
            assertFalse(merged.insert(ConflictIndex.new(0), lookahead("x")))
            assertFalse(merged.insert(ConflictIndex.new(1), lookahead("y")))

            val rightOverlapping = ContextSet.new(2)
            assertTrue(rightOverlapping.insert(ConflictIndex.new(1), lookahead("x")))
            assertFailsWith<OverlappingLookahead> {
                ContextSet.union(left, rightOverlapping)
            }
        } finally {
            lr1Tls.drop()
        }
    }
}
