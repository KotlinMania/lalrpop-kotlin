# lalrpop-kotlin — agent guidelines

Higher-altitude summary of [CLAUDE.md](./CLAUDE.md). When the two
documents conflict, CLAUDE.md is authoritative.

## What this project is

A Kotlin Multiplatform LR(1) parser generator. Standalone — there is no
upstream Rust source in this repo. The original Rust code reached output
parity 2026-04-30 and was removed; `tmp/` is empty and stays empty.

Two backends share the front end:

- **Rust-emitting backend** (`lalrpop.rust`, `lalrpop.lr1.codegen`) —
  feature-complete, byte-equivalent to upstream LALRPOP. Maintenance mode.
- **Kotlin-emitting backend** (`lalrpop.kotlintarget` and forthcoming
  `lalrpop.lr1.kotlintarget`) — currently being built. Idiomatic Kotlin
  written from scratch; does not mirror the Rust backend's class shapes.

## The gate

`./gradlew test`. The Kotlin compiler is a precondition, not the gate.

## Hard rules

- **No JVM imports** (`kotlin.jvm.*`, `java.*`, `javax.*`).
- **No `@Suppress`.** Warnings are errors — fix the cause.
- **No stubs, no `TODO()`, no `FIXME`, no operator-graded test gates.** A
  test fails when actual ≠ expected.
- **No Rust idioms in Kotlin source or KDoc.** Rust syntax appears only in
  the *output strings* of the Rust-emitting backend. Existing
  Rust-residue (port-lint headers, "Mirrors the Rust X" cross-references,
  fluent builders that copy Rust struct-init patterns) is technical debt
  to clean up, not a pattern to follow.
- **No subagents for `.kt` edits.** Search and read-only reports only.
  All source edits in the main loop.
- **No writing to `/tmp` or `tmp/`.** Anything else either lives in
  `src/` as a committed file or doesn't exist.
- **Approved deps only:** `kotlinx-coroutines-core`,
  `kotlinx-serialization-{core,json}`, `kotlinx-collections-immutable`,
  `kotlinx-datetime`, `kotlinx-atomicfu`. Anything else needs Slack
  approval and a target-coverage check.

## Trait default methods with `where` clauses → method-level Kotlin generic bounds

Rust traits routinely declare a default method whose body only typechecks
when the type parameter satisfies a stricter bound:

```rust
pub trait RangeBounds<T> {
    fn start_bound(&self) -> Bound<&T>;
    fn end_bound(&self) -> Bound<&T>;

    fn is_empty(&self) -> bool
    where T: PartialOrd,
    { /* default body uses < */ }
}
```

The trait stays unconstrained; the *method* picks up the bound via its
own `where` clause. Kotlin has no per-method `where` on an interface
member. Three obvious mappings fail:

1. **Tighten the interface to `<T : Comparable<T>>`.** Breaks every
   caller that holds the unbounded interface type.
2. **Make the method abstract on the interface.** Forces every concrete
   impl to invent a body and pile on `override` boilerplate, even when
   the Rust counterpart inherits the default unchanged.
3. **Runtime cast helper** — `if (left is Comparable<*> ...) ... else throw IllegalStateException(...)`.
   Compile-time bounds become runtime crashes; the cheat detector flags
   this and zeros the file's score.

### The faithful pattern

Translate the default to a Kotlin **extension function whose own type
parameter carries the bound**:

```kotlin
interface RangeBounds<T> {
    fun startBound(): Bound<T>
    fun endBound(): Bound<T>
}

fun <T : Comparable<T>> RangeBounds<T>.isEmpty(): Boolean { /* default body */ }
```

Concrete impls that want to specialise the default supply a same-named
**member function**. Kotlin resolves `range.isEmpty()` to the member
when the static receiver type is the concrete class and to the
extension when it is the interface — exactly mirroring Rust's
"default method, per-impl override". No `override` keyword on the
member; there is nothing on the interface to override.

Recipe:

1. Interface keeps only the methods declared without where-clauses.
2. Each default-method-with-where-clause becomes a Kotlin extension
   whose own type-parameter bound mirrors the where-clause.
3. Concrete subtypes specialise by declaring a same-named member.
4. Callers holding the unbounded interface type cannot invoke the
   comparison-using methods — correct, Rust would reject the same
   call without the bound.

### Pair with the dual-overload pattern when both paths are needed

When a function has to work in both the comparator-aware and natural-order
paths, expose two overloads — the unbounded one takes the comparator
explicitly, the bounded one is sugar:

```kotlin
internal fun <Q> Tree.search(key: Q, compare: (Stored, Q) -> Int): Hit { /* heavy */ }

internal fun <Q : Comparable<Q>> Tree.search(key: Q): Hit
    where Stored : Comparable<Q> =
    search(key) { stored, query -> stored.compareTo(query) }
```

Heavy lifting in the comparator overload; natural-order overload is a
one-line delegation. The canonical implementation lives in
[`btree-kotlin`](../btree-kotlin/) `Search.kt::searchTree` /
`searchNode` / `findLowerBoundEdge` / `findUpperBoundEdge` and
`Navigate.kt::searchTreeForBifurcation` / `lowerBound` / `upperBound`.

### Why this is faithful, not engineering

- Interface mirrors Rust's trait declaration shape exactly.
- Extension's bound mirrors Rust's `where` clause exactly.
- Concrete-class members shadow the extension exactly the way Rust
  inherent-impl methods override a trait default.
- "Unbounded callers can't use these methods" mirrors Rust's
  compile-time rejection without the bound.
- No runtime casts, no `IllegalStateException`, no `is Comparable<*>`.

### When you cannot apply this

When the bound is on a *class* type parameter (e.g. `impl<K: Ord> Map<K, V>`),
Kotlin has no method-level analog — class type parameters bind for the
whole class. Use the `Comparator<in K>` field pattern with a
`compareKeys(a, b)` dispatch helper that prefers the supplied
comparator and falls back to a `Comparable<K>`-based path. The fallback
is the design contract, not a translation hack.

## Naming

- camelCase for functions, parameters, locals.
- PascalCase for classes, data classes, sealed types, interfaces (no `I`
  prefix).
- `SCREAMING_SNAKE_CASE` only for `const val`, top-level/`object` `val`
  constants, and enum entries.
- Packages all lowercase, no camelCase.
- No underscores in identifiers anywhere else.

Visibility maps directly: `internal` for module-internal, `private` for
file-private, omitted for public.

## Blast radius

- Changes are task-scoped. Touched files named up front, or discovered
  as a direct compile/test failure caused by the primary change.
- No repo-wide scripting (`find … -exec`, global `sed`, blanket regex).
- No drive-by refactors, renames, or formatting churn.
- More than ~5 files in a single change → stop and ask.
- Commit after every file edit. Squash later if you want a logical unit.

## Backend architecture (one paragraph each)

**Rust-emitting backend.** `lalrpop.rust.RustWrite` formats Rust source.
`lalrpop.lr1.codegen.{Ascent,ParseTable,Base}` walks the LR(1) state
machine and emits a Rust parser through `RustWrite`. The Rust syntax in
this package is intentional — its output strings are Rust code.

**Kotlin-emitting backend.** `lalrpop.kotlintarget.IndentedWriter` is the
emit primitive: a small `block { … }` / `indented { … }` DSL with no
Rust DNA. Codegen subclasses live under `lalrpop.lr1.kotlintarget` and
should be split so no single file exceeds ~500–800 lines. Phase-2
constraint: written from scratch as idiomatic Kotlin. If a piece looks
like the Rust backend with Kotlin syntax in the output strings, it is
wrong.

## Build / test commands

```bash
./gradlew test                   # all targets — the gate
./gradlew macosArm64Test         # specific platform
./gradlew linuxX64Test
./gradlew jsNodeTest
./gradlew wasmJsNodeTest
```

`./gradlew jvmTest` is **not** valid — there is no JVM target.

## Cross-project coordination

`*-kotlin` repos under `kotlinmania/` import each other as Maven
artifacts. Bumping a version in this repo cascades; raise on Slack before
bumping if other consumers pin the current version.

## Final report

When a run finishes, post a status update via Slack:

- Files touched.
- Tests added and passing.
- Blockers needing a human decision.

## Commit messages

No AI attribution, no `Co-Authored-By`, no emoji, no robot references.
Clear and specific about what changed and why.
