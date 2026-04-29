# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 64/106 (60.4%)
- **Function parity:** 2063/2073 matched (target 2312) — 99.5%
- **Class/type parity:** 137/147 matched (target 381) — 93.2%
- **Combined symbol parity:** 2200/2220 matched (target 2693) — 99.1%
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 6 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. lr1.tls
- **Similarity:** 0.67 (needs 18% improvement)
- **Dependencies:** 16
- **Priority Score:** 16000403.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Action:** Review and complete missing sections

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lr1.tls

- **Target:** `lr1.Tls`
- **Similarity:** 0.67
- **Dependents:** 16
- **Priority Score:** 16000403.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 2. session

- **Target:** `lalrpop.Session`
- **Similarity:** 0.86
- **Dependents:** 8
- **Priority Score:** 8010801.5
- **Functions:** 5/6 matched (target 7)
- **Missing functions:** `test`
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 3. collections.set

- **Target:** `collections.Set`
- **Similarity:** 0.71
- **Dependents:** 6
- **Priority Score:** 6000203.0
- **Functions:** 1/1 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 4. lr1.state_graph

- **Target:** `lr1.StateGraph`
- **Similarity:** 0.89
- **Dependents:** 5
- **Priority Score:** 5000501.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 5. lr1.interpret

- **Target:** `lr1.Interpret`
- **Similarity:** 0.82
- **Dependents:** 4
- **Priority Score:** 4001201.8
- **Functions:** 9/9 matched (target 12)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 6)
- **Missing types:** _none_

### 6. lr1.lookahead

- **Target:** `lr1.Lookahead`
- **Similarity:** 0.81
- **Dependents:** 2
- **Priority Score:** 2023501.9
- **Functions:** 28/28 matched (target 51)
- **Missing functions:** _none_
- **Types:** 5/7 matched (target 8)
- **Missing types:** `Item`, `IntoIter`

### 7. file_text

- **Target:** `lalrpop.FileText`
- **Similarity:** 0.73
- **Dependents:** 2
- **Priority Score:** 2021202.8
- **Functions:** 8/10 matched (target 11)
- **Missing functions:** `test`, `fmt`
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 8. construct.state_set

- **Target:** `stateset.StateSet`
- **Similarity:** 0.60
- **Dependents:** 2
- **Priority Score:** 2010704.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 2)
- **Missing types:** `Error`

### 9. test_util

- **Target:** `lalrpop.TestUtil`
- **Similarity:** 0.63
- **Dependents:** 2
- **Priority Score:** 2010703.6
- **Functions:** 5/6 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 1/1 matched (target 6)
- **Missing types:** _none_

### 10. grammar.pattern

- **Target:** `grammar.Pattern`
- **Similarity:** 0.59
- **Dependents:** 2
- **Priority Score:** 2000604.1
- **Functions:** 3/3 matched (target 14)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 14)
- **Missing types:** _none_

### 11. message.message

- **Target:** `message.MessageContent`
- **Similarity:** 0.79
- **Dependents:** 2
- **Priority Score:** 2000602.1
- **Functions:** 5/5 matched (target 9)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 12. grammar.repr

- **Target:** `repr.Repr`
- **Similarity:** 0.66
- **Dependents:** 1
- **Priority Score:** 1015303.4
- **Functions:** 33/34 matched (target 67)
- **Missing functions:** `from`
- **Types:** 19/19 matched (target 38)
- **Missing types:** _none_

### 13. collections.multimap

- **Target:** `collections.Multimap`
- **Similarity:** 0.59
- **Dependents:** 1
- **Priority Score:** 1011304.1
- **Functions:** 9/9 matched (target 17)
- **Missing functions:** _none_
- **Types:** 3/4 matched (target 8)
- **Missing types:** `Item`
- **Tests:** 1/1 matched

### 14. grammar.parse_tree

- **Target:** `parsetree.ParseTree`
- **Similarity:** 0.57
- **Dependents:** 1
- **Priority Score:** 1008204.3
- **Functions:** 40/40 matched (target 112)
- **Missing functions:** _none_
- **Types:** 42/42 matched (target 88)
- **Missing types:** _none_

### 15. construct.merge

- **Target:** `construct.Merge`
- **Similarity:** 0.87
- **Dependents:** 1
- **Priority Score:** 1001301.3
- **Functions:** 11/11 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 16. api.test

- **Target:** `api.ApiTest`
- **Similarity:** 0.78
- **Dependents:** 1
- **Priority Score:** 1001202.2
- **Functions:** 10/10 matched (target 18)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 6)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 17. message.horiz

- **Target:** `horiz.Horiz`
- **Similarity:** 0.77
- **Dependents:** 1
- **Priority Score:** 1000602.2
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 18. message.vert

- **Target:** `vert.Vert`
- **Similarity:** 0.86
- **Dependents:** 1
- **Priority Score:** 1000601.4
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 19. message.styled

- **Target:** `styled.Styled`
- **Similarity:** 0.88
- **Dependents:** 1
- **Priority Score:** 1000601.2
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 20. message.text

- **Target:** `text.Text`
- **Similarity:** 0.85
- **Dependents:** 1
- **Priority Score:** 1000501.6
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 21. message.wrap

- **Target:** `wrap.Wrap`
- **Similarity:** 0.85
- **Dependents:** 1
- **Priority Score:** 1000501.5
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 22. message.indent

- **Target:** `indent.Indent`
- **Similarity:** 0.88
- **Dependents:** 1
- **Priority Score:** 1000501.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 23. collections.map

- **Target:** `collections.Map`
- **Similarity:** 0.70
- **Dependents:** 1
- **Priority Score:** 1000202.9
- **Functions:** 1/1 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 24. parser.lrgrammar

- **Target:** `parser.LrGrammar`
- **Similarity:** 0.70
- **Dependents:** 0
- **Priority Score:** 201103.0
- **Functions:** 1498/1498 matched (target 1506)
- **Missing functions:** _none_
- **Types:** 8/13 matched (target 111)
- **Missing types:** `Error`, `Token`, `Success`, `StateIndex`, `Action`
- **Lint issues:** 874

### 25. main

- **Target:** `lalrpop.Main`
- **Similarity:** 0.74
- **Dependents:** 0
- **Priority Score:** 31602.6
- **Functions:** 11/13 matched (target 20)
- **Missing functions:** `os_vec`, `parse_args_slice`
- **Types:** 2/3 matched (target 4)
- **Missing types:** `Err`
- **Tests:** 8/10 matched

### 26. message.builder

- **Target:** `builder.Builder`
- **Similarity:** 0.85
- **Dependents:** 0
- **Priority Score:** 13101.5
- **Functions:** 20/21 matched (target 31)
- **Missing functions:** `from`
- **Types:** 10/10 matched
- **Missing types:** _none_

### 27. codegen.parse_table

- **Target:** `codegen.ParseTable`
- **Similarity:** 0.82
- **Dependents:** 0
- **Priority Score:** 13001.8
- **Functions:** 26/27 matched (target 30)
- **Missing functions:** `fmt`
- **Types:** 3/3 matched (target 7)
- **Missing types:** _none_

### 28. dfa.overlap

- **Target:** `dfa.Overlap`
- **Similarity:** 0.64
- **Dependents:** 0
- **Priority Score:** 10703.6
- **Functions:** 6/7 matched (target 8)
- **Missing functions:** `null`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/5 matched

### 29. tok.test

- **Target:** `tok.TokTest`
- **Similarity:** 0.62
- **Dependents:** 0
- **Priority Score:** 6603.8
- **Functions:** 65/65 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_
- **Tests:** 62/62 matched

### 30. prevalidate.test

- **Target:** `prevalidate.PrevalidateTest`
- **Similarity:** 0.98
- **Dependents:** 0
- **Priority Score:** 2600.2
- **Functions:** 26/26 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 25/25 matched

### 31. codegen.ascent

- **Target:** `codegen.Ascent`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 2401.3
- **Functions:** 22/22 matched (target 23)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 32. token_check.test

- **Target:** `tokencheck.TokenCheckTest`
- **Similarity:** 0.82
- **Dependents:** 0
- **Priority Score:** 1901.8
- **Functions:** 19/19 matched (target 20)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched

### 33. tyinfer.test

- **Target:** `tyinfer.TyinferTest`
- **Similarity:** 0.62
- **Dependents:** 0
- **Priority Score:** 1703.8
- **Functions:** 17/17 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 15/15 matched

### 34. lane_table.test

- **Target:** `lanetable.LaneTableTest`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 1601.3
- **Functions:** 16/16 matched (target 17)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 35. nfa.test

- **Target:** `nfa.NfaTest`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 1202.7
- **Functions:** 12/12 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched

### 36. normalize.norm_util

- **Target:** `normutil.NormUtil`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 1102.0
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 8)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 37. example.test

- **Target:** `example.ExampleTest`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 1001.3
- **Functions:** 10/10 matched (target 11)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 38. codegen.base

- **Target:** `codegen.Base`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 1001.3
- **Functions:** 9/9 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 39. error.test

- **Target:** `error.ErrorTest`
- **Similarity:** 0.92
- **Dependents:** 0
- **Priority Score:** 900.8
- **Functions:** 9/9 matched (target 10)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 40. dfa.test

- **Target:** `dfa.DfaTest`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 802.7
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 41. resolve.test

- **Target:** `resolve.ResolveTest`
- **Similarity:** 0.96
- **Dependents:** 0
- **Priority Score:** 800.4
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 42. reduce.test

- **Target:** `reduce.ReduceTest`
- **Similarity:** 0.90
- **Dependents:** 0
- **Priority Score:** 701.0
- **Functions:** 7/7 matched (target 8)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 43. generate

- **Target:** `lalrpop.Generate`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 700.9
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_

### 44. first.test

- **Target:** `first.FirstTest`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 700.9
- **Functions:** 7/7 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 45. codegen.test_all

- **Target:** `codegen.TestAll`
- **Similarity:** 0.83
- **Dependents:** 0
- **Priority Score:** 601.7
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 46. kernel_set

- **Target:** `lalrpop.KernelSet`
- **Similarity:** 0.65
- **Dependents:** 0
- **Priority Score:** 503.5
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 47. parser.test

- **Target:** `parser.ParserTest`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 503.2
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 48. util

- **Target:** `lalrpop.Util`
- **Similarity:** 0.71
- **Dependents:** 0
- **Priority Score:** 502.9
- **Functions:** 2/2 matched (target 7)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 49. precedence.test

- **Target:** `precedence.PrecedenceTest`
- **Similarity:** 0.76
- **Dependents:** 0
- **Priority Score:** 502.4
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 50. log

- **Target:** `lalrpop.Log`
- **Similarity:** 0.97
- **Dependents:** 0
- **Priority Score:** 500.3
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 51. macro_expand.test

- **Target:** `macroexpand.MacroExpandTest`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 403.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 52. graph.test

- **Target:** `graph.GraphTest`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 302.7
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/3 matched

### 53. inline.test

- **Target:** `inline.InlineTest`
- **Similarity:** 0.77
- **Dependents:** 0
- **Priority Score:** 302.3
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 54. message.test

- **Target:** `message.MessageTest`
- **Similarity:** 0.96
- **Dependents:** 0
- **Priority Score:** 300.4
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 55. re.test

- **Target:** `lexer.ReTest`
- **Similarity:** 0.71
- **Dependents:** 0
- **Priority Score:** 202.9
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 56. trace_graph.test

- **Target:** `tracegraph.TraceGraphTest`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 202.0
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 57. nfa.interpret

- **Target:** `nfa.Interpret`
- **Similarity:** 0.84
- **Dependents:** 0
- **Priority Score:** 201.6
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 58. shift.test

- **Target:** `shift.ShiftTest`
- **Similarity:** 0.92
- **Dependents:** 0
- **Priority Score:** 200.8
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 59. cond_comp.test

- **Target:** `condcomp.CondCompTest`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 103.2
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 60. report.test

- **Target:** `report.ReportTest`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 102.0
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 61. dfa.interpret

- **Target:** `dfa.Interpret`
- **Similarity:** 0.89
- **Dependents:** 0
- **Priority Score:** 101.1
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 62. free_variables.test

- **Target:** `grammar.FreeVariablesTest`
- **Similarity:** 0.95
- **Dependents:** 0
- **Priority Score:** 100.5
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 63. grammar.consts

- **Target:** `grammar.Consts [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 64. context_set.test

- **Target:** `lr1.BuildTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 13)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_

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
./ast_distance --init-tasks ../../tmp/lalrpop-rs/lalrpop/src rust ../../src/commonMain/kotlin/io/github/kotlinmania/lalrpop kotlin tasks.json ../../AGENTS.md

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

