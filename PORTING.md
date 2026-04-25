# Starlark-Kotlin Porting Guide

This guide explains how to use the ast_distance tool for systematic Rust→Kotlin porting.

## Quick Start

### Initial Setup (First Time Only)

If stub files don't exist yet, generate them:

```bash
python tools/generate_stubs.py \
  tmp/starlark/src \
  src/commonMain/kotlin/io/github/kotlinmania/starlark_kotlin \
  io.github.kotlinmania.starlark_kotlin
```

This creates:
- Proper folder structure matching Rust modules
- CamelCase filenames (Value.kt, Ty.kt)
- Lowercase directories (values/, typing/)
- Port-lint headers in every file

### Get a Task Assignment
```bash
./tools/ast_distance/ast_distance --assign tasks.json agent-1
```

This will:
1. Assign you the highest-priority unassigned task
2. Output complete porting instructions
3. Lock the task to prevent conflicts

### Complete a Task
```bash
./tools/ast_distance/ast_distance --complete tasks.json <source_qualified_name>
```

### Release a Task (if you can't complete it)
```bash
./tools/ast_distance/ast_distance --release tasks.json <source_qualified_name>
```

## Port-Lint Headers

**IMPORTANT:** Add this header to EVERY Kotlin file you create:

```kotlin
// port-lint: source <relative-path-to-rust-file>
package your.package.name

// Your Kotlin code...
```

Example:
```kotlin
// port-lint: source src/values/layout.rs
package io.github.kotlinmania.starlark_kotlin.values

data class Layout(...)
```

This enables:
- Accurate file matching (no guessing based on names)
- Documentation coverage tracking
- AST similarity verification

## Analysis Commands

### Check Overall Progress
```bash
./tools/ast_distance/ast_distance --deep tmp/starlark rust src kotlin
```

Shows:
- Files matched by port-lint header vs name
- Total TODOs and lint issues
- Similarity scores
- Documentation gaps
- Priority recommendations

### View Task Status
```bash
./tools/ast_distance/ast_distance --tasks tasks.json
```

### Find Missing Files
```bash
./tools/ast_distance/ast_distance --missing tmp/starlark rust src kotlin
```

### Scan for TODOs
```bash
./tools/ast_distance/ast_distance --todos src
```

### Run Lint Checks
```bash
./tools/ast_distance/ast_distance --lint src
```

## Porting Priority

Files are ranked by:
1. **Dependency count** - files with more dependents = higher priority
2. **Current similarity** - incomplete ports need finishing

Top priorities:
- `layout.value` (160 deps)
- `typing.ty` (107 deps)
- `layout.heap` (100 deps)
- `params.display` (68 deps)
- `typing.starlark_value` (67 deps)

## Quality Targets

For each ported file:
- ✅ Port-lint header present
- ✅ AST similarity ≥ 0.85 (excellent)
- ✅ Documentation coverage matches or exceeds Rust source
- ✅ No TODO comments (or tagged with plan to resolve)
- ✅ No lint errors

## Multi-Agent Workflow

The task system supports parallel porting:

1. Each agent gets unique assignments via `--assign`
2. File locking prevents conflicts
3. Tasks track: pending → assigned → completed
4. Automatic priority re-ranking as dependencies complete

## Verification

Compare a specific file pair:
```bash
./tools/ast_distance/ast_distance \
  tmp/starlark/src/values/layout.rs rust \
  src/commonMain/kotlin/io/github/kotlinmania/starlark_kotlin/values/Layout.kt kotlin
```

Look for:
- Cosine similarity ≥ 0.90 (structural match)
- Documentation line counts close
- Function count matches

## Tips

1. **Start with leaves** - Port files with 0 dependencies first
2. **Add headers immediately** - Don't forget the port-lint comment
3. **Check similarity early** - Run comparisons while porting, not after
4. **Port documentation** - Include all doc comments from Rust
5. **Use stubs sparingly** - Mark with TODO if you create placeholder implementations

## References

- [AGENTS.md](./AGENTS.md) - Detailed porting patterns and conventions
- [README.md](./README.md) - Project overview
- [ASTDistance](../ASTDistance) - Tool source code
