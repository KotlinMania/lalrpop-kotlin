# port-lint Proposed Changes

**Generated:** 2026-04-29
**Source:** tmp/lalrpop-rs/lalrpop/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/lalrpop

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonMain/kotlin/io/github/kotlinmania/lalrpop/lr1/Build.kt` | `// port-lint: source lr1/build/mod.rs` | `// port-lint: source message/mod.rs` | `message/mod.rs` | `port-lint provenance header matched only by basename: 'lr1/build/mod.rs' vs expected 'message/mod.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/lalrpop/message/AsciiCanvas.kt` | `// port-lint: source external/ascii-canvas/src/lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: 'external/ascii-canvas/src/lib.rs' vs expected 'lib.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/lalrpop/lr1/BuildLalr.kt` | `// port-lint: source lr1/build_lalr/mod.rs` | `// port-lint: source grammar/mod.rs` | `grammar/mod.rs` | `port-lint provenance header matched only by basename: 'lr1/build_lalr/mod.rs' vs expected 'grammar/mod.rs'` |
