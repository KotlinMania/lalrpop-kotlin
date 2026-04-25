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
./tools/ast_distance/ast_distance --deep tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin kotlin

# Rank missing/priority (if available in your ast_distance build)
./tools/ast_distance/ast_distance --rank tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin kotlin
```

### 2. Port-Lint Headers (REQUIRED)

Every Kotlin file MUST start with:

```kotlin
// port-lint: source <path-relative-to-tmp/lalrpop-rs/lalrpop>
package io.github.kotlinmania.lalrpop_kotlin.module
```

Example:
```kotlin
// port-lint: source src/lr1/build.rs
package io.github.kotlinmania.lalrpop_kotlin.lr1
```

This is how `ast_distance` tracks provenance — which Rust file each Kotlin file was translated from. Without this header, the file is invisible to all port analysis tooling. Never remove, move, or alter the header unless the file is being re-targeted to a different Rust source.

### 3. Quality Verification

After porting a file, verify with:

```bash
./tools/ast_distance/ast_distance \
  tmp/lalrpop-rs/lalrpop/src/lr1/build.rs rust \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin/lr1/Build.kt kotlin
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
./tools/ast_distance/ast_distance --deep tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin kotlin

# Missing files by priority
./tools/ast_distance/ast_distance --missing tmp/lalrpop-rs/lalrpop/src rust src/commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin kotlin
```

## Naming Conventions

- **Files:** PascalCase (e.g., `build.rs` → `Build.kt`, `first.rs` → `First.kt`)
- **Packages:** Mirror Rust module structure (e.g., `src/lr1/build.rs` → `lalrpop_kotlin.lr1`)
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
├── commonMain/kotlin/io/github/kotlinmania/lalrpop_kotlin/
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
└── commonTest/kotlin/io/github/kotlinmania/lalrpop_kotlin/
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

## The Generator Emits Kotlin

Upstream LALRPOP emits Rust source code (`src/rust/mod.rs` and
`src/lr1/codegen/{ascent,parse_table}.rs` are its back-end). Faithfully
transliterating those modules verbatim would leave us with a
Kotlin-native tool that still produces Rust — useless to every consumer
in the kotlinmania ecosystem.

**The generator must emit Kotlin.** This is the explicit goal of
lalrpop-kotlin, not a post-parity nice-to-have. The parser-table layout,
the LR(1) construction, the AST normalization, the macro expansion —
all of that is faithful translation. The **back-end** is where we
diverge from upstream by design: instead of writing Rust syntax to the
output stream, the back-end writes Kotlin syntax to the output stream.

What this means in practice:

- **`src/rust/mod.rs` → `src/commonMain/.../rust/Rust.kt`**: the
  upstream `RustWrite` helper produces Rust tokens (`pub fn`, `match`,
  `impl`, etc.). Our port replaces these with the Kotlin equivalents
  (`fun`, `when`, `class`, etc.). The *shape* of the helper (push/pop
  indentation, write_table_row, emit comments) is preserved from the
  Rust source — only the literal output strings change. Keep the
  `// port-lint: source src/rust/mod.rs` header.
- **`src/lr1/codegen/ascent.rs` and `parse_table.rs`**: same rule. The
  control flow, the state-machine layout, the action/goto tables — all
  ported faithfully. The `write!` / `writeln!` calls that produce Rust
  syntax (`return Ok(...)`, `try!(...)`, struct literals, lifetime
  parameters) are replaced with calls that produce Kotlin syntax
  (`return Result.success(...)`, `try { ... }`, data class
  constructors, generic parameters). Keep the port-lint headers.
- **A `lalrpop-runtime-kotlin` module** holds the runtime types the
  emitted parsers depend on (`ParseError`, `Lexer`, action enums,
  symbol stack). Upstream LALRPOP has a `lalrpop-util` crate for the
  same purpose — port that to Kotlin alongside the generator.

The two-pass discipline still holds for *everything except the
literal output strings*: read the Rust source, port the surrounding
logic line-by-line, and only at the leaves (where Rust syntax tokens
are written) substitute Kotlin syntax. Document each substitution
inline so a future reader can audit what changed and why:

```kotlin
// Rust:  write!(w, "pub fn {}<{}>(...)", name, type_params)?;
// Kotlin emits `fun` instead of `pub fn` and uses `<>` for generics.
write(w, "fun $name<$typeParams>(...)")
```

This keeps provenance auditable: every Kotlin emission decision is
traceable back to the Rust line it replaces, and `ast_distance` can
still score the surrounding control flow even though the output
literals diverge.

### Consumers

The primary consumer is `starlark-kotlin`'s `grammar.lalrpop`. Once
the Kotlin-emitting back-end works, regenerating that grammar should
produce drop-in replacements for the hand-transliterated `Grammar.kt`
and `GrammarReducers.kt` (~12.5k lines). Future kotlinmania projects
that want a parser write a `.lalrpop` grammar and run lalrpop-kotlin
on it — no Rust ever appears in the pipeline.
