# lalrpop-kotlin Port — Agent Guidelines

This file contains guidelines for AI agents and human contributors working on the lalrpop-kotlin port.

## Project Context

This is a **line-by-line transliteration port** of [lalrpop/lalrpop](https://github.com/lalrpop/lalrpop) to Kotlin Multiplatform. The goal is semantic parity with the Rust implementation, proven by **byte-identical emitted Rust output** against the upstream `cargo run -p lalrpop` oracle for every grammar in the codegen-parity corpus.

The upstream Rust sources live in `tmp/lalrpop-rs/` and are the reference. Never edit them.

## The Parity Gate

The headline gate for this project is the codegen-parity test under
`src/commonTest/kotlin/.../codegen/`. The Kotlin pipeline (parse →
normalize → lr1 → emit) must produce Rust source byte-identical (modulo
deterministic whitespace) to upstream LALRPOP's output for every grammar
in the corpus. Every entry has an embedded oracle in
`CodegenParityCorpus.kt`; the harness diffs emitted vs oracle byte-by-byte.

`NotWired` is **not** a passing state. An entry without an embedded
oracle is not a real test. Earlier in this project a NotWired test
silently passed for months while emitting wrong output — that exact
failure mode is why structural similarity scoring is no longer the gate.

For files outside the codegen pipeline (collections, message
infrastructure, normalize transforms), the gate is the corresponding
ported Rust test passing in Kotlin against the same fixtures the Rust
tests use.

## ast_distance (sidecar only)

`ast_distance` is useful for coverage accounting and cheat detection, but it is
not the gate. Do not chase cosine/similarity scores; wire the runtime parity
tests (codegen-parity corpus and translated Rust tests) and make them pass.

## General Porting Principles

### 1. Semantic Parity (The "Dishonest Code" Rule)

- **Port the intent and behavior**, not just syntax
- Rust's traits often carry specific formatting contracts, behavioral expectations, or performance characteristics
- Do **not** oversimplify implementations if the original code performed non-trivial work
- Example: Rust's `Display` trait implementations often handle formatting, ANSI codes, truncation - replicate this logic in Kotlin's `toString()` or helper methods
- The proof of semantic parity is the parity gate, not your judgment about whether a function "looks right"

### 2. Research First

- **Do not guess** at the behavior of Rust functions, traits, or types
- Look up official Rust documentation when uncertain
- Rust's type system and traits carry subtle behaviors (buffering, blocking, formatting state, ownership) that aren't obvious from signatures

### 3. Line-by-Line Transliteration

- Maintain file structure and organization from the Rust codebase
- Port modules to packages with equivalent naming (snake_case → camelCase for functions/variables, but preserve file/package structure)
- Preserve comments and documentation (translate to KDoc format)
- Translate Rust syntax inside comments to Kotlin equivalents — do not delete a comment to silence a rule

### 4. Provenance Markers (REQUIRED)

Every ported Kotlin file **must** start with a provenance marker:

```kotlin
// port-lint: source <relative-path-to-rust-file>
package io.github.kotlinmania.lalrpop.<module>

// Rest of file...
```

Examples:
```kotlin
// port-lint: source src/lr1/build.rs
package io.github.kotlinmania.lalrpop.lr1

// port-lint: source src/parser/lrgrammar.rs
package io.github.kotlinmania.lalrpop.parser
```

**Path Format:** The path is relative to `tmp/lalrpop-rs/lalrpop/`. So for a file at `tmp/lalrpop-rs/lalrpop/src/parser/lrgrammar.rs`, use `src/parser/lrgrammar.rs`.

The header documents which Rust file this Kotlin file was translated from. It is the only place provenance is recorded.

### 5. Copyright Headers

**REQUIRED:** Every ported Kotlin file must include this copyright header immediately after the port-lint header:

```kotlin
// port-lint: source <path>
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

This preserves the original Rust copyright while adding the maintainer's copyright for the Kotlin port.

### 6. Documentation

- Translate Rust doc comments (`///`, `//!`) to KDoc format
- Preserve examples, code blocks, and explanatory text
- Update references to Rust-specific concepts (e.g., "this trait" → "this interface")
- Translate Rust syntax inside comments to Kotlin equivalents (`Vec<T>` → `List<T>`, `Option<&str>` → `String?`, `crate::util::Map` → `[BTreeMap]`, etc.). Don't strip comments to avoid translating them.
- Add KDoc for public APIs

### 7. TODO Policy (IMPORTANT)

**DO NOT add TODO comments without explicit user approval.**

- If you cannot implement something fully, ASK the user first
- Research Rust documentation before adding TODOs
- Look for similar patterns in the codebase
- Prefer complete implementations or approved placeholder strategies
- TODOs should only be added when the user explicitly approves them

### 8. No no-op shells for Rust constructs the GC subsumes

Rust primitives that exist only to manage memory or interior mutability — `drop_in_place`, `mem::forget`, `Pin`, `Box<T>`, `Cell<T>`, `RefCell<T>`, `Arc<T>`, `Rc<T>`, `NonNull<T>`, `MaybeUninit<T>`, `dyn Trait` — get **deleted** in the port, not translated as empty `fun dropInPlace() {}`. Inline the wrapped value or use the closest Kotlin idiom (plain reference, `var`, atomic ref where threaded). Empty shells look like a port without porting any behavior.

### 9. mod.rs files do not become Mod.kt

Never transliterate `mod.rs` into a Kotlin `Mod.kt` file. When upstream's `mod.rs` is pure reexport glue (`pub mod foo; pub use foo::Bar;`), drop the file and rewire callers to import from the real defining package. When `mod.rs` contains real implementation alongside its reexports, re-home the implementation into properly-named files (`Tok.kt`, `Tokenizer.kt`, `Escapes.kt`) in the right package. Do not synthesize a `typealias Bar = foo.Bar` to mimic the reexport — those create ambiguity errors and debugging spiderwebs.

If a re-homed file has no single Rust source file (because the implementation came from `mod.rs`), use `// port-lint: ignore` and a short prose note like "transliterated from upstream module root." Do not put `// port-lint: source .../mod.rs` on a re-homed file.

## Kotlin-Specific Guidelines

### CRITICAL: Kotlin Multiplatform — NO JAVA in commonMain

**This is a Kotlin Multiplatform project targeting JVM, Native, and JS.**

**No Java-specific code in commonMain:**
- NO `import java.*`
- NO `java.util.concurrent.*`
- NO `java.io.*`
- NO `java.nio.*`
- NO JVM-only APIs

**Use Kotlin Multiplatform alternatives:**
- `kotlin.collections.*` for collections
- `kotlinx.atomicfu` for atomic operations
- `kotlinx.coroutines` for concurrency
- `expect`/`actual` for platform-specific implementations
- Pure Kotlin standard library APIs

Tests belong in `commonTest`, not `commonMain`. Platform-specific code goes in `<platform>Main` (e.g., `jvmMain`, `nativeMain`) and only when absolutely necessary.

### Naming Conventions

- **Files:** Match Rust file names but use PascalCase for Kotlin files (e.g., `module.rs` → `Module.kt`, `lrgrammar.rs` → `LrGrammar.kt`)
- **Packages:** Mirror Rust crate structure (e.g., `lalrpop::lr1` → `io.github.kotlinmania.lalrpop.lr1`)
- **Types:** PascalCase (same as Rust)
- **Functions/Variables:** camelCase (Rust snake_case → Kotlin camelCase)
- **Constants:** UPPER_SNAKE_CASE (same as Rust)

### Error Handling

- Rust `Result<T, E>` → Kotlin `Result<T>` with appropriate exception types, or sealed-class Either where the error variant carries data
- Preserve error messages and context from Rust
- LALRPOP's `Message` infrastructure in `src/message/` is the project's diagnostic system — port it faithfully, don't replace with bare exceptions

### Collections

- Rust `Vec<T>` → Kotlin `MutableList<T>` or `List<T>` (prefer immutable when possible)
- Rust `HashMap<K, V>` → Kotlin `MutableMap<K, V>` or `Map<K, V>`
- Rust `BTreeMap<K, V>` → ordered map (kotlinx-collections-immutable's `PersistentMap` or `sortedMapOf`)
- Use `kotlinx-collections-immutable` for persistent collections where Rust uses immutable structures

### Concurrency

- Rust `Arc<T>` / `Mutex<T>` → plain reference + atomicfu where Rust uses interior mutability across threads
- Rust async → Kotlin `suspend fun`
- Be mindful of thread safety — Kotlin Multiplatform has different concurrency models per platform

### Traits vs Interfaces

- Rust trait → Kotlin interface (with default implementations where appropriate)
- Rust trait objects (`Box<dyn Trait>`) → Kotlin interface references
- Rust trait bounds → Kotlin generic constraints (`where T : SomeTrait`)

### Macros

- Rust procedural macros cannot be directly ported
- Implement equivalent functionality using Kotlin's language features:
  - Code generation if needed
  - Inline functions
  - Delegation
  - Annotation processing (JVM-only)

## Testing

- Port Rust tests to Kotlin tests in `commonTest`
- Use `kotlin.test` for multiplatform compatibility
- Maintain test structure and organization
- The codegen-parity harness is the headline gate; widen the corpus rather than trusting toy grammars

## Building

```bash
# Build all targets
./gradlew build

# Run tests (includes the codegen-parity gate)
./gradlew test

# Specific platform
./gradlew macosArm64Test
./gradlew jvmTest
```

Compilation is **not** the gate; it's a precondition for running the parity tests. Don't chase a green compile by inventing shims, deleting code, or relaxing types — fix the underlying translation.

## Code Style

### Formatting

- Default Kotlin formatting (ktlint/IntelliJ defaults)
- 4-space indentation
- Max line length: 120 characters (flexible for readability)

### Commenting

- Only comment code that needs clarification
- Do not add redundant comments
- Translate meaningful Rust comments to Kotlin
- Translate Rust syntax inside comments to Kotlin syntax — do not strip comments to avoid translating them
- Preserve algorithmic explanations and rationale

### Prefer Kotlin Idioms

- Use Kotlin's standard library when equivalent to Rust's
- Use data classes for simple structs
- Use sealed classes for Rust enums with data
- Use object for Rust unit structs with no data
- Don't add Rust-named methods (`len()`, `iter()`, `insert()`) on top of Kotlin stdlib types — use `size`, `iterator()`, `add()` directly

## Dependencies

This port uses minimal dependencies:

- `kotlinx-coroutines-core` — async/concurrency
- `kotlinx-serialization` — serialization (if needed)
- `kotlinx-collections-immutable` — persistent collections
- `kotlinx-datetime` — date/time handling
- `kotlinx-atomicfu` — atomic operations

Add new dependencies only when necessary and document the rationale.

## Platform-Specific Code

When porting platform-specific Rust code:

- Use `expect`/`actual` declarations for platform differences
- Place common code in `commonMain`
- Platform-specific implementations in `<platform>Main` (e.g., `jvmMain`, `nativeMain`)

## Commit Discipline

- One file edited → one commit
- Commit message describes what changed in that file
- No AI branding, no Co-Authored-By lines, no emoji
- See [CLAUDE.md](./CLAUDE.md) for the full rule and rationale

## References

- [LALRPOP](https://github.com/lalrpop/lalrpop) — upstream Rust implementation
- [LALRPOP Book](https://lalrpop.github.io/lalrpop/)
- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [CLAUDE.md](./CLAUDE.md) — project-specific rules and the parity gate
- [PORTING.md](./PORTING.md) — porting workflow

## Questions?

For questions about porting strategy or architecture decisions, ask the user.
