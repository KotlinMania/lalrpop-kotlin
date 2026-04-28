// port-lint: source grammar/freeVariables/mod.rs
package io.github.kotlinmania.lalrpop.grammar.freeVariables

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parseTree.Lifetime
import io.github.kotlinmania.lalrpop.grammar.parseTree.Path
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeBound
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeBoundParameter
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.parseTree.WhereClause as ParseTreeWhereClause
import io.github.kotlinmania.lalrpop.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause as ReprWhereClause

/**
 * Finds the set of "free variables" in something -- that is, the
 * type/lifetime parameters that appear and are not bound. For
 * example, `T: Foo<U>` would return `[T, U]`.
 *
 * The Rust source defines this via a `trait FreeVariables`. The
 * Kotlin port preserves the trait as an interface and ALSO exposes
 * the same functionality as a family of overloaded extension
 * functions (because Kotlin sealed-class members cannot retroactively
 * gain interface implementations the way the upstream free-standing `implementation`
 * blocks can on foreign types).
 */
interface FreeVariables {
    fun freeVariables(typeParameters: List<TypeParameter>): List<TypeParameter>
}

/**
 * Subtle: the free-variables code sometimes encounter ambiguous
 * names.  For example, we might see `Vec<Foo>` -- in that case, we
 * look at the list of declared type parameters to decide whether
 * `Foo` is a type parameter or just some other type name.
 */
private fun freeType(typeParameters: List<TypeParameter>, id: Atom): List<TypeParameter> {
    val tp = TypeParameter.Id(id)
    return if (typeParameters.contains(tp)) {
        listOf(tp)
    } else {
        listOf()
    }
}

/**
 * Same as above: really, the only lifetime where this is relevant is
 * `'static`, but it does not hurt to be careful.
 */
private fun freeLifetime(typeParameters: List<TypeParameter>, lt: Lifetime): List<TypeParameter> {
    val tp = TypeParameter.LifetimeTp(lt)
    return if (typeParameters.contains(tp)) {
        listOf(tp)
    } else {
        listOf()
    }
}

fun TypeRepr.freeVariables(typeParameters: List<TypeParameter>): List<TypeParameter> =
    when (this) {
        is TypeRepr.Tuple -> this.types.flatMap { t -> t.freeVariables(typeParameters) }
        is TypeRepr.Slice -> this.ty.freeVariables(typeParameters)
        is TypeRepr.Nominal -> this.data.freeVariables(typeParameters)
        is TypeRepr.TraitObject -> this.data.freeVariables(typeParameters)
        is TypeRepr.Associated -> freeType(typeParameters, this.typeParameter)
        is TypeRepr.LifetimeRepr -> freeLifetime(typeParameters, this.lifetime)
        is TypeRepr.Ref -> {
            val fromLifetime = this.lifetime?.let { listOf(TypeParameter.LifetimeTp(it)) } ?: listOf()
            fromLifetime + this.referent.freeVariables(typeParameters)
        }
        is TypeRepr.Fn -> {
            val pathVars = this.path.freeVariables(typeParameters)
            val paramVars = this.parameters.flatMap { param -> param.freeVariables(typeParameters) }
            val retVars = this.ret?.freeVariables(typeParameters) ?: listOf()
            (pathVars + paramVars + retVars).filter { tp -> !this.forall.contains(tp) }
        }
    }

fun ReprWhereClause.freeVariables(typeParameters: List<TypeParameter>): List<TypeParameter> =
    when (this) {
        is ReprWhereClause.Forall ->
            this.clause.freeVariables(typeParameters)
                .filter { tp -> !this.binder.contains(tp) }

        is ReprWhereClause.Bound ->
            this.subject.freeVariables(typeParameters) +
                this.bound.freeVariables(typeParameters) { t, tps -> t.freeVariables(tps) }
    }

fun Path.freeVariables(typeParameters: List<TypeParameter>): List<TypeParameter> {
    // A path like `foo::Bar` is considered no free variables; a
    // single identifier like `T` is a free variable `T`. Note
    // that we cannot distinguish type parameters from random names
    // like `String`.
    val id = this.asId()
    return if (id != null) {
        freeType(typeParameters, id)
    } else {
        listOf()
    }
}

fun NominalTypeRepr.freeVariables(typeParameters: List<TypeParameter>): List<TypeParameter> {
    val path = this.path
    val types = this.types
    return path.freeVariables(typeParameters) +
        types.flatMap { t -> t.freeVariables(typeParameters) }
}

fun <T> ParseTreeWhereClause<T>.freeVariables(
    typeParameters: List<TypeParameter>,
    freeVars: (T, List<TypeParameter>) -> List<TypeParameter>,
): List<TypeParameter> =
    when (this) {
        is ParseTreeWhereClause.LifetimeClause<T> ->
            (sequenceOf(TypeParameter.LifetimeTp(this.lifetime)) +
                this.bounds.asSequence().map { l -> TypeParameter.LifetimeTp(l) })
                .toList()

        is ParseTreeWhereClause.Type<T> -> {
            val tyVars = freeVars(this.ty, typeParameters)
            val boundVars = this.bounds.flatMap { b -> b.freeVariables(typeParameters, freeVars) }
            (tyVars + boundVars).filter { tp -> !this.forall.contains(tp) }
        }
    }

fun <T> TypeBoundParameter<T>.freeVariables(
    typeParameters: List<TypeParameter>,
    freeVars: (T, List<TypeParameter>) -> List<TypeParameter>,
): List<TypeParameter> =
    when (this) {
        is TypeBoundParameter.LifetimeParam<T> -> freeLifetime(typeParameters, this.lifetime)
        is TypeBoundParameter.TypeParameterParam<T> -> freeVars(this.ty, typeParameters)
        is TypeBoundParameter.Associated<T> -> listOf()
    }

fun <T> TypeBound<T>.freeVariables(
    typeParameters: List<TypeParameter>,
    freeVars: (T, List<TypeParameter>) -> List<TypeParameter>,
): List<TypeParameter> =
    when (this) {
        is TypeBound.LifetimeBound<T> -> freeLifetime(typeParameters, this.lifetime)
        is TypeBound.Fn<T> -> {
            val paramVars = this.parameters.flatMap { p -> freeVars(p, typeParameters) }
            val retVars = this.ret?.let { freeVars(it, typeParameters) } ?: listOf()
            (paramVars + retVars).filter { tp -> !this.forall.contains(tp) }
        }
        is TypeBound.Trait<T> ->
            this.parameters
                .flatMap { p -> p.freeVariables(typeParameters, freeVars) }
                .filter { tp -> !this.forall.contains(tp) }
    }
