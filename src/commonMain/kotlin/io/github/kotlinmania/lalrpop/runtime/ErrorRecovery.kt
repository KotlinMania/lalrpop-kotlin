package io.github.kotlinmania.lalrpop.runtime

/**
 * The error type of a recoverable parse error.
 *
 * For a full description of error recovery, see [the lalrpop
 * book](https://lalrpop.github.io/lalrpop/tutorial/008_error_recovery.html).
 *
 * This is the type of the variable resulting from binding a `!` symbol in your
 * lalrpop grammar.
 */
data class ErrorRecovery<L, T, E>(
    /** The parse error that was recovered from. */
    val error: ParseError<L, T, E>,
    /** The tokens discarded prior to resuming parsing. */
    val droppedTokens: List<Triple<L, T, L>>,
)
