# port-lint Proposed Changes

**Generated:** 2026-08-27
**Source:** lalrpop/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/lalrpop

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/lr1/BuildLalrTest.kt` | `// port-lint: source lr1/build_lalr/test.rs` | `// port-lint: source api/test.rs` | `api/test.rs` | `port-lint provenance header matched only by basename: 'lr1/build_lalr/test.rs' vs expected 'api/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/runtime/LibTest.kt` | `// port-lint: source lalrpop-util/src/lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: 'lalrpop-util/src/lib.rs' vs expected 'lib.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/lr1/BuildTest.kt` | `// port-lint: source lr1/build/test.rs` | `// port-lint: source lr1/lane_table/table/context_set/test.rs` | `lr1/lane_table/table/context_set/test.rs` | `port-lint provenance header matched only by basename: 'lr1/build/test.rs' vs expected 'lr1/lane_table/table/context_set/test.rs'` |
