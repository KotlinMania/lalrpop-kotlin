# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Current Progress:** 100.0% (82/64 files)
- **Matched Files:** 64
- **Average Similarity:** 0.81
- **Critical Issues:** 2 files with <0.60 similarity

## Priority 1: Fix Incomplete High-Dependency Files

### 1. lr1.tls
- **Similarity:** 0.74 (needs 11% improvement)
- **Dependencies:** 13
- **Priority Score:** 3.4
- **Action:** Review and complete missing sections

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

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
