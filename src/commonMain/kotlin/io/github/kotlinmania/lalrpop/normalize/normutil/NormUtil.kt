// port-lint: source normalize/norm_util.rs
package io.github.kotlinmania.lalrpop.normalize.normutil

import io.github.kotlinmania.lalrpop.grammar.parsetree.ActionKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.ArgPattern
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind

internal sealed class AlternativeAction {
    data class User(val code: ActionKind) : AlternativeAction()
    data class Default(val symbols: Symbols) : AlternativeAction()
}

internal sealed class Symbols {
    data class Named(val list: List<Triple<Int, ArgPattern, Symbol>>) : Symbols()
    data class Anon(val list: List<Pair<Int, Symbol>>) : Symbols()
}

internal fun analyzeAction(alt: Alternative): AlternativeAction {
    // We cannot infer types for alternatives with actions
    val code = alt.action
    if (code != null) {
        return AlternativeAction.User(code)
    }

    return AlternativeAction.Default(analyzeExpr(alt.expr))
}

internal fun analyzeExpr(expr: ExprSymbol): Symbols {
    // First look for named symbols.
    val namedSymbols: List<Triple<Int, ArgPattern, Symbol>> = expr
        .symbols
        .withIndex()
        .mapNotNull { (idx, sym) ->
            when (val kind = sym.kind) {
                is SymbolKind.Name -> Triple(idx, ArgPattern.NamePat(kind.name), kind.sym)
                is SymbolKind.TupleKind -> Triple(idx, ArgPattern.TuplePat(kind.tuple), kind.sym)
                else -> null
            }
        }
        .toList()
    if (namedSymbols.isNotEmpty()) {
        return Symbols.Named(namedSymbols)
    }

    // Otherwise, make a tuple of the items they chose with `<>`.
    val chosenSymbolTypes: List<Pair<Int, Symbol>> = expr
        .symbols
        .withIndex()
        .mapNotNull { (idx, sym) ->
            when (val kind = sym.kind) {
                is SymbolKind.Choose -> Pair(idx, kind.sym)
                else -> null
            }
        }
        .toList()
    if (chosenSymbolTypes.isNotEmpty()) {
        return Symbols.Anon(chosenSymbolTypes)
    }

    // If they did not choose anything with `<>`, make a tuple of everything.
    return Symbols.Anon(expr.symbols.withIndex().map { (idx, sym) -> Pair(idx, sym) }.toList())
}

internal enum class Presence {
    None,
    InCurlyBrackets,
    Normal;

    fun isInCurlyBrackets(): Boolean = this == InCurlyBrackets
}

internal fun checkBetweenBraces(action: String): Presence {
    val funkyIndex = action.indexOf("<>")
    if (funkyIndex >= 0) {
        val (before, after) = run {
            val rawBefore = action.substring(0, funkyIndex)
            val rawAfter = action.substring(funkyIndex + 2)
            Pair(rawBefore.trim(), rawAfter.trim())
        }

        // If we have an odd number of quotes on both sides, we are inside a string, and therefore,
        // this is a format arg, not a struct.  That considered "Normal" here, because this is
        // detecting if our expansion is for a struct definition.
        val beforeQuotes = before.count { c -> c == '"' }
        val afterQuotes = after.count { c -> c == '"' }

        if (beforeQuotes % 2 == 1 && afterQuotes % 2 == 1) {
            return Presence.Normal
        }

        val lastBefore = before.lastOrNull()
        val nextAfter = after.firstOrNull()
        return if (lastBefore == '{' && nextAfter == '}') {
            Presence.InCurlyBrackets
        } else {
            Presence.Normal
        }
    } else {
        return Presence.None
    }
}
