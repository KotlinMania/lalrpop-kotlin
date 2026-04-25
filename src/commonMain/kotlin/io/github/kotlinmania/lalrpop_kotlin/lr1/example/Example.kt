// port-lint: source src/lr1/example/mod.rs
//! Code to compute example inputs given a backtrace.
package io.github.kotlinmania.lalrpop_kotlin.lr1.example

import io.github.kotlinmania.lalrpop_kotlin.Style
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.toContent
import io.github.kotlinmania.lalrpop_kotlin.message.AsciiView
import io.github.kotlinmania.lalrpop_kotlin.message.drawHorizontalLine
import io.github.kotlinmania.lalrpop_kotlin.message.drawVerticalLine
import io.github.kotlinmania.lalrpop_kotlin.message.writeChars
import io.github.kotlinmania.lalrpop_kotlin.message.message.Content
import io.github.kotlinmania.lalrpop_kotlin.message.builder.InlineBuilder
import io.github.kotlinmania.lalrpop_kotlin.tls.Tls

// mod test

/// An "example" input and the way it was derived. This can be
/// serialized into useful text. For example, it might represent
/// something like this:
///
/// ```text
///          Looking at
///              |
///              v
/// Ty "->" Ty "->" Ty
/// |        |       |
/// +-Ty-----+       |
/// |                |
/// +-Ty-------------+
/// ```
///
/// The top-line is the `symbols` vector. The groupings below are
/// stored in the `reductions` vector, in order from smallest to
/// largest (they are always properly nested). The `cursor` field
/// indicates the current lookahead token.
///
/// The `symbols` vector is actually `Option<Symbol>` to account
/// for empty reductions:
///
/// ```text
/// A       B
/// | |   | |
/// | +-Y-+ |
/// +-Z-----+
/// ```
///
/// The "empty space" between A and B would be represented as `None`.
data class Example(
    val symbols: MutableList<ExampleSymbol>,
    val cursor: Int,
    val reductions: MutableList<Reduction>,
) {
    /// Length of each symbol. Each will need *at least* that amount
    /// of space. :) Measure in characters, under the assumption of a
    /// mono-spaced font. Also add a final `0` marker which will serve
    /// as the end position.
    private fun lengths(): MutableList<Int> {
        val out: MutableList<Int> = symbols.map { s ->
            when (s) {
                is ExampleSymbol.SymbolValue -> s.symbol.toString().length
                ExampleSymbol.Epsilon -> 1 // display as " "
            }
        }.toMutableList()
        out.add(0)
        return out
    }

    /// Extract a prefix of the list of symbols from this `Example`
    /// and make a styled list of them, like:
    ///
    ///    Ty "->" Ty -> "Ty"
    fun toSymbolList(length: Int, styles: ExampleStyles): Content {
        var builder = InlineBuilder.new().beginSpaced()

        for ((index, symbol) in symbols.subList(0, length).withIndex()) {
            val style = when {
                index < this.cursor -> styles.beforeCursor
                index == this.cursor -> when (symbol) {
                    is ExampleSymbol.SymbolValue -> when (symbol.symbol) {
                        is Symbol.Terminal -> styles.onCursor
                        is Symbol.Nonterminal -> styles.afterCursor
                    }
                    ExampleSymbol.Epsilon -> styles.afterCursor
                }
                else -> styles.afterCursor
            }

            if (symbol is ExampleSymbol.SymbolValue) {
                builder = builder.push(symbol.symbol.toContent()).styled(style)
            }
        }

        return builder.end().indented().end()
    }

    /// Render the example into a styled diagram suitable for
    /// embedding in an error message.
    fun intoPicture(styles: ExampleStyles): Content {
        val lengths = this.lengths()
        val positions = this.positions(lengths)
        return InlineBuilder.new()
            .push(ExamplePicture(
                example = this,
                positions = positions,
                styles = styles,
            ))
            .indented()
            .end()
    }

    private fun startingPositions(lengths: List<Int>): MutableList<Int> {
        val out: MutableList<Int> = mutableListOf()
        var counter = 0
        for (len in lengths) {
            val start = counter

            // Leave space for "NT " (if "NT" is the name
            // of the nonterminal).
            counter = start + len + 1

            out.add(start)
        }
        return out
    }

    /// Start index where each symbol in the example should appear,
    /// measured in characters. These are spaced to leave enough room
    /// for the reductions below.
    private fun positions(lengths: List<Int>): MutableList<Int> {
        // Initially, position each symbol with one space in between,
        // like:
        //
        //     X Y Z
        val positions = startingPositions(lengths)

        // Adjust spacing to account for the nonterminal labels
        // we will have to add. It will display
        // like this:
        //
        //    A1 B2 C3 D4 E5 F6
        //    |         |
        //    +-Label---+
        //
        // But if the label is long we may have to adjust the spacing
        // of the covered items (here, we changed them to two spaces,
        // except the first gap, which got 3 spaces):
        //
        //    A1   B2  C3  D4 E5 F6
        //    |             |
        //    +-LongLabel22-+
        for (reduction in this.reductions) {
            val start = reduction.start
            val end = reduction.end
            val nonterminal = reduction.nonterminal
            val ntLen = nonterminal.toString().length

            // Number of symbols we are reducing. This should always
            // be non-zero because even in the case of a \epsilon
            // rule, we ought to be have a `None` entry in the symbol array.
            val numSyms = end - start
            check(numSyms > 0)

            // Let's use the expansion from above as our running example.
            // We start out with positions like this:
            //
            //    A1 B2 C3 D4 E5 F6
            //    |             |
            //    +-LongLabel22-+
            //
            // But we want LongLabel to end at D4. No good.

            // Start of first symbol to be reduced. Here, 0.
            //
            // A1 B2 C3 D4
            // ^ here
            val startPosition = positions[start]

            // End of last symbol to be reduced. Here, 11.
            //
            // A1 B2 C3 D4 E5
            //             ^ positions[end]
            //            ^ here -- positions[end] - 1
            val endPosition = positions[end] - 1

            // We need space to draw `+-Label-+` between
            // start_position and end_position.
            val requiredLen = ntLen + 4 // here, 15
            val actualLen = endPosition - startPosition // here, 10
            if (requiredLen < actualLen) {
                continue // Got enough space, all set.
            }

            // Have to add `difference` characters altogether.
            val difference = requiredLen - actualLen // here, 4

            // Increment over everything that is not part of this nonterminal.
            // In the example above, that is E5 and F6.
            shift(positions, end, positions.size, difference)

            if (numSyms > 1) {
                // If there is just one symbol being reduced here,
                // then we have shifted over the things that follow
                // it, and we are done. This would be a case like:
                //
                //     X         Y Z
                //     |       |
                //     +-Label-+
                //
                // (which maybe ought to be rendered slightly
                // differently).
                //
                // But if there are multiple symbols, we're not quite
                // done, because there would be an unsightly gap:
                //
                //       (gaps)
                //      |  |  |
                //      v  v  v
                //    A1 B2 C3 D4     E5 F6
                //    |             |
                //    +-LongLabel22-+
                //
                // we'd like to make things line up, so we have to
                // distribute that extra space internally by
                // increasing the "gaps" (marked above) as evenly as
                // possible (basically, full justification).
                //
                // We do this by dividing up the spaces evenly and
                // then taking the remainder `N` and distributing 1
                // extra to the first N.
                val numGaps = numSyms - 1 // number of gaps we can adjust. Here, 3.
                val amount = difference / numGaps // what to add to each gap. Here, 1.
                val extra = difference % numGaps // the remainder. Here, 1.

                // For the first `extra` symbols, give them amount + 1
                // extra space. After that, just amount. (O(n^2). Sue me.)
                for (i in 0 until extra) {
                    shift(positions, start + 1 + i, end, amount + 1)
                }
                for (i in extra until numGaps) {
                    shift(positions, start + 1 + i, end, amount)
                }
            }
        }

        return positions
    }

    internal fun paintOn(styles: ExampleStyles, positions: List<Int>, view: AsciiView) {
        // Draw the brackets for each reduction:
        for ((index, reduction) in this.reductions.withIndex()) {
            val startColumn = positions[reduction.start]
            val endColumn = positions[reduction.end] - 1
            val row = 1 + index
            view.drawVerticalLine(0 until row + 1, startColumn)
            view.drawVerticalLine(0 until row + 1, endColumn - 1)
            view.drawHorizontalLine(row, startColumn until endColumn)
        }

        // Write the labels for each reduction. Do this after the
        // brackets so that ascii canvas can convert `|` to `+`
        // without interfering with the text (in case of weird overlap).
        val session = Tls.session()
        for ((index, reduction) in this.reductions.withIndex()) {
            val column = positions[reduction.start] + 2
            val row = 1 + index
            view.writeChars(
                row,
                column,
                reduction.nonterminal.toString().iterator(),
                session.nonterminalSymbol,
            )
        }

        // Write the labels on top:
        //    A1   B2  C3  D4 E5 F6
        paintSymbolsOn(this.symbols, positions, styles, view)
    }

    private fun paintSymbolsOn(
        symbols: List<ExampleSymbol>,
        positions: List<Int>,
        styles: ExampleStyles,
        view: AsciiView,
    ) {
        val session = Tls.session()
        for ((index, exSymbol) in symbols.withIndex()) {
            val style = when {
                index < this.cursor -> styles.beforeCursor
                index == this.cursor -> {
                    // Only display actual terminals in the "on-cursor"
                    // font, because it might be misleading to show a
                    // nonterminal that way. Really it'd be nice to expand
                    // so that the cursor is always a terminal.
                    when (exSymbol) {
                        is ExampleSymbol.SymbolValue -> when (exSymbol.symbol) {
                            is Symbol.Terminal -> styles.onCursor
                            else -> styles.afterCursor
                        }
                        else -> styles.afterCursor
                    }
                }
                else -> styles.afterCursor
            }

            val column = positions[index]
            when (exSymbol) {
                is ExampleSymbol.SymbolValue -> when (val sym = exSymbol.symbol) {
                    is Symbol.Terminal -> {
                        view.writeChars(
                            0,
                            column,
                            sym.term.toString().iterator(),
                            style.with(session.terminalSymbol),
                        )
                    }
                    is Symbol.Nonterminal -> {
                        view.writeChars(
                            0,
                            column,
                            sym.nt.toString().iterator(),
                            style.with(session.nonterminalSymbol),
                        )
                    }
                }
                ExampleSymbol.Epsilon -> {}
            }
        }
    }
}

sealed class ExampleSymbol {
    data class SymbolValue(val symbol: Symbol) : ExampleSymbol()
    object Epsilon : ExampleSymbol()
}

data class ExampleStyles(
    val beforeCursor: Style,
    val onCursor: Style,
    val afterCursor: Style,
) {
    companion object {
        fun ambig(): ExampleStyles {
            val session = Tls.session()
            return ExampleStyles(
                beforeCursor = session.ambigSymbols,
                onCursor = session.ambigSymbols,
                afterCursor = session.ambigSymbols,
            )
        }

        fun new(): ExampleStyles {
            val session = Tls.session()
            return ExampleStyles(
                beforeCursor = session.observedSymbols,
                onCursor = session.cursorSymbol,
                afterCursor = session.unobservedSymbols,
            )
        }

        val default: ExampleStyles get() = ExampleStyles(
            beforeCursor = Style.new(),
            onCursor = Style.new(),
            afterCursor = Style.new(),
        )
    }
}

data class Reduction(
    val start: Int,
    val end: Int,
    val nonterminal: NonterminalString,
)

private class ExamplePicture(
    val example: Example,
    val positions: List<Int>,
    val styles: ExampleStyles,
) : Content {
    override fun minWidth(): Int = positions.last()

    override fun emit(view: AsciiView) {
        example.paintOn(styles, positions, view)
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.add(this)
    }
}

private fun shift(positions: MutableList<Int>, from: Int, until: Int, amount: Int) {
    for (i in from until until) {
        positions[i] = positions[i] + amount
    }
}
