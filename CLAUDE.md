# Claude Code Project Instructions

## Project Overview

This is **lalrpop-kotlin**, a Kotlin Multiplatform port of LALRPOP (the
Rust LR(1) parser generator by Niko Matsakis and the LALRPOP Project
Developers). The upstream Rust sources live in `tmp/lalrpop-rs/` and
are read-only oracles; the Kotlin implementation is built under `src/`.

Upstream: https://github.com/lalrpop/lalrpop

## Project Goals (the contract)

These five goals govern every decision in this repo. When a rule below
seems to conflict with one of these, the goals win.

1. **Full API parity.** Every public Rust item (function, struct, enum,
   trait, impl, type alias, const) has a Kotlin counterpart. Names
   follow the conversion table in [Naming Conventions](#naming-conventions);
   semantics follow upstream.
2. **All Rust tests are ported.** Every `#[test]` and every
   `#[cfg(test)] mod tests { ... }` body in `tmp/lalrpop-rs/lalrpop/src/`
   has a corresponding Kotlin test under `src/commonTest/`, exercising
   the same inputs and asserting the same outputs. No skips, no "TODO:
   port later." Integration grammars under `tmp/lalrpop-rs/lalrpop-test/`
   are likewise mirrored.
3. **Tooling is a tool, not a warden.** ast_distance is the working
   coverage and cheat-detection tool. Earlier revisions of this doc
   forbade piping/redirecting its output; that restriction is lifted.
   The blocker survives as opt-in policy via the `"strict_redirects"`
   field in `.ast_distance_config.json` (default `false`). Do **not**
   chase ast_distance similarity scores — a faithful Kotlin port using
   stdlib idioms can score low because identifier overlap is the
   dominant signal. Coverage (zero missing symbols, zero stubs) is the
   gate; per-file similarity is diagnostic only.
4. **Anything goes that is faithful to Rust.** A faithful translation
   is one that produces the same observable behavior on the same
   input. Within that constraint, use Kotlin idioms, Kotlin stdlib,
   kotlinx libraries, sealed classes, data classes, extension
   functions, coroutines — whatever makes the Kotlin clearer. The
   default is still "translate the Rust line-by-line" because that
   produces parity with the lowest cognitive cost; deviate when the
   Rust idiom has a strictly better Kotlin counterpart with no
   behavioral change.
5. **No hacks. Hacks are bugs.** No stubs, no `TODO()`, no `FIXME`, no
   `@Suppress` annotations, no JVM imports, no porter-invented
   typealiases, no operator-graded test gates, no "fix it later"
   comments. Warnings are errors — fix the cause. If you can't, stop
   and ask. Do not park the problem.

## Verification

The build gate is **`./gradlew test`** — the ported Rust tests must
pass on the same inputs the Rust tests use. The Kotlin compiler is a
precondition, not the gate. The earlier `astDistanceParity` Gradle gate
that short-circuited every build target on coverage / stub thresholds
has been removed; that style of gate ranks symbol counts above test
correctness and pushes the port toward rust-ifying Kotlin to chase
numbers.

For each module you touch:

1. Find the corresponding Rust tests in
   `tmp/lalrpop-rs/lalrpop/src/<path>` (inline `#[cfg(test)] mod tests`
   or sibling `tests.rs`).
2. Port them to
   `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/<path>/`,
   using `kotlin.test`. Test fixtures (`.lalrpop` grammars, expected
   tables, etc.) move under `src/commonTest/resources/`.
3. Run `./gradlew test` (or the platform-specific test task). Tests
   must pass on the same input the Rust test uses.
4. Spot-check coverage by running `ast_distance --symbol-parity` on
   the file pair (see commands below). Use the result diagnostically:
   missing-symbol counts are real coverage gaps; per-file similarity
   numbers are noisy and should not be chased.

ast_distance is the working tool for narrower questions:

```bash
# Coverage map: which Rust symbols are missing from the Kotlin tree
./tools/ast_distance/ast_distance --symbol-parity \
  tmp/lalrpop-rs/lalrpop/src \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop \
  --kotlin-test-root src/commonTest/kotlin/io/github/kotlinmania/lalrpop \
  --missing-only

# Function-by-function comparison for a single file pair
./tools/ast_distance/ast_distance --compare-functions \
  tmp/lalrpop-rs/lalrpop/src/lr1/build.rs rust \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop/lr1/Build.kt kotlin

# Whole-tree report (regenerates port_status_report.md, NEXT_ACTIONS.md, etc.)
./tools/ast_distance/ast_distance --deep \
  tmp/lalrpop-rs/lalrpop/src rust \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin
```

Pipe and redirect freely. The redirect-guard is now off by default. If
you want to re-enforce it on a branch, set `"strict_redirects": true`
in `.ast_distance_config.json`.

A low ast_distance score on a faithful port whose tests pass and whose
coverage is 100% is a tooling signal, not a verdict — usually it means
the score model doesn't like Kotlin's stdlib idioms. A high score on a
port whose tests fail is worthless.

## Targets — Kotlin Multiplatform, no JVM

The project ships these targets (see `build.gradle.kts`):

- `macosArm64`, `macosX64`
- `linuxX64`
- `mingwX64`
- `iosArm64`, `iosX64`, `iosSimulatorArm64`
- `js` (browser + nodejs)
- `wasmJs` (browser + nodejs)
- `androidLibrary`

**There is no `jvm()` target.** The `jvmToolchain(21)` line in the
build script configures the JDK that runs Gradle itself; it does not
add a JVM target.

### Forbidden imports

- `import kotlin.jvm.JvmName`
- `import kotlin.jvm.JvmStatic`
- `import kotlin.jvm.JvmField`
- `import kotlin.jvm.JvmOverloads`
- any `import kotlin.jvm.*`
- any `import java.*`
- any `import javax.*`

If you find yourself reaching for one of these, the answer is in the
Kotlin stdlib or a kotlinx library. If neither covers the case, raise
on Slack — do not import a JVM-only API.

### Approved dependencies

- `kotlinx-coroutines-core`
- `kotlinx-serialization-core`, `kotlinx-serialization-json`
- `kotlinx-collections-immutable`
- `kotlinx-datetime`
- `kotlinx-atomicfu` (when atomic operations are required)

Add a new dependency only when stdlib + the above cannot reproduce the
Rust crate's behavior, and only after confirming the dependency
publishes artifacts for **every** target listed above. Document the
addition in the commit message with the Rust crate it replaces and why
stdlib was insufficient.

## Port-Lint Headers (REQUIRED)

Every ported Kotlin file MUST start with:

```kotlin
// port-lint: source <path-relative-to-tmp/lalrpop-rs/lalrpop>
package io.github.kotlinmania.lalrpop.<module>
```

Example:

```kotlin
// port-lint: source src/lr1/build.rs
package io.github.kotlinmania.lalrpop.lr1
```

The header documents which Rust file the Kotlin came from. It is the
only place provenance is recorded. Never remove, move, or alter it
unless you are re-targeting the file to a different Rust source.

Test files use the same convention:

```kotlin
// port-lint: source src/lr1/build.rs (tests)
package io.github.kotlinmania.lalrpop.lr1
```

Files with no single Rust source (because the implementation came from
a `mod.rs` re-home) use `// port-lint: ignore` and a short prose note
like `// re-homed from upstream module root`.

## Naming Conventions

Faithful Kotlin names — Rust naming idioms map mechanically. **No
underscores in Kotlin identifiers** except in the four contexts where
Kotlin's coding conventions explicitly permit `SCREAMING_SNAKE_CASE`
(see below).

### Identifiers

| Rust                                 | Kotlin                                                       |
|--------------------------------------|--------------------------------------------------------------|
| `fn parse_grammar()`                 | `fun parseGrammar()`                                         |
| `let mut state_count`                | `var stateCount`                                             |
| `struct ParseTable`                  | `class ParseTable` / `data class ParseTable`                 |
| `enum Action { Shift, Reduce(...) }` | `sealed class Action { object Shift; data class Reduce(...) }` |
| `trait Display`                      | `interface Display`                                          |
| `impl Display for Foo { ... }`       | `class Foo : Display { ... }`                                |
| `pub type Lr1Result<T> = ...`        | `typealias Lr1Result<T> = ...`                               |
| `mod lr1`                            | package `lalrpop.lr1`                                        |
| `r#type` (raw identifier)            | `` `type` `` (backtick literal)                              |

### `SCREAMING_SNAKE_CASE` — the only place underscores appear

Per the Kotlin coding conventions, `SCREAMING_SNAKE_CASE` is permitted
in exactly four contexts:

1. `const val` compile-time constants.
   `const val MAX_STATES: Int = 256`
2. Top-level or `object` `val` properties holding immutable data with
   no custom getter.
   `val USER_AGENT_HEADER = "lalrpop-kotlin/1.0"`
3. `enum class` entries (either `SCREAMING_SNAKE_CASE` or
   `UpperCamelCase` — pick one and be consistent within a given enum).
4. Nowhere else.

Rust → Kotlin mechanical rule for constants:

| Rust                                       | Kotlin                            |
|--------------------------------------------|-----------------------------------|
| `const MAX_STATES: usize = 256;`           | `const val MAX_STATES: Int = 256` |
| `static GLOBAL_TABLE: Lazy<...> = ...;`    | `val GLOBAL_TABLE: ... = ...`     |
| `static mut COUNTER: u32 = 0;`             | (avoid mutable static; prefer a class-scoped property or `kotlinx-atomicfu`) |

Function names, parameter names, local `var`/`val` bindings, class
names, property names with custom getters, type aliases, generic
parameters: **camelCase / PascalCase only**. If you find yourself
writing `parse_table` or `stateCount_` in Kotlin, you've broken the
rule.

### Tests

| Rust                              | Kotlin                                       |
|-----------------------------------|----------------------------------------------|
| `#[test] fn test_first_set()`     | `@Test fun testFirstSet()`                   |
| `#[cfg(test)] mod tests { ... }`  | one `class FirstTest { ... }` in commonTest  |
| `assert_eq!(a, b)`                | `assertEquals(b, a)` (note: Kotlin order is expected first) |
| `assert!(cond)`                   | `assertTrue(cond)`                           |
| `#[should_panic]`                 | `assertFailsWith<...> { ... }`               |
| `#[ignore]`                       | `@Ignore`                                    |

### Visibility

| Rust            | Kotlin                       |
|-----------------|------------------------------|
| `pub`           | `public` (default — omit)    |
| `pub(crate)`    | `internal`                   |
| `pub(super)`    | `internal`                   |
| (no modifier)   | `private`                    |

### Types

| Rust                  | Kotlin                                                                                |
|-----------------------|---------------------------------------------------------------------------------------|
| `i8 / u8`             | `Byte / UByte`                                                                        |
| `i16 / u16`           | `Short / UShort`                                                                      |
| `i32 / u32`           | `Int / UInt`                                                                          |
| `i64 / u64`           | `Long / ULong`                                                                        |
| `usize / isize`       | `Int` (or `Long` if 64-bit indices are required)                                      |
| `f32 / f64`           | `Float / Double`                                                                      |
| `bool`                | `Boolean`                                                                             |
| `char`                | `Char` (24-bit Rust scalar value vs 16-bit Kotlin code unit — note when this matters) |
| `String / &str`       | `String`                                                                              |
| `Option<T>`           | `T?`                                                                                  |
| `Result<T, E>`        | `Result<T>` (E carried via exception) or sealed `Either<E, T>` if the error type carries data and call sites pattern-match on it |
| `Vec<T>`              | `MutableList<T>` (mutable) or `List<T>` (read-only)                                   |
| `&[T]`                | `List<T>`                                                                             |
| `HashMap<K, V>`       | `MutableMap<K, V>` / `Map<K, V>`                                                      |
| `BTreeMap<K, V>`      | `sortedMapOf` or kotlinx-collections-immutable persistent ordered map                 |
| `HashSet<T>`          | `MutableSet<T>` / `Set<T>`                                                            |
| `BTreeSet<T>`         | sorted set                                                                            |
| `Box<T>`              | plain `T` (GC owns)                                                                   |
| `Rc<T> / Arc<T>`      | plain reference                                                                       |
| `Cell<T> / RefCell<T>`| mutable property (single-threaded) or `kotlinx-atomicfu` ref (threaded)               |
| `NonNull<T>`          | non-null `T`                                                                          |
| `MaybeUninit<T>`      | nullable `T?` initialized lazily, or `lateinit var`                                   |
| `dyn Trait`           | interface type (Kotlin interfaces are already polymorphic)                            |
| `&'a T`               | `T` (lifetimes are erased; Kotlin GC owns)                                            |
| `Pin<T>`              | plain `T` (Kotlin has no pinning concept)                                             |
| `()`                  | `Unit`                                                                                |
| `!` (never)           | `Nothing`                                                                             |

## Translation Discipline

### Faithful is the default; Kotlin idioms allowed when behaviorally identical

The starting point is "translate the Rust line-by-line." This produces
parity with the least guesswork and the least drift. You may use a
Kotlin idiom — `when` instead of `match`, `data class` instead of
`#[derive(Clone, Debug, Eq)] struct`, `?:` instead of `unwrap_or`,
`apply { ... }` instead of explicit field assignment, `sequence { ... }`
instead of an iterator state machine — when the substitution is
behaviorally identical and the Kotlin is genuinely clearer.

What "behaviorally identical" rules out: lazy where Rust was eager,
unboxed where Rust was boxed, throwing where Rust returned `Result`,
collecting where Rust streamed, swallowing where Rust propagated.

### No `@Suppress`, ever. Warnings are errors.

Warnings indicate the port is wrong. Fix the cause. The annotation is
never the answer.

- "unused" → the Kotlin code is genuinely dead; un-collapse the Rust
  concept the port flattened.
- "UNCHECKED_CAST" → the Kotlin lost a Rust invariant; encode it in
  the type system (sealed classes, generics, `inline class`).
- "UNUSED_VARIABLE" → port the binding as `_` or destructure without
  it.

### No stubs, no shims, no operator-graded gates

- No `class Foo` with an empty body when the Rust struct has fields.
- No `fun bar() = TODO()` or `fun bar() { error("not implemented") }`.
- No partial ports that translate the class declaration but skip the
  methods.
- No "placeholder until X is ready" comments with commented-out code.
- No empty-body translations of Rust constructs the GC subsumes
  (`drop_in_place`, `mem::forget`, `Pin`, `Box<T>`, `Cell<T>`,
  `RefCell<T>`, `Arc<T>`, `Rc<T>`, `NonNull<T>`, `MaybeUninit<T>`,
  `dyn Trait`). Delete them or inline the wrapped value; do not
  translate as `fun dropInPlace() {}` shells. Empty shells inflate
  symbol counts without porting any behavior.
- No tests whose pass/fail is determined by an operator-set status
  field. A test fails when the actual output differs from the expected
  output, full stop.

If a dependency doesn't exist yet, port that dependency first. If the
dependency chain is too deep, pick a different file to work on.

### No `mod.rs` translations or porter-invented typealiases

Rust uses `mod.rs` files as glue: `pub mod foo; pub use foo::Bar;`.
These re-publish a sibling module's types to flatten the import path.
Do not port them as `Mod.kt` and do not mirror their `pub use` chain
with `internal typealias Bar = foo.Bar`. Rewire callers to import from
the defining package directly.

A `mod.rs` (or any other file) that contains real type or function
definitions in addition to its `pub use` chain is a normal port
target — only the reexports are dropped, not the real definitions.

When upstream Rust **does** declare a `pub type X = Y;` (common in
`typing` and similar modules), make a conscious decision: if the alias
carries semantic meaning that's load-bearing in the Rust code, mirror
it 1:1 in Kotlin. If it exists only because the underlying Rust type
was syntactically inconvenient, inline the type and skip the alias.
Default toward mirroring when the Rust source has the alias; default
toward inlining when it doesn't.

Porter-invented typealiases — ones with no corresponding Rust `pub
type` — are forbidden. They're a name pretending to be part of the API
that exists because the porter found the underlying type inconvenient.
Inline the type instead.

### Doc comments are part of the port

Translate Rust doc comments to KDoc, **including the Rust syntax
inside them**. A comment that mentions `crate::util::Map`, `Vec<T>`,
`Option<&str>`, `Self::foo()`, `cfg(test)`, `#[derive(...)]`,
lifetimes like `'a`, or any other Rust syntax must be rewritten to its
Kotlin equivalent (`BTreeMap`, `List<T>`, `String?`, `foo()`, KDoc
links like `[BTreeMap]`).

Translate the code-in-comment. Do not delete the comment to silence
the cheat detector.

### Do not delegate edits to sub-agents

Anthropic and OpenAI both expose Task / Agent tools that spawn cheaper,
less thoughtful agents for grunt work. Those agents cheat on
translation: they hollow out KDoc, add Kotlin-only filler to inflate
scores, drop semantically load-bearing Rust constructs because they
"look unused," and produce confident summaries that mask the damage.
The only signal back is a tidy report.

**All `.kt` edits happen in the main loop.** The Task / Agent tool is
allowed for searches, file location, and read-only reports — never for
writing or editing source files. If translation volume feels
overwhelming, slow down; do not parallelize.

### Operational rules

- **Do not write to `/tmp` or to project-local `tmp/` for staging.**
  `tmp/` is reserved for read-only upstream Rust oracles
  (`tmp/lalrpop-rs/`). Anything else either lives in `src/` as a
  committed file or doesn't exist. ast_distance regenerates
  `port_status_report.md`, `NEXT_ACTIONS.md`, etc. at the project
  root — those are derived artifacts, not staging files; don't edit
  them by hand.
- **Blast Radius Rule.**
  - **No repo-wide scripting.** No `find … -exec`, no global `sed` /
    `perl`, no blanket regex replacements across many files.
  - **Changes must be task-scoped, not pattern-scoped.** Every touched
    file is named up front by path, or discovered as a direct
    compiler/test failure caused by the initial change.
  - **Small multi-file edits are allowed when they're mechanically
    coupled** — one Rust→Kotlin transliteration plus its directly
    corresponding tests (`commonTest` / `nativeTest` / `jsTest`) and
    any required call-site rewires. No drive-by refactors, renames, or
    formatting churn outside that slice.
  - **A follow-on fix must be justified by a concrete signal** — a
    compilation error, a failing test, or a proven missing
    symbol/wire-up caused by the primary change.
  - **Comments and docstrings are first-class.** Never edited by bulk
    operations. Any comment change is intentional and reviewed in the
    diff like code.
  - **More than ~5 files in a single change?** Stop and ask before
    applying it. The only exception is a deletion that requires
    scrubbing references from every caller — and even then, name the
    referrers up front.
  - `sed -i` on a single file is allowed only when the working tree is
    clean for that file, the substitution is a single specific token
    (not a regex over many patterns), and you re-read the file
    afterward to verify.
- **Commit after every file edit.** One file edited → one commit. Do
  not batch edits across files. The commit message describes what
  changed in that one file (e.g. "Build.kt: inline `Lr1Result`
  typealias at use sites"). Squash later via `git rebase -i` if you
  want a logical unit; never withhold commits up front.
- **Deletions require `git rm` plus reference scrubs.** Plain `rm`
  leaves the file in git and the next branch op restores it; surviving
  references in other files (`[Foo](./Foo.md)`, `// see Foo.md`) tell
  future instances to recreate the file. Both halves are required for
  a deletion to stick.

## Cross-Project Coordination

The `*-kotlin` repos under `kotlinmania/` import each other as
published Maven artifacts (`io.github.kotlinmania:btree-kotlin`,
`io.github.kotlinmania:starlarkmap-kotlin`, etc.). When you touch one,
check that the version pinned by every consumer matches what's
published.

```bash
# Find every Gradle build script that imports a kotlinmania artifact
find /Volumes/stuff/Projects/kotlinmania -name "build.gradle.kts" \
  -not -path "*/tmp/*" -not -path "*/build/*" \
  -exec grep -l "io.github.kotlinmania" {} \;
```

If versions are mismatched, raise on Slack before bumping — version
bumps cascade and a cheap-agent might miss call sites.

## CI

Use the `gh` CLI to check workflow status when work affects multiple
repos.

```bash
gh run list --repo <owner>/<repo> --workflow ci.yml --limit 5
gh pr checks <pr-number>
```

Read failing logs before claiming the change is done.

## New Ports / Large Work

If you discover a Rust crate that needs porting and isn't yet a
project under `kotlinmania/`, you may create a new `<crate>-kotlin`
project using existing projects as templates. **For any port estimated
at more than ~5,000 lines of Rust**, raise on Slack first with the
crate name, line count, and which existing kotlinmania projects depend
on it. Mention any blockers (missing dependencies, KMP-incompatible
Rust crates, unclear semantics). Wait for Sydney's reply before
starting.

## Backend Phases

Upstream LALRPOP emits Rust source code (`src/rust/mod.rs` and
`src/lr1/codegen/{ascent,parse_table}.rs` are its back-end). The final
goal of lalrpop-kotlin is Kotlin source emission. The backend is a
two-phase port.

### Phase 1: Rust-output back-end (transliteration)

Transliterate the upstream backend so the Kotlin port can produce the
same Rust-shaped output. Keep `// port-lint: source src/rust/mod.rs`
and the corresponding headers on the codegen files. Translate
`write!` / `writeln!` calls to Kotlin calls that still write the
corresponding Rust text. Do not Kotlin-ify the emitted output during
phase 1.

Verification in phase 1: feed a `.lalrpop` grammar to both upstream
LALRPOP and lalrpop-kotlin, diff the emitted Rust. Any divergence is a
phase-1 bug. The diff comparison can be a Kotlin test that invokes
upstream as a subprocess and asserts byte-equality, or a manual
side-by-side run during development. There is currently no in-tree
harness — earlier instances added one with operator-graded status;
that was a bug per goal 5 and was removed. If a new harness is added
it must fail on any divergence with no porter-toggleable status.

### Phase 2: Kotlin-output back-end

After phase 1 produces matching Rust output, add a Kotlin-emitting
backend under a distinct namespace (`codegen.kotlinTarget`) and make
it the project default. Keep the phase-1 Rust-emitting backend
(`codegen.rustTarget`) as a reference until you no longer need it. In
phase 2 only, replace output leaves that write Rust syntax (`return
Ok(...)`, struct literals, lifetime parameters) with Kotlin syntax
(`return Result.success(...)`, Kotlin constructors, generic
parameters, `when`, etc.). Document each phase-2 substitution inline
so a future reader can audit what changed and why.

The primary downstream consumer is `starlark-kotlin`'s
`grammar.lalrpop`. Once phase 2 works, regenerating that grammar
should produce drop-in replacements for the hand-transliterated
`Grammar.kt` and `GrammarReducers.kt` (~12.5k lines).

## File Organization

```
src/
├── commonMain/kotlin/io/github/kotlinmania/lalrpop/
│   ├── api/         # ← tmp/lalrpop-rs/lalrpop/src/api/
│   ├── build/       # ← tmp/lalrpop-rs/lalrpop/src/build/
│   ├── collections/ # ← tmp/lalrpop-rs/lalrpop/src/collections/
│   ├── grammar/     # ← tmp/lalrpop-rs/lalrpop/src/grammar/
│   ├── lexer/       # ← tmp/lalrpop-rs/lalrpop/src/lexer/
│   ├── lr1/         # ← tmp/lalrpop-rs/lalrpop/src/lr1/
│   ├── message/     # ← tmp/lalrpop-rs/lalrpop/src/message/
│   ├── normalize/   # ← tmp/lalrpop-rs/lalrpop/src/normalize/
│   ├── parser/      # ← tmp/lalrpop-rs/lalrpop/src/parser/
│   ├── rust/        # ← tmp/lalrpop-rs/lalrpop/src/rust/
│   ├── tls/         # ← tmp/lalrpop-rs/lalrpop/src/tls/
│   ├── tok/         # ← tmp/lalrpop-rs/lalrpop/src/tok/
│   └── ...
└── commonTest/
    ├── kotlin/io/github/kotlinmania/lalrpop/   # ported tests
    └── resources/                                # ported fixtures
```

## Porting Order (suggested, leaf-first)

1. `util`, `tls`, `session`, `log`, `file_text` — infrastructure leaves
2. `collections/` — data structures used everywhere
3. `message/` — diagnostic system
4. `grammar/` — AST for the grammar language
5. `tok/`, `lexer/` — tokenizer and lexer generation
6. `parser/` — the bootstrapped LALRPOP grammar parser
7. `normalize/` — grammar transformations (macros, etc.)
8. `lr1/` — LR(1) state machine construction
9. `rust/` — code emission (phase 1)
10. `build/`, `api/` — top-level driver
11. Phase 2 Kotlin emitter

## Build Commands

```bash
# Tests across all targets (the gate)
./gradlew test

# Compile every target without running tests
./gradlew build

# Specific platform tests
./gradlew macosArm64Test
./gradlew linuxX64Test
./gradlew jsNodeTest
./gradlew wasmJsNodeTest
```

`./gradlew jvmTest` is **not** a valid command — there is no JVM
target.

## Final Report

When a run finishes, post a status update via the Slack MCP / skill /
connector. Include:

- Files touched.
- Tests added and passing.
- ast_distance coverage delta (production / supplementary / tests /
  stubs) if relevant.
- Blockers that need a human decision.

## Commit Messages

- No AI branding or attribution.
- Clear, descriptive, focused on what changed and why.
- No "Co-Authored-By" lines.
- No emoji or robot references.

Example:

```
Add lr1/first port from Rust

Port first.rs to First.kt. Includes:
- FIRST set construction
- Nullable nonterminal propagation
- 8 tests ported to FirstTest.kt under commonTest
```

## Companion Documents

- [AGENTS.md](./AGENTS.md) — agent-facing guidelines (mirrors this
  document at a higher altitude; treat CLAUDE.md as authoritative when
  they conflict).
- [README.md](./README.md) — project overview.
- [NOTICE](./NOTICE) — attribution and licensing.
- Upstream: https://github.com/lalrpop/lalrpop
- LALRPOP Book: https://lalrpop.github.io/lalrpop/
