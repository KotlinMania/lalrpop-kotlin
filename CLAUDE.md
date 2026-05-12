# Claude Code Project Instructions

## Project Overview

**lalrpop-kotlin** is a Kotlin Multiplatform LR(1) parser generator. It is a
standalone Kotlin project — there is no upstream Rust source in this repo.
The original LALRPOP Rust source was used as a reference during the initial
port, hit byte-for-byte output parity on 2026-04-30, and was then removed.
The `tmp/` directory is empty and should stay empty; do not populate it.

The project ships two emission backends:

- **`io.github.kotlinmania.lalrpop.rust`** + **`io.github.kotlinmania.lalrpop.lr1.codegen`** —
  the Rust-emitting backend. Takes a `.lalrpop` grammar and produces Rust
  source code byte-equivalent to upstream LALRPOP's output. This is shipped
  for users who want to generate parsers for Rust projects.
- **`io.github.kotlinmania.lalrpop.kotlintarget`** — the Kotlin-emitting
  backend, currently being built out. Takes the same `.lalrpop` grammar and
  produces Kotlin source code. This will become the project default.

Both backends share the front end (`grammar/`, `lexer/`, `lr1/` state
machine construction, `normalize/`, etc.).

## Project Goals (the contract)

When a rule below seems to conflict with these, the goals win.

1. **Both backends pass their gate.** Rust-emit: byte-equivalence against a
   fixture corpus of pre-generated Rust output. Kotlin-emit: the emitted
   Kotlin parser parses its fixture inputs to the expected ASTs.
2. **All tests pass.** Every `@Test` in `commonTest/` runs and passes on
   every shipped target (`./gradlew test`). No skips, no `@Ignore`, no
   "TODO: re-enable later."
3. **Tooling is diagnostic.** The lint and signature tools under `tools/`
   flag drift patterns and spot-check coverage. They do not produce
   verdicts. The runtime gate is `./gradlew test`.
4. **Kotlin source looks like Kotlin source.** No carried-over Rust
   idioms in the Kotlin codebase. Rust syntax appears only in the *output*
   strings of the Rust-emitting backend, never in Kotlin source, KDoc,
   comments, or API shapes. Existing Rust-residue (port-lint headers, "Mirrors
   the Rust X" KDoc, fluent builders that mimic Rust struct-init patterns)
   is technical debt to be cleaned up over time; do not add more.
5. **No hacks.** No stubs, no `TODO()`, no `FIXME`, no `@Suppress`
   annotations, no JVM imports, no synthetic typealiases, no operator-graded
   test gates, no "fix it later" comments. Warnings are errors — fix the
   cause. If you can't, stop and ask.

## Verification

The build gate is **`./gradlew test`**.

```bash
./gradlew test                   # all targets
./gradlew macosArm64Test         # specific platform
./gradlew linuxX64Test
./gradlew jsNodeTest
./gradlew wasmJsNodeTest
```

`./gradlew jvmTest` is **not** valid — there is no JVM target. The
`jvmToolchain(21)` line in `build.gradle.kts` configures the JDK that runs
Gradle itself; it does not add a JVM target.

Compilation is a precondition for tests, not a gate. A green
`./gradlew build` proves nothing about correctness on its own.

## Targets — Kotlin Multiplatform, no JVM

Shipped targets (see `build.gradle.kts`):

- `macosArm64`, `macosX64`
- `linuxX64`
- `mingwX64`
- `iosArm64`, `iosX64`, `iosSimulatorArm64`
- `js` (browser + nodejs)
- `wasmJs` (browser + nodejs)
- `androidLibrary`

### Forbidden imports

- `import kotlin.jvm.*` (`JvmName`, `JvmStatic`, `JvmField`, `JvmOverloads`, …)
- any `import java.*`
- any `import javax.*`

If you find yourself reaching for one, the answer is in the Kotlin stdlib
or a kotlinx library. If neither covers the case, raise on Slack — do not
import a JVM-only API.

### Approved dependencies

- `kotlinx-coroutines-core`
- `kotlinx-serialization-core`, `kotlinx-serialization-json`
- `kotlinx-collections-immutable`
- `kotlinx-datetime`
- `kotlinx-atomicfu`

Add a new dependency only when stdlib + the above cannot reproduce the
required behavior, and only after confirming it publishes artifacts for
**every** target above. Document the addition in the commit message.

## Naming Conventions

**No underscores in Kotlin identifiers** except in `SCREAMING_SNAKE_CASE`
contexts where the Kotlin coding conventions explicitly permit them.

| Kind                                 | Form                                                |
|--------------------------------------|-----------------------------------------------------|
| Functions, parameters, locals        | `camelCase` (`parseGrammar`, `stateCount`)          |
| Classes, data classes, sealed types  | `PascalCase`                                        |
| Interfaces                           | `PascalCase`, no `I` prefix                         |
| `const val`, `enum` entries          | `SCREAMING_SNAKE_CASE` permitted                    |
| Top-level/`object` `val` constants   | `SCREAMING_SNAKE_CASE` permitted                    |
| Type parameters                      | `T`, `K`, `V` (single uppercase)                    |
| Packages                             | all lowercase, no camelCase                         |

| Visibility        | Kotlin keyword |
|-------------------|----------------|
| public (default)  | omitted        |
| module-internal   | `internal`     |
| file-private      | `private`      |

## Code Discipline

### No `@Suppress`. Warnings are errors.

Warnings indicate the code is wrong. Fix the cause. The annotation is
never the answer.

- `unused` → the symbol is genuinely dead; delete it.
- `UNCHECKED_CAST` → encode the missing invariant in the type system
  (sealed classes, generics, `inline class`).
- `UNUSED_VARIABLE` → use `_` for the unused destructure slot.

### No stubs, no shims, no operator-graded gates

- No empty-body classes when the type has fields and methods.
- No `fun bar() = TODO()` or `fun bar() { error("not implemented") }`.
- No partial implementations that declare a class but skip its methods.
- No "placeholder until X is ready" comments with commented-out code.
- No tests whose pass/fail is determined by an operator-set status field.
  A test fails when actual ≠ expected, full stop.

### KDoc

KDoc describes Kotlin behavior in Kotlin terms. Don't reference Rust types
(`Vec<T>`, `Option<&str>`, `crate::util::Map`, `cfg(test)`, lifetimes,
`#[derive(...)]`), Rust call patterns (`Self::foo()`), or "Mirrors the Rust
X" cross-references. The reader of this codebase is a Kotlin developer.

### Blast Radius Rule

- **No repo-wide scripting.** No `find … -exec`, no global `sed` / `perl`,
  no blanket regex replacements across many files.
- **Changes are task-scoped, not pattern-scoped.** Every touched file is
  named up front, or discovered as a direct compile/test failure caused by
  the primary change.
- **Small multi-file edits are allowed when mechanically coupled** — the
  primary file plus its corresponding `commonTest` / platform tests and
  any required call-site rewires. No drive-by refactors, renames, or
  formatting churn outside that slice.
- **Comments and docstrings are first-class.** Never edited by bulk
  operations. Any comment change is intentional and reviewed in the diff
  like code.
- `sed -i` on a single file is allowed only when the working tree is clean
  for that file, the substitution is a single specific token (not a regex
  over many patterns), and you re-read the file afterward to verify.

### Operational rules

- **Do not write to `/tmp` or to project-local `tmp/` for staging.** `tmp/`
  is intentionally empty and should stay that way. Anything else either
  lives in `src/` as a committed file or doesn't exist.
- **Commit after every file edit.** One file edited → one commit. Do not
  batch edits across files. The commit message describes what changed in
  that one file. Squash later via `git rebase -i` if you want a logical
  unit; never withhold commits up front.
- **Deletions require `git rm` plus reference scrubs.** Plain `rm` leaves
  the file in git; surviving references in other files (`[Foo](./Foo.md)`,
  `// see Foo.md`) tell future runs to recreate the file. Both halves are
  required for a deletion to stick.

### Do not delegate `.kt` edits to subagents

Subagents (Task / Agent tool) are allowed for searches, file location, and
read-only reports. They are **not** allowed for writing or editing `.kt`
source files. Subagents cheat on translation: they hollow out KDoc, add
filler to inflate scores, drop semantically load-bearing constructs because
they "look unused," and produce confident summaries that mask the damage.
The only signal back is a tidy report.

All `.kt` edits happen in the main loop. If volume feels overwhelming,
slow down; do not parallelize.

## Backend Architecture

### Front end (shared)

- `lalrpop.grammar.parsetree` — AST for the `.lalrpop` grammar language.
- `lalrpop.grammar.repr` — normalized grammar representation.
- `lalrpop.lexer` — tokenizer / lexer-DFA generation.
- `lalrpop.parser` — bootstrapped parser for `.lalrpop` files.
- `lalrpop.normalize` — macro expansion, type inference, etc.
- `lalrpop.lr1` — state-machine construction (no codegen).
- `lalrpop.collections`, `lalrpop.message`, `lalrpop.session`,
  `lalrpop.tls`, `lalrpop.tok`, `lalrpop.util` — supporting infrastructure.

### Rust-emitting backend

- `lalrpop.rust` — `RustWrite` and helpers that format Rust source. The
  Rust syntax in this package is intentional: this is the package whose
  *output strings* are Rust code.
- `lalrpop.lr1.codegen` — `Ascent.kt`, `ParseTable.kt`, `Base.kt`. Walks
  the LR(1) state machine and emits a Rust parser through `RustWrite`.

This backend is feature-complete and at byte parity with upstream LALRPOP
on the fixture corpus. Treat it as maintenance mode — fix bugs, don't
restructure.

### Kotlin-emitting backend (in progress)

- `lalrpop.kotlintarget` — Kotlin-source emitter primitives. Currently
  contains `IndentedWriter`, a small `block { … }` / `indented { … }` DSL
  with no Rust DNA. The codegen subclasses (`Ascent`-equivalent,
  `ParseTable`-equivalent) are the next pieces; they will live under
  `lalrpop.lr1.kotlintarget` and should be split so no single file
  exceeds ~500–800 lines.

Phase-2 design constraint: this backend is written from scratch as
idiomatic Kotlin. It does not mirror the Rust-emitting backend's class
shapes, builder fluencies, or macro vocabularies. If a piece looks like
"the Rust version of this, with Kotlin syntax in the output strings," it
is wrong.

## File Organization

```
src/
├── commonMain/kotlin/io/github/kotlinmania/lalrpop/
│   ├── grammar/
│   ├── lexer/
│   ├── parser/
│   ├── normalize/
│   ├── lr1/
│   │   └── codegen/      # Rust-emit backend codegen
│   ├── rust/             # Rust-emit primitives
│   ├── kotlintarget/     # Kotlin-emit primitives
│   ├── collections/
│   ├── message/
│   ├── session/
│   ├── tls/
│   ├── tok/
│   └── …
└── commonTest/
    ├── kotlin/io/github/kotlinmania/lalrpop/   # tests, mirroring main
    └── resources/                                # grammar / table fixtures
```

## Cross-Project Coordination

`*-kotlin` repos under `kotlinmania/` import each other as published
Maven artifacts. When you bump a version in this repo, check that every
consumer's pinned version matches what's published.

```bash
find /Volumes/stuff/Projects/kotlinmania -name "build.gradle.kts" \
  -not -path "*/build/*" \
  -exec grep -l "io.github.kotlinmania" {} \;
```

If versions are mismatched, raise on Slack before bumping — version
bumps cascade.

## CI

```bash
gh run list --workflow ci.yml --limit 5
gh pr checks <pr-number>
```

Read failing logs before claiming the change is done.

## Final Report

When a run finishes, post a status update via the Slack MCP / skill /
connector. Include:

- Files touched.
- Tests added and passing.
- Blockers that need a human decision.

## Commit Messages

- No AI branding or attribution.
- Clear, descriptive, focused on what changed and why.
- No "Co-Authored-By" lines.
- No emoji or robot references.

Example:

```
Add IndentedWriter for the Kotlin-emit backend

Small block-DSL writer (line, indented, block) with no Rust idioms
carried over. Six unit tests in commonTest covering depth tracking,
custom footers, exception safety, and custom indent strings.
```

## Companion Documents

- [AGENTS.md](./AGENTS.md) — agent-facing guidelines (mirrors this
  document at a higher altitude; treat CLAUDE.md as authoritative when
  they conflict).
- [README.md](./README.md) — project overview.
- [NOTICE](./NOTICE) — attribution and licensing.

## Re-exports from upstream `mod.rs` files

When an upstream Rust `mod.rs` is **only re-exporting** something that actually lives elsewhere
(`pub use <crate-path>::<Name>;`, often under a different name), do **not** preserve that
re-export shape in Kotlin as a "central alias" API. Do not write a `typealias` for the
re-exported name. The existing `Forbidden` rule against "Re-export typealias files at root
packages" is enforced through this procedure.

Workflow:

1. **Identify what the `mod.rs` is re-exporting and the name it's exported as.** Record both
   the original symbol's fully-qualified upstream path and the (possibly different) re-export
   name.

2. **Find callers across the kotlinmania monorepo.** A caller is any Kotlin file in another
   `*-kotlin` repo that has both a `tmp/` folder and a Cargo.toml depending on the Rust
   counterpart of *this* crate, where the file references the re-exported name. Search for:
   - direct imports: `import <reexport-package>.<Name>`
   - wildcard imports of the re-export package, when `<Name>` is used in the file body
   - fully-qualified inline references

3. **Rewrite each caller to reference the upstream/original symbol directly.** If the caller
   still needs to write `<Name>` unchanged, use Kotlin aliasing:
   `import <upstream-fully-qualified-name> as <Name>`. Never bridge with a Kotlin `typealias`.

4. **Keep `Mod.kt` (or the equivalent file for that package) as a tracking file.** It carries
   the translated upstream module-level comments and a literal-quoted reference to each upstream
   `pub use` line (e.g. `// pub use crate::lib::result::Result;`). Each time a caller is migrated
   off the re-export, append the caller's absolute path under a `// Callers migrated:` ledger in
   `Mod.kt`. Append, never delete. Once all callers are migrated, the `typealias` (if any) is
   removed; the tracking file remains as the ledger of the migration.

Reference example: [/Volumes/stuff/Projects/kotlinmania/serde-kotlin/tmp/serde/serde_core/src/private/mod.rs](/Volumes/stuff/Projects/kotlinmania/serde-kotlin/tmp/serde/serde_core/src/private/mod.rs)
re-exports `Result` from `crate::lib::result`. The Kotlin tracking file lives at
[/Volumes/stuff/Projects/kotlinmania/serde-kotlin/src/commonMain/kotlin/io/github/kotlinmania/serde/core/private/Mod.kt](/Volumes/stuff/Projects/kotlinmania/serde-kotlin/src/commonMain/kotlin/io/github/kotlinmania/serde/core/private/Mod.kt).
A caller that previously did `import io.github.kotlinmania.serde.core.private.Result` is
rewritten to `import kotlin.Result as Result` (or just removes the import and relies on the
auto-imported `kotlin.Result`).
