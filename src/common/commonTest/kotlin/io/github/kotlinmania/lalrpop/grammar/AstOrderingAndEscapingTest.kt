package io.github.kotlinmania.lalrpop.grammar

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.Lifetime
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Path
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalLiteral
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeBound
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeBoundParameter
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.lexer.rustDebugQuote
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.buildLr1States
import io.github.kotlinmania.lalrpop.lr1.codegen.compileParseTable
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.kotlinmania.lalrpop.grammar.parsetree.WhereClause as PtWhereClause
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause as ReprWhereClause

class AstOrderingAndEscapingTest {
    private fun nominal(vararg segments: String, typeArgs: List<TypeRepr> = emptyList()): TypeRepr =
        TypeRepr.Nominal(
            NominalTypeRepr(
                path = Path(absolute = false, ids = segments.mapTo(mutableListOf()) { Atom.from(it) }),
                types = typeArgs.toMutableList(),
            ),
        )

    @Test
    fun typeReprRefOrderingMatchesUpstreamDerivedOrd() {
        val lifetimeA = Lifetime(Atom.from("'a"))
        val u32 = nominal("u32")
        val typeA = nominal("A")

        val sharedAnonU32 = TypeRepr.Ref(lifetime = null, mutable = false, referent = u32)
        val sharedNamedU32 = TypeRepr.Ref(lifetime = lifetimeA, mutable = false, referent = u32)
        val mutAnonA = TypeRepr.Ref(lifetime = null, mutable = true, referent = typeA)
        val mutNamedA = TypeRepr.Ref(lifetime = lifetimeA, mutable = true, referent = typeA)

        // Upstream Option<Lifetime> sorts None < Some('a), whereas toString() sorted "&'a u32" < "&u32".
        assertTrue(sharedAnonU32 < sharedNamedU32)
        assertTrue(sharedNamedU32 > sharedAnonU32)

        // Upstream bool sorts false < true, whereas toString() sorted "&mut A" < "&u32".
        assertTrue(sharedAnonU32 < mutAnonA)
        assertTrue(sharedNamedU32 < mutNamedA)
    }

    @Test
    fun nominalTypeReprAndWhereClauseOrderingMatchesUpstreamDerivedOrd() {
        val typeA = nominal("A")
        val typeB = nominal("B")
        val refA = TypeRepr.Ref(lifetime = null, mutable = false, referent = typeA)

        // TypeRepr.Nominal (ordinal 2) < TypeRepr.Ref (ordinal 5), so Vec<B> < Vec<&A>
        // even though toString() sorted "Vec<&A>" < "Vec<B>" ('&' < 'B').
        val vecB = nominal("Vec", typeArgs = listOf(typeB))
        val vecRefA = nominal("Vec", typeArgs = listOf(refA))
        assertTrue(vecB < vecRefA)

        // Path(["Alpha"]) < Path(["Alpha", "Beta"]) (shorter prefix first), so Alpha<Beta> < Alpha::Beta
        // even though toString() sorted "Alpha::Beta" < "Alpha<Beta>" (':' < '<').
        val alphaOfBeta = nominal("Alpha", typeArgs = listOf(nominal("Beta")))
        val alphaColonBeta = nominal("Alpha", "Beta")
        assertTrue(alphaOfBeta < alphaColonBeta)

        // Repr WhereClause::Forall (ordinal 0) < WhereClause::Bound (ordinal 1),
        // even when Bound renders "A: 'a" ('A' < 'f' in "for<'z> ...").
        val lifetimeA = Lifetime(Atom.from("'a"))
        val lifetimeZ = Lifetime(Atom.from("'z"))
        val boundClause =
            ReprWhereClause.Bound(
                subject = typeA,
                bound = TypeBound.LifetimeBound(lifetimeA),
            )
        val forallClause =
            ReprWhereClause.Forall(
                binder = mutableListOf(TypeParameter.LifetimeTp(lifetimeZ)),
                clause = boundClause,
            )
        assertTrue(forallClause < boundClause)
    }

    @Test
    fun matchMappingAndParseTreeBoundsOrderingMatchesUpstreamDerivedOrd() {
        // MatchMapping::Terminal (ordinal 0) < MatchMapping::Skip (ordinal 1),
        // even when terminal renders "~" (126) > Skip "{ }" (123).
        val tildeTerminal = MatchMapping.Terminal(TerminalString.Bare(Atom.from("~")))
        assertTrue(tildeTerminal < MatchMapping.Skip)

        // Within MatchMapping::Terminal, TerminalString::Literal (ordinal 0) < TerminalString::Bare (ordinal 1),
        // even though Hir renders r#"a"# ('r') > Bare "b" ('b').
        val regexTerminal = MatchMapping.Terminal(TerminalString.regex(Atom.from("a")))
        val bareTerminal = MatchMapping.Terminal(TerminalString.Bare(Atom.from("b")))
        assertTrue(regexTerminal < bareTerminal)

        // TypeParameter::Lifetime (ordinal 0) < TypeParameter::Id (ordinal 1).
        val tpLifetime = TypeParameter.LifetimeTp(Lifetime(Atom.from("'z")))
        val tpId = TypeParameter.Id(Atom.from("!"))
        assertTrue(tpLifetime < tpId)

        // ParseTree WhereClause::Lifetime (ordinal 0) < WhereClause::Type (ordinal 1),
        // even when TypeClause subject renders "&u32" ('&' = 38 < '\'' = 39).
        val lifetimeZ = Lifetime(Atom.from("'z"))
        val lifetimeA = Lifetime(Atom.from("'a"))
        val refU32 = TypeRepr.Ref(lifetime = null, mutable = false, referent = nominal("u32"))
        val ptLifetimeClause =
            PtWhereClause.LifetimeClause<TypeRepr>(
                lifetime = lifetimeZ,
                bounds = mutableListOf(lifetimeA),
            )
        val ptTypeClause =
            PtWhereClause.TypeClause<TypeRepr>(
                forall = mutableListOf(),
                ty = refU32,
                bounds = mutableListOf(TypeBound.LifetimeBound(lifetimeA)),
            )
        assertTrue(ptLifetimeClause < ptTypeClause)

        // TypeBound::Fn (ordinal 1) < TypeBound::Trait (ordinal 2), even when Fn path "Z" > Trait path "A".
        val fnBound =
            TypeBound.Fn<TypeRepr>(
                forall = mutableListOf(),
                path = Path.fromId(Atom.from("Z")),
                parameters = mutableListOf(),
                ret = null,
            )
        val traitBound =
            TypeBound.Trait<TypeRepr>(
                forall = mutableListOf(),
                path = Path.fromId(Atom.from("A")),
                parameters = mutableListOf(),
            )
        assertTrue(fnBound < traitBound)

        // TypeBoundParameter::TypeParameter (ordinal 1) < TypeBoundParameter::Associated (ordinal 2).
        val typeParamArg = TypeBoundParameter.TypeParameterParam(nominal("Z"))
        val assocParamArg = TypeBoundParameter.Associated(Atom.from("A"), nominal("B"))
        assertTrue(typeParamArg < assocParamArg)
    }

    @Test
    fun parseTableVariantDowncastOrderUsesStructuralTypeReprOrd() {
        Tls.test().use {
            val grammar =
                normalizedGrammar(
                    """
                    grammar<'a>(x: &'a u32, y: &'a mut A);
                    pub S: () = { M U => () };
                    M: &'a mut A = "m" => y;
                    U: &'a u32 = "u" => x;
                    """.trimIndent(),
                )
            val userStart = NonterminalString(Atom.from("S"))
            val startSymbol = grammar.startNonterminals.getValue(userStart)
            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLr1States(grammar, startSymbol)
                    val emitted = StringBuilder()
                    compileParseTable(
                        grammar = grammar,
                        userStartSymbol = userStart,
                        startSymbol = startSymbol,
                        states = states,
                        actionModule = "super",
                        out = RustWrite.new(emitted),
                    )
                    val code = emitted.toString()
                    val sharedIdx = code.indexOf("-> (usize, &'a u32, usize)")
                    val mutIdx = code.indexOf("-> (usize, &'a mut A, usize)")
                    assertTrue(sharedIdx >= 0, "expected downcast fn for &'a u32")
                    assertTrue(mutIdx >= 0, "expected downcast fn for &'a mut A")
                    assertTrue(
                        sharedIdx < mutIdx,
                        "expected &'a u32 downcast fn before &'a mut A downcast fn, got shared=$sharedIdx mut=$mutIdx",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }

    @Test
    fun patternKindStringAndRustDebugQuoteEscapeControlCharsAndSurrogatePairs() {
        val strPat = PatternKind.StringKind<TypeRepr>("a\"b\\c\nd\t\r\u0000\u001f")
        assertEquals("\"a\\\"b\\\\c\\nd\\t\\r\\0\\x1f\"", strPat.toString())

        val crab = "\uD83E\uDD80" // U+1F980
        assertEquals("\"\\u{1f980}\"", rustDebugQuote(crab))
        assertEquals("\"\\u{1f980}\"", TerminalLiteral.Quoted(Atom.from(crab)).toString())
        assertEquals("\"\\u{1f980}\"", PatternKind.StringKind<TypeRepr>(crab).toString())

        val mixed = "hi\u00A0\uD83D\uDE80!" // NBSP + U+1F680
        assertEquals("\"hi\\u{a0}\\u{1f680}!\"", rustDebugQuote(mixed))
    }
}
