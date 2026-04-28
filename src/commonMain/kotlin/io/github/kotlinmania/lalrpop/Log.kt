// port-lint: source log.rs
package io.github.kotlinmania.lalrpop

class Log(var level: Level) {
    fun setLevel(level: Level) {
        this.level = level
    }

    fun log(level: Level, message: () -> String) {
        if (this.level >= level) {
            println(message())
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

// NOTE: Rust macros `log!`, `debug!`, and `profile!` are intentionally omitted —
// macros are a Rust-specific construct. Call `session.log(Level.X) { ... }`
// directly. A `profile` helper belongs with Session once that ported.
