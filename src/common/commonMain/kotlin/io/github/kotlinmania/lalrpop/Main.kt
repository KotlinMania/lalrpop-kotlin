// port-lint: source main.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.api.Configuration
import io.github.kotlinmania.lalrpop.api.apiEPrintln

internal const val VERSION: String = "0.23.1"

internal const val USAGE: String =
    "" +
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

internal enum class LevelFlag {
    Quiet,
    Info,
    Verbose,
    Debug,
    ;

    companion object {
        typealias Err = String

        fun fromStr(s: String): Result<LevelFlag> =
            when (s) {
                "quiet" -> Result.success(Quiet)
                "info" -> Result.success(Info)
                "verbose" -> Result.success(Verbose)
                "debug" -> Result.success(Debug)
                else -> Result.failure(IllegalArgumentException("Unknown level: $s"))
            }
    }
}

internal class Arguments(
    args: List<String>,
) {
    private val args: MutableList<String> = args.toMutableList()

    private fun optionBoundary(): Int {
        val sep = args.indexOf("--")
        return if (sep < 0) args.size else sep
    }

    fun contains(keys: Array<String>): Boolean {
        val shortChars =
            keys
                .filter { it.length == 2 && it[0] == '-' && it[1] != '-' }
                .map { it[1] }
                .toSet()
        var found = false
        var i = 0
        while (i < optionBoundary()) {
            val a = args[i]
            if (keys.any { it == a }) {
                args.removeAt(i)
                found = true
            } else if (shortChars.isNotEmpty() && a.length > 2 && a[0] == '-' && a[1] != '-') {
                val remaining = a.substring(1).filter { it !in shortChars }
                if (remaining.length < a.length - 1) {
                    found = true
                    if (remaining.isEmpty()) {
                        args.removeAt(i)
                    } else {
                        args[i] = "-$remaining"
                        i += 1
                    }
                } else {
                    i += 1
                }
            } else {
                i += 1
            }
        }
        return found
    }

    fun contains(key: String): Boolean = contains(arrayOf(key))

    fun <T> optValueFromFn(keys: Array<String>, parser: (String) -> Result<T>): Result<T?> {
        val limit = optionBoundary()
        for (idx in 0 until limit) {
            val a = args[idx]
            if (keys.any { k -> a == k }) {
                if (idx + 1 >= limit) {
                    return Result.failure(IllegalArgumentException("missing value for $a"))
                }
                val value = args[idx + 1]
                args.removeAt(idx + 1)
                args.removeAt(idx)
                return parser(value).map { it as T? }
            }
            val longInlineKey = keys.firstOrNull { k -> k.startsWith("--") && a.startsWith("$k=") }
            if (longInlineKey != null) {
                val value = a.substring(longInlineKey.length + 1)
                args.removeAt(idx)
                return parser(value).map { it as T? }
            }
            val shortInlineKey =
                keys.firstOrNull { k ->
                    k.length == 2 && k[0] == '-' && k[1] != '-' && a.length > 2 && a.startsWith(k)
                }
            if (shortInlineKey != null) {
                val value = a.substring(2)
                args.removeAt(idx)
                return parser(value).map { it as T? }
            }
        }
        return Result.success(null)
    }

    fun optValueFromStr(key: String): Result<String?> =
        optValueFromFn(arrayOf(key)) { Result.success(it) }

    fun finish(): List<String> {
        val sepIdx = args.indexOf("--")
        if (sepIdx >= 0) {
            args.removeAt(sepIdx)
        }
        return args.toList()
    }

    companion object {
        fun fromEnv(args: Array<String>): Arguments = Arguments(args.toList())

        fun fromVec(args: List<String>): Arguments = Arguments(args)
    }
}

internal fun parseArgs(args: Arguments): Result<Args> {
    val flagOutDir =
        args
            .optValueFromFn(arrayOf("-o", "--out-dir")) { Result.success(it) }
            .getOrElse { return Result.failure(it) }
    val flagFeatures =
        args
            .optValueFromStr("--features")
            .getOrElse { return Result.failure(it) }
    val flagLevel =
        args
            .optValueFromFn(arrayOf("-l", "--level")) { LevelFlag.fromStr(it) }
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
        ),
    )
}

internal fun main(args: Array<String>): Int = runMain(args)

internal fun runMain(
    args: Array<String>,
    out: (String) -> Unit = ::println,
    err: (String) -> Unit = ::apiEPrintln,
): Int {
    val parsed = parseArgs(Arguments.fromEnv(args))
    val parsedArgs =
        parsed.getOrElse { parseErr ->
            err(parseErr.message ?: parseErr.toString())
            return 1
        }

    if (parsedArgs.flagHelp) {
        out(USAGE)
        return 0
    }

    if (parsedArgs.flagVersion) {
        out(VERSION)
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
        err("Error: no input files specified! Try --help for help.")
        return 1
    }

    if (parsedArgs.flagOutDir != null) {
        config.setOutDir(parsedArgs.flagOutDir)
    }

    val flagFeatures = parsedArgs.flagFeatures
    if (flagFeatures != null) {
        config.setFeatures(flagFeatures.split(','))
    }

    for (arg in parsedArgs.argInputs) {
        try {
            config.processFile(arg)
        } catch (procErr: Throwable) {
            err("Error encountered processing `$arg`: ${procErr.message ?: procErr}")
            return 1
        }
    }

    return 0
}
