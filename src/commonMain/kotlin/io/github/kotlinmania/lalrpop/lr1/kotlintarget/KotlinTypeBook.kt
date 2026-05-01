package io.github.kotlinmania.lalrpop.lr1.kotlintarget

/**
 * The Kotlin idiom code book: how each [GrammarTypeKind] is rendered as a Kotlin type
 * expression.
 *
 * Each [GrammarTypeKind] case has its own rendering rule chosen for *what reads
 * idiomatically in Kotlin*, not for "what does the Rust type look like." When Kotlin's
 * type system can't natively express a recipe (the canonical case is two nested
 * `Optional`s, which would collapse to a single `T?`), the code book records a wrapper
 * requirement so the generated source can declare the wrapper sealed class alongside.
 *
 * Adding a new target language is a matter of writing a parallel code book — for
 * example a `RustTypeBook` would render `Optional` as `Option<T>` and `ZeroOrMore` as
 * `Vec<T>`, with no wrappers required because Rust's type system handles the
 * recipes natively. The interpreter and the IR don't change.
 */
class KotlinTypeBook {

    /**
     * The wrappers this book has requested across [render] calls. Caller emits each
     * one once at the top of the generated file, then references them by name.
     */
    private val _wrappersNeeded: MutableSet<KotlinWrapper> = mutableSetOf()

    val wrappersNeeded: Set<KotlinWrapper> get() = _wrappersNeeded

    /**
     * Render a [GrammarTypeKind] as a Kotlin type expression. May add to
     * [wrappersNeeded] if the rendering depends on a wrapper sealed class the
     * generated file must also declare.
     */
    fun render(kind: GrammarTypeKind): RenderedKotlinType = when (kind) {
        is GrammarTypeKind.Optional -> renderOptional(kind)
        is GrammarTypeKind.ZeroOrMore -> renderZeroOrMore(kind)
        is GrammarTypeKind.Tuple -> renderTuple(kind)
        is GrammarTypeKind.Primitive -> renderPrimitive(kind.kind)
        is GrammarTypeKind.UserDefined -> renderUserDefined(kind)
        is GrammarTypeKind.FunctionOf -> renderFunction(kind)
        is GrammarTypeKind.Erased -> render(kind.inner)
        GrammarTypeKind.Unit -> RenderedKotlinType("Unit")
    }

    // -------- per-kind renderers --------

    /**
     * `Optional<T>` renders as `T?` when T is something Kotlin can make nullable.
     * When T itself is already nullable (a nested `Optional<Optional<U>>` — Rust's
     * `Option<Option<U>>`), Kotlin's nullable types collapse and lose the
     * three-state distinction. The book emits a [KotlinWrapper.NULLABLE_OPTION]
     * sealed class instead so the three states (`Absent` / `Empty` / `Present`)
     * survive at the type level.
     */
    private fun renderOptional(kind: GrammarTypeKind.Optional): RenderedKotlinType {
        val inner = render(kind.inner)
        return if (inner.isAlreadyNullable) {
            _wrappersNeeded.add(KotlinWrapper.NULLABLE_OPTION)
            val unwrapped = inner.expression.removeSuffix("?")
            RenderedKotlinType("NullableOption<$unwrapped>")
        } else {
            RenderedKotlinType("${inner.expression}?", isAlreadyNullable = true)
        }
    }

    /**
     * `ZeroOrMore<T>` renders as `MutableList<T>`. Kotlin's `MutableList` is the
     * idiomatic accumulator type for sequences built up by reduction — production
     * action lambdas append into them. A read-only `List<T>` would need defensive
     * copying at every push and isn't what generated reducer code needs.
     */
    private fun renderZeroOrMore(kind: GrammarTypeKind.ZeroOrMore): RenderedKotlinType {
        val inner = render(kind.inner)
        return RenderedKotlinType("MutableList<${inner.expression}>")
    }

    /**
     * `Tuple` renders as Kotlin's `Pair<A, B>` for two parts, `Triple<A, B, C>` for
     * three, and as a per-grammar generated data class for four or more (Kotlin has
     * no built-in 4-tuple). Single-part tuples are degenerate; the interpreter
     * peels them off, but if one slips through we render the inner directly.
     */
    private fun renderTuple(kind: GrammarTypeKind.Tuple): RenderedKotlinType =
        when (kind.parts.size) {
            0 -> RenderedKotlinType("Unit")
            1 -> render(kind.parts[0])
            2 -> RenderedKotlinType(
                "Pair<${render(kind.parts[0]).expression}, ${render(kind.parts[1]).expression}>",
            )
            3 -> RenderedKotlinType(
                "Triple<${render(kind.parts[0]).expression}, " +
                    "${render(kind.parts[1]).expression}, " +
                    "${render(kind.parts[2]).expression}>",
            )
            else -> {
                // Kotlin has no built-in N-tuple for N ≥ 4. The right answer is a
                // generated data class for the specific shape; until the codegen
                // pipeline supports that, fall back to a list-of-Any with a TODO
                // surfaced in the rendered text. (Marked as a known limitation
                // rather than silently downgrading to `List<Any>`.)
                val parts = kind.parts.joinToString(", ") { render(it).expression }
                RenderedKotlinType("/* TODO: data class for ${kind.parts.size}-tuple */ List<Any /* $parts */>")
            }
        }

    /**
     * Idiomatic Kotlin scalar for each grammar primitive. `usize` and `isize` map to
     * `Int`, which is Kotlin's natural index type — a 64-bit-index grammar would
     * need a different mapping, but that's rare and the grammar would have to
     * declare it deliberately as a user-defined type.
     */
    private fun renderPrimitive(kind: PrimitiveKind): RenderedKotlinType =
        when (kind) {
            PrimitiveKind.BOOL -> RenderedKotlinType("Boolean")
            PrimitiveKind.CHAR -> RenderedKotlinType("Char")
            PrimitiveKind.STRING -> RenderedKotlinType("String")
            PrimitiveKind.I8 -> RenderedKotlinType("Byte")
            PrimitiveKind.I16 -> RenderedKotlinType("Short")
            PrimitiveKind.I32 -> RenderedKotlinType("Int")
            PrimitiveKind.I64 -> RenderedKotlinType("Long")
            PrimitiveKind.ISIZE -> RenderedKotlinType("Int")
            PrimitiveKind.U8 -> RenderedKotlinType("UByte")
            PrimitiveKind.U16 -> RenderedKotlinType("UShort")
            PrimitiveKind.U32 -> RenderedKotlinType("UInt")
            PrimitiveKind.U64 -> RenderedKotlinType("ULong")
            PrimitiveKind.USIZE -> RenderedKotlinType("Int")
            PrimitiveKind.F32 -> RenderedKotlinType("Float")
            PrimitiveKind.F64 -> RenderedKotlinType("Double")
        }

    /**
     * User-defined types pass through with their path joined by `.` (Kotlin's path
     * separator). The interpreter has already split the path into segments and
     * dropped any `crate::` prefix, so this renderer just rejoins.
     */
    private fun renderUserDefined(kind: GrammarTypeKind.UserDefined): RenderedKotlinType {
        val basePath = kind.pathSegments.joinToString(".")
        val expression = if (kind.typeArgs.isEmpty()) {
            basePath
        } else {
            val args = kind.typeArgs.joinToString(", ") { render(it).expression }
            "$basePath<$args>"
        }
        return RenderedKotlinType(expression)
    }

    /** Function types render as Kotlin's `(A, B) -> R` lambda type. */
    private fun renderFunction(kind: GrammarTypeKind.FunctionOf): RenderedKotlinType {
        val params = kind.params.joinToString(", ") { render(it).expression }
        val ret = kind.ret?.let { render(it).expression } ?: "Unit"
        return RenderedKotlinType("($params) -> $ret")
    }
}

/**
 * One rendering of a [GrammarTypeKind] in Kotlin syntax. The `isAlreadyNullable` flag
 * lets a surrounding [GrammarTypeKind.Optional] detect Kotlin's collapsing-nullables
 * limitation and request the [KotlinWrapper.NULLABLE_OPTION] wrapper.
 */
data class RenderedKotlinType(
    val expression: String,
    val isAlreadyNullable: Boolean = false,
)

/**
 * Wrapper sealed classes the [KotlinTypeBook] may request alongside a generated
 * `Symbol` class because the Kotlin type system can't natively express the
 * corresponding semantic recipe.
 */
enum class KotlinWrapper {
    /**
     * Three-state wrapper for nested `Optional<Optional<T>>`. Kotlin's nullable types
     * collapse (`T??` is `T?`); the wrapper preserves the three states the recipe
     * carries: `Absent`, `Empty`, `Present(value)`.
     */
    NULLABLE_OPTION,

    /**
     * Either-style wrapper for a result with a typed (non-`Throwable`) error. Kotlin's
     * stdlib `Result<T>` requires `E : Throwable`; grammars that declare a custom
     * non-Throwable `Error` need this shape instead.
     */
    PARSE_RESULT,
}
