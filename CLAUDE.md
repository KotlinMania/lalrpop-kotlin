# Claude Code Project Instructions

## Project Overview

This is **lalrpop-kotlin**, a line-by-line port of LALRPOP (the Rust LR(1) parser generator by Niko Matsakis and the LALRPOP Project Developers) to Kotlin Multiplatform. The upstream Rust sources are in `tmp/lalrpop-rs/` and we're building the Kotlin implementation in `src/`.

Upstream: https://github.com/lalrpop/lalrpop

## Critical Workflows

### 1. Task Assignment (DISABLED)

The `ast_distance` swarm task-assignment flags are **disabled** in this workspace:
`--init-tasks`, `--tasks`, `--assign`, `--complete`, `--release`, `--agent`, `--task-file`, `--override`.

Use file comparisons and directory-level checks instead:

```bash
# Deep comparison over directories
./tools/ast_distance/ast_distance --deep tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin

# Rank missing/priority (if available in your ast_distance build)
./tools/ast_distance/ast_distance --rank tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin
```

### 2. Port-Lint Headers (REQUIRED)

Every Kotlin file MUST start with:

```kotlin
// port-lint: source <path-relative-to-tmp/lalrpop-rs/lalrpop>
package io.github.kotlinmania.lalrpop.module
```

Example:
```kotlin
// port-lint: source src/lr1/build.rs
package io.github.kotlinmania.lalrpop.lr1
```

This is how `ast_distance` tracks provenance — which Rust file each Kotlin file was translated from. Without this header, the file is invisible to all port analysis tooling. Never remove, move, or alter the header unless the file is being re-targeted to a different Rust source.

### 3. Quality Verification

After porting a file, verify with:

```bash
./tools/ast_distance/ast_distance \
  tmp/lalrpop-rs/lalrpop/src/lr1/build.rs rust \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop/lr1/Build.kt kotlin
```

**Target: Similarity ≥ 0.85** (excellent port)

## Build Commands

```bash
# Full build
./gradlew build

# Run tests
./gradlew test

# Specific platform
./gradlew jvmTest
./gradlew macosArm64Test
```

## Porting Guidelines

See [AGENTS.md](./AGENTS.md) for complete porting patterns.

**Key principles:**
1. **Semantic parity** - Port behavior, not just syntax
2. **Research first** - Don't guess at Rust semantics
3. **Line-by-line** - Maintain file structure
4. **Documentation** - Translate all doc comments to KDoc
5. **No oversimplification** - Replicate grammar-language semantics, LR(1) table construction, macro expansion, and code generation faithfully

## STRICT RULES — Translation, Not Engineering

### This is a translation project.

Every Kotlin file is a line-by-line port of a Rust source file in `tmp/lalrpop-rs/lalrpop/src/`. The `// port-lint: source` header at the top of each `.kt` file tells you which Rust file it came from. That header is how `ast_distance` tracks provenance — never remove or change it.

**When you encounter a compile error, the fix is ALWAYS in the Rust source.** Do not invent solutions to make the Kotlin compiler happy. Do not make classes extend Exception because it "seems right." Do not change visibility, delete code, or add shims. Read the corresponding Rust file and translate faithfully.

### No code stubs. Period.

Do not write stub files, placeholder classes, empty implementations, or skeleton code. Every line of Kotlin must be a faithful translation of the corresponding Rust source. If you can't fully translate a file, don't create it at all — a missing file is better than a stub that will conflict with the real implementation later.

This means:
- **No `class Foo` with an empty body** when the Rust struct has fields and methods
- **No `fun bar() = TODO()`** or `fun bar() { error("not implemented") }`
- **No partial ports** that translate the class declaration but skip the methods
- **No "placeholder until the lr1 builder is ready"** comments with commented-out code

If a dependency doesn't exist yet, port that dependency first. If the dependency chain is too deep, pick a different file to work on.

### Use ast_distance for all analysis.

The `tools/ast_distance/ast_distance` tool is the single source of truth for:
- `--import-map`: Finding unresolved types, duplicate definitions, and ambiguous imports
- `--symbols-duplicates`: Finding duplicate symbol definitions across files
- `--compiler-fixup`: Suggesting import fixes from gradle error output
- `--symbol-parity`: Comparing Rust vs Kotlin symbol coverage
- `--deep`: Full cross-language AST comparison report

### Do NOT pipe, redirect, or wrap ast_distance output.

The tool detects and rejects stdout piping (`|`), redirection (`>`), and wrappers like `script -q`. Run it directly in the terminal. Read its output directly from the tool result.

### Do NOT create typealias re-export files.

Root-package `.kt` files that re-export types from subpackages via `typealias` cause massive ambiguity errors across the codebase. Types like `Grammar`, `NonterminalString`, `TypeRepr` must be imported from their actual defining package, not from a convenience re-export.

### Do NOT create stub/placeholder types.

If a type doesn't exist yet, port the file that defines it. Don't create placeholder classes like `class Grammar` or `class Session` in random files — they conflict with real implementations when those files get ported.

## Progress Tracking

```bash
# Overall progress
./tools/ast_distance/ast_distance --deep tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin

# Missing files by priority
./tools/ast_distance/ast_distance --missing tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin
```

## Naming Conventions

- **Files:** PascalCase (e.g., `build.rs` → `Build.kt`, `first.rs` → `First.kt`)
- **Packages:** Mirror Rust module structure (e.g., `src/lr1/build.rs` → `lalrpop.lr1`)
- **Functions/Variables:** camelCase (Rust snake_case → Kotlin camelCase)
- **Types:** PascalCase
- **Constants:** UPPER_SNAKE_CASE

## Common Patterns

### Rust → Kotlin Mappings

- `Result<T, E>` → `Result<T>` with exceptions, or sealed-class Either where the error variant carries data
- `Option<T>` → `T?` (nullable types)
- `Vec<T>` → `MutableList<T>` or `List<T>`
- `HashMap<K, V>` → `MutableMap<K, V>` or `Map<K, V>`
- `BTreeMap<K, V>` → `sortedMapOf` / kotlinx-collections-immutable ordered map
- `Rc<T>`, `Arc<T>` → plain reference; Kotlin has GC. Use atomic refs only when Rust uses interior mutability across threads
- `RefCell<T>` → mutable property (single-threaded) or atomic ref (threaded)
- Trait → Interface
- Enum with data → Sealed class
- Struct → Data class

### Error Handling

Preserve error messages and context. Use Kotlin's `Result` type or throw appropriate exceptions. LALRPOP's `Message` infrastructure in `src/message/` is the project's diagnostic system — port it faithfully, don't replace with bare exceptions.

## File Organization

```
src/
├── commonMain/kotlin/io/github/kotlinmania/lalrpop/
│   ├── api/         # Port of tmp/lalrpop-rs/lalrpop/src/api/
│   ├── build/       # Port of tmp/lalrpop-rs/lalrpop/src/build/
│   ├── collections/ # Port of tmp/lalrpop-rs/lalrpop/src/collections/
│   ├── grammar/     # Port of tmp/lalrpop-rs/lalrpop/src/grammar/
│   ├── lexer/       # Port of tmp/lalrpop-rs/lalrpop/src/lexer/
│   ├── lr1/         # Port of tmp/lalrpop-rs/lalrpop/src/lr1/
│   ├── message/     # Port of tmp/lalrpop-rs/lalrpop/src/message/
│   ├── normalize/   # Port of tmp/lalrpop-rs/lalrpop/src/normalize/
│   ├── parser/      # Port of tmp/lalrpop-rs/lalrpop/src/parser/
│   ├── rust/        # Port of tmp/lalrpop-rs/lalrpop/src/rust/
│   ├── tls/         # Port of tmp/lalrpop-rs/lalrpop/src/tls/
│   ├── tok/         # Port of tmp/lalrpop-rs/lalrpop/src/tok/
│   └── ...
└── commonTest/kotlin/io/github/kotlinmania/lalrpop/
```

## Testing

- Port all Rust tests
- Use `kotlin.test` for multiplatform compatibility
- Maintain test structure and coverage
- LALRPOP's integration tests live in `tmp/lalrpop-rs/lalrpop-test/` — plan to mirror those once the core is ported

## Dependencies

Minimal approach:
- kotlinx-coroutines-core
- kotlinx-serialization
- kotlinx-collections-immutable
- kotlinx-datetime

Add new dependencies only when necessary.

## Documentation References

- [AGENTS.md](./AGENTS.md) - Detailed porting patterns
- [PORTING.md](./PORTING.md) - Quick reference workflow
- [README.md](./README.md) - Project overview
- [NOTICE](./NOTICE) - Attribution and licensing
- Upstream: https://github.com/lalrpop/lalrpop
- LALRPOP Book: https://lalrpop.github.io/lalrpop/

## Commit Messages

Follow Sydney's style:
- No AI branding or attribution
- Clear, descriptive messages focused on what changed and why
- No "Co-Authored-By" lines
- No emoji or robot references

Example:
```
Add lr1/first port from Rust

Port first.rs to First.kt with semantic parity. Includes:
- FIRST set construction
- Nullable nonterminal propagation
- AST similarity: 0.89
```

## When to Ask

Ask the user for clarification if:
- Rust semantics are unclear and documentation doesn't help
- Architectural decisions affect multiple files
- Build configuration needs changes
- You encounter blocking issues
- **You want to add a TODO comment** - get user approval first

## TODO Policy

**DO NOT add TODO comments without explicit user approval.**

If you encounter something that cannot be fully implemented:
1. Ask the user if a TODO is appropriate
2. If approved, use the format: `// TODO: <description>`
3. Better: Ask the user how they want to handle the incomplete functionality
4. Best: Complete the implementation or find an alternative approach

Avoid TODOs by:
- Researching Rust documentation thoroughly
- Looking at similar patterns in the codebase
- Asking the user for guidance on complex Rust idioms
- Using placeholder implementations only when explicitly approved

## Porting Order

Start with leaf modules (few dependencies) and build upward:

1. `util`, `tls`, `session`, `log`, `file_text` — infrastructure leaves
2. `collections/` — data structures used everywhere
3. `message/` — diagnostic system
4. `grammar/` — AST for the grammar language
5. `tok/`, `lexer/` — tokenizer and lexer generation
6. `parser/` — the bootstrapped LALRPOP grammar parser
7. `normalize/` — grammar transformations (macros, etc.)
8. `lr1/` — LR(1) state machine construction
9. `rust/` — code emission
10. `build/`, `api/` — top-level driver

Use `ast_distance --missing` once the tool is wired up to confirm priority order.

## Generator Backend Phases

Upstream LALRPOP emits Rust source code (`src/rust/mod.rs` and
`src/lr1/codegen/{ascent,parse_table}.rs` are its back-end). The final
goal of lalrpop-kotlin is Kotlin source emission, but the backend is a
special case where we need two explicit phases.

### Phase 1: Rust-output parity witness

First, transliterate the upstream backend so the Kotlin port can produce
the same Rust-shaped output as upstream LALRPOP for the same grammar.
This is intentional, not a failure of the port. Keeping a Rust emitter
during phase 1 gives us a strong parity witness for table layout, state
machine shape, production/reduction ordering, symbol variant numbering,
and error paths before the emitted language changes.

What this means in practice:

- **`src/rust/mod.rs` → `src/commonMain/.../rust/Rust.kt`**: preserve the
  upstream writer semantics and Rust output tokens (`pub fn`, `match`,
  `impl`, etc.) while transliterating the implementation into Kotlin.
  Keep the `// port-lint: source src/rust/mod.rs` header.
- **`src/lr1/codegen/ascent.rs` and `parse_table.rs`**: preserve the
  backend control flow and the emitted Rust syntax during phase 1. The
  `write!` / `writeln!` calls should become Kotlin calls that still write
  the corresponding Rust text.
- Phase-1 backend work should be judged by generated Rust-output parity,
  not by whether the string literals already contain Kotlin syntax.

### Phase 1 completion: finish the generator-output comparison

**Phase 1 is not complete until lalrpop-kotlin's emitted Rust source is
byte-identical (modulo deterministic whitespace) to upstream LALRPOP's
emitted Rust source for every grammar in the test corpus.** This is the
hard parity gate. Until this lands, do not start phase 2 — score-padding
the Kotlin port without a parity witness loses the only ground truth we
have for the back-end.

The reference implementation lives in `tmp/lalrpop-rs/`. Treat it as the
oracle. The `tmp/lalrpop-rs/target/lrgrammar.rs` checked into the tree
is one such oracle artifact: it is upstream LALRPOP's Rust output for
`lrgrammar.lalrpop`, and lalrpop-kotlin should produce the same file
when fed the same grammar.

Concrete steps to drive phase 1 to done:

1. **Pick a corpus.** Start with the grammars already in
   `tmp/lalrpop-rs/lalrpop/src/parser/lrgrammar.lalrpop` and the
   integration grammars under `tmp/lalrpop-rs/lalrpop-test/src/`.
   Each grammar has a known-good Rust output produced by upstream
   LALRPOP — generate it with `cargo run -p lalrpop ...` against the
   `tmp/lalrpop-rs/` checkout if it is not already cached.
2. **Run lalrpop-kotlin against the same input.** Add or extend a
   harness under `src/commonTest/kotlin/.../codegen/` that takes a
   `.lalrpop` file, runs the Kotlin pipeline (parse → normalize → lr1
   → emit), and writes the emitted Rust to a temp file.
3. **Diff against the upstream output.** The harness must compare the
   two files byte-for-byte (after stripping trailing whitespace and
   normalizing line endings). Any divergence is a phase-1 bug; fix it
   in lalrpop-kotlin, never in the upstream oracle.
4. **Triage divergences from the leaves up.** When a diff shows up,
   bisect by section: token enum, action functions, state table,
   reduction dispatch, error recovery, header. Resolve sections in
   order — a wrong token enum will cascade into every later section.
5. **Lock parity in CI.** Once a grammar matches, snapshot the
   upstream output under `src/commonTest/resources/codegen-parity/`
   and add a regression test that re-runs the comparison on every
   build. The snapshot is the contract; do not regenerate it from
   lalrpop-kotlin (that would let drift hide).
6. **Track the corpus to completion.** Maintain a checklist of
   grammars and their parity status (matching / divergent / not yet
   wired up). Phase 1 is done when every grammar in the checklist is
   `matching` and a CI job blocks regressions.

Rules while finishing phase 1:

- **Do not edit `tmp/lalrpop-rs/`** to make a diff disappear. That
  directory is the oracle. If upstream's output looks wrong, the bug
  is in lalrpop-kotlin or in your understanding of the Rust source.
- **Do not normalize away semantic differences.** Whitespace and
  line-ending normalization is fine; renaming a generated function,
  reordering match arms, or dropping a `#[allow(...)]` attribute is
  not. Those are real divergences and they will bite during phase 2.
- **Do not declare parity from a single grammar.** A toy grammar can
  hit every code path in `parse_table.rs` and miss most of `ascent.rs`,
  or vice versa. The corpus must exercise both back-ends.
- **Do not skip the macro-expanded grammars.** `normalize/macro_expand`
  produces synthetic non-terminals whose codegen ordering is the most
  common source of subtle divergence. Include at least one grammar
  with `#[inline]` rules, one with parameterized macros, and one with
  precedence/associativity declarations.
- **Do not gate phase 2 on "close enough".** Either the bytes match
  or phase 1 is not finished. Document any deliberate, approved
  divergence (e.g. a deterministic Kotlin-side improvement that
  upstream cannot match) in `PORTING.md` with rationale, and exclude
  it from the diff via a targeted normalizer — never via a
  whole-file allowlist.

### Phase 2: Kotlin output backend

After phase 1 can prove parity, add a Kotlin-emitting backend and make
that backend the project default. Do not overwrite the phase-1 Rust
emitter blindly: keep it quarantined as a reference/debug backend unless
the user explicitly approves its removal.

Recommended structure:

- Keep the Rust-output backend under an explicit namespace such as
  `codegen.rustTarget` or `rustTarget` so it cannot be mistaken for the
  default Kotlin backend.
- Add a Kotlin-output backend under a distinct namespace such as
  `codegen.kotlinTarget`; prefer names like `KotlinWriter` or
  `KotlinOutput` over a bare `Kotlin.kt` name.
- In phase 2 only, replace output leaves that write Rust syntax
  (`return Ok(...)`, `try!(...)`, struct literals, lifetime parameters)
  with Kotlin syntax (`return Result.success(...)`, Kotlin constructors,
  generic parameters, `when`, etc.).

The two-pass discipline still holds: read the Rust source, port the
surrounding logic line-by-line, and only change emitted syntax in the
phase-2 Kotlin backend. Document each phase-2 substitution inline so a
future reader can audit what changed and why.

### Consumers

The primary consumer is `starlark-kotlin`'s `grammar.lalrpop`. Once
the Kotlin-emitting back-end works, regenerating that grammar should
produce drop-in replacements for the hand-transliterated `Grammar.kt`
and `GrammarReducers.kt` (~12.5k lines). Future kotlinmania projects
that want a parser write a `.lalrpop` grammar and run lalrpop-kotlin
on it — no Rust ever appears in the pipeline.
