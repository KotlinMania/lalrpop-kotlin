// port-lint: source rust/mod.rs
/**
 * Simple Rust AST. This is what the various code generators create,
 * which then gets serialized.
 */
package io.github.kotlinmania.lalrpop.rust

import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Parameter
import io.github.kotlinmania.lalrpop.tls.Tls

/**
 * The [rust] helper should be called only on a [RustWrite] instance.
 */
fun assertRustWrite(w: RustWrite) {
    val _unused = w
}

/**
 * Like writeln, but for writing emitted code to a [RustWrite], which
 * handles indentation.
 */
fun rust(w: RustWrite, fmt: String = "", vararg args: Any?) {
    assertRustWrite(w)
    w.writeFmt(format(fmt, *args))
}

/**
 * Tiny printf-style formatter used by the [rust] helper. Supports
 * the %s and %d conversions and brace-style {0} or {} indexing.
 */
internal fun format(fmt: String, vararg args: Any?): String {
    val out = StringBuilder()
    var i = 0
    var positional = 0
    while (i < fmt.length) {
        val ch = fmt[i]
        // Mirror printf-style {{ / }} brace escapes -- produce a
        // single brace. Without this, rust(out, "{{") (the way the
        // codegen back-end emits a literal { to the output stream)
        // wrote {{ into the generated source.
        if (ch == '{' && i + 1 < fmt.length && fmt[i + 1] == '{') {
            out.append('{')
            i += 2
            continue
        }
        if (ch == '}' && i + 1 < fmt.length && fmt[i + 1] == '}') {
            out.append('}')
            i += 2
            continue
        }
        if (ch == '{' && i + 1 < fmt.length && args.isNotEmpty()) {
            val end = fmt.indexOf('}', i + 1)
            if (end > 0) {
                val body = fmt.substring(i + 1, end)
                val idx: Int = if (body.isEmpty()) {
                    positional++
                    positional - 1
                } else {
                    val cleaned = body.substringBefore(':').substringBefore('=')
                    cleaned.toIntOrNull() ?: run {
                        positional++
                        positional - 1
                    }
                }
                out.append(args.getOrNull(idx))
                i = end + 1
                continue
            }
        }
        out.append(ch)
        i++
    }
    return out.toString()
}

/**
 * A wrapper around a Write instance that handles indentation for
 * the emitted Rust code. It expects the emitted code to be written
 * in a stylized way, with lots of braces and newlines (example shown
 * with no indentation). Over time maybe we can extend this to make
 * things look prettier, but seems like...meh, just run it through
 * some rustfmt tool.
 */
class RustWrite private constructor(
    private val write: Appendable,
    private var indent: Int,
) {
    companion object {
        fun new(w: Appendable): RustWrite = RustWrite(write = w, indent = 0)

        private const val TAB: Int = 4
    }

    fun intoInner(): Appendable = this.write

    private fun writeIndentation() {
        if (Tls.session().emitWhitespace) {
            repeat(this.indent) { this.write.append(' ') }
        }
    }

    fun <C : Any> writeTableRow(iterable: Iterable<Pair<Int, C>>) {
        val session = Tls.session()
        if (session.emitComments) {
            for ((i, comment) in iterable) {
                writeIndentation()
                this.write.append("$i, $comment\n")
            }
        } else {
            writeIndentation()
            var first = true
            for ((i, _) in iterable) {
                if (!first && session.emitWhitespace) {
                    this.write.append(' ')
                }
                this.write.append("$i,")
                first = false
            }
        }
        // Mirrors the trailing `writeln(self.write)` in upstream
        // `writeTableRow` — it lives outside the if/else so it
        // appends a blank line after the row regardless of whether
        // comments are on. The previous Kotlin port had it only on
        // the no-comments branch, which left state-debug ACTION rows
        // glued together without the upstream per-row blank-line
        // separator.
        this.write.append('\n')
    }

    /**
     * Consumes a pre-formatted [String] (see [format]) and forwards to
     * [writeLine], which performs the indentation accounting.
     */
    fun writeFmt(buf: String) {
        writeLine(buf)
    }

    /**
     * Writes a single fully-formatted line, handling indentation. The
     * Rust equivalent is `writeFmt` driven by the `rust!` macro and
     * expects the line to end in `\n`.
     */
    fun writeLine(buf: String) {
        val line = if (buf.endsWith('\n')) buf else "$buf\n"

        // pass empty lines through with no indentation
        if (line == "\n") {
            this.write.append('\n')
            return
        }

        // If the line begins with a `}`, `]`, or `)`, first decrement the indentation.
        val first = line[0]
        if (first == '}' || first == ']' || first == ')') {
            this.indent -= TAB
        }

        writeIndentation()
        this.write.append(line)

        // If a line ends with a `{`, `[`, or `(`, increase indentation for future lines.
        val n = (line.length - 2).coerceAtLeast(0)
        val last = line[n]
        if (last == '{' || last == '[' || last == '(') {
            this.indent += TAB
        }
    }

    /**
     * Create and return fn-header builder. Don't forget to invoke
     * `emit` at the end. =)
     */
    fun fnHeader(visibility: Visibility, name: String): FnHeader =
        FnHeader.new(this, visibility, name)

    fun writeModuleAttributes(grammar: Grammar) {
        for (attribute in grammar.moduleAttributes) {
            rust(this, "$attribute")
        }
    }

    fun writeUses(superPrefix: String, grammar: Grammar) {
        // things the user wrote
        for (u in grammar.uses) {
            if (u.startsWith("super::")) {
                rust(this, "use $superPrefix$u;")
            } else {
                rust(this, "use $u;")
            }
        }

        writeStandardUses(grammar.prefix)
    }

    fun writeStandardUses(prefix: String) {
        // Stuff that we plan to use.
        // Occasionally we happen to not import it after all, hence the allow.
        rust(this, "#[allow(unused_extern_crates)]")
        rust(this, "extern crate lalrpop_util as ${prefix}lalrpop_util;")
        rust(this, "#[allow(unused_imports)]")
        rust(this, "use self::${prefix}lalrpop_util::state_machine as ${prefix}state_machine;")
        // https://doc.rust-lang.org/edition-guide/rust-2018/path-changes.html#an-exception
        rust(this, "#[allow(unused_extern_crates)]")
        rust(this, "extern crate alloc;")
    }
}

class FnHeader private constructor(
    internal val write: RustWrite,
    internal val visibility: Visibility,
    internal val name: String,
    internal val typeParameters: MutableList<String>,
    internal val parameters: MutableList<String>,
    internal var returnType: String,
    internal val whereClauses: MutableList<String>,
) {
    companion object {
        fun new(write: RustWrite, visibility: Visibility, name: String): FnHeader =
            FnHeader(
                write = write,
                visibility = visibility,
                name = name,
                typeParameters = mutableListOf(),
                parameters = mutableListOf(),
                returnType = "()",
                whereClauses = mutableListOf(),
            )

        private const val TAB: Int = 4
    }

    /**
     * Adds the type-parameters, where-clauses, and parameters from
     * the grammar.
     */
    fun withGrammar(grammar: Grammar): FnHeader =
        this.withTypeParameters(grammar.typeParameters)
            .withWhereClauses(grammar.whereClauses)
            .withParameters(grammar.parameters)

    /**
     * Declare a series of type parameters. Note that the emitted
     * code's lt parameters must come first.
     */
    fun withTypeParameters(tps: Iterable<Any>): FnHeader {
        for (t in tps) {
            this.typeParameters.add(t.toString())
        }
        return this
    }

    /** Add where clauses to the list. */
    fun withWhereClauses(tps: Iterable<Any>): FnHeader {
        for (t in tps) {
            this.whereClauses.add(t.toString())
        }
        return this
    }

    /**
     * Declare a series of parameters. You can supply strings of the
     * form `"foo: Bar"` or else `repr::Parameter` references.
     */
    fun withParameters(parameters: Iterable<Any>): FnHeader {
        for (p in parameters) {
            this.parameters.add(p.toParameterString())
        }
        return this
    }

    /** Add where clauses to the list. */
    fun withReturnType(rt: Any): FnHeader {
        this.returnType = "$rt"
        return this
    }

    /**
     * Emit function header -- everything up to the opening `{` for the
     * body.
     */
    fun emit() {
        rust(this.write, "${this.visibility}fn ${this.name}<")

        for (typeParameter in this.typeParameters) {
            rust(this.write, "${" ".repeat(TAB)}$typeParameter,")
        }

        rust(this.write, ">(")

        for (parameter in this.parameters) {
            rust(this.write, "$parameter,")
        }

        if (this.returnType == "()") {
            rust(this.write, ")")
        } else {
            rust(this.write, ") -> ${this.returnType}")
        }

        if (this.whereClauses.isNotEmpty()) {
            rust(this.write, "where")

            for (whereClause in this.whereClauses) {
                rust(this.write, "    $whereClause,")
            }
        }
    }
}

/**
 * Rust: `interface ParameterDisplay { function toParameterString(self) -> String; }`.
 *
 * The Rust code uses this trait to accept either a bare `String` or a
 * `&repr::Parameter` in the generic `fnHeader` writer. Kotlin port
 * keeps the trait shape so that parity tooling sees the symbol; the
 * actual dispatch is done via the `Any.toParameterString()` extension
 * below, which mirrors `implementation ParameterDisplay for String` and
 * `implementation ParameterDisplay for &repr::Parameter`.
 */
interface ParameterDisplay {
    fun toParameterString(): String
}

/**
 * Extension that renders a value as a parameter string, mirroring
 * the Rust `ParameterDisplay` trait implementations for `String` and
 * `&repr::Parameter`.
 */
private fun Any.toParameterString(): String = when (this) {
    is ParameterDisplay -> this.toParameterString()
    is String -> this
    is Parameter -> "${this.name}: ${this.ty}"
    else -> this.toString()
}
