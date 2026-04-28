// port-lint: source lr1/codegen/base.rs
//! Base helper routines for a code generator.
package io.github.kotlinmania.lalrpop.lr1.codegen

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.freeVariables.freeVariables
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.Types
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust
import io.github.kotlinmania.lalrpop.tls.Tls
import io.github.kotlinmania.btree.BTreeSet
import io.github.kotlinmania.lalrpop.lr1.Token

/**
 * Base struct for various kinds of code generator. The flavor of
 * code generator is customized by supplying distinct types for `C`
 * (e.g., `self::ascent::RecursiveAscent`).
 */
class CodeGenerator<C>(
    /** the complete grammar */
    val grammar: Grammar,

    /** some suitable prefix to separate our identifiers from the user */
    val prefix: String,

    /** types from the grammar */
    val types: Types,

    /** the start symbol S the user specified */
    val userStartSymbol: NonterminalString,

    /** the synthetic start symbol S' that we specified */
    val startSymbol: NonterminalString,

    /** the vector of states */
    val states: List<State<TokenSet>>,

    /** where we write output */
    val out: RustWrite,

    /** where to find the action routines (typically `super`) */
    val actionModule: String,

    /**
     * custom fields for the specific kind of codegenerator
     * (recursive ascent, table-driven, etc)
     */
    val custom: C,

    val repeatable: Boolean,
) {
    companion object {
        fun <C> new(
            grammar: Grammar,
            userStartSymbol: NonterminalString,
            startSymbol: NonterminalString,
            states: List<State<TokenSet>>,
            out: RustWrite,
            repeatable: Boolean,
            actionModule: String,
            custom: C,
        ): CodeGenerator<C> = CodeGenerator(
            grammar = grammar,
            prefix = grammar.prefix,
            types = grammar.types,
            states = states,
            userStartSymbol = userStartSymbol,
            startSymbol = startSymbol,
            out = out,
            custom = custom,
            repeatable = repeatable,
            actionModule = actionModule,
        )

        /**
         * We often create meta types that pull together a bunch of
         * user-given types -- basically describing (e.g.) the full set
         * of return values from any nonterminal (and, in some cases,
         * terminals). These types need to carry generic parameters from
         * the grammar, since the nonterminals may include generic
         * parameters -- but we do not want them to carry *all* the
         * generic parameters, since that can be unnecessarily
         * restrictive.
         *
         * In particular, consider something like this:
         *
         * ```notrust
         * grammar<'a>(buffer: &'a mut Vec<u32>);
         * ```
         *
         * Here, we likely do not want the `'a` in the type of `buffer` to appear
         * in the nonterminal result. That because, if it did, then the
         * action functions will have a signature like:
         *
         * ```ignore
         * function foo<'a, T>(x: &'a mut Vec<T>) -> Result<'a> { ... }
         * ```
         *
         * In that case, we would only be able to call one action function and
         * will in fact get borrowck errors, because Rust would think we
         * were potentially returning this `&'a mut Vec<T>`.
         *
         * Therefore, we take the full list of type parameters and we
         * filter them down to those that appear in the types that we
         * need to include (those that appear in the `tys` parameter).
         *
         * In some cases, we need to include a few more than just that
         * obviously appear textually: for example, if we have `T::Foo`,
         * and we see a where-clause `T: Bar<'a>`, then we need to
         * include both `T` and `'a`, since that bound may be important
         * for resolving `T::Foo` (in other words, `T::Foo` may expand to
         * `<T as Bar<'a>>::Foo`).
         */
        fun filterTypeParametersAndWhereClauses(
            grammar: Grammar,
            tys: Iterable<TypeRepr>,
        ): Pair<List<TypeParameter>, List<WhereClause>> {
            val referencedTyParams: BTreeSet<TypeParameter> = tys
                .asSequence()
                .flatMap { t -> t.freeVariables(grammar.typeParameters).asSequence() }
                .toCollection(set())

            val filteredTypeParams: List<TypeParameter> = grammar
                .typeParameters
                .filter { t -> referencedTyParams.contains(t) }
                .toList()

            // If `T` is referenced in the types we need to keep, then
            // include any bounds like `T: Foo`. This may be needed for
            // the well-formedness conditions on `T` (e.g., maybe we have
            // `T: Hash` and a `HashSet<T>` or something) but it may also
            // be needed because of `T::Foo`-like types.
            //
            // Do not however include a bound like `T: 'a` unless both `T`
            // **and** `'a` are referenced -- same with bounds like `T:
            // Foo<U>`. If those were needed, then `'a` or `U` would also
            // have to appear in the types.
            Tls.session().log.log(Level.Debug) {
                "filtered_type_params = $filteredTypeParams"
            }
            val filteredWhereClauses: List<WhereClause> = grammar
                .whereClauses
                .filter { wc ->
                    Tls.session().log.log(Level.Debug) {
                        "wc = $wc free_variables = ${wc.freeVariables(grammar.typeParameters)}"
                    }
                    wc.freeVariables(grammar.typeParameters)
                        .all { p -> referencedTyParams.contains(p) }
                }
                .toList()
            Tls.session().log.log(Level.Debug) {
                "filtered_where_clauses = $filteredWhereClauses"
            }

            return Pair(filteredTypeParams, filteredWhereClauses)
        }
    }

    fun writeParseMod(body: (CodeGenerator<C>) -> Unit) {
        rust(this.out, "")
        rust(this.out, "#[rustfmt::skip]")
        rust(
            this.out,
            "#[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, " +
                "unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]",
        )
        rust(this.out, "mod ${this.prefix}parse${this.startSymbol} {")
        rust(this.out, "")

        this.writeUses()

        body(this)

        rust(this.out, "}")
    }

    fun writeUses() {
        this.out.writeUses("${this.actionModule}::", this.grammar)

        if (this.grammar.internToken != null) {
            rust(
                this.out,
                "use self::${this.prefix}lalrpop_util::lexer::Token;",
            )
        } else {
            rust(
                this.out,
                "use ${this.actionModule}::${this.prefix}ToTriple;",
            )
        }
    }

    fun startParserFn() {
        val parseErrorType = this.types.parseErrorType()

        val typeParameters: List<String>
        val parameters: List<String>

        val internToken = this.grammar.internToken != null
        if (internToken) {
            // if we are generating the tokenizer, we just need the
            // input, and that has already been added as one of the
            // user parameters
            typeParameters = listOf()
            parameters = listOf()
        } else {
            // otherwise, we need an iterator of type `TOKENS`
            val userTypeParameters = StringBuilder()
            for (typeParameter in this.grammar.typeParameters) {
                userTypeParameters.append("$typeParameter, ")
            }
            typeParameters = listOf(
                "${this.prefix}TOKEN: ${this.prefix}ToTriple<$userTypeParameters>",
                "${this.prefix}TOKENS: IntoIterator<Item=${this.prefix}TOKEN>${if (this.repeatable) " + Clone" else ""}",
            )
            parameters = listOf("${this.prefix}tokens0: ${this.prefix}TOKENS")
        }

        rust(
            this.out,
            "${this.grammar.nonterminals.getValue(this.startSymbol).visibility}struct ${this.userStartSymbol}Parser {",
        )
        if (internToken) {
            rust(
                this.out,
                "builder: ${this.prefix}lalrpop_util::lexer::MatcherBuilder,",
            )
        }
        rust(this.out, "_priv: (),")
        rust(this.out, "}")
        rust(this.out, "")

        // Start default implementation
        rust(
            this.out,
            "impl Default for ${this.userStartSymbol}Parser { fn default() -> Self { Self::new() } }",
        )

        // Start parser implementation
        rust(this.out, "impl ${this.userStartSymbol}Parser {")
        rust(
            this.out,
            "${this.grammar.nonterminals.getValue(this.startSymbol).visibility}fn new() -> ${this.userStartSymbol}Parser {",
        )
        if (internToken) {
            rust(
                this.out,
                "let ${this.prefix}builder = ${this.actionModule}::${this.prefix}intern_token::new_builder();",
            )
        }
        rust(this.out, "${this.userStartSymbol}Parser {")
        if (internToken) {
            rust(this.out, "builder: ${this.prefix}builder,")
        }
        rust(this.out, "_priv: (),")
        rust(this.out, "}") // Parser
        rust(this.out, "}") // new()
        rust(this.out, "")

        rust(this.out, "#[allow(dead_code)]")
        this.out
            .fnHeader(
                this.grammar.nonterminals.getValue(this.startSymbol).visibility,
                "parse",
            )
            .withParameters(listOf("&self"))
            .withGrammar(this.grammar)
            .withTypeParameters(typeParameters)
            .withParameters(parameters)
            .withReturnType(
                "Result<${this.types.nonterminalType(this.startSymbol)}, $parseErrorType>",
            )
            .emit()
        rust(this.out, "{")
    }

    fun defineTokens() {
        if (this.grammar.internToken != null) {
            // if we are generating the tokenizer, create a matcher as our input iterator
            rust(
                this.out,
                "let mut ${this.prefix}tokens = self.builder.matcher(input);",
            )
        } else {
            // otherwise, convert one from the `IntoIterator`
            // supplied, using the `ToTriple` trait which inserts
            // errors/locations etc if none are given
            val cloneCall = if (this.repeatable) ".clone()" else ""
            rust(
                this.out,
                "let ${this.prefix}tokens = ${this.prefix}tokens0$cloneCall.into_iter();",
            )

            rust(
                this.out,
                "let mut ${this.prefix}tokens = ${this.prefix}tokens.map(|t| ${this.prefix}ToTriple::to_triple(t));",
            )
        }
    }

    fun endParserFn() {
        rust(this.out, "}") // function         rust(this.out, "}") // implementation
    }

    /**
     * Returns phantom data type that captures the user-declared type
     * parameters in a phantom-data. This helps with ensuring that
     * all type parameters are constrained, even if they are not
     * used.
     */
    fun phantomDataType(): String {
        val phantomBits: List<String> = this
            .grammar
            .typeParameters
            .map { tp ->
                when (tp) {
                    is TypeParameter.LifetimeTp -> "&${tp.lifetime} ()"
                    is TypeParameter.Id -> tp.atom.toString()
                }
            }
        return "core::marker::PhantomData<(${Sep(", ", phantomBits)})>"
    }

    /**
     * Returns expression that captures the user-declared type
     * parameters in a phantom-data. This helps with ensuring that
     * all type parameters are constrained, even if they are not
     * used.
     */
    fun phantomDataExpr(): String {
        val phantomBits: List<String> = this
            .grammar
            .typeParameters
            .map { tp ->
                when (tp) {
                    is TypeParameter.LifetimeTp -> "&()"
                    is TypeParameter.Id -> tp.atom.toString()
                }
            }
        return "core::marker::PhantomData::<(${Sep(", ", phantomBits)})>"
    }
}
