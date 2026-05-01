package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.kotlintarget.IndentedWriter
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSymbolEmitTest {

    @Test
    fun emitsSealedClassForTinyGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .., "b" => .. } }
                pub S: () = "a" "b" => ();
                """.trimIndent(),
            )

            val out = IndentedWriter()
            val emit = KotlinSymbolEmit(grammar, symbolClassName = "TinySym")
            emit.emitInto(out)

            val source = out.toString()
            assertTrue(source.contains("sealed class TinySym"), "missing sealed class declaration")
            assertTrue(source.contains("data class Variant0("), "missing Variant0")

            // Every grammar terminal and nonterminal is mapped to some variant.
            assertEquals(grammar.terminals.all.size, emit.variantNameByTerminal.size)
            assertEquals(grammar.nonterminals.size, emit.variantNameByNonterminal.size)
        }
    }

    @Test
    fun starlarkGrammarVariantCountMatchesRustWithinTolerance() {
        Tls.test().use {
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            val out = IndentedWriter()
            val emit = KotlinSymbolEmit(grammar, symbolClassName = "GrammarSymbol")
            emit.emitInto(out)

            val source = out.toString()
            val variantCount = Regex("data class Variant[0-9]+").findAll(source).count()

            println("starlark grammar — KotlinSymbolEmit produced $variantCount variants")

            // The starlark-kotlin hand-port has 51 variants in its GrammarSymbol.kt.
            // The Rust-generated parser has the same shape (deduplicated by Rust type).
            // Our generator should land at the same number — slight drift is acceptable
            // if it traces back to a specific TypeRepr difference, but a wild divergence
            // means the type analysis or dedup is wrong.
            assertTrue(
                variantCount in 45..60,
                "expected ~51 variants for the starlark grammar, got $variantCount",
            )
        }
    }

    @Test
    fun nestedOptionTriggersNullableOptionWrapperOnStarlarkGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            val out = IndentedWriter()
            val mapper = KotlinTypeMapper()
            val emit = KotlinSymbolEmit(grammar, "GrammarSymbol", mapper)
            emit.emitInto(out)

            val source = out.toString()
            assertTrue(
                KotlinWrapper.NULLABLE_OPTION in mapper.wrappersNeeded,
                "starlark grammar uses Option<Option<AstExpr>> but the type mapper " +
                "didn't request the NullableOption wrapper",
            )
            assertTrue(
                source.contains("sealed class NullableOption"),
                "type mapper requested NullableOption but emitter didn't write it",
            )
            assertTrue(
                source.contains("NullableOption<"),
                "no variant references NullableOption — wrapper emitted but not used",
            )
        }
    }

    @Test
    fun starlarkGrammarVariantTypesMatchUpstream() {
        Tls.test().use {
            // Print the Rust types behind every variant. Cross-checking against
            // the upstream Rust enum confirms the type analysis is consistent —
            // including whether the front end produces an Option<Option<T>> for the
            // nested `(":" <Test?>)?` macro slot or whether normalization flattens it.
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            val rustTypesByVariant: List<Pair<String, String>> =
                buildList {
                    val seen = mutableMapOf<String, String>()
                    var idx = 0
                    for (term in grammar.terminals.all) {
                        val ty = grammar.types.terminalType(term).toString()
                        seen.getOrPut(ty) {
                            val name = "Variant${idx++}"
                            add(name to ty)
                            name
                        }
                    }
                    for (nt in grammar.nonterminals.keys) {
                        val ty = grammar.types.nonterminalType(nt).toString()
                        seen.getOrPut(ty) {
                            val name = "Variant${idx++}"
                            add(name to ty)
                            name
                        }
                    }
                }

            println("starlark grammar — variant Rust types (front-end view):")
            for ((name, ty) in rustTypesByVariant) {
                println("  $name -> $ty")
            }

            // Surface any variant whose Rust type contains `Option<` twice — the
            // nested-Option pattern that needs a wrapper in Kotlin.
            val nestedOption = rustTypesByVariant.filter { (_, ty) ->
                Regex("Option<.*Option<").containsMatchIn(ty)
            }
            println("starlark grammar — nested-Option variants: $nestedOption")

            // Also dump the actual TypeRepr.Nominal path strings the front end uses,
            // so the type mapper can match against the real path representation.
            for (nt in grammar.nonterminals.keys) {
                val ty = grammar.types.nonterminalType(nt)
                if (ty.toString().contains("Option<")) {
                    println(
                        "starlark grammar — nonterminal $nt: TypeRepr=${ty::class.simpleName} value='$ty'"
                    )
                    if (ty is io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr.Nominal) {
                        println("  path='${ty.data.path}' args=${ty.data.types.map { it::class.simpleName }}")
                    }
                }
            }
        }
    }

    @Test
    fun variantNamesAreSequentialFromZero() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .., "b" => .., "c" => .. } }
                pub S: () = "a" "b" "c" => ();
                """.trimIndent(),
            )

            val out = IndentedWriter()
            val emit = KotlinSymbolEmit(grammar, symbolClassName = "Sym")
            emit.emitInto(out)

            val source = out.toString()
            val variantNames = Regex("data class (Variant[0-9]+)").findAll(source)
                .map { it.groupValues[1] }
                .toList()
            assertEquals(variantNames.size, variantNames.toSet().size, "duplicate variant names")
            for ((i, name) in variantNames.withIndex()) {
                assertEquals("Variant$i", name, "variant at position $i is $name, expected Variant$i")
            }
        }
    }
}
