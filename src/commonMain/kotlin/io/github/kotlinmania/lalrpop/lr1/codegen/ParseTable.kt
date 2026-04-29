// port-lint: source lr1/codegen/parse_table.rs
//! A compiler from an LR(1) table to a traditional table driven parser.
package io.github.kotlinmania.lalrpop.lr1.codegen

import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Parameter
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust
import io.github.kotlinmania.lalrpop.tls.Tls

object ParseTable {
    const val DEBUG_PRINT: Boolean = false

    fun compile(
        grammar: Grammar,
        userStartSymbol: NonterminalString,
        startSymbol: NonterminalString,
        states: List<State<TokenSet>>,
        actionModule: String,
        out: RustWrite,
    ) {
        compileParseTable(grammar, userStartSymbol, startSymbol, states, actionModule, out)
    }
}

fun compileParseTable(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    states: List<State<TokenSet>>,
    actionModule: String,
    out: RustWrite,
) {
    val tableDriven = newTableDriven(
        grammar,
        userStartSymbol,
        startSymbol,
        states,
        actionModule,
        out,
    )
    tableDriven.write()
}

sealed class Comment<T> {
    /**
     * Mirrors `implementation<T: fmt::Display> fmt::Display for Comment<'_, T>`.
     * Each variant overrides `toString()` so dispatchers like
     * `format("{}", comment)` produce the same text as upstream.
     * Without these overrides the parse-table emitter wrote
     * `Goto(token=Terminal(terminalString="b"), newState=2)` into the
     * generated Rust source instead of ` // on "b", goto 2`.
     */
    abstract override fun toString(): String

    data class Goto<T>(val token: T, val newState: Int) : Comment<T>() {
        override fun toString(): String = " // on $token, goto $newState"
    }

    data class Error<T>(val token: T) : Comment<T>() {
        override fun toString(): String = " // on $token, error"
    }

    data class Reduce<T>(val token: T, val production: Production) : Comment<T>() {
        override fun toString(): String = " // on $token, reduce `$production`"
    }
}

class TableDriven(
    /** type parameters for the `Nonterminal` type */
    val symbolTypeParams: List<TypeParameter>,

    val symbolWhereClauses: List<WhereClause>,

    val machine: MachineParameters,

    /** a list of each nonterminal in some specific order */
    val allNonterminals: List<NonterminalString>,

    val reduceIndices: MutableMap<Production, Int>,

    val stateType: String,

    val variantNames: MutableMap<Symbol, String> = map(),
    val variants: MutableMap<TypeRepr, String> = map(),
    val reduceFunctions: MutableSet<Int> = set(),
)

private fun newTableDriven(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    states: List<State<TokenSet>>,
    actionModule: String,
    out: RustWrite,
): CodeGenerator<TableDriven> {
    val (symbolTypeParams, symbolWhereClauses) =
        CodeGenerator.filterTypeParametersAndWhereClauses(
            grammar,
            grammar.types.nonterminalTypes()
                .asSequence()
                .plus(grammar.types.terminalTypes().asSequence())
                .asIterable(),
        )

    val machine = MachineParameters.new(grammar)

    // Assign each production a unique index to use as the values for reduce
    // actions in the ACTION and EOF_ACTION tables.
    val reduceIndices: MutableMap<Production, Int> = map()
    var idx = 0
    for (nt in grammar.nonterminals.values) {
        for (p in nt.productions) {
            reduceIndices[p] = idx
            idx += 1
        }
    }

    val stateType: String = run {
        // [reduceIndices] are allowed to be +1 since the negative maximum of any integer type
        // is one larger than the positive maximum
        val maxValue = maxOf(states.size, reduceIndices.size)
        if (maxValue <= Byte.MAX_VALUE.toInt()) {
            "i8"
        } else if (maxValue <= Short.MAX_VALUE.toInt()) {
            "i16"
        } else {
            "i32"
        }
    }

    return CodeGenerator.new(
        grammar,
        userStartSymbol,
        startSymbol,
        states,
        out,
        false,
        actionModule,
        TableDriven(
            symbolTypeParams = symbolTypeParams,
            symbolWhereClauses = symbolWhereClauses,
            machine = machine,
            allNonterminals = grammar.nonterminals.keys.toList(),
            reduceIndices = reduceIndices,
            stateType = stateType,
        ),
    )
}

private fun CodeGenerator<TableDriven>.write() {
    this.writeParseMod { this1 ->
        this1.writeValueTypeDefn()
        this1.writeParseTable()
        this1.writeMachineDefinition()
        this1.writeTokenToIntegerFn()
        this1.writeTokenToSymbolFn()
        this1.writeSimulateReduceFn()
        this1.writeParserFn()
        this1.writeAcceptsFn()
        this1.emitReduceActions()
        this1.emitDowncastFns()
        this1.emitReduceActionFunctions()
    }
}

private fun CodeGenerator<TableDriven>.writeMachineDefinition() {
    val errorType = this.types.errorType()
    val tokenType = this.types.terminalTokenType()
    val locType = this.types.terminalLocType()
    val startType = this.types.nonterminalType(this.startSymbol)
    val stateType = this.custom.stateType
    val symbolType = this.symbolType()
    val phantomDataType = this.phantomDataType()
    val phantomDataExpr = this.phantomDataExpr()
    val machine = this.custom.machine
    val machineTypeParameters = Sep(", ", machine.typeParameters)
    val machineWhereClauses = Sep(", ", machine.whereClauses)

    rust(this.out, "struct ${this.prefix}StateMachine<$machineTypeParameters>")
    rust(this.out, "where $machineWhereClauses")
    rust(this.out, "{")
    for (param in machine.fields) {
        rust(this.out, "${param.name}: ${param.ty},")
    }
    rust(this.out, "${this.prefix}phantom: $phantomDataType,")
    rust(this.out, "}")

    rust(
        this.out,
        "impl<$machineTypeParameters> ${this.prefix}state_machine::ParserDefinition for ${this.prefix}StateMachine<$machineTypeParameters>",
    )
    rust(this.out, "where $machineWhereClauses")
    rust(this.out, "{")
    rust(this.out, "type Location = $locType;")
    rust(this.out, "type Error = $errorType;")
    rust(this.out, "type Token = $tokenType;")
    rust(this.out, "type TokenIndex = usize;")
    rust(this.out, "type Symbol = $symbolType;")
    rust(this.out, "type Success = $startType;")
    rust(this.out, "type StateIndex = $stateType;")
    rust(this.out, "type Action = $stateType;")
    rust(this.out, "type ReduceIndex = $stateType;")
    rust(this.out, "type NonterminalIndex = usize;")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(this.out, "fn start_location(&self) -> Self::Location {")
    rust(this.out, "  Default::default()")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(this.out, "fn start_state(&self) -> Self::StateIndex {")
    rust(this.out, "  0")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(this.out, "fn token_to_index(&self, token: &Self::Token) -> Option<usize> {")
    rust(this.out, "${this.prefix}token_to_integer(token, $phantomDataExpr)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(
        this.out,
        "fn action(&self, state: $stateType, integer: usize) -> $stateType {",
    )
    rust(this.out, "${this.prefix}action(state, integer)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(
        this.out,
        "fn error_action(&self, state: $stateType) -> $stateType {",
    )
    // Avoid needless 1 subtract by 1
    val errorActionArg: String = if (this.grammar.terminals.all.size == 1) {
        "0"
    } else {
        "${this.grammar.terminals.all.size} - 1"
    }
    rust(this.out, "${this.prefix}action(state, $errorActionArg)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(
        this.out,
        "fn eof_action(&self, state: $stateType) -> $stateType {",
    )
    rust(this.out, "${this.prefix}EOF_ACTION[state as usize]")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(
        this.out,
        "fn goto(&self, state: $stateType, nt: usize) -> $stateType {",
    )
    rust(this.out, "${this.prefix}goto(state, nt)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(
        this.out,
        "fn token_to_symbol(&self, token_index: usize, token: Self::Token) -> Self::Symbol {",
    )
    rust(this.out, "${this.prefix}token_to_symbol(token_index, token, $phantomDataExpr)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(
        this.out,
        "fn expected_tokens(&self, state: $stateType) -> alloc::vec::Vec<alloc::string::String> {",
    )
    rust(this.out, "${this.prefix}expected_tokens(state)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(
        this.out,
        "fn expected_tokens_from_states(&self, states: &[$stateType]) -> alloc::vec::Vec<alloc::string::String> {",
    )
    rust(this.out, "${this.prefix}expected_tokens_from_states(states, $phantomDataExpr)")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(this.out, "fn uses_error_recovery(&self) -> bool {")
    rust(this.out, "${this.grammar.usesErrorRecovery}")
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "#[inline]")
    rust(this.out, "fn error_recovery_symbol(")
    rust(this.out, "&self,")
    rust(this.out, "recovery: ${this.prefix}state_machine::ErrorRecovery<Self>,")
    rust(this.out, ") -> Self::Symbol {")
    if (this.grammar.usesErrorRecovery) {
        val errorVariant = this.variantNameForSymbol(Symbol.Terminal(TerminalString.Error))
        rust(this.out, "${this.prefix}Symbol::$errorVariant(recovery)")
    } else {
        rust(this.out, "panic!(\"error recovery not enabled for this grammar\")")
    }
    rust(this.out, "}")

    rust(this.out, "")
    rust(this.out, "fn reduce(")
    rust(this.out, "&mut self,")
    rust(this.out, "action: $stateType,")
    rust(this.out, "start_location: Option<&Self::Location>,")
    rust(this.out, "states: &mut alloc::vec::Vec<$stateType>,")
    rust(
        this.out,
        "symbols: &mut alloc::vec::Vec<${this.prefix}state_machine::SymbolTriple<Self>>,",
    )
    rust(this.out, ") -> Option<${this.prefix}state_machine::ParseResult<Self>> {")
    rust(this.out, "${this.prefix}reduce(")
    for (param in this.grammar.parameters) {
        rust(this.out, "self.${param.name},")
    }
    rust(this.out, "action,")
    rust(this.out, "start_location,")
    rust(this.out, "states,")
    rust(this.out, "symbols,")
    rust(this.out, "$phantomDataExpr,")
    rust(this.out, ")")
    rust(this.out, "}")

    rust(this.out, "")
    rust(
        this.out,
        "fn simulate_reduce(&self, action: $stateType) -> ${this.prefix}state_machine::SimulatedReduce<Self> {",
    )
    rust(this.out, "${this.prefix}simulate_reduce(action, $phantomDataExpr)")
    rust(this.out, "}")

    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.writeValueTypeDefn() {
    // sometimes some of the variants are not used, particularly
    // if we are generating multiple parsers from the same file:
    rust(this.out, "#[allow(dead_code)]")
    rust(
        this.out,
        "pub(crate) enum ${this.prefix}Symbol<${Sep(", ", this.custom.symbolTypeParams)}>",
    )

    if (this.custom.symbolWhereClauses.isNotEmpty()) {
        rust(
            this.out,
            " where ${Sep(", ", this.custom.symbolWhereClauses)}",
        )
    }

    rust(this.out, " {")

    // make one variant per terminal
    for (term in this.grammar.terminals.all) {
        val ty = this.types.terminalType(term)
        val len = this.custom.variants.size
        val name = this.custom.variants[ty] ?: run {
            val newName = "Variant$len"
            rust(this.out, "$newName($ty),")
            this.custom.variants[ty] = newName
            newName
        }

        this.custom.variantNames[Symbol.Terminal(term)] = name
    }

    // make one variant per nonterminal
    for (nt in this.grammar.nonterminals.keys) {
        val ty = this.types.nonterminalType(nt)
        val len = this.custom.variants.size
        val name = this.custom.variants[ty] ?: run {
            val newName = "Variant$len"
            rust(this.out, "$newName($ty),")
            this.custom.variants[ty] = newName
            newName
        }

        this.custom.variantNames[Symbol.Nonterminal(nt)] = name
    }

    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.writeParseTable() {
    val stateType = this.custom.stateType

    // The table is a two-dimensional matrix indexed first by state
    // and then by the terminal index. The value is described above.
    rust(this.out, "const ${this.prefix}ACTION: &[$stateType] = &[")

    for ((index, state) in this.states.withIndex()) {
        rust(this.out, "// State $index")

        if (Tls.session().emitComments) {
            for (item in state.items.vec) {
                rust(this.out, "//     $item")
            }
        }

        // Write an action for each terminal (either shift, reduce, or error).
        val custom = this.custom
        val iterator: List<Pair<Int, Comment<Token>>> = this.grammar.terminals.all.map { terminal ->
            val newState = state.shifts[terminal]
            if (newState != null) {
                Pair(
                    newState.value + 1,
                    Comment.Goto(Token.Terminal(terminal), newState.value),
                )
            } else {
                writeReduction(custom, state, Token.Terminal(terminal))
            }
        }
        this.out.writeTableRow(iterator)
    }

    rust(this.out, "];")

    rust(
        this.out,
        "fn ${this.prefix}action(state: $stateType, integer: usize) -> $stateType {",
    )

    // Leads to multliplication by 1
    val multiplier: String = if (this.grammar.terminals.all.size == 1) {
        ""
    } else {
        "* ${this.grammar.terminals.all.size}"
    }
    rust(this.out, "${this.prefix}ACTION[(state as usize) $multiplier + integer]")

    rust(this.out, "}")

    // Actions on EOF. Indexed just by state.
    rust(this.out, "const ${this.prefix}EOF_ACTION: &[${this.custom.stateType}] = &[")
    for ((index, state) in this.states.withIndex()) {
        rust(this.out, "// State $index")
        val reduction = writeReduction(this.custom, state, Token.Eof)
        this.out.writeTableRow(listOf(reduction))
    }
    rust(this.out, "];")

    rust(
        this.out,
        "fn ${this.prefix}goto(state: $stateType, nt: usize) -> $stateType {",
    )

    emitGotoMatch(
        this.out,
        "nt",
        this.grammar.nonterminals.keys,
        "state",
        this.states,
    ) { nonterminal, state ->
        val newState = state.gotos[nonterminal]
        if (newState != null) {
            Pair(
                newState.value,
                Comment.Goto(nonterminal, newState.value),
            )
        } else {
            Pair(null, Comment.Error(nonterminal))
        }
    }

    rust(this.out, "}")

    this.emitTerminalReprList()
    this.emitExpectedTokensFn()
    this.emitExpectedTokensFromStatesFn()
}

private fun <K, K2, T> emitGotoMatch(
    out: RustWrite,
    kName: String,
    iter: Iterable<K>,
    k2Name: String,
    iter2: Iterable<K2>,
    stateLookup: (K, K2) -> Pair<Int?, Comment<T>>,
) {
    val emitComments = Tls.session().emitComments

    rust(out, "match $kName {")

    for ((kIndex, k) in iter.withIndex()) {
        // Build enumerated (index, (nextState, comment)) then group consecutive indices by
        // nextState so we can compress them as a..=b
        val enumerated: List<Pair<Int, Pair<Int?, Comment<T>>>> =
            iter2.withIndex().map { (i, k2) -> Pair(i, stateLookup(k, k2)) }

        // chunkBy: group CONSECUTIVE items with same nextState
        val row: MutableList<Pair<Int?, MutableList<Pair<Int, Pair<Int?, Comment<T>>>>>> =
            mutableListOf()
        for (entry in enumerated) {
            val ns = entry.second.first
            val last = row.lastOrNull()
            if (last != null && last.first == ns) {
                last.second.add(entry)
            } else {
                row.add(Pair(ns, mutableListOf(entry)))
            }
        }

        // If the row was all errors we do not need to emit it
        if (row.size == 1 && row[0].first == null) {
            continue
        }

        row.sortBy { it.first }

        // Since the parser will always select a non-error (non-zero) nextState we can import the
        // catch all in the match to represent the largest variant
        var largestVariantIndex = 0
        var largestVariant = 0

        // Group by nextState
        val filtered: List<Pair<Int, MutableList<Pair<Int, Pair<Int?, Comment<T>>>>>> = row
            // We always emit a catch-all for 0 error states (which will never be hit)
            .mapNotNull { (opt, group) -> opt?.let { Pair(it, group) } }
            .let { flat ->
                // chunkBy nextState
                val chunks: MutableList<Pair<Int, MutableList<Pair<Int, Pair<Int?, Comment<T>>>>>> =
                    mutableListOf()
                for ((ns, group) in flat) {
                    val last = chunks.lastOrNull()
                    if (last != null && last.first == ns) {
                        last.second.addAll(group)
                    } else {
                        chunks.add(Pair(ns, group.toMutableList()))
                    }
                }
                chunks
            }

        val variants: List<Triple<Int, List<Pair<Int, Int?>>, Comment<T>?>> = filtered
            .mapIndexed { i, (nextState, groupGroup) ->
                var comment: Comment<T>? = null
                val vec: MutableList<Pair<Int, Int?>> = mutableListOf()
                // groupGroup is already flat list of (index, (nextState, comment)); but the rust
                // code treats it as an iterator of (index, group) pairs from chunkBy; since our
                // earlier chunking already flattened, each consecutive-index run from the original
                // row is preserved in order. Re-group consecutive indices here:
                val runs: MutableList<MutableList<Pair<Int, Pair<Int?, Comment<T>>>>> =
                    mutableListOf()
                for (entry in groupGroup) {
                    val last = runs.lastOrNull()
                    if (last != null && last.last().first + 1 == entry.first) {
                        last.add(entry)
                    } else {
                        runs.add(mutableListOf(entry))
                    }
                }
                for (run in runs) {
                    val first = run.first()
                    val start = first.first
                    val (_, c) = first.second
                    comment = c
                    val end: Int? = if (run.size > 1) run.last().first else null
                    vec.add(Pair(start, end))
                }
                if (vec.size > largestVariant) {
                    largestVariantIndex = i
                    largestVariant = vec.size
                }
                Triple(nextState, vec.toList(), comment)
            }

        if (variants.size == 1) {
            rust(out, "$kIndex => ${variants[0].first},")
        } else {
            rust(out, "$kIndex => match $k2Name {")

            for ((i, tv) in variants.withIndex()) {
                if (i == largestVariantIndex) {
                    continue
                }
                val (nextState, ranges, comment) = tv
                if (comment != null) {
                    if (emitComments) {
                        rust(out, "$comment")
                    }
                }
                val rangeStr = ranges.joinToString(" | ") { (start, end) ->
                    if (end == null) "$start" else "$start..=$end"
                }
                rust(out, "$rangeStr => $nextState,")
            }

            rust(out, "_ => ${variants[largestVariantIndex].first},")
            rust(out, "},")
        }
    }

    rust(out, "_ => 0,") // unreachable
    rust(out, "}")
}

private fun writeReduction(
    custom: TableDriven,
    state: State<TokenSet>,
    token: Token,
): Pair<Int, Comment<Token>> {
    val reduction = state
        .reductions
        .asSequence()
        .filter { (t, _) -> t.contains(token) }
        .map { (_, p) -> p }
        .firstOrNull()
    return if (reduction != null) {
        val action = custom.reduceIndices.getValue(reduction)
        Pair(
            -(action + 1),
            Comment.Reduce(token, reduction),
        )
    } else {
        // Otherwise, this is an error. Store 0.
        Pair(0, Comment.Error(token))
    }
}

private fun CodeGenerator<TableDriven>.writeParserFn() {
    val phantomDataExpr = this.phantomDataExpr()

    this.startParserFn()

    this.defineTokens()

    rust(this.out, "${this.prefix}state_machine::Parser::drive(")
    rust(this.out, "${this.prefix}StateMachine {")
    for (param in this.grammar.parameters) {
        rust(this.out, "${param.name},")
    }
    rust(this.out, "${this.prefix}phantom: $phantomDataExpr,")
    rust(this.out, "},")
    rust(this.out, "${this.prefix}tokens,")
    rust(this.out, ")")

    this.endParserFn()
}

private fun CodeGenerator<TableDriven>.writeTokenToIntegerFn() {
    val tokenType = this.types.terminalTokenType()

    val parameters = listOf(
        "${this.prefix}token: &$tokenType",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(
            Visibility.Priv,
            "${this.prefix}token_to_integer",
        )
        .withTypeParameters(this.grammar.typeParameters)
        .withWhereClauses(this.grammar.whereClauses)
        .withParameters(parameters)
        .withReturnType("Option<usize>")
        .emit()
    rust(this.out, "{")

    // This match contains user-supplied token names.  Reenable some warnings to help them
    // catch errors if they've got a bug in their custom lexer implementation
    rust(this.out, "#[warn(unused_variables)]")
    rust(this.out, "match ${this.prefix}token {")

    for ((index, terminal) in this.grammar.terminals.all.withIndex()) {
        if (terminal == TerminalString.Error) {
            continue
        }
        val pattern = this.grammar.pattern(terminal).map { "_" }
        rust(this.out, "$pattern if true => Some($index),")
    }

    rust(this.out, "_ => None,")

    rust(this.out, "}")
    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.writeTokenToSymbolFn() {
    val symbolType = this.symbolType()
    val tokenType = this.types.terminalTokenType()

    val parameters = listOf(
        "${this.prefix}token_index: usize",
        "${this.prefix}token: $tokenType",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(
            Visibility.Priv,
            "${this.prefix}token_to_symbol",
        )
        .withTypeParameters(this.grammar.typeParameters)
        .withWhereClauses(this.grammar.whereClauses)
        .withParameters(parameters)
        .withReturnType(symbolType)
        .emit()
    rust(this.out, "{")

    rust(
        this.out,
        "#[allow(clippy::manual_range_patterns)]match ${this.prefix}token_index {",
    )

    val tokenToSymbolMapping: MutableList<Pair<String, MutableList<Pair<Int, Any>>>> =
        mutableListOf()

    for ((index, terminal) in this.grammar.terminals.all.withIndex()) {
        if (terminal == TerminalString.Error) {
            continue
        }
        val variantName = this.variantNameForSymbol(Symbol.Terminal(terminal))
        val pattern = this.grammar.pattern(terminal)

        val existing = tokenToSymbolMapping.firstOrNull { (v, _) -> v == variantName }
        if (existing == null) {
            tokenToSymbolMapping.add(Pair(variantName, mutableListOf(Pair(index, pattern))))
        } else {
            existing.second.add(Pair(index, pattern))
        }
    }

    for ((variantName, indices) in tokenToSymbolMapping) {
        val patternNames: MutableList<String> = mutableListOf()
        var first = true
        val patterns: List<String> = indices.map { (_, pattern) ->
            val p = pattern as io.github.kotlinmania.lalrpop.grammar.pattern.Pattern<TypeRepr>
            var nameIndex = 0
            val mapped = p.map {
                val name = "${this.prefix}tok$nameIndex"
                nameIndex += 1
                if (first) {
                    patternNames.add(name)
                }
                name
            }
            first = false
            "$mapped"
        }

        if (patternNames.isNotEmpty()) {
            val indexStr = indices.joinToString(" | ") { (i, _) -> "$i" }
            rust(this.out, "$indexStr => match ${this.prefix}token {")
            val open = if (patternNames.size > 1) "(" else ""
            val close = if (patternNames.size > 1) ")" else ""
            val patternJoined = patterns.joinToString(" | ")
            rust(
                this.out,
                "$patternJoined if true => ${this.prefix}Symbol::$variantName($open${patternNames.joinToString(", ")}$close),",
            )
            rust(this.out, "_ => unreachable!(),")
            rust(this.out, "},")
        } else {
            val indexStr = indices.joinToString(" | ") { (i, _) -> "$i" }
            rust(
                this.out,
                "$indexStr => ${this.prefix}Symbol::$variantName(${this.prefix}token),",
            )
        }
    }

    rust(this.out, "_ => unreachable!(),")

    rust(this.out, "}")
    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.emitReduceActions() {
    val successType = this.types.nonterminalType(this.startSymbol)
    val parseErrorType = this.types.parseErrorType()
    val locType = this.types.terminalLocType()
    val spannedSymbolType = this.spannedSymbolType()

    val parameters = listOf(
        "${this.prefix}action: ${this.custom.stateType}",
        "${this.prefix}lookahead_start: Option<&$locType>",
        "${this.prefix}states: &mut alloc::vec::Vec<${this.custom.stateType}>",
        "${this.prefix}symbols: &mut alloc::vec::Vec<$spannedSymbolType>",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(Visibility.Priv, "${this.prefix}reduce")
        .withGrammar(this.grammar)
        .withParameters(parameters)
        .withReturnType("Option<Result<$successType,$parseErrorType>>")
        .emit()
    rust(this.out, "{")

    rust(
        this.out,
        "let (${this.prefix}pop_states, ${this.prefix}nonterminal) = match ${this.prefix}action {",
    )
    var index = 0
    for (nt in this.grammar.nonterminals.values) {
        for (production in nt.productions) {
            rust(this.out, "$index => {")
            // In debug builds LLVM is not very good at reusing stack space which makes this
            // reduce function take up O(number of states) space. By wrapping each reduce action in
            // an immediately called function each reduction takes place in their own function
            // context which ends up reducing the stack space used.

            // Fallible actions and the start symbol may do early returns so we avoid wrapping
            // those
            val isFallible = this.grammar.actionIsFallible(production.action)
            val reduceStackSpace = !isFallible && production.nonterminal != this.startSymbol

            if (reduceStackSpace) {
                this.custom.reduceFunctions.add(index)
                val phantomDataExpr = this.phantomDataExpr()
                rust(
                    this.out,
                    "${this.prefix}reduce$index(${this.grammar.userParameterRefs()}${this.prefix}lookahead_start, ${this.prefix}symbols, $phantomDataExpr)",
                )
            } else {
                this.emitReduceAction(production)
            }

            rust(this.out, "}")
            index += 1
        }
    }
    rust(this.out, "_ => panic!(\"invalid action code {${this.prefix}action}\")")
    rust(this.out, "};")

    // pop the consumed states from the stack
    rust(this.out, "let ${this.prefix}states_len = ${this.prefix}states.len();")
    rust(
        this.out,
        "${this.prefix}states.truncate(${this.prefix}states_len - ${this.prefix}pop_states);",
    )

    rust(this.out, "let ${this.prefix}state = *${this.prefix}states.last().unwrap();")

    rust(
        this.out,
        "let ${this.prefix}next_state = ${this.prefix}goto(${this.prefix}state, ${this.prefix}nonterminal);",
    )
    if (ParseTable.DEBUG_PRINT) {
        rust(
            this.out,
            "println!(\"goto state {} from {} due to nonterminal {}\", ${this.prefix}next_state, ${this.prefix}state, ${this.prefix}nonterminal);",
        )
    }
    rust(this.out, "${this.prefix}states.push(${this.prefix}next_state);")
    rust(this.out, "None")
    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.emitReduceActionFunctions() {
    var index = 0
    for (nt in this.grammar.nonterminals.values) {
        for (production in nt.productions) {
            if (this.custom.reduceFunctions.contains(index)) {
                this.emitReduceAlternativeFnHeader(index)
                this.emitReduceAction(production)
                rust(this.out, "}")
            }
            index += 1
        }
    }
}

private fun CodeGenerator<TableDriven>.emitReduceAlternativeFnHeader(index: Int) {
    val locType = this.types.terminalLocType()
    val spannedSymbolType = this.spannedSymbolType()

    val parameters = listOf(
        "${this.prefix}lookahead_start: Option<&$locType>",
        "${this.prefix}symbols: &mut alloc::vec::Vec<$spannedSymbolType>",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(Visibility.Priv, "${this.prefix}reduce$index")
        .withGrammar(this.grammar)
        .withParameters(parameters)
        .withReturnType("(usize, usize)")
        .emit()
    rust(this.out, "{")
}

private fun CodeGenerator<TableDriven>.emitReduceAction(production: Production) {
    rust(this.out, "// $production")

    // Pop each of the symbols and their associated states.
    if (production.symbols.size > 1) {
        // By asserting that there are enough elements to pop before popping multiple elements
        // we may help LLVM to optimize better since it does not need to generate panic
        // branches for each unwrap
        rust(
            this.out,
            "assert!(${this.prefix}symbols.len() >= ${production.symbols.size});",
        )
    }
    for ((idx, symbol) in production.symbols.withIndex().reversed()) {
        val name = this.variantNameForSymbol(symbol)
        rust(
            this.out,
            "let ${this.prefix}sym$idx = ${this.prefix}pop_$name(${this.prefix}symbols);",
        )
    }
    val transferSyms: List<String> = (0 until production.symbols.size)
        .map { i -> "${this.prefix}sym$i" }

    // Execute the action function     // identify the "start" and "end" location for this production; this
    // is typically the start of the first symbol and end of the last symbol we are
    // reducing; but in the case of an empty production, it will come from the
    // lookahead
    val firstSym = transferSyms.firstOrNull()
    val lastSym = transferSyms.lastOrNull()
    if (firstSym != null && lastSym != null) {
        rust(this.out, "let ${this.prefix}start = $firstSym.0.clone();")
        rust(this.out, "let ${this.prefix}end = $lastSym.2.clone();")
    } else {
        // we pop no symbols, so grab from the top of the stack
        // (unless we are in the start state, in which case the
        // stack will be empty)
        rust(
            this.out,
            "let ${this.prefix}start = ${this.prefix}lookahead_start.cloned().or_else(|| ${this.prefix}symbols.last().map(|s| s.2)).unwrap_or_default();",
        )
        rust(this.out, "let ${this.prefix}end = ${this.prefix}start;")
    }

    val transferredSyms = transferSyms.size

    val args: MutableList<String> = transferSyms.toMutableList()
    if (transferredSyms == 0) {
        args.add("&${this.prefix}start")
        args.add("&${this.prefix}end")
    }

    // invoke the action code
    val isFallible = this.grammar.actionIsFallible(production.action)
    if (isFallible) {
        rust(
            this.out,
            "let ${this.prefix}nt = match ${this.actionModule}::${this.prefix}action${production.action.index()}::<${Sep(", ", this.grammar.nonLifetimeTypeParameters())}>(${this.grammar.userParameterRefs()}${Sep(", ", args)}) {",
        )
        rust(this.out, "Ok(v) => v,")
        rust(this.out, "Err(e) => return Some(Err(e)),")
        rust(this.out, "};")
    } else {
        rust(
            this.out,
            "let ${this.prefix}nt = ${this.actionModule}::${this.prefix}action${production.action.index()}::<${Sep(", ", this.grammar.nonLifetimeTypeParameters())}>(${this.grammar.userParameterRefs()}${Sep(", ", args)});",
        )
    }

    // if this is the final state, return it
    if (production.nonterminal == this.startSymbol) {
        rust(this.out, "return Some(Ok(${this.prefix}nt));")
        return
    }

    // push the produced value on the stack
    val name = this.variantNameForSymbol(Symbol.Nonterminal(production.nonterminal))
    rust(
        this.out,
        "${this.prefix}symbols.push((${this.prefix}start, ${this.prefix}Symbol::$name(${this.prefix}nt), ${this.prefix}end));",
    )

    // produce the index that we will import to extract the next state
    // from GOTO array
    val index = this.custom.allNonterminals.indexOfFirst { x -> x == production.nonterminal }
    rust(this.out, "(${production.symbols.size}, $index)")
}

private fun CodeGenerator<TableDriven>.variantNameForSymbol(s: Symbol): String =
    this.custom.variantNames.getValue(s)

private fun CodeGenerator<TableDriven>.emitDowncastFns() {
    rust(this.out, "#[inline(never)]")
    rust(this.out, "fn ${this.prefix}symbol_type_mismatch() -> ! {")
    rust(this.out, "panic!(\"symbol type mismatch\")")
    rust(this.out, "}")

    for ((ty, name) in this.custom.variants.toMap()) {
        this.emitDowncastFn(name, ty)
    }
}

private fun CodeGenerator<TableDriven>.emitDowncastFn(variantName: String, variantTy: TypeRepr) {
    val spannedSymbolType = this.spannedSymbolType()

    rust(this.out, "fn ${this.prefix}pop_$variantName<")
    for (typeParameter in this.custom.symbolTypeParams) {
        rust(this.out, "  $typeParameter,")
    }
    rust(this.out, ">(")
    rust(
        this.out,
        "${this.prefix}symbols: &mut alloc::vec::Vec<$spannedSymbolType>",
    )
    rust(this.out, ") -> ${this.types.spannedType(variantTy)}")

    if (this.custom.symbolWhereClauses.isNotEmpty()) {
        rust(this.out, " where ${Sep(", ", this.custom.symbolWhereClauses)}")
    }

    rust(this.out, " {")

    if (ParseTable.DEBUG_PRINT) {
        rust(this.out, "println!(\"pop_$variantName\");")
    }
    rust(this.out, "match ${this.prefix}symbols.pop() {")
    rust(
        this.out,
        "Some((${this.prefix}l, ${this.prefix}Symbol::$variantName(${this.prefix}v), ${this.prefix}r)) => (${this.prefix}l, ${this.prefix}v, ${this.prefix}r),",
    )
    rust(this.out, "_ => ${this.prefix}symbol_type_mismatch()")
    rust(this.out, "}")

    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.writeSimulateReduceFn() {
    val stateType = this.custom.stateType

    val parameters = listOf(
        "${this.prefix}reduce_index: $stateType",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(Visibility.Priv, "${this.prefix}simulate_reduce")
        .withTypeParameters(this.custom.machine.typeParameters)
        .withWhereClauses(this.custom.machine.whereClauses)
        .withParameters(parameters)
        .withReturnType(
            "${this.prefix}state_machine::SimulatedReduce<${this.prefix}StateMachine<${Sep(", ", this.custom.machine.typeParameters)}>>",
        )
        .emit()
    rust(this.out, "{")

    rust(this.out, "match ${this.prefix}reduce_index {")
    var index = 0
    for (nt in this.grammar.nonterminals.values) {
        for (production in nt.productions) {
            if (Tls.session().emitComments) {
                rust(this.out, "// simulate $production")
            }

            // if we just reduced the start symbol, that is also an accept criteria
            if (production.nonterminal == this.startSymbol) {
                rust(
                    this.out,
                    "$index => ${this.prefix}state_machine::SimulatedReduce::Accept,",
                )
            } else {
                val numSymbols = production.symbols.size
                val nonterminalIdx = this.custom.allNonterminals
                    .indexOfFirst { x -> x == production.nonterminal }
                rust(this.out, "$index => {")
                if (ParseTable.DEBUG_PRINT) {
                    rust(this.out, "println!(r##\"accepts: simulating $production\"##);")
                }
                rust(this.out, "${this.prefix}state_machine::SimulatedReduce::Reduce {")
                rust(this.out, "states_to_pop: $numSymbols,")
                rust(this.out, "nonterminal_produced: $nonterminalIdx,")
                rust(this.out, "}")
                rust(this.out, "}")
            }
            index += 1
        }
    }
    rust(
        this.out,
        "_ => panic!(\"invalid reduction index {${this.prefix}reduce_index}\")",
    )
    rust(this.out, "}") // end match

    rust(this.out, "}")
}

/**
 * The `accepts` function
 *
 * ```ignore
 * function __accepts() {
 * errorState: Option<i32>,
 * states: &Vec<i32>,
 * optInteger: Option<usize>,
 * ) -> bool {
 * ...
 * }
 * ```
 *
 * has the job of figuring out whether the given state stack (with the
 * optional error state appended) would "accept" the given lookahead. We
 * basically trace through the LR automaton looking for one of two
 * outcomes:
 *
 * - the lookahead is eventually shifted
 * - we reduce to the end state successfully (in the case of EOF).
 *
 * If we used the pure LR(1) algorithm, we would not need this
 * function, because we would be guaranteed to error immediately
 * (and not after some number of reductions). But with an LALR
 * (or Lane Table) generated automaton, it is possible to reduce
 * some number of times before encountering an error. Failing to
 * take this into account can lead error recovery into an
 * infinite loop (see the `errorRecoveryLalrLoop` test) or
 * produce crappy results (see `errorRecoveryLockIn`).
 */
private fun CodeGenerator<TableDriven>.writeAcceptsFn() {
    val phantomDataExpr = this.phantomDataExpr()
    val parameters = listOf(
        "${this.prefix}error_state: Option<${this.custom.stateType}>",
        "${this.prefix}states: &[${this.custom.stateType}]",
        "${this.prefix}opt_integer: Option<usize>",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(Visibility.Priv, "${this.prefix}accepts")
        .withTypeParameters(this.custom.machine.typeParameters)
        .withWhereClauses(this.custom.machine.whereClauses)
        .withParameters(parameters)
        .withReturnType("bool")
        .emit()
    rust(this.out, "{")

    if (ParseTable.DEBUG_PRINT) {
        rust(
            this.out,
            "println!(\"Testing whether state {} accepts token {:?}\", ${this.prefix}error_state, ${this.prefix}opt_integer);",
        )
    }

    // Create our own copy of the state stack to play with.
    rust(this.out, "let mut ${this.prefix}states = ${this.prefix}states.to_vec();")
    rust(this.out, "${this.prefix}states.extend(${this.prefix}error_state);")

    rust(this.out, "loop {")

    rust(this.out, "let mut ${this.prefix}states_len = ${this.prefix}states.len();")

    rust(this.out, "let ${this.prefix}top = ${this.prefix}states[${this.prefix}states_len - 1];")

    if (ParseTable.DEBUG_PRINT) {
        rust(
            this.out,
            "println!(\"accepts: top-state={} num-states={}\", ${this.prefix}top, ${this.prefix}states_len);",
        )
    }

    rust(this.out, "let ${this.prefix}action = match ${this.prefix}opt_integer {")
    rust(this.out, "None => ${this.prefix}EOF_ACTION[${this.prefix}top as usize],")
    rust(
        this.out,
        "Some(${this.prefix}integer) => ${this.prefix}action(${this.prefix}top, ${this.prefix}integer),",
    )
    rust(this.out, "};") // end `match`

    // If we encounter an error action, we do **not** accept.
    rust(this.out, "if ${this.prefix}action == 0 { return false; }")

    // If we encounter a shift action, we DO accept.
    rust(this.out, "if ${this.prefix}action > 0 { return true; }")

    // If we encounter a reduce action, we need to simulate its
    // effect on the state stack.
    rust(
        this.out,
        "let (${this.prefix}to_pop, ${this.prefix}nt) = match ${this.prefix}simulate_reduce(-(${this.prefix}action + 1), $phantomDataExpr) {",
    )
    rust(this.out, "${this.prefix}state_machine::SimulatedReduce::Reduce {")
    rust(this.out, "states_to_pop, nonterminal_produced")
    rust(this.out, "} => (states_to_pop, nonterminal_produced),")
    rust(
        this.out,
        "${this.prefix}state_machine::SimulatedReduce::Accept => return true,",
    )
    rust(this.out, "};")

    rust(this.out, "${this.prefix}states_len -= ${this.prefix}to_pop;")
    rust(this.out, "${this.prefix}states.truncate(${this.prefix}states_len);")
    rust(this.out, "let ${this.prefix}top = ${this.prefix}states[${this.prefix}states_len - 1];")

    if (ParseTable.DEBUG_PRINT) {
        rust(
            this.out,
            "println!(\"accepts: popped {} symbols, new top is {}, nt is {}\", ${this.prefix}to_pop, ${this.prefix}top, ${this.prefix}nt, );",
        )
    }

    rust(
        this.out,
        "let ${this.prefix}next_state = ${this.prefix}goto(${this.prefix}top, ${this.prefix}nt);",
    )

    rust(this.out, "${this.prefix}states.push(${this.prefix}next_state);")

    rust(this.out, "}") // end loop
    rust(this.out, "}") // end function }
}

private fun CodeGenerator<TableDriven>.symbolType(): String =
    "${this.prefix}Symbol<${Sep(", ", this.custom.symbolTypeParams)}>"

private fun CodeGenerator<TableDriven>.spannedSymbolType(): String {
    val locType = this.types.terminalLocType()
    return "($locType,${this.symbolType()},$locType)"
}

/** Emit the array of terminal tokens for use in generating error output */
private fun CodeGenerator<TableDriven>.emitTerminalReprList() {
    rust(this.out, "#[allow(clippy::needless_raw_string_hashes)]")
    rust(this.out, "const ${this.prefix}TERMINAL: &[&str] = &[")
    val allTerminals = if (this.grammar.usesErrorRecovery) {
        // Subtract one to exclude the error terminal
        this.grammar.terminals.all.subList(0, this.grammar.terminals.all.size - 1)
    } else {
        this.grammar.terminals.all
    }
    for (terminal in allTerminals) {
        // Three # should hopefully be enough to prevent any
        // reasonable terminal from escaping the literal
        rust(this.out, "r###\"$terminal\"###,")
    }
    rust(this.out, "];")
}

private fun CodeGenerator<TableDriven>.emitExpectedTokensFn() {
    rust(
        this.out,
        "fn ${this.prefix}expected_tokens(${this.prefix}state: ${this.custom.stateType}) -> alloc::vec::Vec<alloc::string::String> {",
    )

    // Grab any terminals in the current state which could have resulted in a successful parse
    rust(
        this.out,
        "${this.prefix}TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {",
    )
    rust(this.out, "let next_state = ${this.prefix}action(${this.prefix}state, index);")
    rust(this.out, "if next_state == 0 {")
    rust(this.out, "None")
    rust(this.out, "} else {")
    rust(this.out, "Some(alloc::string::ToString::to_string(terminal))")
    rust(this.out, "}")
    rust(this.out, "}).collect()")
    rust(this.out, "}")
}

private fun CodeGenerator<TableDriven>.emitExpectedTokensFromStatesFn() {
    val parameters = listOf(
        "${this.prefix}states: &[${this.custom.stateType}]",
        "_: ${this.phantomDataType()}",
    )

    this.out
        .fnHeader(
            Visibility.Priv,
            "${this.prefix}expected_tokens_from_states",
        )
        .withTypeParameters(this.custom.machine.typeParameters)
        .withWhereClauses(this.custom.machine.whereClauses)
        .withParameters(parameters)
        .withReturnType("alloc::vec::Vec<alloc::string::String>")
        .emit()

    rust(this.out, "{")

    // Grab any terminals in the current state which would have resulted in a successful parse,
    // as verified using accepts()
    rust(
        this.out,
        "${this.prefix}TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {",
    )
    rust(
        this.out,
        "if ${this.prefix}accepts(None, ${this.prefix}states, Some(index), ${this.phantomDataExpr()}) {",
    )
    rust(this.out, "Some(alloc::string::ToString::to_string(terminal))")
    rust(this.out, "} else {")
    rust(this.out, "None")
    rust(this.out, "}")
    rust(this.out, "}).collect()")
    rust(this.out, "}")
}

class MachineParameters(
    val typeParameters: MutableList<TypeParameter>,
    val fields: List<Parameter>,
    val whereClauses: MutableList<WhereClause>,
) {
    companion object {
        fun new(grammar: Grammar): MachineParameters {
            val typeParameters: MutableList<TypeParameter> = grammar.typeParameters.toMutableList()
            val whereClauses: MutableList<WhereClause> = grammar.whereClauses.toMutableList()

            val fields: List<Parameter> = grammar.parameters.map { param ->
                val namedTy = param.ty.nameAnonymousLifetimesAndComputeImpliedOutlives(
                    grammar.prefix,
                    typeParameters,
                    whereClauses,
                )
                Parameter(
                    name = param.name,
                    ty = namedTy,
                )
            }

            // Put lifetimes first (this is stable, mind, so order remains
            // largely unperturbed):
            typeParameters.sortBy { tp ->
                when (tp) {
                    is TypeParameter.LifetimeTp -> 0
                    is TypeParameter.Id -> 1
                }
            }

            return MachineParameters(
                typeParameters = typeParameters,
                fields = fields,
                whereClauses = whereClauses,
            )
        }
    }
}
