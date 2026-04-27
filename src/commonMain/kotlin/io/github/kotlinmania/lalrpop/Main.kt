// port-lint: source src/main.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.api.Configuration

/**
 * Direct port of upstream `const VERSION: &str = env("CARGO_PKG_VERSION");`.
 *
 * the upstream `env!` macro substitutes `CARGO_PKG_VERSION` at compile time from
 * Cargo.toml. Kotlin has no equivalent of cargo metadata at compile time,
 * so we mirror the upstream value used at the time of porting. Bump in
 * lockstep with the Cargo.toml version when re-syncing from upstream.
 */
const val VERSION: String = "0.22.2"

/**
 * Direct port of upstream `const USAGE: &str = "..."`.
 */
const val USAGE: String = "" +
    "Usage: lalrpop [options] <inputs>...\n" +
    "       lalrpop --help\n" +
    "       lalrpop (-V | --version)\n" +
    "\n" +
    "Options:\n" +
    "    -h, --help           Print help.\n" +
    "    -V, --version        Print version.\n" +
    "    -l, --level LEVEL    Set the debug level. (Default: info)\n" +
    "                         Valid values: quiet, info, verbose, debug.\n" +
    "    -o, --out-dir DIR    Sets the directory in which to output the .rs file(s).\n" +
    "    --features FEATURES  Comma separated list of features for conditional compilation.\n" +
    "    -f, --force          Force execution, even if the .lalrpop file is older than the .rs file.\n" +
    "    -c, --color          Force colorful output, even if this is not a TTY.\n" +
    "    --no-whitespace      Removes redundant whitespace from the generated file. (Default: false)\n" +
    "    --comments           Enable comments in the generated code.\n" +
    "    --report             Generate report files."

/**
 * Direct port of upstream `(derive(Debug)) struct Args { ... }`.
 *
 * the upstream `OsString` (`argInputs`) is modelled here as `String`: Kotlin
 * `Array<String>` (the `main` argv) is already decoded by the platform,
 * matching what `Arguments::fromEnv`/`fromVec` produces.
 *
 * `flagOutDir` (Rust `Option<PathBuf>`) → `String?` since Kotlin lacks a
 * dedicated `PathBuf`; conversion to a filesystem path happens at the
 * `Configuration::setOutDir` boundary.
 */
internal data class Args(
    val argInputs: List<String>,
    val flagOutDir: String?,
    val flagFeatures: String?,
    val flagLevel: LevelFlag?,
    val flagHelp: Boolean,
    val flagForce: Boolean,
    val flagColor: Boolean,
    val flagComments: Boolean,
    val flagNoWhitespace: Boolean,
    val flagReport: Boolean,
    val flagVersion: Boolean,
)

/**
 * Direct port of upstream `(derive(...)) enum LevelFlag { Quiet, Info, Verbose, Debug }`.
 */
internal enum class LevelFlag {
    Quiet,
    Info,
    Verbose,
    Debug;

    companion object {
        /**
         * Direct port of upstream `implementation FromStr for LevelFlag { function fromStr(...) }`.
         * Returns `Result<LevelFlag>` mirroring the upstream `Result<LevelFlag, String>`.
         */
        fun fromStr(s: String): Result<LevelFlag> = when (s) {
            "quiet" -> Result.success(Quiet)
            "info" -> Result.success(Info)
            "verbose" -> Result.success(Verbose)
            "debug" -> Result.success(Debug)
            else -> Result.failure(IllegalArgumentException("Unknown level: $s"))
        }
    }
}

/**
 * Tiny hand-rolled translation of the upstream `picoArgs::Arguments` covering
 * exactly the surface `parseArgs` uses: `optValueFromFn`,
 * `optValueFromStr`, `contains`, and `finish`. Upstream `picoArgs`
 * is not a project dependency and the AGENTS.md "no new deps" rule
 * forbids adding one; this is the minimum faithful equivalent.
 *
 * `contains` and `optValueFrom*` consume matching tokens (and any
 * required option-argument) from the internal buffer; `finish` returns
 * the remaining positional inputs, matching picoArgs contract.
 */
internal class Arguments(args: List<String>) {
    private val args: MutableList<String> = args.toMutableList()

    fun contains(keys: Array<String>): Boolean {
        var found = false
        val it = args.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (keys.any { it == a }) {
                it.remove()
                found = true
            }
        }
        return found
    }

    fun contains(key: String): Boolean = contains(arrayOf(key))

    fun <T> optValueFromFn(keys: Array<String>, parser: (String) -> Result<T>): Result<T?> {
        val idx = args.indexOfFirst { keys.any { k -> it == k } }
        if (idx < 0) return Result.success(null)
        if (idx + 1 >= args.size) {
            return Result.failure(IllegalArgumentException("missing value for ${args[idx]}"))
        }
        val value = args[idx + 1]
        args.removeAt(idx + 1)
        args.removeAt(idx)
        return parser(value).map { it as T? }
    }

    fun optValueFromStr(key: String): Result<String?> =
        optValueFromFn(arrayOf(key)) { Result.success(it) }

    fun finish(): List<String> = args.toList()

    companion object {
        fun fromEnv(args: Array<String>): Arguments = Arguments(args.toList())
        fun fromVec(args: List<String>): Arguments = Arguments(args)
    }
}

/**
 * Direct port of upstream `function parseArgs(mut args: Arguments) -> Result<Args, picoArgs::Error>`.
 * Field-evaluation order matches the Rust struct literal.
 */
internal fun parseArgs(args: Arguments): Result<Args> {
    val flagOutDir = args.optValueFromFn(arrayOf("-o", "--out-dir")) { Result.success(it) }
        .getOrElse { return Result.failure(it) }
    val flagFeatures = args.optValueFromStr("--features")
        .getOrElse { return Result.failure(it) }
    val flagLevel = args.optValueFromFn(arrayOf("-l", "--level")) { LevelFlag.fromStr(it) }
        .getOrElse { return Result.failure(it) }
    val flagHelp = args.contains(arrayOf("-h", "--help"))
    val flagForce = args.contains(arrayOf("-f", "--force"))
    val flagColor = args.contains(arrayOf("-c", "--color"))
    val flagComments = args.contains("--comments")
    val flagNoWhitespace = args.contains("--no-whitespace")
    val flagReport = args.contains("--report")
    val flagVersion = args.contains(arrayOf("-V", "--version"))
    val argInputs = args.finish()
    return Result.success(
        Args(
            argInputs = argInputs,
            flagOutDir = flagOutDir,
            flagFeatures = flagFeatures,
            flagLevel = flagLevel,
            flagHelp = flagHelp,
            flagForce = flagForce,
            flagColor = flagColor,
            flagComments = flagComments,
            flagNoWhitespace = flagNoWhitespace,
            flagReport = flagReport,
            flagVersion = flagVersion,
        )
    )
}

/**
 * Direct port of upstream `function main() -> Result<(), Box<dyn std::error::Error>>`.
 *
 * Returns the exit code instead of relying on `process::exit`; per-target
 * `main(args: Array<String>)` thunks call this and feed the result into
 * `kotlin.system.exitProcess`. This split is the same shape Atom.kt /
 * FileText.kt import for native-specific glue: keep the logic in commonMain,
 * keep the platform plumbing per-target.
 */
fun main(args: Array<String>): Int {
    val parsed = parseArgs(Arguments.fromEnv(args))
    val parsedArgs = parsed.getOrElse { err ->
        println(err.message ?: err.toString())
        return 1
    }

    if (parsedArgs.flagHelp) {
        println(USAGE)
        return 0
    }

    if (parsedArgs.flagVersion) {
        println(VERSION)
        return 0
    }

    val config = Configuration.new()

    when (parsedArgs.flagLevel ?: LevelFlag.Info) {
        LevelFlag.Quiet -> config.logQuiet()
        LevelFlag.Info -> config.logInfo()
        LevelFlag.Verbose -> config.logVerbose()
        LevelFlag.Debug -> config.logDebug()
    }

    if (parsedArgs.flagForce) {
        config.forceBuild(true)
    }

    if (parsedArgs.flagColor) {
        config.alwaysUseColors()
    }

    if (parsedArgs.flagComments) {
        config.emitComments(true)
    }

    if (parsedArgs.flagNoWhitespace) {
        config.emitWhitespace(false)
    }

    if (parsedArgs.flagReport) {
        config.emitReport(true)
    }

    if (parsedArgs.argInputs.isEmpty()) {
        println("Error: no input files specified! Try --help for help.")
        return 1
    }

    if (parsedArgs.flagOutDir != null) {
        config.setOutDir(parsedArgs.flagOutDir)
    }

    val flagFeatures = parsedArgs.flagFeatures
    if (flagFeatures != null) {
        config.setFeatures(flagFeatures.split(',').map { it })
    }

    for (arg in parsedArgs.argInputs) {
        try {
            config.processFile(arg)
        } catch (err: Throwable) {
            println("Error encountered processing `$arg`: ${err?.message ?: err}")
            return 1
        }
    }

    return 0
}

// Per-target `fun main(args: Array<String>)` thunks (calling
// `kotlin.system.exitProcess(main(args))`) belong in target-specific
// source sets — JVM, macosArm64, linuxX64, mingwX64, etc. Wiring them up
// is exit-process plumbing only; the upstream Rust logic above is fully
// translated. The lalrpop-kotlin Gradle module currently exposes the
// library API only, so the binary thunk is left to a downstream consumer
// (or a future `cli/` target) to add — this mirrors how Atom.kt /
// FileText.kt keep platform-specific concerns out of commonMain.
