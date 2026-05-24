package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.kotlintarget.IndentedWriter

/**
 * Walks every terminal and nonterminal in a [Grammar], deduplicates by their semantic
 * [GrammarTypeKind], and emits a per-grammar `Symbol` sealed class via [IndentedWriter].
 * Each unique stack-element kind becomes one `data class VariantN(val value: T) : Symbol()`.
 *
 * The pipeline:
 *   1. The grammar's [Grammar.types] gives the parsed input-format type for each role.
 *   2. [GrammarTypeKindInterpreter] interprets that into a [GrammarTypeKind] — the
 *      semantic intent of the role (Optional, ZeroOrMore, Tuple, …).
 *   3. The Kotlin code book ([KotlinTypeBook]) renders each kind as the idiomatic
 *      Kotlin type expression and may request wrapper sealed classes when Kotlin's
 *      type system can't represent a recipe natively.
 *
 * Deduplication is keyed on [GrammarTypeKind] — the *intent* — not the rendered Kotlin
 * type. That way two distinct grammar roles whose Kotlin renderings happen to coincide
 * (e.g. an `Optional<T>` and an `Optional<Optional<T>>` both rendering as `T?` in
 * less-typed pipelines) still get distinct variants. The variant wrapper is the
 * discriminant the parse table relies on; collapsing distinct intents would lose it.
 *
 * Variant ordering follows the same convention as `lr1/codegen/ParseTable.kt`:
 * terminals first in declaration order, then nonterminals in declaration order. The
 * parse-table generator ([tablesFromLr1States]) refers to variants positionally, so
 * any change here has to land in lockstep there.
 */
class KotlinSymbolEmit(
    private val grammar: Grammar,
    private val symbolClassName: String,
    private val typeBook: KotlinTypeBook = KotlinTypeBook(),
) {

    /**
     * Map from grammar symbol (terminal or nonterminal) to the variant class name it
     * pushes onto the parse stack. Populated as a side-effect of [emitInto] — the
     * subsequent codegen pass that emits the productions array uses this to produce
     * `Symbol.VariantN(value)` literals at each push site.
     */
    val variantNameByTerminal: MutableMap<TerminalString, String> = mutableMapOf()
    val variantNameByNonterminal: MutableMap<NonterminalString, String> = mutableMapOf()

    /** The unique grammar-type kinds in their assigned `VariantN` order, after [emitInto] runs. */
    private val variantOrder: MutableList<Pair<String, GrammarTypeKind>> = mutableListOf()
    private val variantByKind: MutableMap<GrammarTypeKind, String> = mutableMapOf()

    /** Emit the sealed class declaration plus any required wrapper classes. */
    fun emitInto(out: IndentedWriter) {
        // Pass 1: walk every terminal and nonterminal, assigning a variant name to
        // each unique grammar type. Each grammar type is interpreted once into its
        // semantic [GrammarTypeKind], then the Kotlin code book renders it. The
        // rendering is what populates [typeBook.wrappersNeeded], so it has to run
        // before pass 2 emits wrappers — otherwise the wrapper loop sees an empty
        // set and we silently skip needed declarations.
        val renderedByVariant: MutableMap<String, RenderedKotlinType> = mutableMapOf()
        for (terminal in grammar.terminals.all) {
            val parsedType = grammar.types.terminalType(terminal)
            val kind = GrammarTypeKindInterpreter.interpret(parsedType)
            val name = ensureVariant(kind)
            variantNameByTerminal[terminal] = name
            renderedByVariant.getOrPut(name) { typeBook.render(kind) }
        }
        for (nonterminal in grammar.nonterminals.keys) {
            val parsedType = grammar.types.nonterminalType(nonterminal)
            val kind = GrammarTypeKindInterpreter.interpret(parsedType)
            val name = ensureVariant(kind)
            variantNameByNonterminal[nonterminal] = name
            renderedByVariant.getOrPut(name) { typeBook.render(kind) }
        }

        // Pass 2: emit any helper classes the code book requested. By now every
        // variant's Kotlin type has been rendered, so the helper requests are final.
        for (wrapper in typeBook.wrappersNeeded) {
            emitWrapper(out, wrapper)
            out.line()
        }
        for (arity in typeBook.tupleAritiesNeeded.sorted()) {
            emitTupleDataClass(out, arity)
            out.line()
        }

        // Pass 3: emit the Symbol sealed class itself, one variant per unique kind.
        out.block("sealed class $symbolClassName {") {
            for ((variantName, _) in variantOrder) {
                val rendered = renderedByVariant.getValue(variantName)
                line("data class $variantName(val value: ${rendered.expression}) : $symbolClassName()")
            }
        }
    }

    private fun emitTupleDataClass(out: IndentedWriter, arity: Int) {
        val typeParams = (0 until arity).joinToString(", ") { "T$it" }
        out.block("data class GrammarTuple$arity<$typeParams>(", footer = ")") {
            for (index in 0 until arity) {
                val comma = if (index == arity - 1) "" else ","
                line("val item$index: T$index$comma")
            }
        }
    }

    private fun emitWrapper(out: IndentedWriter, wrapper: KotlinWrapper) {
        when (wrapper) {
            KotlinWrapper.NULLABLE_OPTION -> emitNullableOption(out)
            KotlinWrapper.PARSE_RESULT -> emitParseEither(out)
        }
    }

    private fun emitNullableOption(out: IndentedWriter) {
        out.line("/**")
        out.line(" * Three-state value distinguishing outer-absent, inner-empty, and present cases.")
        out.line(" *")
        out.line(" * Necessary because nested nullable values would otherwise collapse to a single")
        out.line(" * nullable type and lose which layer matched. Some grammar productions push")
        out.line(" * values that depend on this distinction at the symbol-stack level.")
        out.line(" */")
        out.block("sealed class NullableOption<out T> {") {
            line("object Absent : NullableOption<Nothing>()")
            line("object Empty : NullableOption<Nothing>()")
            line("data class Present<T>(val value: T) : NullableOption<T>()")
            line()
            line("/** Equivalent of unwrap-or-null on the outer optional: collapses Absent and Empty to null. */")
            block("fun unwrapOrNull(): T? = when (this) {") {
                line("Absent -> null")
                line("Empty -> null")
                line("is Present -> value")
            }
        }
    }

    private fun emitParseEither(out: IndentedWriter) {
        out.line("/**")
        out.line(" * Either-style result with a typed error. Kotlin's stdlib `Result` only accepts")
        out.line(" * `Throwable` errors; some grammars declare a custom `Error` type that doesn't")
        out.line(" * extend `Throwable`, so this wrapper carries the typed Err alongside Ok.")
        out.line(" */")
        out.block("sealed class ParseEither<out T, out E> {") {
            line("data class Ok<T, E>(val value: T) : ParseEither<T, E>()")
            line("data class Err<T, E>(val error: E) : ParseEither<T, E>()")
        }
    }

    /**
     * If this [GrammarTypeKind] has not been seen before, assign it the next
     * sequential `VariantN` name, record it in the per-grammar maps, and return that
     * name. Otherwise return the previously assigned name.
     */
    private fun ensureVariant(kind: GrammarTypeKind): String {
        variantByKind[kind]?.let { return it }
        val name = "Variant${variantOrder.size}"
        variantOrder.add(name to kind)
        variantByKind[kind] = name
        return name
    }

}
