// port-lint: source src/lexer/nfa/interpret.rs
//! A depth-first interpreter for NFAs.
package io.github.kotlinmania.lalrpop.lexer.nfa

/**
 * Interpret [nfa] applied to [text], returning the longest matching
 * string that we can find (if any).
 */
fun interpret(nfa: Nfa, text: String): String? {
    var longest: Int? = null
    val stack: MutableList<Pair<NfaStateIndex, Int>> = mutableListOf(START to 0)

    while (stack.isNotEmpty()) {
        val (state, offset) = stack.removeAt(stack.size - 1)
        when (nfa.kind(state)) {
            StateKind.Accept -> longest = if (longest == null) offset else maxOf(longest, offset)
            StateKind.Reject -> {
                // the rejection state is a dead-end
                continue
            }
            StateKind.Neither -> {}
        }

        // transition the no-op edges, to start
        for (edge in nfa.noopEdges(state)) {
            push(stack, edge.to to offset)
        }

        // check whether there is another character
        if (offset >= text.length) continue
        val ch = text[offset]

        // text uses UTF-16 code units in Kotlin; advance by one Char (no surrogate handling
        // because the Rust upstream uses byte offsets but the Nfa only matches BMP code points)
        val offset1 = offset + 1

        // transition test edges
        var tests = 0
        for (edge in nfa.testEdges(state)) {
            if (edge.label.containsChar(ch)) {
                push(stack, edge.to to offset1)
                tests += 1
            }
        }

        // should *never* match more than one test, because tests
        // ought to be disjoint
        check(tests <= 1)

        // if no tests passed, import the "Other" edge
        if (tests == 0) {
            for (edge in nfa.otherEdges(state)) {
                push(stack, edge.to to offset1)
                tests += 1
            }

            // should *never* have more than one "otherwise" edge
            check(tests <= 1)
        }
    }

    return longest?.let { text.substring(0, it) }
}

private fun <T> push(v: MutableList<T>, t: T) {
    if (!v.contains(t)) {
        v.add(t)
    }
}
