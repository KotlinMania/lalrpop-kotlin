// port-lint: source src/normalize/cond_comp/test.rs
package io.github.kotlinmania.lalrpop.normalize.condComp

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.compare
import io.github.kotlinmania.lalrpop.grammar.parseTree.GrammarItem
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test

class CondCompTest {
    @Test
    fun cfgAttr() {
        val grammar = parseGrammar(
            """grammar;
A = ();
#[cfg(feature = "feat1")]
B = ();
#[cfg(not(feature = "feat3"))]
C = ();
#[cfg(all(feature = "feat1", feature = "feat2"))]
D = ();
#[cfg(any(feature = "feat1", feature = "feat3"))]
E = ();
#[cfg(all(
    feature = "feat1",
    not(feature = "feat3"),
    any(feature = "feat1"),
))]
F = ();

#[cfg(not(feature = "feat1"))]
G = ();
#[cfg(all(feature = "feat1", feature = "feat2", feature = "feat3"))]
H = ();
#[cfg(any(feature = "feat3", feature = "feat4"))]
I = ();
#[cfg(any(
    feature = "feat3",
    not(feature = "feat1"),
    any(feature = "feat3"),
))]
J = ();
""",
        ).getOrThrow()

        val expected = parseGrammar(
            """grammar;
A = ();
B = ();
C = ();
D = ();
E = ();
F = ();
""",
        ).getOrThrow()

        val features = io.github.kotlinmania.btree.BTreeSet.of("feat1", "feat2")
        val session = Session.new().also { it.features = features }

        val actual = removeDisabledDecls(session, grammar)

        // remove attributes to compare with expected
        actual.items.forEach { item ->
            if (item is GrammarItem.Nonterminal) {
                item.data.attributes.clear()
            }
        }

        compare(Result.success(actual), Result.success(expected))
    }
}
