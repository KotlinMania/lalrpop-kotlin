package io.github.kotlinmania.lalrpop.lr1.kotlintarget

/**
 * Semantic-token IR for the type a grammar role produces.
 *
 * Every stack-element type a generated parser pushes corresponds to one [GrammarTypeKind].
 * The IR describes *what the role does* (the recipe), not how any particular target
 * language encodes it. Each subclass carries pseudocode-style documentation of its
 * intent so a code book for a new target language can be written by reading this file
 * alone — no need to look at how the Rust or Kotlin emitter encodes the same role.
 *
 * The pipeline is:
 *
 * ```
 * .lalrpop source ──parse──▶ TypeRepr (Rust-flavored syntax tree)
 *                  ──interpret──▶ GrammarTypeKind (semantic intent)
 *                  ──code book──▶ Kotlin (or Rust v2, or Python, …) type expression
 * ```
 *
 * Interpretation happens once. Code books are independent, language-idiomatic
 * derivations from the same IR.
 */
sealed class GrammarTypeKind {

    /**
     * "May or may not have a value" — at most one instance present.
     *
     * Pseudocode of the role: `value: Option { match input { Empty -> None;
     * Has(v) -> Some(v) } }`.
     *
     * Targets:
     *   Rust  → `Option<T>`
     *   Kotlin → `T?`           (when T is not itself optional)
     *   Kotlin → `NullableOption<T>` (when T is itself optional — Kotlin's `T??`
     *            collapses, losing the three-state distinction; the wrapper carries it)
     */
    data class Optional(val inner: GrammarTypeKind) : GrammarTypeKind()

    /**
     * "Accumulate zero or more values into a sequence" — the LALRPOP `*` quantifier.
     *
     * Pseudocode: `values: Sequence { repeat: while input.matches(T) { append(T) } }`.
     *
     * Targets:
     *   Rust  → `Vec<T>`
     *   Kotlin → `MutableList<T>` (matches Vec's mutability — reductions append)
     */
    data class ZeroOrMore(val inner: GrammarTypeKind) : GrammarTypeKind()

    /**
     * "Bundle several pieces into one stack element" — produced by parenthesised
     * grammar groups that capture multiple symbols.
     *
     * Pseudocode: `bundle: (T1, T2, …): each part remains addressable by index`.
     *
     * Targets:
     *   Rust  → `(T1, T2, …)`
     *   Kotlin (2 parts) → `Pair<T1, T2>`
     *   Kotlin (3 parts) → `Triple<T1, T2, T3>`
     *   Kotlin (≥4 parts) → generated data class (Kotlin has no built-in 4-tuple)
     */
    data class Tuple(val parts: List<GrammarTypeKind>) : GrammarTypeKind()

    /**
     * A primitive value type — not user-defined, not collection-shaped.
     *
     * Pseudocode: `value: one of the language's built-in scalar types`.
     *
     * Targets pick the matching primitive in their own type system. Kotlin maps
     * `USize` and `ISize` to `Int` (Kotlin's natural index type); a 64-bit-index
     * grammar would need a different mapping, which is rare.
     */
    data class Primitive(val kind: PrimitiveKind) : GrammarTypeKind()

    /**
     * A user-defined nominal type referenced by the grammar's host language. The path
     * is split into segments so each target can re-join with its own separator
     * (`::` for Rust, `.` for Kotlin) and apply its own scope rules (e.g. dropping a
     * leading `crate::`).
     *
     * Pseudocode: `value: instance of the host code's named type`.
     *
     * Type arguments are recursively interpreted, so `Spanned<ExprP<AstNoPayload>>`
     * is a `UserDefined(["Spanned"], [UserDefined(["ExprP"], [UserDefined(["AstNoPayload"])])])`.
     */
    data class UserDefined(
        val pathSegments: List<String>,
        val typeArgs: List<GrammarTypeKind>,
    ) : GrammarTypeKind()

    /**
     * Function/closure type. Rare in stack-element types but appears in associated
     * types of some grammar configurations.
     *
     * Pseudocode: `value: callable taking [params] returning [ret]`.
     */
    data class FunctionOf(
        val params: List<GrammarTypeKind>,
        val ret: GrammarTypeKind?,
    ) : GrammarTypeKind()

    /**
     * The "no value" / unit type — what an action returns when it has no result.
     *
     * Pseudocode: `value: nothing meaningful, present for shape only`.
     *
     * Targets:
     *   Rust  → `()`
     *   Kotlin → `Unit`
     */
    object Unit : GrammarTypeKind()

    /**
     * Wrapper that the GC subsumes — Rust's references, lifetimes, `Box<T>`, `Rc<T>`,
     * `Arc<T>`. The interpreter peels these off because they have no Kotlin (or any
     * GC'd-language) analogue. Stored in the IR as an explicit case rather than
     * silently flattened so language code books can re-introduce a wrapper if their
     * memory model wants one (e.g. a Rust v2 emitter would).
     *
     * Pseudocode: `value: T, with a memory wrapper the GC handles for free`.
     */
    data class Erased(val inner: GrammarTypeKind) : GrammarTypeKind()
}

/**
 * The set of primitive scalar types a grammar can declare. A grammar that uses an
 * unusual primitive (Rust's `f128` or some platform-specific size) has to declare it
 * via [GrammarTypeKind.UserDefined] instead.
 */
enum class PrimitiveKind {
    BOOL, CHAR, STRING,
    I8, I16, I32, I64, ISIZE,
    U8, U16, U32, U64, USIZE,
    F32, F64,
}
