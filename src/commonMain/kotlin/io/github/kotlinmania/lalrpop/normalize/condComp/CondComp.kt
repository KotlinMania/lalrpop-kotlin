// port-lint: source normalize/condComp/mod.rs
//! Compute cfg directives.
package io.github.kotlinmania.lalrpop.normalize.condComp

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parseTree.Attribute
import io.github.kotlinmania.lalrpop.grammar.parseTree.AttributeArg
import io.github.kotlinmania.lalrpop.grammar.consts.CFG
import io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parseTree.GrammarItem

fun removeDisabledDecls(session: Session, grammar: Grammar): Grammar {
    grammar.items.retainAll { item ->
        when (item) {
            is GrammarItem.ExternToken -> {
                val enumToken = item.inner.enumToken
                if (enumToken != null) {
                    enumToken.conversions.retainAll { c -> cfgActive(session, c.attributes) }
                }
                true
            }

            is GrammarItem.Nonterminal -> {
                val active = cfgActive(session, item.data.attributes)
                if (active) {
                    item.data.alternatives.retainAll { prod -> cfgActive(session, prod.attributes) }
                }
                active
            }

            else -> true
        }
    }
    return grammar
}

fun cfgActive(session: Session, attrs: List<Attribute>): Boolean {
    fun testFeatAttr(attr: Attribute): Boolean {
        val arg = attr.arg
        return when {
            arg is AttributeArg.Paren && attr.id == Atom.from("not") ->
                arg.attrs.firstOrNull()?.let { a -> !testFeatAttr(a) } ?: false

            arg is AttributeArg.Paren && attr.id == Atom.from("all") ->
                arg.attrs.all { a -> testFeatAttr(a) }

            arg is AttributeArg.Paren && attr.id == Atom.from("any") ->
                arg.attrs.any { a -> testFeatAttr(a) }

            arg is AttributeArg.Equal && attr.id == Atom.from("feature") ->
                session.features?.contains(arg.value) ?: false

            else -> false
        }
    }

    val cfgAtom = Atom.from(CFG)
    return attrs
        .filter { attr -> attr.id == cfgAtom }
        .all { attr ->
            when (val arg = attr.arg) {
                is AttributeArg.Paren -> arg.attrs.firstOrNull()?.let { a -> testFeatAttr(a) } ?: false
                else -> false
            }
        }
}
