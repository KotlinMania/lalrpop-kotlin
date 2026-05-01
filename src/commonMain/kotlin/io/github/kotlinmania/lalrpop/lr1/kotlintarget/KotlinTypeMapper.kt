package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr

/**
 * Maps a Rust [TypeRepr] (the front end's parsed representation of a Rust type as
 * written in a `.lalrpop` source) to a Kotlin type expression.
 *
 * Returns a [MappedType] carrying the Kotlin type string plus a set of *wrapper
 * requirements* — sealed classes the emitter must declare alongside the Symbol class
 * because the Kotlin type system can't natively express the corresponding Rust shape
 * (the canonical case is `Option<Option<T>>`, which collapses to `T?` in Kotlin and
 * needs a typed wrapper to preserve the three-state value).
 *
 * The mapper does not ship every possible mapping the Rust type system supports — it
 * covers the patterns that actually appear in the LALRPOP grammars we generate parsers
 * for, plus the core stdlib types every grammar uses (`Option`, `Vec`, `Box`, `String`,
 * `HashMap`, the integer primitives, tuples, slices, references). Unrecognized
 * nominal types pass through verbatim — the caller's grammar can declare its own AST
 * types and our emitter just refers to them by their original Kotlin name.
 */
class KotlinTypeMapper {

    /**
     * The full set of wrappers requested across multiple [map] calls. The emitter reads
     * this after walking every grammar type and emits the corresponding wrapper
     * declarations once at the top of the generated parser file.
     */
    private val _wrappersNeeded: MutableSet<KotlinWrapper> = mutableSetOf()

    val wrappersNeeded: Set<KotlinWrapper> get() = _wrappersNeeded

    /** Translate a single Rust [TypeRepr] to its Kotlin equivalent. */
    fun map(rust: TypeRepr): MappedType = when (rust) {
        is TypeRepr.Tuple -> mapTuple(rust)
        is TypeRepr.Slice -> {
            val inner = map(rust.ty)
            MappedType("List<${inner.kotlinType}>")
        }
        is TypeRepr.Nominal -> mapNominal(rust.data)
        is TypeRepr.Ref -> map(rust.referent) // Kotlin GC owns; references erase.
        is TypeRepr.LifetimeRepr -> MappedType("Unit") // pure-marker lifetimes erase.
        is TypeRepr.Associated -> MappedType("${rust.typeParameter}.${rust.id}")
        is TypeRepr.TraitObject -> MappedType(rust.data.path.toString())
        is TypeRepr.Fn -> mapFn(rust)
    }

    private fun mapTuple(t: TypeRepr.Tuple): MappedType = when (t.types.size) {
        0 -> MappedType("Unit")
        1 -> map(t.types[0])
        2 -> {
            val a = map(t.types[0])
            val b = map(t.types[1])
            MappedType("Pair<${a.kotlinType}, ${b.kotlinType}>")
        }
        3 -> {
            val a = map(t.types[0])
            val b = map(t.types[1])
            val c = map(t.types[2])
            MappedType("Triple<${a.kotlinType}, ${b.kotlinType}, ${c.kotlinType}>")
        }
        else -> {
            // Kotlin has no built-in 4+ tuple. The Rust LALRPOP grammars we target
            // generally don't produce wide tuples on the symbol stack, but if they
            // did the right answer would be a generated data class. For now, render
            // as `List<Any>` and flag for a future review — surfacing this case as
            // a runtime stdlib type is honest about the loss of fidelity.
            MappedType("List<Any>")
        }
    }

    private fun mapNominal(n: NominalTypeRepr): MappedType {
        val name = n.path.toString()
        val args = n.types

        return when {
            // Option<T>
            (name == "Option" || name == "core::option::Option") && args.size == 1 -> {
                val inner = map(args[0])
                if (inner.isAlreadyNullable) {
                    // Option<Option<T>> — Kotlin would collapse to T?. Emit the
                    // wrapper sealed class so the three-state value is preserved.
                    val unwrapped = inner.kotlinType.removeSuffix("?")
                    _wrappersNeeded.add(KotlinWrapper.NULLABLE_OPTION)
                    MappedType("NullableOption<$unwrapped>")
                } else {
                    MappedType("${inner.kotlinType}?", isAlreadyNullable = true)
                }
            }

            // Vec<T> / VecDeque<T> — flat list. Use MutableList because reducer code
            // routinely appends; the upstream Rust signatures all use Vec, never &[T].
            (name == "Vec" || name == "alloc::vec::Vec" ||
                name == "VecDeque" || name == "std::collections::VecDeque") && args.size == 1 -> {
                val inner = map(args[0])
                MappedType("MutableList<${inner.kotlinType}>")
            }

            // Box<T> — heap allocation in Rust, plain reference in Kotlin (GC owns).
            (name == "Box" || name == "alloc::boxed::Box" ||
                name == "Rc" || name == "Arc") && args.size == 1 -> {
                map(args[0])
            }

            // HashMap<K, V> / BTreeMap<K, V>
            (name == "HashMap" || name == "std::collections::HashMap") && args.size == 2 -> {
                val k = map(args[0])
                val v = map(args[1])
                MappedType("MutableMap<${k.kotlinType}, ${v.kotlinType}>")
            }
            (name == "BTreeMap" || name == "std::collections::BTreeMap") && args.size == 2 -> {
                val k = map(args[0])
                val v = map(args[1])
                MappedType("MutableMap<${k.kotlinType}, ${v.kotlinType}>")
            }

            // HashSet<T> / BTreeSet<T>
            (name == "HashSet" || name == "std::collections::HashSet" ||
                name == "BTreeSet" || name == "std::collections::BTreeSet") && args.size == 1 -> {
                val inner = map(args[0])
                MappedType("MutableSet<${inner.kotlinType}>")
            }

            // Result<T, E> — Kotlin's stdlib Result only takes Throwable errors. Many
            // LALRPOP grammars use Result with a custom E, which doesn't fit. Emit as
            // a typed Either-style wrapper. Pulled in only when actually needed.
            (name == "Result" || name == "core::result::Result") && args.size == 2 -> {
                val ok = map(args[0])
                val err = map(args[1])
                _wrappersNeeded.add(KotlinWrapper.PARSE_RESULT)
                MappedType("ParseEither<${ok.kotlinType}, ${err.kotlinType}>")
            }

            // Integer primitives.
            name == "usize" || name == "isize" -> MappedType("Int")
            name == "u8" -> MappedType("UByte")
            name == "u16" -> MappedType("UShort")
            name == "u32" -> MappedType("UInt")
            name == "u64" -> MappedType("ULong")
            name == "i8" -> MappedType("Byte")
            name == "i16" -> MappedType("Short")
            name == "i32" -> MappedType("Int")
            name == "i64" -> MappedType("Long")
            name == "f32" -> MappedType("Float")
            name == "f64" -> MappedType("Double")
            name == "bool" -> MappedType("Boolean")
            name == "char" -> MappedType("Char")

            // Strings — `String`, `&str`, and `str` all become Kotlin String.
            name == "String" || name == "alloc::string::String" || name == "str" ->
                MappedType("String")

            // Default: a user-defined nominal type. The grammar author wrote a Rust
            // type name; the emitter assumes a corresponding Kotlin type exists and
            // refers to it verbatim. Generic args are recursively mapped.
            args.isEmpty() -> MappedType(name)
            else -> {
                val mappedArgs = args.joinToString(", ") { map(it).kotlinType }
                MappedType("$name<$mappedArgs>")
            }
        }
    }

    private fun mapFn(fn: TypeRepr.Fn): MappedType {
        val params = fn.parameters.joinToString(", ") { map(it).kotlinType }
        val ret = fn.ret?.let { map(it).kotlinType } ?: "Unit"
        return MappedType("($params) -> $ret")
    }
}

/**
 * Result of mapping a single Rust type. Carries the Kotlin type expression and a flag
 * tracking whether the mapped type is already nullable, so a surrounding `Option<...>`
 * can detect the Kotlin-collapsing-nullables case and request a wrapper.
 */
data class MappedType(
    val kotlinType: String,
    val isAlreadyNullable: Boolean = false,
)

/**
 * One of the standard wrapper sealed classes the emitter may need to declare alongside
 * a generated parser, because the Kotlin type system cannot natively express the
 * corresponding Rust shape.
 */
enum class KotlinWrapper {
    /**
     * Carries the three states of a Rust `Option<Option<T>>` that Kotlin's nullable
     * types collapse: `Absent` / `Empty` / `Present`. See `NullableOption` in the
     * runtime package or the regenerated `GrammarSymbol.kt`.
     */
    NULLABLE_OPTION,

    /**
     * Carries a `Result<T, E>` with a non-`Throwable` error type. Kotlin's stdlib
     * `Result` requires `E : Throwable`, so a typed-error variant uses this Either
     * shape instead.
     */
    PARSE_RESULT,
}
