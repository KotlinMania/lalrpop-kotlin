package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr

/**
 * Converts the parsed [TypeRepr] (the front end's representation of the type
 * annotations a grammar author wrote in the `.lalrpop` source) into a semantic
 * [GrammarTypeKind].
 *
 * This is the single place in the codegen pipeline that has knowledge of the input
 * format. The `.lalrpop` source format spells type annotations in Rust syntax —
 * `Option<T>`, `Vec<T>`, `Box<T>`, `i32`, `&'a str`, `(T1, T2)` — because that's the
 * grammar language's syntax. We interpret that syntax once, here, into intent
 * tokens. Every code book downstream consumes [GrammarTypeKind]; nothing else looks
 * at [TypeRepr].
 *
 * If the grammar language ever grew alternative type-annotation syntax (or if we
 * supported a non-LALRPOP grammar source), we'd add a parallel interpreter that
 * targets the same [GrammarTypeKind] IR. Code books wouldn't need to change.
 */
object GrammarTypeKindInterpreter {

    fun interpret(rust: TypeRepr): GrammarTypeKind = when (rust) {
        is TypeRepr.Tuple -> interpretTuple(rust)
        is TypeRepr.Slice -> GrammarTypeKind.ZeroOrMore(interpret(rust.ty))
        is TypeRepr.Nominal -> interpretNominal(rust.data)
        is TypeRepr.Ref -> GrammarTypeKind.Erased(interpret(rust.referent))
        is TypeRepr.LifetimeRepr -> GrammarTypeKind.Unit
        is TypeRepr.Associated -> GrammarTypeKind.UserDefined(
            pathSegments = listOf(rust.typeParameter.toString(), rust.id.toString()),
            typeArgs = emptyList(),
        )
        is TypeRepr.TraitObject -> interpretNominal(rust.data)
        is TypeRepr.Fn -> GrammarTypeKind.FunctionOf(
            params = rust.parameters.map { interpret(it) },
            ret = rust.ret?.let { interpret(it) },
        )
    }

    private fun interpretTuple(t: TypeRepr.Tuple): GrammarTypeKind = when (t.types.size) {
        0 -> GrammarTypeKind.Unit
        1 -> interpret(t.types[0])
        else -> GrammarTypeKind.Tuple(t.types.map { interpret(it) })
    }

    private fun interpretNominal(n: NominalTypeRepr): GrammarTypeKind {
        val name = n.path.toString()
        val args = n.types

        return when {
            // Optional value — Rust spells this `Option<T>`.
            (name == "Option" || name == "core::option::Option") && args.size == 1 -> {
                GrammarTypeKind.Optional(interpret(args[0]))
            }

            // Zero-or-more sequence — Rust spells this `Vec<T>` (or `VecDeque<T>`,
            // which has the same intent for grammar purposes).
            (name == "Vec" || name == "alloc::vec::Vec" ||
                name == "VecDeque" || name == "std::collections::VecDeque") && args.size == 1 -> {
                GrammarTypeKind.ZeroOrMore(interpret(args[0]))
            }

            // GC-subsumed wrappers — Rust uses `Box<T>` / `Rc<T>` / `Arc<T>` for
            // ownership reasons that don't translate to managed-memory languages.
            (name == "Box" || name == "alloc::boxed::Box" ||
                name == "Rc" || name == "alloc::rc::Rc" ||
                name == "Arc" || name == "alloc::sync::Arc") && args.size == 1 -> {
                GrammarTypeKind.Erased(interpret(args[0]))
            }

            // Primitive scalars.
            name == "bool" -> GrammarTypeKind.Primitive(PrimitiveKind.BOOL)
            name == "char" -> GrammarTypeKind.Primitive(PrimitiveKind.CHAR)
            name == "str" || name == "String" || name == "alloc::string::String" ->
                GrammarTypeKind.Primitive(PrimitiveKind.STRING)
            name == "i8" -> GrammarTypeKind.Primitive(PrimitiveKind.I8)
            name == "i16" -> GrammarTypeKind.Primitive(PrimitiveKind.I16)
            name == "i32" -> GrammarTypeKind.Primitive(PrimitiveKind.I32)
            name == "i64" -> GrammarTypeKind.Primitive(PrimitiveKind.I64)
            name == "isize" -> GrammarTypeKind.Primitive(PrimitiveKind.ISIZE)
            name == "u8" -> GrammarTypeKind.Primitive(PrimitiveKind.U8)
            name == "u16" -> GrammarTypeKind.Primitive(PrimitiveKind.U16)
            name == "u32" -> GrammarTypeKind.Primitive(PrimitiveKind.U32)
            name == "u64" -> GrammarTypeKind.Primitive(PrimitiveKind.U64)
            name == "usize" -> GrammarTypeKind.Primitive(PrimitiveKind.USIZE)
            name == "f32" -> GrammarTypeKind.Primitive(PrimitiveKind.F32)
            name == "f64" -> GrammarTypeKind.Primitive(PrimitiveKind.F64)

            // User-defined nominal type — Rust spells the path with `::`. We split it
            // into segments so each target language can re-join with its own
            // separator and apply its own scope rules.
            else -> {
                val segments = name
                    .removePrefix("crate::")
                    .split("::")
                    .filter { it.isNotEmpty() }
                GrammarTypeKind.UserDefined(
                    pathSegments = segments,
                    typeArgs = args.map { interpret(it) },
                )
            }
        }
    }
}
