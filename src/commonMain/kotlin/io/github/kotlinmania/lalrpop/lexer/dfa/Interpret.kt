// port-lint: source lexer/dfa/interpret.rs
package io.github.kotlinmania.lalrpop.lexer.dfa

fun interpret(dfa: Dfa, input: String): Pair<NfaIndex, String>? {
    var longest: Pair<NfaIndex, Int>? = null
    var stateIndex = START

    for (offset in input.indices) {
        val ch = input[offset]
        val state = dfa.states[stateIndex.value]

        val target = dfa.state(stateIndex).testEdges
            .firstOrNull { (test, _) -> test.containsChar(ch) }
            ?.second

        stateIndex = target ?: state.otherEdge

        when (val k = dfa.state(stateIndex).kind) {
            is Kind.Accepts -> {
                longest = k.nfa to (offset + 1)
            }
            Kind.Reject -> break
            Kind.Neither -> {}
        }
    }

    return longest?.let { (index, offset) -> index to input.substring(0, offset) }
}
