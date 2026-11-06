// port-lint: source log.rs
package io.github.kotlinmania.lalrpop

import kotlin.time.TimeSource

internal class Log(
    level: Level,
    private val writer: (String) -> Unit = ::println,
) {
    var level: Level = level
        private set

    fun setLevel(level: Level) {
        this.level = level
    }

    fun log(level: Level, message: () -> String) {
        if (this.level >= level) {
            writer(message())
        }
    }

    companion object {
        fun new(level: Level): Log = Log(level)
    }
}

enum class Level {
    /** No updates unless an error arises. */
    Taciturn,

    /** Timing and minimal progress. */
    Informative,

    /** More details, but still stuff an end-user is likely to understand. */
    Verbose,

    /** Everything you could ever want and then some more. */
    Debug,
}

internal inline fun <T> profile(
    session: Session,
    phaseName: String,
    action: () -> T,
): T {
    session.log(Level.Verbose) { "Phase `$phaseName` begun" }
    val timeStamp = TimeSource.Monotonic.markNow()
    val result = action()
    val elapsed = timeStamp.elapsedNow().inWholeNanoseconds.toDouble() / 1_000_000_000.0
    session.log(Level.Verbose) { "Phase `$phaseName` completed in $elapsed seconds" }
    return result
}
