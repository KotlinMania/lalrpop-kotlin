# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 63/106 (59.4%)
- **Function parity:** 2117/2157 matched (target 2337) — 98.1%
- **Class/type parity:** 139/147 matched (target 375) — 94.6%
- **Combined symbol parity:** 2256/2304 matched (target 2712) — 97.9%
- **Average inline-code cosine:** 0.33 (function body across 62 matched files)
- **Average documentation cosine:** 0.45 (doc text across 62 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 61 files with <0.60 function similarity
- **Needs Review:** 1 files with 0.60-0.84 function similarity
- **Excellent:** 1 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. lr1.tls
- **Similarity:** 0.13 (needs 72% improvement)
- **Dependencies:** 16
- **Priority Score:** 16000409.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Action:** Deep review - likely missing major functionality

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lr1.tls

- **Target:** `lr1.Tls`
- **Similarity:** 0.13
- **Dependents:** 16
- **Priority Score:** 16000409.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 2. session

- **Target:** `lalrpop.Session`
- **Similarity:** 0.41
- **Dependents:** 8
- **Priority Score:** 8010806.0
- **Functions:** 5/6 matched (target 7)
- **Missing functions:** `test`
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 3. collections.set

- **Target:** `collections.Set`
- **Similarity:** 0.00
- **Dependents:** 6
- **Priority Score:** 6000210.0
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 4. lr1.state_graph

- **Target:** `lr1.StateGraph`
- **Similarity:** 0.44
- **Dependents:** 5
- **Priority Score:** 5000505.5
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 5. lr1.interpret

- **Target:** `lr1.Interpret`
- **Similarity:** 0.18
- **Dependents:** 4
- **Priority Score:** 4041408.2
- **Functions:** 7/11 matched (target 8)
- **Missing functions:** `fmt`, `fmt`, `reduction`, `reduction`
- **Types:** 3/3 matched (target 4)
- **Missing types:** _none_

### 6. file_text

- **Target:** `lalrpop.FileText`
- **Similarity:** 0.20
- **Dependents:** 2
- **Priority Score:** 2031208.0
- **Functions:** 8/10 matched (target 9)
- **Missing functions:** `test`, `fmt`
- **Types:** 1/2 matched (target 1)
- **Missing types:** `Repeat`
- **Tests:** 0/1 matched

### 7. lr1.lookahead

- **Target:** `lr1.Lookahead`
- **Similarity:** 0.33
- **Dependents:** 2
- **Priority Score:** 2023806.6
- **Functions:** 31/31 matched (target 51)
- **Missing functions:** _none_
- **Types:** 5/7 matched (target 8)
- **Missing types:** `Item`, `IntoIter`

### 8. test_util

- **Target:** `lalrpop.TestUtil [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2010710.0
- **Functions:** 5/6 matched (target 9)
- **Missing functions:** `fmt`
- **Types:** 1/1 matched (target 6)
- **Missing types:** _none_

### 9. construct.state_set

- **Target:** `stateset.StateSet`
- **Similarity:** 0.20
- **Dependents:** 2
- **Priority Score:** 2010708.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 2)
- **Missing types:** `Value`

### 10. grammar.pattern

- **Target:** `grammar.Pattern`
- **Similarity:** 0.21
- **Dependents:** 2
- **Priority Score:** 2001007.9
- **Functions:** 7/7 matched (target 23)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 14)
- **Missing types:** _none_

### 11. message.message

- **Target:** `message.Message`
- **Similarity:** 0.39
- **Dependents:** 2
- **Priority Score:** 2000606.1
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 12. api.test

- **Target:** `api.ApiTestSupport`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1121210.0
- **Functions:** 0/10 matched (target 1)
- **Missing functions:** `new`, `drop`, `setup`, `remove_local_generated_files`, `verify_file`, `test_process_root`, `test_process_src`, `test_process_file`, `test_explicit_in_out`, `test_cargo_dir_conventions`
- **Types:** 0/2 matched (target 0)
- **Missing types:** `GenFileLoc`, `TestState`
- **Tests:** 0/5 matched

### 13. grammar.parse_tree

- **Target:** `parsetree.ParseTree`
- **Similarity:** 0.18
- **Dependents:** 1
- **Priority Score:** 1051908.1
- **Functions:** 73/77 matched (target 158)
- **Missing functions:** `fmt`, `fmt`, `fmt`, `fmt`
- **Types:** 42/42 matched (target 88)
- **Missing types:** _none_

### 14. grammar.repr

- **Target:** `repr.Repr`
- **Similarity:** 0.18
- **Dependents:** 1
- **Priority Score:** 1046508.2
- **Functions:** 42/46 matched (target 70)
- **Missing functions:** `fmt`, `fmt`, `fmt`, `fmt`
- **Types:** 19/19 matched (target 38)
- **Missing types:** _none_

### 15. collections.multimap

- **Target:** `collections.Multimap`
- **Similarity:** 0.26
- **Dependents:** 1
- **Priority Score:** 1032007.4
- **Functions:** 14/16 matched (target 17)
- **Missing functions:** `into_iter`, `into_iter`
- **Types:** 3/4 matched (target 8)
- **Missing types:** `Item`
- **Tests:** 2/2 matched

### 16. construct.merge

- **Target:** `construct.Merge`
- **Similarity:** 0.29
- **Dependents:** 1
- **Priority Score:** 1001307.1
- **Functions:** 11/11 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 17. message.horiz

- **Target:** `horiz.Horiz`
- **Similarity:** 0.35
- **Dependents:** 1
- **Priority Score:** 1000606.5
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 18. message.styled

- **Target:** `styled.Styled`
- **Similarity:** 0.36
- **Dependents:** 1
- **Priority Score:** 1000606.4
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 19. message.vert

- **Target:** `vert.Vert`
- **Similarity:** 0.39
- **Dependents:** 1
- **Priority Score:** 1000606.1
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 20. message.text

- **Target:** `text.Text`
- **Similarity:** 0.35
- **Dependents:** 1
- **Priority Score:** 1000506.6
- **Functions:** 4/4 matched (target 5)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 21. message.indent

- **Target:** `indent.Indent`
- **Similarity:** 0.38
- **Dependents:** 1
- **Priority Score:** 1000506.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 22. message.wrap

- **Target:** `wrap.Wrap`
- **Similarity:** 0.41
- **Dependents:** 1
- **Priority Score:** 1000505.9
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 23. collections.map

- **Target:** `collections.Map`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1000210.0
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 24. parser.lrgrammar

- **Target:** `parser.LrGrammar`
- **Similarity:** 0.23
- **Dependents:** 0
- **Priority Score:** 201907.7
- **Functions:** 1501/1506 matched (target 1524)
- **Missing functions:** `action`, `goto`, `expected_tokens`, `expected_tokens_from_states`, `___token_to_symbol`
- **Types:** 13/13 matched (target 116)
- **Missing types:** _none_
- **Lint issues:** 503

### 25. message.builder

- **Target:** `builder.Builder`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 24006.6
- **Functions:** 29/30 matched (target 31)
- **Missing functions:** `from`
- **Types:** 9/10 matched (target 9)
- **Missing types:** `End`

### 26. main

- **Target:** `lalrpop.Main`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 21607.2
- **Functions:** 11/13 matched (target 20)
- **Missing functions:** `os_vec`, `parse_args_slice`
- **Types:** 3/3 matched (target 5)
- **Missing types:** _none_
- **Tests:** 8/10 matched

### 27. codegen.parse_table

- **Target:** `codegen.ParseTable`
- **Similarity:** 0.26
- **Dependents:** 0
- **Priority Score:** 13007.4
- **Functions:** 26/27 matched (target 30)
- **Missing functions:** `fmt`
- **Types:** 3/3 matched (target 7)
- **Missing types:** _none_

### 28. lane_table.test

- **Target:** `lanetable.LaneTableTest`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 11606.1
- **Functions:** 15/16 matched (target 17)
- **Missing functions:** `sym`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 29. example.test

- **Target:** `example.ExampleTest`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 11006.1
- **Functions:** 9/10 matched
- **Missing functions:** `term`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 30. dfa.overlap

- **Target:** `dfa.Overlap`
- **Similarity:** 0.38
- **Dependents:** 0
- **Priority Score:** 10706.2
- **Functions:** 6/7 matched (target 8)
- **Missing functions:** `null`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/5 matched

### 31. tok.test

- **Target:** `tok.TokTest`
- **Similarity:** 0.22
- **Dependents:** 0
- **Priority Score:** 6607.8
- **Functions:** 65/65 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_
- **Tests:** 62/62 matched

### 32. prevalidate.test

- **Target:** `prevalidate.PrevalidateTest`
- **Similarity:** 0.30
- **Dependents:** 0
- **Priority Score:** 2607.0
- **Functions:** 26/26 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 25/25 matched

### 33. codegen.ascent

- **Target:** `codegen.Ascent [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 2410.0
- **Functions:** 22/22 matched (target 23)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 34. token_check.test

- **Target:** `tokencheck.TokenCheckTest`
- **Similarity:** 0.29
- **Dependents:** 0
- **Priority Score:** 1907.1
- **Functions:** 19/19 matched (target 20)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched

### 35. tyinfer.test

- **Target:** `tyinfer.TyinferTest`
- **Similarity:** 0.30
- **Dependents:** 0
- **Priority Score:** 1707.0
- **Functions:** 17/17 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 15/15 matched

### 36. nfa.test

- **Target:** `nfa.NfaTest`
- **Similarity:** 0.29
- **Dependents:** 0
- **Priority Score:** 1207.1
- **Functions:** 12/12 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched

### 37. normalize.norm_util

- **Target:** `normutil.NormUtil`
- **Similarity:** 0.35
- **Dependents:** 0
- **Priority Score:** 1106.5
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 8)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 38. codegen.base

- **Target:** `codegen.Base`
- **Similarity:** 0.25
- **Dependents:** 0
- **Priority Score:** 1007.5
- **Functions:** 9/9 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 39. error.test

- **Target:** `error.ErrorTest`
- **Similarity:** 0.47
- **Dependents:** 0
- **Priority Score:** 905.3
- **Functions:** 9/9 matched (target 10)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 40. dfa.test

- **Target:** `dfa.DfaTest`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 808.1
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 41. resolve.test

- **Target:** `resolve.ResolveTest`
- **Similarity:** 0.59
- **Dependents:** 0
- **Priority Score:** 804.1
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 42. util

- **Target:** `lalrpop.Util`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 708.1
- **Functions:** 4/4 matched (target 7)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 43. generate

- **Target:** `lalrpop.Generate`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 706.6
- **Functions:** 5/5 matched (target 7)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_

### 44. reduce.test

- **Target:** `reduce.ReduceTest`
- **Similarity:** 0.47
- **Dependents:** 0
- **Priority Score:** 705.3
- **Functions:** 7/7 matched (target 8)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 45. first.test

- **Target:** `first.FirstTest`
- **Similarity:** 0.58
- **Dependents:** 0
- **Priority Score:** 704.2
- **Functions:** 7/7 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 46. codegen.test_all

- **Target:** `codegen.TestAll`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 606.3
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 47. kernel_set

- **Target:** `lalrpop.KernelSet`
- **Similarity:** 0.18
- **Dependents:** 0
- **Priority Score:** 508.2
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 48. parser.test

- **Target:** `parser.ParserTest`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 507.2
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 49. precedence.test

- **Target:** `precedence.PrecedenceTest`
- **Similarity:** 0.32
- **Dependents:** 0
- **Priority Score:** 506.8
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 50. log

- **Target:** `lalrpop.Log`
- **Similarity:** 0.54
- **Dependents:** 0
- **Priority Score:** 504.6
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 51. macro_expand.test

- **Target:** `macroexpand.MacroExpandTest`
- **Similarity:** 0.40
- **Dependents:** 0
- **Priority Score:** 406.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 52. graph.test

- **Target:** `graph.GraphTest`
- **Similarity:** 0.35
- **Dependents:** 0
- **Priority Score:** 306.5
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/3 matched

### 53. inline.test

- **Target:** `inline.InlineTest`
- **Similarity:** 0.41
- **Dependents:** 0
- **Priority Score:** 305.9
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 54. message.test

- **Target:** `message.MessageTest`
- **Similarity:** 0.66
- **Dependents:** 0
- **Priority Score:** 303.4
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 55. re.test

- **Target:** `lexer.ReTest`
- **Similarity:** 0.33
- **Dependents:** 0
- **Priority Score:** 206.7
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 56. trace_graph.test

- **Target:** `tracegraph.TraceGraphTest`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 206.6
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 57. shift.test

- **Target:** `shift.ShiftTest`
- **Similarity:** 0.54
- **Dependents:** 0
- **Priority Score:** 204.6
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 58. nfa.interpret

- **Target:** `nfa.Interpret`
- **Similarity:** 0.55
- **Dependents:** 0
- **Priority Score:** 204.5
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 59. cond_comp.test

- **Target:** `condcomp.CondCompTest`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 108.1
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 60. report.test

- **Target:** `report.ReportTest`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 106.6
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 61. dfa.interpret

- **Target:** `dfa.Interpret`
- **Similarity:** 0.37
- **Dependents:** 0
- **Priority Score:** 106.3
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 62. free_variables.test

- **Target:** `grammar.FreeVariablesTest`
- **Similarity:** 0.58
- **Dependents:** 0
- **Priority Score:** 104.2
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 63. grammar.consts

- **Target:** `grammar.Consts`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 0.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

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

