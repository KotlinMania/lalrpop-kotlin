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
