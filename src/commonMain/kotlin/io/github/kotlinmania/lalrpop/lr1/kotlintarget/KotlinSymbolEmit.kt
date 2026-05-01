package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.kotlintarget.IndentedWriter

/**
 * Walks every terminal and nonterminal in a [Grammar], deduplicates by `TypeRepr`, and
 * emits a per-grammar `Symbol` sealed class via [IndentedWriter]. Each unique stack
 * element type becomes one `data class VariantN(val value: T) : Symbol()`.
 *
 * The deduplication key is the original Rust [TypeRepr], not the mapped Kotlin type.
 * Two Rust types that collapse to the same Kotlin type (e.g. `Option<T>` and
 * `Option<Option<T>>` both flattening to `T?`) still get distinct variants —
 * preserving the variant *wrapper* as the discriminant the parse table relies on.
 * Where the Kotlin type can't represent the full Rust shape, [KotlinTypeMapper]
 * requests a wrapper sealed class and this emitter writes it alongside.
 *
 * Variant ordering follows the upstream Rust LALRPOP convention also used by
 * `lr1/codegen/ParseTable.kt`: terminals first in declaration order, then nonterminals
 * in declaration order, with each new unique type assigned the next sequential
 * `VariantN` name. That ordering matters because the parse table generator (see
 * `tablesFromLr1States`) refers to variants positionally.
 */
class KotlinSymbolEmit(
    private val grammar: Grammar,
    private val symbolClassName: String,
    private val typeMapper: KotlinTypeMapper = KotlinTypeMapper(),
) {

    /**
     * Map from grammar symbol (terminal or nonterminal) to the variant class name it
     * pushes onto the parse stack. Populated as a side-effect of [emitInto] — the
     * subsequent codegen pass that emits the productions array uses this to produce
     * `Symbol.VariantN(value)` literals at each push site.
     */
    val variantNameByTerminal: MutableMap<TerminalString, String> = mutableMapOf()
    val variantNameByNonterminal: MutableMap<NonterminalString, String> = mutableMapOf()

    /** The unique types in their assigned `VariantN` order, after [emitInto] runs. */
    private val variantOrder: MutableList<Pair<TypeRepr, String>> = mutableListOf()
    private val variantByType: MutableMap<TypeRepr, String> = mutableMapOf()

    /** Emit the sealed class declaration plus any required wrapper classes. */
    fun emitInto(out: IndentedWriter) {
        // Pass 1: walk every terminal and nonterminal, assigning a variant name to
        // each unique TypeRepr and pre-mapping each one to its Kotlin type. The
        // pre-mapping is what populates [typeMapper.wrappersNeeded], so it has to
        // run before we emit the wrapper declarations — otherwise the wrapper-loop
        // sees an empty set and we silently skip needed wrappers.
        val mappedByVariant: MutableMap<String, MappedType> = mutableMapOf()
        for (terminal in grammar.terminals.all) {
            val rustType = grammar.types.terminalType(terminal)
            val name = ensureVariant(rustType)
            variantNameByTerminal[terminal] = name
            mappedByVariant.getOrPut(name) { typeMapper.map(rustType) }
        }
        for (nonterminal in grammar.nonterminals.keys) {
            val rustType = grammar.types.nonterminalType(nonterminal)
            val name = ensureVariant(rustType)
            variantNameByNonterminal[nonterminal] = name
            mappedByVariant.getOrPut(name) { typeMapper.map(rustType) }
        }

        // Pass 2: emit any wrapper sealed classes the type mapper requested. By now
        // every variant's Kotlin type has been resolved, so [wrappersNeeded] is final.
        for (wrapper in typeMapper.wrappersNeeded) {
            emitWrapper(out, wrapper)
            out.line()
        }

        // Pass 3: emit the Symbol sealed class itself, one variant per unique type.
        out.block("sealed class $symbolClassName {") {
            for ((rustType, variantName) in variantOrder) {
                val mapped = mappedByVariant.getValue(variantName)
                line("/** Rust source type: `$rustType` */")
                line("data class $variantName(val value: ${mapped.kotlinType}) : $symbolClassName()")
            }
        }
    }

    /**
     * If [rustType] has not been seen before, assign it the next sequential `VariantN`
     * name, record it in the per-grammar maps, and return that name. Otherwise return
     * the previously assigned name.
     */
    private fun ensureVariant(rustType: TypeRepr): String {
        variantByType[rustType]?.let { return it }
        val name = "Variant${variantOrder.size}"
        variantOrder.add(rustType to name)
        variantByType[rustType] = name
        return name
    }

    private fun emitWrapper(out: IndentedWriter, wrapper: KotlinWrapper) {
        when (wrapper) {
            KotlinWrapper.NULLABLE_OPTION -> emitNullableOption(out)
            KotlinWrapper.PARSE_RESULT -> emitParseEither(out)
        }
    }

    private fun emitNullableOption(out: IndentedWriter) {
        out.line("/**")
        out.line(" * Three-state value distinguishing the cases of a nested Rust `Option<Option<T>>`.")
        out.line(" *")
        out.line(" * Necessary because Kotlin's nullable types collapse: `T??` is the same as `T?`,")
        out.line(" * which would lose the distinction between `None`, `Some(None)`, and")
        out.line(" * `Some(Some(value))`. Some grammar productions push values that depend on this")
        out.line(" * three-state distinction at the symbol-stack level, so the wrapper is required.")
        out.line(" */")
        out.block("sealed class NullableOption<out T> {") {
            line("object Absent : NullableOption<Nothing>()")
            line("object Empty : NullableOption<Nothing>()")
            line("data class Present<T>(val value: T) : NullableOption<T>()")
            line()
            line("/** Mirror of Rust's `option.unwrap_or(None)`: collapses Absent and Empty to null. */")
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
}
