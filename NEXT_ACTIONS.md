# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Current Progress:** 95.3% (123/106 files)
- **Matched Files:** 101
- **Average Similarity:** 0.60
- **Critical Issues:** 58 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. lr1.tls
- **Similarity:** 0.82 (needs 3% improvement)
- **Dependencies:** 16
- **Priority Score:** 16000402.0
- **Functions:** 3/3 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_
- **Action:** Minor refinements needed

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lr1.tls

- **Target:** `lr1.Tls`
- **Similarity:** 0.82
- **Dependents:** 16
- **Priority Score:** 16000402.0
- **Functions:** 3/3 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
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
- **Similarity:** 0.21
- **Dependents:** 6
- **Priority Score:** 6000208.0
- **Functions:** 1/1 matched
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
- **Similarity:** 0.78
- **Dependents:** 4
- **Priority Score:** 4001202.2
- **Functions:** 9/9 matched (target 11)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 6)
- **Missing types:** _none_
- **Lint issues:** 1

### 6. lr1.lookahead

- **Target:** `lr1.Lookahead`
- **Similarity:** 0.76
- **Dependents:** 2
- **Priority Score:** 2043502.5
- **Functions:** 26/28 matched (target 49)
- **Missing functions:** `fmt`, `into_iter`
- **Types:** 5/7 matched (target 8)
- **Missing types:** `Item`, `IntoIter`

### 7. construct.state_set

- **Target:** `stateSet.StateSet`
- **Similarity:** 0.59
- **Dependents:** 2
- **Priority Score:** 2020704.1
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/3 matched (target 1)
- **Missing types:** `Value`, `Error`

### 8. file_text

- **Target:** `lalrpop.FileText`
- **Similarity:** 0.76
- **Dependents:** 2
- **Priority Score:** 2011202.4
- **Functions:** 9/10 matched (target 12)
- **Missing functions:** `test`
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 9. test_util

- **Target:** `lalrpop.TestUtil`
- **Similarity:** 0.61
- **Dependents:** 2
- **Priority Score:** 2010703.9
- **Functions:** 5/6 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 1/1 matched (target 5)
- **Missing types:** _none_

### 10. grammar.pattern

- **Target:** `pattern.Pattern`
- **Similarity:** 0.44
- **Dependents:** 2
- **Priority Score:** 2010605.5
- **Functions:** 2/3 matched (target 21)
- **Missing functions:** `fmt`
- **Types:** 3/3 matched (target 14)
- **Missing types:** _none_

### 11. message.message

- **Target:** `message.MessageContent`
- **Similarity:** 0.67
- **Dependents:** 2
- **Priority Score:** 2010603.2
- **Functions:** 4/5 matched
- **Missing functions:** `fmt`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 12. grammar.parse_tree

- **Target:** `parseTree.ParseTree [STUB]`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1038210.0
- **Functions:** 38/40 matched (target 109)
- **Missing functions:** `from`, `fmt`
- **Types:** 41/42 matched (target 87)
- **Missing types:** `MatchSymbol`

### 13. grammar.repr

- **Target:** `repr.Repr [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1025310.0
- **Functions:** 32/34 matched (target 61)
- **Missing functions:** `fmt`, `from`
- **Types:** 19/19 matched (target 38)
- **Missing types:** _none_

### 14. collections.multimap

- **Target:** `multimap.Multimap`
- **Similarity:** 0.59
- **Dependents:** 1
- **Priority Score:** 1021304.1
- **Functions:** 9/9 matched (target 17)
- **Missing functions:** _none_
- **Types:** 2/4 matched (target 7)
- **Missing types:** `Item`, `IntoIter`
- **Tests:** 1/1 matched

### 15. message.styled

- **Target:** `styled.Styled`
- **Similarity:** 0.73
- **Dependents:** 1
- **Priority Score:** 1010602.7
- **Functions:** 4/5 matched
- **Missing functions:** `fmt`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 16. construct.merge

- **Target:** `construct.Merge`
- **Similarity:** 0.87
- **Dependents:** 1
- **Priority Score:** 1001301.3
- **Functions:** 11/11 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 17. api.test

- **Target:** `api.ApiTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 1
- **Priority Score:** 1001210.0
- **Functions:** 10/10 matched (target 15)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 18. message.horiz

- **Target:** `horiz.Horiz`
- **Similarity:** 0.77
- **Dependents:** 1
- **Priority Score:** 1000602.2
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 19. message.vert

- **Target:** `vert.Vert`
- **Similarity:** 0.86
- **Dependents:** 1
- **Priority Score:** 1000601.4
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 20. message.text

- **Target:** `text.Text`
- **Similarity:** 0.75
- **Dependents:** 1
- **Priority Score:** 1000502.5
- **Functions:** 4/4 matched
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
- **Similarity:** 0.21
- **Dependents:** 1
- **Priority Score:** 1000207.9
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 24. parser.lrgrammar

- **Target:** `parser.LrGrammar [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 251110.0
- **Functions:** 1498/1498 matched (target 1506)
- **Missing functions:** _none_
- **Types:** 3/13 matched (target 107)
- **Missing types:** `___Symbol`, `Location`, `Error`, `Token`, `TokenIndex`, `Success`, `StateIndex`, `Action`, `ReduceIndex`, `NonterminalIndex`
- **Lint issues:** 619

### 25. core.mod

- **Target:** `core.Core [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 133610.0
- **Functions:** 15/16 matched (target 24)
- **Missing functions:** `fmt`
- **Types:** 8/20 matched (target 10)
- **Missing types:** `Lr0Item`, `Lr1Item`, `Lr0Items`, `Lr1Items`, `Lr0State`, `Lr1State`, `Lr0Conflict`, `Lr1Conflict`, `Lr0TableConstructionError`, `Lr1TableConstructionError`, `LrResult`, `Lr1Result`

### 26. nfa.mod

- **Target:** `nfa.Nfa [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 54810.0
- **Functions:** 32/35 matched (target 65)
- **Missing functions:** `partial_cmp`, `cmp`, `len`
- **Types:** 11/13 matched
- **Missing types:** `State`, `Item`
- **Lint issues:** 2

### 27. dfa.mod

- **Target:** `dfa.Dfa [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 32310.0
- **Functions:** 10/11 matched (target 20)
- **Missing functions:** `fmt`
- **Types:** 10/12 matched (target 16)
- **Missing types:** `DfaKernelSet`, `Index`

### 28. main

- **Target:** `lalrpop.Main`
- **Similarity:** 0.74
- **Dependents:** 0
- **Priority Score:** 31602.6
- **Functions:** 11/13 matched (target 20)
- **Missing functions:** `os_vec`, `parse_args_slice`
- **Types:** 2/3 matched (target 4)
- **Missing types:** `Err`
- **Tests:** 8/10 matched

### 29. precedence.mod

- **Target:** `precedence.Precedence [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 31310.0
- **Functions:** 6/8 matched (target 9)
- **Missing functions:** `fmt`, `from_str`
- **Types:** 4/5 matched (target 7)
- **Missing types:** `Err`

### 30. parser.mod

- **Target:** `parser.Parser [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 30710.0
- **Functions:** 3/5 matched (target 8)
- **Missing functions:** `parse_type_ref`, `parse_where_clauses`
- **Types:** 1/2 matched (target 6)
- **Missing types:** `ParseError`
- **Tests:** 0/2 matched

### 31. tok.mod

- **Target:** `tok.Tok [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 23210.0
- **Functions:** 25/25 matched (target 32)
- **Missing functions:** _none_
- **Types:** 5/7 matched (target 68)
- **Missing types:** `State`, `Item`
- **Lint issues:** 3

### 32. message.builder

- **Target:** `builder.Builder`
- **Similarity:** 0.85
- **Dependents:** 0
- **Priority Score:** 23101.5
- **Functions:** 20/21 matched (target 31)
- **Missing functions:** `from`
- **Types:** 9/10 matched (target 9)
- **Missing types:** `End`

### 33. report.mod

- **Target:** `report.Report [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 22610.0
- **Functions:** 21/22 matched (target 28)
- **Missing functions:** `new`
- **Types:** 3/4 matched (target 10)
- **Missing types:** `ConflictStateMap`
- **Lint issues:** 1

### 34. trace_graph.mod

- **Target:** `traceGraph.TraceGraph [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 22310.0
- **Functions:** 15/16 matched (target 25)
- **Missing functions:** `fmt`
- **Types:** 6/7 matched (target 8)
- **Missing types:** `Item`

### 35. example.mod

- **Target:** `example.Example [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 22010.0
- **Functions:** 13/15 matched
- **Missing functions:** `paint_unstyled`, `fmt`
- **Types:** 5/5 matched (target 7)
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 36. tls.mod

- **Target:** `tls.Tls [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20910.0
- **Functions:** 5/7 matched (target 10)
- **Missing functions:** `test`, `test_string`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Tests:** 0/2 matched

### 37. normalize.mod

- **Target:** `normalize.Normalize [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20610.0
- **Functions:** 3/4 matched (target 6)
- **Missing functions:** `normalize_without_validating`
- **Types:** 1/2 matched
- **Missing types:** `NormResult`
- **Tests:** 0/1 matched

### 38. re.mod

- **Target:** `re.Re [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 20410.0
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/2 matched (target 0)
- **Missing types:** `Regex`, `RegexError`

### 39. codegen.parse_table

- **Target:** `codegen.ParseTable [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 13010.0
- **Functions:** 26/27 matched (target 30)
- **Missing functions:** `fmt`
- **Types:** 3/3 matched (target 7)
- **Missing types:** _none_

### 40. error.mod

- **Target:** `error.Error [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 12610.0
- **Functions:** 23/23 matched (target 24)
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 9)
- **Missing types:** `TokenConflict`

### 41. table.mod

- **Target:** `table.Table [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 11110.0
- **Functions:** 8/9 matched (target 11)
- **Missing functions:** `fmt`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 42. dfa.overlap

- **Target:** `dfa.Overlap [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10710.0
- **Functions:** 6/7 matched (target 8)
- **Missing functions:** `null`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/5 matched

### 43. context_set.mod

- **Target:** `contextSet.ContextSet [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10610.0
- **Functions:** 4/4 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/2 matched
- **Missing types:** `OverlappingLookahead`

### 44. util

- **Target:** `lalrpop.Util`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 10508.7
- **Functions:** 1/2 matched (target 4)
- **Missing functions:** `fmt`
- **Types:** 3/3 matched
- **Missing types:** _none_

### 45. intern_token.mod

- **Target:** `internToken.InternToken [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10110.0
- **Functions:** 0/1 matched (target 2)
- **Missing functions:** `compile`
- **Types:** 0/0 matched
- **Missing types:** _none_

### 46. tok.test

- **Target:** `tok.TokTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 6610.0
- **Functions:** 65/65 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_
- **Tests:** 62/62 matched

### 47. api.mod

- **Target:** `api.Api [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 2910.0
- **Functions:** 28/28 matched (target 29)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 48. prevalidate.test

- **Target:** `prevalidate.PrevalidateTest`
- **Similarity:** 0.98
- **Dependents:** 0
- **Priority Score:** 2600.2
- **Functions:** 26/26 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 25/25 matched

### 49. codegen.ascent

- **Target:** `codegen.Ascent [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 2410.0
- **Functions:** 22/22 matched (target 23)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 50. macro_expand.mod

- **Target:** `macroExpand.MacroExpand [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 2110.0
- **Functions:** 20/20 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 51. rust.mod

- **Target:** `rust.Rust [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 2010.0
- **Functions:** 17/17 matched (target 21)
- **Missing functions:** _none_
- **Types:** 3/3 matched
- **Missing types:** _none_

### 52. token_check.test

- **Target:** `tokenCheck.TokenCheckTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.82
- **Dependents:** 0
- **Priority Score:** 1901.8
- **Functions:** 19/19 matched (target 20)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `normalize/tokenCheck/test.rs` vs expected `normalize/token_check/test.rs`
- **Proposed provenance header:** `// port-lint: source normalize/token_check/test.rs` (current: `// port-lint: source normalize/tokenCheck/test.rs`)
- **Lint issues:** 1

### 53. tyinfer.test

- **Target:** `tyinfer.TyinferTest`
- **Similarity:** 0.62
- **Dependents:** 0
- **Priority Score:** 1703.8
- **Functions:** 17/17 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 15/15 matched

### 54. lane_table.test

- **Target:** `laneTable.LaneTableTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 1601.3
- **Functions:** 16/16 matched (target 17)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `lr1/laneTable/test.rs` vs expected `lr1/lane_table/test.rs`
- **Proposed provenance header:** `// port-lint: source lr1/lane_table/test.rs` (current: `// port-lint: source lr1/laneTable/test.rs`)
- **Lint issues:** 1

### 55. lower.mod

- **Target:** `lower.Lower [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1410.0
- **Functions:** 13/13 matched (target 14)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 56. resolve.mod

- **Target:** `resolve.Resolve [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1410.0
- **Functions:** 11/11 matched (target 14)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 6)
- **Missing types:** _none_

### 57. nfa.test

- **Target:** `nfa.NfaTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1210.0
- **Functions:** 12/12 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 11/11 matched

### 58. token_check.mod

- **Target:** `tokenCheck.TokenCheck [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1210.0
- **Functions:** 9/9 matched (target 10)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 5)
- **Missing types:** _none_

### 59. tyinfer.mod

- **Target:** `tyinfer.Tyinfer [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1210.0
- **Functions:** 10/10 matched (target 12)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 60. normalize.norm_util

- **Target:** `normUtil.NormUtil [PROVENANCE-FALLBACK]`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 1102.0
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 8)
- **Missing types:** _none_
- **Tests:** 4/4 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `normalize/normUtil.rs` vs expected `normalize/norm_util.rs`
- **Proposed provenance header:** `// port-lint: source normalize/norm_util.rs` (current: `// port-lint: source normalize/normUtil.rs`)
- **Lint issues:** 1

### 61. codegen.base

- **Target:** `codegen.Base [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1010.0
- **Functions:** 9/9 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 62. example.test

- **Target:** `example.ExampleTest`
- **Similarity:** 0.87
- **Dependents:** 0
- **Priority Score:** 1001.3
- **Functions:** 10/10 matched (target 11)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 63. error.test

- **Target:** `error.ErrorTest`
- **Similarity:** 0.92
- **Dependents:** 0
- **Priority Score:** 900.8
- **Functions:** 9/9 matched (target 10)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 64. prevalidate.mod

- **Target:** `prevalidate.Prevalidate [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 810.0
- **Functions:** 7/7 matched (target 8)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 65. dfa.test

- **Target:** `dfa.DfaTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 810.0
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 66. resolve.test

- **Target:** `resolve.ResolveTest`
- **Similarity:** 0.96
- **Dependents:** 0
- **Priority Score:** 800.4
- **Functions:** 8/8 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 7/7 matched

### 67. construct.mod

- **Target:** `construct.Construct [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 710.0
- **Functions:** 6/6 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 68. graph.mod

- **Target:** `graph.Graph [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 710.0
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 69. reduce.test

- **Target:** `reduce.ReduceTest`
- **Similarity:** 0.90
- **Dependents:** 0
- **Priority Score:** 701.0
- **Functions:** 7/7 matched (target 8)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 70. generate

- **Target:** `lalrpop.Generate`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 700.9
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_

### 71. first.test

- **Target:** `first.FirstTest`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 700.9
- **Functions:** 7/7 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 72. codegen.test_all

- **Target:** `codegen.TestAll`
- **Similarity:** 0.83
- **Dependents:** 0
- **Priority Score:** 601.7
- **Functions:** 5/5 matched (target 6)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 73. lane.mod

- **Target:** `lane.Lane [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 510.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 74. parser.test

- **Target:** `parser.ParserTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 510.0
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 75. kernel_set

- **Target:** `lalrpop.KernelSet`
- **Similarity:** 0.65
- **Dependents:** 0
- **Priority Score:** 503.5
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 76. precedence.test

- **Target:** `precedence.PrecedenceTest`
- **Similarity:** 0.76
- **Dependents:** 0
- **Priority Score:** 502.4
- **Functions:** 5/5 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 77. log

- **Target:** `lalrpop.Log`
- **Similarity:** 0.97
- **Dependents:** 0
- **Priority Score:** 500.3
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 78. first.mod

- **Target:** `first.First [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 410.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 79. message.mod

- **Target:** `message.Message [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 410.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 80. free_variables.mod

- **Target:** `freeVariables.FreeVariables [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 410.0
- **Functions:** 3/3 matched (target 9)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 81. macro_expand.test

- **Target:** `macroExpand.MacroExpandTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 403.2
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 4/4 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `normalize/macroExpand/test.rs` vs expected `normalize/macro_expand/test.rs`
- **Proposed provenance header:** `// port-lint: source normalize/macro_expand/test.rs` (current: `// port-lint: source normalize/macroExpand/test.rs`)
- **Lint issues:** 1

### 82. cond_comp.mod

- **Target:** `condComp.CondComp [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 310.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 83. reduce.mod

- **Target:** `reduce.Reduce [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 310.0
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 84. inline.mod

- **Target:** `inline.Inline [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 310.0
- **Functions:** 2/2 matched (target 3)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 85. lr1.mod

- **Target:** `lr1.Lr1 [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 310.0
- **Functions:** 3/3 matched (target 4)
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 86. graph.test

- **Target:** `graph.GraphTest`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 302.7
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 3/3 matched

### 87. inline.test

- **Target:** `inline.InlineTest`
- **Similarity:** 0.77
- **Dependents:** 0
- **Priority Score:** 302.3
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 88. message.test

- **Target:** `message.MessageTest`
- **Similarity:** 0.96
- **Dependents:** 0
- **Priority Score:** 300.4
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 89. re.test

- **Target:** `re.ReTest [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 210.0
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 90. trace.mod

- **Target:** `trace.Trace [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 210.0
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 91. lane_table.mod

- **Target:** `laneTable.LaneTable [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 210.0
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 92. shift.mod

- **Target:** `shift.Shift [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 210.0
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 93. trace_graph.test

- **Target:** `traceGraph.TraceGraphTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 202.0
- **Functions:** 2/2 matched (target 5)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `lr1/trace/traceGraph/test.rs` vs expected `lr1/trace/trace_graph/test.rs`
- **Proposed provenance header:** `// port-lint: source lr1/trace/trace_graph/test.rs` (current: `// port-lint: source lr1/trace/traceGraph/test.rs`)
- **Lint issues:** 1

### 94. nfa.interpret

- **Target:** `nfa.Interpret`
- **Similarity:** 0.84
- **Dependents:** 0
- **Priority Score:** 201.6
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 95. shift.test

- **Target:** `shift.ShiftTest`
- **Similarity:** 0.92
- **Dependents:** 0
- **Priority Score:** 200.8
- **Functions:** 2/2 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 96. cond_comp.test

- **Target:** `condComp.CondCompTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.68
- **Dependents:** 0
- **Priority Score:** 103.2
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `normalize/condComp/test.rs` vs expected `normalize/cond_comp/test.rs`
- **Proposed provenance header:** `// port-lint: source normalize/cond_comp/test.rs` (current: `// port-lint: source normalize/condComp/test.rs`)
- **Lint issues:** 1

### 97. report.test

- **Target:** `report.ReportTest`
- **Similarity:** 0.80
- **Dependents:** 0
- **Priority Score:** 102.0
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 98. dfa.interpret

- **Target:** `dfa.Interpret`
- **Similarity:** 0.89
- **Dependents:** 0
- **Priority Score:** 101.1
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 99. free_variables.test

- **Target:** `freeVariables.FreeVariablesTest [PROVENANCE-FALLBACK]`
- **Similarity:** 0.95
- **Dependents:** 0
- **Priority Score:** 100.5
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 1/1 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `grammar/freeVariables/test.rs` vs expected `grammar/free_variables/test.rs`
- **Proposed provenance header:** `// port-lint: source grammar/free_variables/test.rs` (current: `// port-lint: source grammar/freeVariables/test.rs`)
- **Lint issues:** 1

### 100. lib

- **Target:** `runtime.Lib [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 12)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 8)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `../lalrpop-util/src/lib.rs` vs expected `lib.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `../lalrpop-util/src/lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source ../lalrpop-util/src/lib.rs`)
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source ../lalrpop-util/src/lib.rs`)
- **Lint issues:** 2

### 101. grammar.consts

- **Target:** `consts.Consts [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
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
| `collections.mod` | `collections.Mod` | 0 | `collections/mod.rs` | `collections/Mod.kt` |
| `grammar.mod` | `grammar.Mod` | 0 | `grammar/mod.rs` | `grammar/Mod.kt` |
| `lexer.mod` | `lexer.Mod` | 0 | `lexer/mod.rs` | `lexer/Mod.kt` |
| `codegen.mod` | `lr1.codegen.Mod` | 0 | `lr1/codegen/mod.rs` | `lr1/codegen/Mod.kt` |

