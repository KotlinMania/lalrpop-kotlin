# port-lint Proposed Changes

**Generated:** 2026-04-28
**Source:** tmp/lalrpop-rs/lalrpop/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/lalrpop

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/normalize/tokenCheck/TokenCheckTest.kt` | `// port-lint: source normalize/tokenCheck/test.rs` | `// port-lint: source normalize/token_check/test.rs` | `normalize/token_check/test.rs` | `port-lint provenance header matched only after fallback normalization: 'normalize/tokenCheck/test.rs' vs expected 'normalize/token_check/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/lr1/laneTable/LaneTableTest.kt` | `// port-lint: source lr1/laneTable/test.rs` | `// port-lint: source lr1/lane_table/test.rs` | `lr1/lane_table/test.rs` | `port-lint provenance header matched only after fallback normalization: 'lr1/laneTable/test.rs' vs expected 'lr1/lane_table/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/normalize/normUtil/NormUtilCheckBetweenBraces.kt` | `// port-lint: source normalize/normUtil.rs` | `// port-lint: source normalize/norm_util.rs` | `normalize/norm_util.rs` | `port-lint provenance header matched only after fallback normalization: 'normalize/normUtil.rs' vs expected 'normalize/norm_util.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/normalize/macroExpand/MacroExpandTest.kt` | `// port-lint: source normalize/macroExpand/test.rs` | `// port-lint: source normalize/macro_expand/test.rs` | `normalize/macro_expand/test.rs` | `port-lint provenance header matched only after fallback normalization: 'normalize/macroExpand/test.rs' vs expected 'normalize/macro_expand/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/lr1/trace/traceGraph/TraceGraphTest.kt` | `// port-lint: source lr1/trace/traceGraph/test.rs` | `// port-lint: source lr1/trace/trace_graph/test.rs` | `lr1/trace/trace_graph/test.rs` | `port-lint provenance header matched only after fallback normalization: 'lr1/trace/traceGraph/test.rs' vs expected 'lr1/trace/trace_graph/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/normalize/condComp/CondCompTest.kt` | `// port-lint: source normalize/condComp/test.rs` | `// port-lint: source normalize/cond_comp/test.rs` | `normalize/cond_comp/test.rs` | `port-lint provenance header matched only after fallback normalization: 'normalize/condComp/test.rs' vs expected 'normalize/cond_comp/test.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/grammar/freeVariables/FreeVariablesTest.kt` | `// port-lint: source grammar/freeVariables/test.rs` | `// port-lint: source grammar/free_variables/test.rs` | `grammar/free_variables/test.rs` | `port-lint provenance header matched only after fallback normalization: 'grammar/freeVariables/test.rs' vs expected 'grammar/free_variables/test.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/lalrpop/runtime/Lib.kt` | `// port-lint: source ../lalrpop-util/src/lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: '../lalrpop-util/src/lib.rs' vs expected 'lib.rs'` |
| `src/commonTest/kotlin/io/github/kotlinmania/lalrpop/runtime/LibTest.kt` | `// port-lint: source ../lalrpop-util/src/lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: '../lalrpop-util/src/lib.rs' vs expected 'lib.rs'` |
