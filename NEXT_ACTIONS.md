# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Current Progress:** 0.0% (123/106 files)
- **Matched Files:** 0
- **Average Similarity:** 0.00
- **Critical Issues:** 0 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

1. **lr1.tls** (16 deps)
   - Path: `lr1/tls.rs`
   - Essential for 16 other files

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Next Commands

```bash
# Initialize task queue for systematic porting
cd tools/ast_distance
./ast_distance --init-tasks ../../tmp/lalrpop-rs/lalrpop/src rust ../../tmp/parity-staging kotlin tasks.json ../../AGENTS.md

# Get next high-priority task
./ast_distance --assign tasks.json <agent-id>
```
## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `api.mod` | `api.Mod` | 0 | `api/mod.rs` | `api/Mod.kt` |
| `collections.mod` | `collections.Mod` | 0 | `collections/mod.rs` | `collections/Mod.kt` |
| `free_variables.mod` | `grammar.freevariables.Mod` | 0 | `grammar/free_variables/mod.rs` | `grammar/freevariables/Mod.kt` |
| `grammar.mod` | `grammar.Mod` | 0 | `grammar/mod.rs` | `grammar/Mod.kt` |
| `dfa.mod` | `lexer.dfa.Mod` | 0 | `lexer/dfa/mod.rs` | `lexer/dfa/Mod.kt` |
| `intern_token.mod` | `lexer.interntoken.Mod` | 0 | `lexer/intern_token/mod.rs` | `lexer/interntoken/Mod.kt` |
| `lexer.mod` | `lexer.Mod` | 0 | `lexer/mod.rs` | `lexer/Mod.kt` |
| `nfa.mod` | `lexer.nfa.Mod` | 0 | `lexer/nfa/mod.rs` | `lexer/nfa/Mod.kt` |
| `re.mod` | `lexer.re.Mod` | 0 | `lexer/re/mod.rs` | `lexer/re/Mod.kt` |
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |
| `codegen.mod` | `lr1.codegen.Mod` | 0 | `lr1/codegen/mod.rs` | `lr1/codegen/Mod.kt` |
| `core.mod` | `lr1.core.Mod` | 0 | `lr1/core/mod.rs` | `lr1/core/Mod.kt` |
| `error.mod` | `lr1.error.Mod` | 0 | `lr1/error/mod.rs` | `lr1/error/Mod.kt` |
| `example.mod` | `lr1.example.Mod` | 0 | `lr1/example/mod.rs` | `lr1/example/Mod.kt` |
| `first.mod` | `lr1.first.Mod` | 0 | `lr1/first/mod.rs` | `lr1/first/Mod.kt` |
| `construct.mod` | `lr1.lanetable.construct.Mod` | 0 | `lr1/lane_table/construct/mod.rs` | `lr1/lanetable/construct/Mod.kt` |
| `lane.mod` | `lr1.lanetable.lane.Mod` | 0 | `lr1/lane_table/lane/mod.rs` | `lr1/lanetable/lane/Mod.kt` |
| `lane_table.mod` | `lr1.lanetable.Mod` | 0 | `lr1/lane_table/mod.rs` | `lr1/lanetable/Mod.kt` |
| `context_set.mod` | `lr1.lanetable.table.contextset.Mod` | 0 | `lr1/lane_table/table/context_set/mod.rs` | `lr1/lanetable/table/contextset/Mod.kt` |
| `table.mod` | `lr1.lanetable.table.Mod` | 0 | `lr1/lane_table/table/mod.rs` | `lr1/lanetable/table/Mod.kt` |
| `lr1.mod` | `lr1.Mod` | 0 | `lr1/mod.rs` | `lr1/Mod.kt` |
| `report.mod` | `lr1.report.Mod` | 0 | `lr1/report/mod.rs` | `lr1/report/Mod.kt` |
| `trace.mod` | `lr1.trace.Mod` | 0 | `lr1/trace/mod.rs` | `lr1/trace/Mod.kt` |
| `reduce.mod` | `lr1.trace.reduce.Mod` | 0 | `lr1/trace/reduce/mod.rs` | `lr1/trace/reduce/Mod.kt` |
| `shift.mod` | `lr1.trace.shift.Mod` | 0 | `lr1/trace/shift/mod.rs` | `lr1/trace/shift/Mod.kt` |
| `trace_graph.mod` | `lr1.trace.tracegraph.Mod` | 0 | `lr1/trace/trace_graph/mod.rs` | `lr1/trace/tracegraph/Mod.kt` |
| `message.mod` | `message.Mod` | 0 | `message/mod.rs` | `message/Mod.kt` |
| `cond_comp.mod` | `normalize.condcomp.Mod` | 0 | `normalize/cond_comp/mod.rs` | `normalize/condcomp/Mod.kt` |
| `graph.mod` | `normalize.inline.graph.Mod` | 0 | `normalize/inline/graph/mod.rs` | `normalize/inline/graph/Mod.kt` |
| `inline.mod` | `normalize.inline.Mod` | 0 | `normalize/inline/mod.rs` | `normalize/inline/Mod.kt` |
| `lower.mod` | `normalize.lower.Mod` | 0 | `normalize/lower/mod.rs` | `normalize/lower/Mod.kt` |
| `macro_expand.mod` | `normalize.macroexpand.Mod` | 0 | `normalize/macro_expand/mod.rs` | `normalize/macroexpand/Mod.kt` |
| `normalize.mod` | `normalize.Mod` | 0 | `normalize/mod.rs` | `normalize/Mod.kt` |
| `precedence.mod` | `normalize.precedence.Mod` | 0 | `normalize/precedence/mod.rs` | `normalize/precedence/Mod.kt` |
| `prevalidate.mod` | `normalize.prevalidate.Mod` | 0 | `normalize/prevalidate/mod.rs` | `normalize/prevalidate/Mod.kt` |
| `resolve.mod` | `normalize.resolve.Mod` | 0 | `normalize/resolve/mod.rs` | `normalize/resolve/Mod.kt` |
| `token_check.mod` | `normalize.tokencheck.Mod` | 0 | `normalize/token_check/mod.rs` | `normalize/tokencheck/Mod.kt` |
| `tyinfer.mod` | `normalize.tyinfer.Mod` | 0 | `normalize/tyinfer/mod.rs` | `normalize/tyinfer/Mod.kt` |
| `parser.mod` | `parser.Mod` | 0 | `parser/mod.rs` | `parser/Mod.kt` |
| `rust.mod` | `rust.Mod` | 0 | `rust/mod.rs` | `rust/Mod.kt` |
| `tls.mod` | `tls.Mod` | 0 | `tls/mod.rs` | `tls/Mod.kt` |
| `tok.mod` | `tok.Mod` | 0 | `tok/mod.rs` | `tok/Mod.kt` |

