// port-lint: source session.rs
/**
 * Internal configuration and session-specific settings. This is similar
 * to `configuration::Configuration`, but it is not exported outside the
 * crate. Note that all fields are public and so forth for convenience.
 */
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.btree.BTreeSet

// These two, ubiquitous types are defined here so that their fields can be private
// across crate, but visible within the crate:

enum class ColorConfig {
    /** Use ANSI colors. */
    Yes,

    /** Do NOT use ANSI colors. */
    No,

    /** Use them if we detect a TTY output (default). */
    IfTty;

    companion object {
        fun default(): ColorConfig = IfTty
    }
}

/**
 * Various options to control debug output. Although this struct is
 * technically part of LALRPOP exported interface, it is not
 * considered part of the semver guarantees as end-users are not
 * expected to use it.
 */
internal data class Session(
    var log: Log,

    var forceBuild: Boolean,

    var inDir: String?,

    var outDir: String?,

    /** Emit `rerun-if-changed` directives for Cargo */
    var emitRerunDirectives: Boolean,

    /**
     * Emit comments in generated code explaining the states and so
     * forth.
     */
    var emitComments: Boolean,

    /** Emit whitespace in the generated code to improve readability. */
    var emitWhitespace: Boolean,

    /** Emit report file about generated code */
    var emitReport: Boolean,

    var colorConfig: ColorConfig,

    /**
     * Stop after you find `maxErrors` errors. If this value is 0,
     * report *all* errors. Note that we MAY always report more than
     * this value if we so choose.
     */
    var maxErrors: Int,

    /**
     * Limit of depth to discover macros needing resolution.  Ensures that compilation terminates
     * in a finite number of steps.
     */
    var macroRecursionLimit: Int,

    // Styles to use when formatting error reports
    /** Applied to the heading in a message. */
    var heading: Style,

    /** Applied to symbols in an ambiguity report (where there is no cursor) */
    var ambigSymbols: Style,

    /** Applied to symbols before the cursor in a local ambiguity report */
    var observedSymbols: Style,

    /**
     * Applied to symbols at the cursor in a local ambiguity report,
     * if it is a non-terminal
     */
    var cursorSymbol: Style,

    /** Applied to symbols after the cursor in a local ambiguity report */
    var unobservedSymbols: Style,

    /** Applied to terminal symbols, in addition to the above styles */
    var terminalSymbol: Style,

    /** Applied to nonterminal symbols, in addition to the above styles */
    var nonterminalSymbol: Style,

    /** Style to use when printing "Hint:" */
    var hintText: Style,

    /** Unit testing (lalrpop-test) configuration */
    var unitTest: Boolean,

    /** Features used for conditional compilation */
    var features: BTreeSet<String>?,
) {
    companion object {
        fun new(): Session = Session(
            log = Log(Level.Informative),
            inDir = null,
            outDir = null,
            forceBuild = false,
            emitRerunDirectives = false,
            emitComments = false,
            emitWhitespace = true,
            emitReport = false,
            colorConfig = ColorConfig.default(),
            maxErrors = 1,
            macroRecursionLimit = 200,
            heading = Style.FG_WHITE.with(Style.BOLD),
            ambigSymbols = Style.FG_WHITE,
            observedSymbols = Style.FG_BRIGHT_GREEN,
            cursorSymbol = Style.FG_BRIGHT_WHITE,
            unobservedSymbols = Style.FG_BRIGHT_RED,
            terminalSymbol = Style.BOLD,
            nonterminalSymbol = Style.DEFAULT,
            hintText = Style.FG_BRIGHT_MAGENTA.with(Style.BOLD),
            unitTest = false,
            features = null,
        )

        /** A session suitable for use in testing. */
        fun test(): Session = Session(
            log = Log(Level.Debug),
            inDir = null,
            outDir = null,
            forceBuild = false,
            emitRerunDirectives = false,
            emitComments = false,
            emitWhitespace = true,
            emitReport = false,
            colorConfig = ColorConfig.IfTty,
            maxErrors = 1,
            macroRecursionLimit = 200,
            heading = Style.new(),
            ambigSymbols = Style.new(),
            observedSymbols = Style.new(),
            cursorSymbol = Style.new(),
            unobservedSymbols = Style.new(),
            terminalSymbol = Style.new(),
            nonterminalSymbol = Style.new(),
            hintText = Style.new(),
            unitTest = true,
            features = null,
        )

        fun default(): Session = new()
    }

    /**
     * Indicates whether we should stop after `actualErrors` number
     * of errors have been reported.
     */
    fun stopAfter(actualErrors: Int): Boolean =
        maxErrors != 0 && actualErrors >= maxErrors

    fun log(level: Level, message: () -> String) {
        log.log(level, message)
    }

    fun emitRerunDirective(path: String) {
        if (emitRerunDirectives) {
            val display: String? = path
            if (display != null) {
                println("cargo:rerun-if-changed=$display")
            } else {
                println(
                    "cargo:warning=LALRPOP is unable to inform Cargo that $path is a dependency because its filename cannot be represented in UTF-8. This is probably because it contains an unpaired surrogate character on Windows. As a result, your build script will not be rerun when it changes."
                )
            }
        }
    }
}
