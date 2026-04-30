# lalrpop-kotlin Port — Agent Guidelines

This file is the agent-facing brief. [CLAUDE.md](./CLAUDE.md) is the
authoritative project doc; this file mirrors its rules at a higher
altitude. When the two conflict, CLAUDE.md wins.

## Project Context

This is a **line-by-line transliteration port** of [lalrpop/lalrpop](https://github.com/lalrpop/lalrpop)
to Kotlin Multiplatform. The upstream Rust sources live in
`tmp/lalrpop-rs/` and are read-only oracles. Never edit them.

These are Rust → Kotlin translations. If you encounter bugs, it is
likely you didn't faithfully translate the code.

## Project Goals (the contract)

These five goals govern every decision in this repo. When a rule below
seems to conflict with one of these, the goals win.

1. **Full API parity.** Every public Rust item (function, struct, enum,
   trait, impl, type alias, const) has a Kotlin counterpart. Names
   follow the conversion tables under [Naming](#naming); semantics
   follow upstream.
2. **All Rust tests are ported.** Every `#[test]` and every
   `#[cfg(test)] mod tests { ... }` body in `tmp/lalrpop-rs/lalrpop/src/`
   has a corresponding Kotlin test under `src/commonTest/` (or the
   appropriate `(platform)Test` directory if the test exercises a
   platform-specific code path). The Kotlin test exercises the same
   inputs and asserts the same outputs as the Rust test. No skips, no
   "TODO: port later." Integration grammars under
   `tmp/lalrpop-rs/lalrpop-test/` are likewise mirrored.
3. **Tooling is a tool, not a warden.** Static analyzers under
   `tools/port_lint/` and `tools/sig_diff/` exist to flag specific
   drift patterns and to spot-check coverage on a single file pair.
   They do not produce verdicts. The runtime gate is `./gradlew test`;
   per-file structural metrics are diagnostic only and are not chased.
4. **Anything goes that is faithful to Rust.** A faithful translation
   is one that produces the same observable behavior on the same
   input. Within that constraint, use Kotlin idioms, Kotlin stdlib,
   kotlinx libraries, sealed classes, data classes, extension
   functions, coroutines — whatever makes the Kotlin clearer. The
   default is still "translate the Rust line-by-line"; deviate when
   the Rust idiom has a strictly better Kotlin counterpart with no
   behavioral change.
5. **No hacks. Hacks are bugs.** No stubs, no `TODO()`, no `FIXME`, no
   `@Suppress` annotations, no JVM imports, no porter-invented
   typealiases, no operator-graded test gates, no "fix it later"
   comments. Warnings are errors — fix the cause. If you can't, stop
   and ask. Do not park the problem.

The build gate is `./gradlew test` — the ported tests must pass on
the same inputs the Rust tests use. The Kotlin compiler is a
precondition, not the gate.

## Naming

- Rust `snake_case` functions → Kotlin `camelCase`: `fn parse_grammar()`
  → `fun parseGrammar()`
- Rust types → Kotlin `PascalCase`: `struct ParseTable` →
  `class ParseTable`
- Rust parameters, locals, properties: `camelCase` — never
  `snake_case`. `let state_count` → `var stateCount`
- Rust `r#type` raw identifiers → Kotlin backtick identifiers:
  `` `type` ``
- **Underscores in Kotlin appear only in `SCREAMING_SNAKE_CASE`**, and
  only in the four places Kotlin's coding conventions permit it:
  - `const val` compile-time constants:
    `const val MAX_STATES: Int = 256`
  - Top-level or `object` `val` properties holding immutable data
    with no custom getter:
    `val USER_AGENT_HEADER = "lalrpop-kotlin/1.0"`
  - `enum class` entries — either `SCREAMING_SNAKE_CASE` or
    `UpperCamelCase`; pick one and be consistent within an enum.
  - Nowhere else.
- Function names, parameter names, locals, class names, property
  names with custom getters, type aliases, generic parameters:
  **camelCase / PascalCase only**.

Rust → Kotlin mechanical rule for constants:

| Rust                                      | Kotlin                            |
|-------------------------------------------|-----------------------------------|
| `const MAX_STATES: usize = 256;`          | `const val MAX_STATES: Int = 256` |
| `static GLOBAL_TABLE: Lazy<...> = ...;`   | `val GLOBAL_TABLE: ... = ...`     |
| `static mut COUNTER: u32 = 0;`            | (avoid mutable static; prefer a class-scoped property or `kotlinx-atomicfu`) |

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

## Tests in `commonTest`

When you see Rust code with inline `#[cfg(test)] mod tests { ... }` or
`#[test] fn ...`, port the test bodies to the corresponding
`commonTest` directory:

| Rust                              | Kotlin                                       |
|-----------------------------------|----------------------------------------------|
| `#[test] fn test_first_set()`     | `@Test fun testFirstSet()`                   |
| `#[cfg(test)] mod tests { ... }`  | one `class FirstTest { ... }` in commonTest  |
| `assert_eq!(a, b)`                | `assertEquals(b, a)` — Kotlin: expected first |
| `assert!(cond)`                   | `assertTrue(cond)`                           |
| `#[should_panic]`                 | `assertFailsWith<...> { ... }`               |
| `#[ignore]`                       | `@Ignore`                                    |

Tests that exercise platform-specific code paths go in
`(platform)Test` (e.g. `nativeTest`, `macosArm64Test`); the default is
`commonTest`. Test fixtures move under `src/commonTest/resources/` and
load via the platform resource API.

If a fixture exercises a code path the Kotlin port doesn't yet
implement, port the implementation. Skipping the test is not an
option.

## Copyright Header

Every ported Kotlin file starts with:

```kotlin
package <package-name>

/*
 * Copyright 2017-present, the LALRPOP Project Developers.
 * Copyright (c) 2025 Sydney Renee, The Solace Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

This preserves the original LALRPOP copyright while adding the
maintainer's copyright for the Kotlin port.

## Targets — Kotlin Multiplatform, no JVM

The project ships these targets (see `build.gradle.kts`):

- `macosArm64`, `macosX64`
- `linuxX64`
- `mingwX64`
- `iosArm64`, `iosX64`, `iosSimulatorArm64`
- `js` (browser + nodejs)
- `wasmJs` (browser + nodejs)
- `androidLibrary`

There is no `jvm()` target. The `jvmToolchain(21)` line in the build
script configures the JDK that runs Gradle itself; it does not add a
JVM target.

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

Add a new dependency only when stdlib + the above cannot reproduce
the Rust crate's behavior, and only after confirming the dependency
publishes artifacts for **every** target listed above. Document the
addition in the commit message.

## Hard rules

### No `@Suppress`. Warnings are errors.

Warnings indicate the port is wrong. Fix the cause. Common cases:

- "unused" → the Kotlin code is genuinely dead; un-collapse the Rust
  concept the port flattened.
- "UNCHECKED_CAST" → the Kotlin lost a Rust invariant; encode it in
  the type system.
- "UNUSED_VARIABLE" → port the binding as `_` or destructure without
  it.

### No stubs, no shims, no operator-graded gates

- No `class Foo` with an empty body when the Rust struct has fields.
- No `fun bar() = TODO()` or `fun bar() { error("not implemented") }`.
- No partial ports.
- No "placeholder until X is ready" comments with commented-out code.
- No tests whose pass/fail is determined by an operator-set status
  field.

If a dependency doesn't exist yet, port it first or pick a different
file.

### No no-op shells for Rust constructs the GC subsumes

Rust primitives that exist only to manage memory or interior
mutability — `drop_in_place`, `mem::forget`, `Pin`, `Box<T>`,
`Cell<T>`, `RefCell<T>`, `Arc<T>`, `Rc<T>`, `NonNull<T>`,
`MaybeUninit<T>`, `dyn Trait` — get **deleted** in the port, not
translated as empty `fun dropInPlace() {}` shells. Inline the wrapped
value or use the closest Kotlin idiom (plain reference, `var`,
`kotlinx-atomicfu` ref where threaded). Empty shells inflate symbol
counts without porting any behavior.

### `mod.rs` and reexports

Rust uses `mod.rs` files as glue (`pub mod foo; pub use foo::Bar;`).
When the entire file is reexport plumbing, do **not** port it as
`Mod.kt` and do **not** mirror its `pub use` chain with
`internal typealias Bar = foo.Bar`. Rewire callers to import from the
defining package directly.

A `mod.rs` (or any other file) that contains real type or function
definitions in addition to its `pub use` chain is a normal port
target — only the reexports are dropped.

When upstream Rust **does** declare `pub type X = Y;` (common in
`typing` and similar modules), make a conscious decision: if the alias
carries semantic meaning that's load-bearing in the Rust code, mirror
it 1:1 in Kotlin. If it exists only because the underlying Rust type
was syntactically inconvenient, inline the type and skip the alias.
Default toward mirroring when the Rust source has the alias; default
toward inlining when it doesn't.

Porter-invented typealiases — ones with no corresponding Rust `pub
type` — are forbidden.

### Doc comments are part of the port

Translate Rust doc comments (`///`, `//!`) to KDoc, **including the
Rust syntax inside them**. A comment that mentions `crate::util::Map`,
`Vec<T>`, `Option<&str>`, `Self::foo()`, `cfg(test)`, `#[derive(...)]`,
lifetimes like `'a`, or any other Rust syntax must be rewritten to its
Kotlin equivalent (`BTreeMap`, `List<T>`, `String?`, `foo()`, KDoc
links like `[BTreeMap]`).

Do not delete comments to silence rules. Translate them.

### Don't "rustify" Kotlin

No Rust-named methods (`len()`, `iter()`, `insert()`) on Kotlin stdlib
types — use `size`, `iterator()`, `add()`. No wrapper class around
`BTreeMap` to host Rust naming. No porter-invented typealiases. A
faithful Kotlin port using stdlib idioms is the goal; passing tests
are the gate.

### Do not delegate edits to sub-agents

Anthropic and OpenAI both expose Task / Agent tools that spawn
cheaper, less thoughtful agents for grunt work. Those agents cheat on
translation: they hollow out KDoc, add Kotlin-only filler to inflate
scores, drop semantically load-bearing Rust constructs because they
"look unused," and produce confident summaries that mask the damage.
The only signal back is a tidy report.

**All `.kt` edits happen in your main loop.** The Task / Agent tool
is allowed for searches, file location, and read-only reports — never
for writing or editing source files. If translation volume feels
overwhelming, slow down; do not parallelize.

## Operational rules

### Blast Radius Rule

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

### Other operational rules

- **Do not write to `/tmp` or to project-local `tmp/`.** `tmp/` is
  reserved for read-only upstream Rust oracles (`tmp/lalrpop-rs/`).
  Anything else either lives in `src/` as a committed file or doesn't
  exist.
- **Commit after every file edit.** One file edited → one commit.
  Squash later via `git rebase -i`; never withhold commits up front.
- **Deletions require `git rm` plus reference scrubs.** Plain `rm`
  leaves the file in git and the next branch op restores it; surviving
  references in other files (`[Foo](./Foo.md)`, `// see Foo.md`) tell
  future instances to recreate the file. Both halves are required for
  a deletion to stick.

## Cross-Project Coordination

The `*-kotlin` repos under `kotlinmania/` import each other as
published Maven artifacts. When you touch one, check that the version
pinned by every consumer matches what's published.

```bash
find /Volumes/stuff/Projects/kotlinmania -name "build.gradle.kts" \
  -not -path "*/tmp/*" -not -path "*/build/*" \
  -exec grep -l "io.github.kotlinmania" {} \;
```

If versions are mismatched, raise on Slack before bumping — version
bumps cascade.

## CI

Use `gh` to check workflow status when work affects multiple repos.

```bash
gh run list --repo <owner>/<repo> --workflow ci.yml --limit 5
gh pr checks <pr-number>
```

Read failing logs before claiming the change is done.

## New Ports / Large Work

If you discover a Rust crate that needs porting and isn't yet a project
under `kotlinmania/`, you may create a new `<crate>-kotlin` project
using existing projects as templates. **For any port estimated at more
than ~5,000 lines of Rust**, raise on Slack first with the crate name,
line count, and which existing kotlinmania projects depend on it.
Mention any blockers (missing dependencies, KMP-incompatible Rust
crates, unclear semantics). Wait for Sydney's reply before starting.

## Backend Phases

Upstream LALRPOP emits Rust source code (`src/rust/mod.rs` and
`src/lr1/codegen/{ascent,parse_table}.rs` are its back-end). The final
goal of lalrpop-kotlin is Kotlin source emission. The backend is a
two-phase port.

### Phase 1: Rust-output back-end (transliteration)

Transliterate the upstream backend so the Kotlin port can produce the
same Rust-shaped output. Translate `write!` / `writeln!` calls to
Kotlin calls that still write the corresponding Rust text. Do not
Kotlin-ify the emitted output during phase 1.

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

## Final report

When a run finishes, post a status update via the Slack MCP / skill /
connector. Include:

- Files touched.
- Tests added and passing.
- Blockers that need a human decision.

## Static-analysis sidecars

Two read-only tools live under `tools/`. Neither is a gate; both are
diagnostic.

- **`tools/port_lint/port_lint.py`** — deterministic Kotlin-port lint.
  Each rule was added in response to a real observed bug (collapsed
  emit-comments, self-recursive overrides, sealed-toString shadowing,
  `@Suppress`, JVM imports, snake-case identifiers). HIGH findings
  block; MEDIUM/LOW are for review. Run with
  `python tools/port_lint/port_lint.py src`.

- **`tools/sig_diff/sig_diff.py`** — paired-file function-signature
  dump and family rollup. Useful for parser-generator-style files
  (e.g. the LrGrammar pair, ~1500 functions). Reports per-name counts,
  family rollup (action / reduce / popVariant / etc.), and Rust-only
  vs Kotlin-only name lists. Snake↔camel aware. Treat output as
  diagnostic; the runtime gate is the test suite.

## Code Style

- Default Kotlin formatting (ktlint/IntelliJ defaults).
- 4-space indentation.
- Max line length: 120 characters (flexible for readability).
- Only comment code that needs clarification; do not add redundant
  comments.
- Translate meaningful Rust comments to Kotlin, including any Rust
  syntax inside them.
- Preserve algorithmic explanations and rationale.

## Commit Discipline

- One file edited → one commit.
- Commit message describes what changed in that file.
- No AI branding, no Co-Authored-By lines, no emoji.
- See [CLAUDE.md](./CLAUDE.md) for full rationale.

## References

- [LALRPOP](https://github.com/lalrpop/lalrpop) — upstream Rust implementation
- [LALRPOP Book](https://lalrpop.github.io/lalrpop/)
- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [CLAUDE.md](./CLAUDE.md) — authoritative project rules

## Questions?

For questions about porting strategy or architecture decisions, ask
on Slack.
