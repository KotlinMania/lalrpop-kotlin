// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.api

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

import io.github.kotlinmania.btree.BTreeSet
import io.github.kotlinmania.lalrpop.ColorConfig
import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Session

/**
 * Configure various aspects of how LALRPOP works.
 * Intended for use within a `build.rs` script.
 * To get the default configuration, use [Configuration.new].
 */
class Configuration internal constructor(
    internal var session: Session,
) {
    /**
     * Always import ANSI colors in output, even if output does not appear to be a TTY.
     */
    fun alwaysUseColors(): Configuration {
        session.colorConfig = ColorConfig.Yes
        return this
    }

    /**
     * Never import ANSI colors in output, even if output appears to be a TTY.
     */
    fun neverUseColors(): Configuration {
        session.colorConfig = ColorConfig.No
        return this
    }

    /**
     * Use ANSI colors in output if output appears to be a TTY, but
     * not otherwise. This is the default.
     */
    fun useColorsIfTty(): Configuration {
        session.colorConfig = ColorConfig.IfTty
        return this
    }

    /**
     * Specify a custom directory to search for input files.
     *
     * This directory is recursively searched for `.lalrpop` files to be
     * considered as input files.  This configuration setting also
     * impacts where output files are placed; paths are made relative
     * to the input path before being resolved relative to the output
     * path.  By default, the input directory is the current working
     * directory.
     */
    fun setInDir(dir: String): Configuration {
        session.inDir = dir
        return this
    }

    /**
     * Specify a custom directory to use when writing output files.
     *
     * By default, the output directory is the same as the input
     * directory.
     */
    fun setOutDir(dir: String): Configuration {
        session.outDir = dir
        return this
    }

    /**
     * Apply `cargo` directory location conventions.
     *
     * This sets the input directory to `src` and the output directory to
     * `$OUT_DIR`.
     */
    fun useCargoDirConventions(): Configuration {
        setInDir("src")
            .setOutDir(apiEnvVar("OUT_DIR") ?: error("OUT_DIR is not set"))
        return this
    }

    /**
     * Write output files in the same directory of the input files.
     *
     * If this option is enabled, you have to load the parser as a module:
     *
     * ```noCompile
     * mod parser; // synthesized from parser.lalrpop
     * ```
     *
     * This was the default behaviour up to version 0.15.
     */
    fun generateInSourceTree(): Configuration {
        return setInDir(".").setOutDir(".")
    }

    /**
     * If true, always convert `.lalrpop` files into `.rs` files, even if the
     * `.rs` file is newer. Default is false.
     */
    fun forceBuild(`val`: Boolean): Configuration {
        session.forceBuild = `val`
        return this
    }

    /**
     * If true, print `rerun-if-changed` directives to standard output.
     *
     * If this is set, Cargo will only rerun the build script if any of the processed
     * `.lalrpop` files are changed. This option is independent of
     * [forceBuild], although it would be usual to set [forceBuild] and
     * [emitRerunDirectives] at the same time.
     *
     * While many build scripts will want to set this to `true`, the default is
     * false, because emitting any rerun directives to Cargo will cause the
     * script to only be rerun when Cargo thinks it is needed. This could lead
     * to hard-to-find bugs if other parts of the build script do not emit
     * directives correctly, or need to be rerun unconditionally.
     */
    fun emitRerunDirectives(`val`: Boolean): Configuration {
        session.emitRerunDirectives = `val`
        return this
    }

    /**
     * If true, emit comments into the generated code.
     *
     * This makes the generated code significantly larger. Default is false.
     */
    fun emitComments(`val`: Boolean): Configuration {
        session.emitComments = `val`
        return this
    }

    /**
     * If false, shrinks the generated code by removing redundant white space.
     * Default is true.
     */
    fun emitWhitespace(`val`: Boolean): Configuration {
        session.emitWhitespace = `val`
        return this
    }

    /** If true, emit report file about generated code. */
    fun emitReport(`val`: Boolean): Configuration {
        session.emitReport = `val`
        return this
    }

    /** Minimal logs: only for errors that halt progress. */
    fun logQuiet(): Configuration {
        session.log.setLevel(Level.Taciturn)
        return this
    }

    /**
     * Informative logs: give some high-level indications of
     * progress (default).
     */
    fun logInfo(): Configuration {
        session.log.setLevel(Level.Informative)
        return this
    }

    /** Verbose logs: more than info, but still not overwhelming. */
    fun logVerbose(): Configuration {
        session.log.setLevel(Level.Verbose)
        return this
    }

    /**
     * Debug logs: better redirect this to a file. Intended for
     * debugging LALRPOP itself.
     */
    fun logDebug(): Configuration {
        session.log.setLevel(Level.Debug)
        return this
    }

    /**
     * Set the max macro recursion depth.
     *
     * As lalrpop is resolving a macro, it may discover new macros uses in the
     * macro definition to resolve.  Typically deep recursion indicates a
     * recursive macro import that is non-resolvable.  The default resolution
     * depth is 200.
     */
    fun setMacroRecursionLimit(`val`: Int): Configuration {
        session.macroRecursionLimit = `val`
        return this
    }

    /**
     * Sets the features used during compilation, disables the import of cargo features.
     * (Default: Loaded from `CARGO_FEATURE_{}` environment variables).
     */
    fun setFeatures(iterable: Iterable<String>): Configuration {
        session.features = BTreeSet.fromIterable(iterable)
        return this
    }

    /**
     * Enables "unit-testing" configuration. This is only for
     * lalrpop-test.
     */
    fun unitTest(): Configuration {
        session.unitTest = true
        return this
    }

    /**
     * Process all files according to the [setInDir] and
     * [setOutDir] configuration.
     */
    fun process() {
        val root = session.inDir ?: "."
        processDir(root)
    }

    /**
     * Process all files in the current directory, which -- unless you
     * have changed it -- is typically the root of the crate being compiled.
     */
    fun processCurrentDir() {
        processDir(apiCurrentDir())
    }

    // The user should only import setInDir() with process(), not processXxx() functions which
    // specify an input.  Check for misuse of that and return an error if inDir was set.
    // dirPath here is specifically a dir, so processFile() should call this with None
    internal fun verifyNoInDirConflict(dirPath: String?) {
        if (session.inDir != null && session.inDir != dirPath) {
            apiEPrintln("Error: \"processXxx()\" contradicts previously set inDir")
            error(
                "\"processXxx()\" functions contradict previously set inDir.  The inDir is set by either `setInDir()` or `useCargoDirConventions()`.  Either use `process()` instead, or omit `setInDir()`.  (Note: in previous versions of lalrpop, this combination could affect the parser output dir.  If you were relying on this behavior to output the parser in your source directory, you may want to use `setOutDir()` to retain that behavior.",
            )

        }
    }

    /** Process all `.lalrpop` files in `path`. */
    fun processDir(path: String) {
        val sessionCopy = session.copy()

        verifyNoInDirConflict(path)
        sessionCopy.inDir = path

        // If out dir is empty, import cargo conventions by default.
        // See https://github.com/lalrpop/lalrpop/issues/280
        if (sessionCopy.outDir == null) {
            val outDir = apiEnvVar("OUT_DIR") ?: throw IllegalStateException("missing OUT_DIR variable")
            sessionCopy.outDir = outDir
        }

        if (session.features == null) {
            // Pick up the features cargo sets for build scripts
            val collected = BTreeSet<String>()
            for ((featureVar, _) in apiEnvVars()) {
                val prefix = "CARGO_FEATURE_"
                if (featureVar.startsWith(prefix)) {
                    val feature = featureVar.substring(prefix.length)
                    collected.insert(feature.replace('_', '-').lowercase())
                }
            }
            sessionCopy.features = collected
        }

        apiBuildProcessDir(sessionCopy, path)
    }

    /** Process the given `.lalrpop` file. */
    fun processFile(path: String) {
        val sessionCopy = session.copy()
        // path is a file, so we send in None instead of path.  That will always fail
        // the check if inDir was set.
        verifyNoInDirConflict(null)
        apiBuildProcessFile(sessionCopy, path)
    }

    companion object {
        /**
         * Creates the default configuration.
         *
         * equivalent to `Configuration::default`.
         */
        fun new(): Configuration = default()

        fun default(): Configuration = Configuration(Session.default())
    }
}

/**
 * Process all files in the current directory.
 *
 * Unless you have changed it this is typically the root of the crate being compiled.
 * If your project only builds one crate and your files are in a ./src directory, you should use
 * [processSrc] instead
 *
 * Equivalent to `Configuration::new().processCurrentDir()`.
 */
fun processRoot() {
    Configuration.new().processCurrentDir()
}

/**
 * Process all files in ./src.
 *
 * In many cargo projects which build only one crate, this is the normal
 * location for source files.  If you are running lalrpop from a top level build.rs in a
 * project that builds multiple crates, you may want [processRoot] instead.
 * See [Configuration] if you would like more fine-grain control over lalrpop.
 */
fun processSrc() {
    Configuration.new().setInDir("./src").process()
}

/**
 * Deprecated in favor of [Configuration].
 *
 * Instead, consider using:
 *
 * ```kotlin
 * Configuration.new().forceBuild(true).processCurrentDir()
 * ```
 */
@Deprecated(
    message = "use `Configuration.new().forceBuild(true).processCurrentDir()` instead",
    level = DeprecationLevel.WARNING,
)
fun processRootUnconditionally() {
    Configuration.new().forceBuild(true).processCurrentDir()
}

internal expect fun apiCurrentDir(): String

internal expect fun apiEnvVar(name: String): String?

internal expect fun apiEnvVars(): Sequence<Pair<String, String>>

internal expect fun apiEPrintln(message: String)

internal expect fun apiBuildProcessDir(session: Session, path: String)

internal expect fun apiBuildProcessFile(session: Session, path: String)
