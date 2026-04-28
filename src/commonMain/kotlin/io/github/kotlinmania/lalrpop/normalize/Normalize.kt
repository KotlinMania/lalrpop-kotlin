// port-lint: source normalize/mod.rs
//! Normalization processes a parse tree until it is in suitable form to
//! be converted to the more canonical form. This is done as a series of
//! passes, each contained in their own module below.
package io.github.kotlinmania.lalrpop.normalize

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar as PtGrammar
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar as RGrammar
import io.github.kotlinmania.lalrpop.normalize.condComp.removeDisabledDecls
import io.github.kotlinmania.lalrpop.normalize.inline.inline
import io.github.kotlinmania.lalrpop.normalize.lower.lower
import io.github.kotlinmania.lalrpop.normalize.macroExpand.expandMacros
import io.github.kotlinmania.lalrpop.normalize.precedence.expandPrecedence
import io.github.kotlinmania.lalrpop.normalize.prevalidate.validate as prevalidateValidate
import io.github.kotlinmania.lalrpop.normalize.resolve.resolve
import io.github.kotlinmania.lalrpop.normalize.tokenCheck.validate as tokenCheckValidate
import io.github.kotlinmania.lalrpop.normalize.tyinfer.inferTypes
import io.github.kotlinmania.lalrpop.lr1.Token


data class NormError(
    val message: String,
    val span: Span,
)

/** Throws the equivalent of `return Err(NormError { ... })` in the Rust original. */
fun returnErr(span: Span, message: String): Nothing =
    throw NormErrorException(NormError(message, span))

class NormErrorException(val err: NormError) : RuntimeException(err.message)

private inline fun <T> profile(session: Session, phaseName: String, action: () -> T): T {
    session.log.log(Level.Verbose) { "Phase `$phaseName` begun" }
    val result = action()
    session.log.log(Level.Verbose) { "Phase `$phaseName` completed" }
    return result
}

fun normalize(session: Session, grammar: PtGrammar): RGrammar =
    normalizeHelper(session, grammar, true)

/** for unit tests, it is convenient to skip the validation step, and supply a dummy session */
fun normalizeWithoutValidating(grammar: PtGrammar): RGrammar =
    normalizeHelper(Session.new(), grammar, false)

private fun normalizeHelper(session: Session, grammar: PtGrammar, validate: Boolean): RGrammar {
    val lowered = lowerHelper(session, grammar, validate)
    return profile(session, "Inlining") { inline(lowered) }
}

internal fun lowerHelper(session: Session, grammar: PtGrammar, validate: Boolean): RGrammar {
    profile(session, "Grammar validation") {
        if (validate) {
            prevalidateValidate(grammar)
        }
    }
    val afterCondComp = profile(session, "Conditional compilation") {
        removeDisabledDecls(session, grammar)
    }
    val resolved = profile(session, "Grammar resolution") { resolve(afterCondComp) }
    val precedenceExpanded = profile(session, "Precedence expansion") {
        expandPrecedence(resolved)
    }
    val macroExpanded = profile(session, "Macro expansion") {
        expandMacros(precedenceExpanded, session.macroRecursionLimit)
    }
    val tokenChecked = profile(session, "Token check") { tokenCheckValidate(macroExpanded) }
    val types = profile(session, "Infer types") { inferTypes(tokenChecked) }
    return profile(session, "Lowering") { lower(session, tokenChecked, types) }
}
