// port-lint: source normalize/precedence/mod.rs
//! Precedence expander.
//!
//! Precedence expansion rewrites rules that contain precedence attribute into several rules
//! without attributes. A new rule is created for each level of precedence. Recursive occurrences
//! of the original rule are syntactically substituted for a level rule in each alternative, where
//! the choice of the precise rule is determined by the precedence level, the possible
//! associativity and the position of this occurrence.
//!
//! For concrete examples, see the [`test`](../tests/index.html) module.
package io.github.kotlinmania.lalrpop.normalize.precedence

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalData
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind
import io.github.kotlinmania.lalrpop.normalize.resolve.resolve

const val PREC_ATTR: String = "precedence"
const val LVL_ARG: String = "level"
const val ASSOC_ATTR: String = "assoc"
const val SIDE_ARG: String = "side"

/**
 * Associativity of an alternative.
 *
 * An alternative may have zero or more recursive occurrence of the current rule. Take for example
 * the common ternary conditional operator `x ? y : z`:
 *
 * ```lalrpop
 * #precedence(level="3")
 * <left: Expression> "?" <middle: Expression> : <right: Expression> => ..
 * ```
 * ## Left
 *
 * Left associativity means that the construction may be iterated on the left. In this case, `x ? y : z ? foo
 * : bar` is parsed as `(x ? y : z) ? foo : bar`. When such associativity is selected, the
 * expander replaces the first recursive occurrence of `Expression` by the current level, and all
 * others by the previous level:
 *
 * ## Right
 *
 * Right associativity means that the construction may be iterated on the right. When selected,
 * the expander replaces the last recursive occurrence of `Expression` by the current level, and
 * all others by the previous level.
 *
 * ## None
 *
 * Non-associativity means that it is not legal to iterate the rule. All recursive occurrences
 * are replaced with the rule corresponding to the previous level.
 *
 * ## Associative (all)
 *
 * All recursive occurrences are replaced with the current level. This is the default associativity.
 */
enum class Assoc {
    Left,
    Right,
    NonAssoc,
    FullyAssoc;

    companion object {
        val default: Assoc = FullyAssoc

        fun parse(s: String): Assoc? = when (s) {
            "left" -> Left
            "right" -> Right
            "none" -> NonAssoc
            "all" -> FullyAssoc
            else -> null
        }

        /**
         * Mirrors `implementation FromStr for Assoc { function fromStr(s: &str) -> Result<Assoc, ParseAssocError> }`.
         * Returns `Result.success(Assoc)` for a valid associativity
         * keyword and `Result.failure(ParseAssocError)` otherwise.
         */
        fun fromString(s: String): Result<Assoc> = when (s) {
            "left" -> Result.success(Left)
            "right" -> Result.success(Right)
            "none" -> Result.success(NonAssoc)
            "all" -> Result.success(FullyAssoc)
            else -> Result.failure(ParseAssocException(ParseAssocError()))
        }
    }
}

/**
 * Exception wrapper around [ParseAssocError] so it can be packaged as
 * a `Result.failure` payload.
 */
class ParseAssocException(val error: ParseAssocError) :
    RuntimeException(error.toString())

/** Substitution plan. */
sealed class Substitution {
    /**
     * Replace the first encountered occurrence by the first argument, and all the following by
     * the second. Used for associativity: typically, a left associativity on level `3` perform a
     * `OneThen(Rule3, Rule2)`.
     */
    data class OneThen(val first: SymbolKind, val then: SymbolKind) : Substitution()

    /** Standard substitution mode. Replace every encountered occurrence with the same given symbol. */
    data class Every(val kind: SymbolKind) : Substitution()
}

/** Direction for substitution. */
enum class Direction {
    Forward,
    Backward,
}

data class ParseAssocError(private val priv: Unit = Unit) {
    override fun toString(): String =
        "provided value was neither `left`, `right` nor `none`"
}

/**
 * Perform precedence expansion. Rewrite rules where at least one alternative has a precedence
 * attribute, and generate derived rules for each level of precedence.
 */
fun expandPrecedence(input: Grammar): Grammar {
    val resolved = resolve(input)
    val result: MutableList<GrammarItem> = ArrayList(resolved.items.size)

    for (item in resolved.items) {
        when {
            item is GrammarItem.Nonterminal && hasPrecAttr(item.data) ->
                result.addAll(expandNonterm(item.data))
            else -> result.add(item)
        }
    }

    return resolved.copy(items = result)
}

/** Determine if a rule has at least one precedence attribute. */
fun hasPrecAttr(nonTerm: NonterminalData): Boolean {
    // After prevalidation, either at least the first alternative of a nonterminal have a
    // precedence attributes, or none have, so we just have to check the first one.
    return nonTerm
        .alternatives
        .firstOrNull()
        ?.let { alt ->
            alt.attributes.any { attr ->
                attr.id == Atom.from(PREC_ATTR) || attr.id == Atom.from(ASSOC_ATTR)
            }
        }
        ?: false
}

/**
 * Expand a rule with precedence attributes. As it implies to generate new rules, return a vector
 * of grammar items.
 */
private fun expandNonterm(nonterm: NonterminalData): List<GrammarItem> {
    val lvls: MutableList<UInt> = mutableListOf()
    val altsWithAttr: MutableList<Triple<UInt, Assoc, Alternative>> = ArrayList(nonterm.alternatives.size)

    val draining = nonterm.alternatives.toMutableList()
    nonterm.alternatives.clear()

    // Thanks to prevalidation, the first alternative must have a precedence attribute that
    // will set lastLvl to an initial value
    var lastLvl: UInt = 0u
    var lastAssoc: Assoc = Assoc.default
    for (altOrig in draining) {
        val alt = altOrig
        // prevalidation. Prevalidation ensures, beside that the first alternative is annotated with
        // a precedence level, that each precedence attribute has an argument which
        // is parsable as an integer, and that each optional assoc attribute which a parsable
        // `Assoc`.

        // Extract precedence and associativity attributes

        // If there is a new precedence association, the associativity is reset to the default
        // one (that is, `FullyAssoc`), instead of using the last one encountered.
        val precIndex = alt.attributes.indexOfFirst { attr -> attr.id == Atom.from(PREC_ATTR) }
        val (lvl, assocAfterPrec) = if (precIndex >= 0) {
            val attr = alt.attributes.removeAt(precIndex)
            // SAFETY: see comment above
            val (_, value) = attr.getArgEqual()!!
            Pair(value.toUInt(), Assoc.default)
        } else {
            Pair(lastLvl, lastAssoc)
        }

        val assocIndex = alt.attributes.indexOfFirst { attr -> attr.id == Atom.from(ASSOC_ATTR) }
        val assoc = if (assocIndex >= 0) {
            val attr = alt.attributes.removeAt(assocIndex)
            // SAFETY: see comment above
            val (_, value) = attr.getArgEqual()!!
            Assoc.parse(value)!!
        } else {
            assocAfterPrec
        }

        altsWithAttr.add(Triple(lvl, assoc, alt))
        lvls.add(lvl)
        lastLvl = lvl
        lastAssoc = assoc
    }

    val sortedLvls: List<UInt> = lvls.sorted().distinct()

    val rest: MutableList<Triple<UInt, Assoc, Alternative>> = altsWithAttr

    val lvlMax: UInt = sortedLvls.last()
    // Iterate on pairs (lvls[i], lvls[i+1])
    val lvlPrecOpts: List<UInt?> = listOf<UInt?>(null) + sortedLvls.dropLast(1).map<UInt, UInt?> { it }
    val result: List<GrammarItem> = lvlPrecOpts.zip(sortedLvls).map { (lvlPrecOpt, lvl) ->
        // The generated non terminal corresponding to the last level keeps the same name as the
        // initial item, so that all external references to it are still valid. Other levels get
        // the names `Name1`, `Name2`, etc. where `Name` is the name of the initial item.
        val name = NonterminalString(
            Atom.from(
                if (lvl == lvlMax) {
                    "${nonterm.name}"
                } else {
                    "${nonterm.name}$lvl"
                },
            ),
        )

        val nontermPrev: SymbolKind? = lvlPrecOpt?.let { lvlPrec ->
            SymbolKind.Nonterminal(NonterminalString(Atom.from("${nonterm.name}$lvlPrec")))
        }

        val altsWithPrec = rest.filter { (l, _, _) -> l == lvl }
        val newRest = rest.filter { (l, _, _) -> l != lvl }
        rest.clear()
        rest.addAll(newRest)

        val altsWithAssoc: MutableList<Pair<Assoc, Alternative>> = altsWithPrec
            .map { (_, a, alt) -> Pair(a, alt) }
            .toMutableList()

        val symbolKind: SymbolKind = SymbolKind.Nonterminal(name)
        for ((currAssoc, alt) in altsWithAssoc) {
            val errMsg = "unexpected associativity attribute on the first precedence level"
            val (subst, dir) = when (currAssoc) {
                Assoc.Left -> Pair(
                    Substitution.OneThen(symbolKind, nontermPrev ?: error(errMsg)),
                    Direction.Forward,
                )
                Assoc.Right -> Pair(
                    Substitution.OneThen(symbolKind, nontermPrev ?: error(errMsg)),
                    Direction.Backward,
                )
                Assoc.NonAssoc -> Pair(
                    Substitution.Every(nontermPrev ?: error(errMsg)),
                    Direction.Forward,
                )
                Assoc.FullyAssoc -> Pair(Substitution.Every(symbolKind), Direction.Forward)
            }
            replaceNonterm(alt, nonterm.name, subst, dir)
        }

        val alternatives: MutableList<Alternative> = altsWithAssoc
            .map { (_, alt) -> alt }
            .toMutableList()

        // Include the previous level
        if (nontermPrev != null) {
            alternatives.add(
                Alternative(
                    // Don't really know what span should we put here
                    span = nonterm.span,
                    expr = ExprSymbol(
                        symbols = mutableListOf(
                            Symbol(
                                kind = nontermPrev,
                                span = nonterm.span,
                            ),
                        ),
                    ),
                    condition = null,
                    action = null,
                    attributes = mutableListOf(),
                ),
            )
        }

        GrammarItem.Nonterminal(
            NonterminalData(
                visibility = nonterm.visibility,
                name = name,
                attributes = nonterm.attributes.toMutableList(),
                span = nonterm.span,
                args = nonterm.args.toMutableList(), // macro arguments
                typeDecl = nonterm.typeDecl,
                alternatives = alternatives,
            ),
        )
    }

    check(rest.isEmpty())
    return result
}

/** Perform substitution of on an non-terminal in an alternative. */
private fun replaceNonterm(
    alt: Alternative,
    target: NonterminalString,
    subst: Substitution,
    dir: Direction,
) {
    replaceSymbols(alt.expr.symbols, target, subst, dir)
}

/** Perform substitution of on an non-terminal in an array of symbols. */
private fun replaceSymbols(
    symbols: MutableList<Symbol>,
    target: NonterminalString,
    subst: Substitution,
    dir: Direction,
): Substitution =
    when (dir) {
        Direction.Forward -> symbols.fold(subst) { s, symbol ->
            replaceSymbol(symbol, target, s, dir)
        }
        Direction.Backward -> symbols.asReversed().fold(subst) { s, symbol ->
            replaceSymbol(symbol, target, s, dir)
        }
    }

/** Perform substitution of a non-terminal in a symbol. */
private fun replaceSymbol(
    symbol: Symbol,
    target: NonterminalString,
    subst: Substitution,
    dir: Direction,
): Substitution {
    return when (val kind = symbol.kind) {
        is SymbolKind.AmbiguousId -> {
            error("ambiguous id `${kind.atom}` encountered after name resolution")
        }
        is SymbolKind.Nonterminal -> if (kind.nt == target) {
            when (subst) {
                is Substitution.Every -> {
                    symbol.kind = subst.kind
                    subst
                }
                is Substitution.OneThen -> {
                    symbol.kind = subst.first
                    Substitution.Every(subst.then)
                }
            }
        } else {
            subst
        }
        is SymbolKind.Macro -> {
            val m = kind.sym
            if (dir == Direction.Forward) {
                m.args.fold(subst) { s, sym -> replaceSymbol(sym, target, s, dir) }
            } else {
                m.args.asReversed().fold(subst) { s, sym -> replaceSymbol(sym, target, s, dir) }
            }
        }
        is SymbolKind.Expr -> replaceSymbols(kind.expr.symbols, target, subst, dir)
        is SymbolKind.Repeat -> replaceSymbol(kind.sym.symbol, target, subst, dir)
        is SymbolKind.Choose -> replaceSymbol(kind.sym, target, subst, dir)
        is SymbolKind.Name -> replaceSymbol(kind.sym, target, subst, dir)
        is SymbolKind.TupleKind -> replaceSymbol(kind.sym, target, subst, dir)
        is SymbolKind.Terminal, SymbolKind.Error, SymbolKind.Lookahead, SymbolKind.Lookbehind -> subst
    }
}
