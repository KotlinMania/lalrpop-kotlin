// port-lint: source src/normalize/inline/mod.rs
//! Inlining of nonterminals
package io.github.kotlinmania.lalrpop.normalize.inline

import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFn
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefnKind
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.InlineActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.InlinedSymbol
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.normalize.inline.graph.inlineOrder

fun inline(grammar: Grammar): Grammar {
    val order = inlineOrder(grammar)
    for (nt in order) {
        inlineNt(grammar, nt)
    }
    return grammar
}

private fun inlineNt(grammar: Grammar, inlineNt: NonterminalString) {
    val inlineProductions = grammar.productionsFor(inlineNt).toList()
    for (data in grammar.nonterminals.values) {
        val newProductions: MutableList<Production> = mutableListOf()
        val newActionFnDefns: MutableList<ActionFnDefn> = mutableListOf()

        for (intoProduction in data.productions) {
            if (!intoProduction.symbols.contains(Symbol.Nonterminal(inlineNt))) {
                newProductions.add(intoProduction.copy())
                continue
            }

            val inliner = Inliner(
                actionFnDefns = grammar.actionFnDefns,
                inlineNonterminal = inlineNt,
                intoProduction = intoProduction,
                inlineFallible = 0u,
                inlineProductions = inlineProductions,
                newSymbols = mutableListOf(),
                newProductions = newProductions,
                newActionFnDefns = newActionFnDefns,
            )

            inliner.inline(intoProduction.symbols)
        }

        data.productions = newProductions
        grammar.actionFnDefns.addAll(newActionFnDefns)
    }
}

private class Inliner(
    /** Action function defns */
    val actionFnDefns: List<ActionFnDefn>,

    /** The nonterminal `A` being inlined */
    val inlineNonterminal: NonterminalString,

    /**
     * The full set of productions `A = B C D | E F G` for the
     * nonterminal `A` being inlined
     */
    val inlineProductions: List<Production>,

    /**
     * Number of actions that we have inlined for `A` so far which
     * have been fallible. IOW, if we are inlining `A` into `X = Y A
     * A Z`, and in the first instance of `A` we used a fallible
     * action, but the second we used an infallible one, count would
     * be 1.
     */
    var inlineFallible: UInt,

    /** The `X = Y A Z` being inlined into */
    val intoProduction: Production,

    /**
     * The list of symbols we building up for the new production.
     * For example, this would (eventually) contain `Y B C D Z`,
     * given our running example.
     */
    val newSymbols: MutableList<InlinedSymbol>,

    /** The output vector of all productions for `X` that we have created */
    val newProductions: MutableList<Production>,

    /** Vector of all action function defns from the grammar. */
    val newActionFnDefns: MutableList<ActionFnDefn>,
)

private fun Inliner.inline(intoSymbols: List<Symbol>) {
    if (intoSymbols.isEmpty()) {
        // create an action function for the result of inlining
        val intoAction = this.intoProduction.action
        val intoFallible = this.actionFnDefns[intoAction.index()].fallible
        val intoRetType = this.actionFnDefns[intoAction.index()].retType
        val inlineFallible = this.inlineFallible != 0u
        val index = this.actionFnDefns.size + this.newActionFnDefns.size
        val actionFn = ActionFn.new(index)
        val inlineDefn = InlineActionFnDefn(
            action = intoAction,
            symbols = this.newSymbols.toMutableList(),
        )
        this.newActionFnDefns.add(
            ActionFnDefn(
                fallible = intoFallible || inlineFallible,
                retType = intoRetType,
                kind = ActionFnDefnKind.Inline(inlineDefn),
            ),
        )
        val prodSymbols: MutableList<Symbol> = this
            .newSymbols
            .flatMap { sym ->
                when (sym) {
                    is InlinedSymbol.Original -> listOf(sym.sym)
                    is InlinedSymbol.Inlined -> sym.syms.toList()
                }
            }
            .toMutableList()
        this.newProductions.add(
            Production(
                nonterminal = this.intoProduction.nonterminal,
                span = this.intoProduction.span,
                symbols = prodSymbols,
                action = actionFn,
            ),
        )
    } else {
        val nextSymbol = intoSymbols[0]
        when {
            nextSymbol is Symbol.Nonterminal && nextSymbol.nt == this.inlineNonterminal -> {
                // Replace the current symbol with each of the
                // `inlineProductions` in turn.
                for (inlineProduction in this.inlineProductions) {
                    // If this production is fallible, increment
                    // count of fallible actions.
                    val inlineAction = inlineProduction.action
                    val fallible = this.actionFnDefns[inlineAction.index()].fallible
                    this.inlineFallible += (if (fallible) 1u else 0u)

                    // Push the symbols of the production inline.
                    this.newSymbols.add(
                        InlinedSymbol.Inlined(
                            inlineProduction.action,
                            inlineProduction.symbols.toMutableList(),
                        ),
                    )

                    // Inline remaining symbols:
                    this.inline(intoSymbols.subList(1, intoSymbols.size))

                    // Reset state after we have inlined remaining symbols:
                    this.newSymbols.removeAt(this.newSymbols.size - 1)
                    this.inlineFallible -= (if (fallible) 1u else 0u)
                }
            }
            else -> {
                this.newSymbols.add(InlinedSymbol.Original(nextSymbol))
                this.inline(intoSymbols.subList(1, intoSymbols.size))
                this.newSymbols.removeAt(this.newSymbols.size - 1)
            }
        }
    }
}
