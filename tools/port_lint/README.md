# port_lint

Deterministic Kotlin-port lint for the lalrpop-kotlin project. Catches
syntactic patterns observed to introduce real bugs or rule violations
during the Rust → Kotlin port. No similarity math, no thresholds; each
rule either matches a given line or does not.

## Usage

```bash
python tools/port_lint/port_lint.py [<root>]
```

Defaults to `src` if no root given. Walks `.kt` files (skipping `build/`,
`.gradle/`, `tmp/`).

Output format:

```
file:line:col  rule-id  HIGH|MEDIUM|LOW  message
```

Exit code is non-zero if any **HIGH** finding fires (CI-friendly).

## Rules

| Rule | Severity | What it catches |
|---|---|---|
| `collapsed-emit-comment` | HIGH | A function call followed on the same line by a comment that itself contains another non-trivial call. **Real bug found:** `InternToken.kt` had `rust(out, "}") // fn   rust(out, "}") // mod` collapsed onto one line, dropping the second emit and breaking Phase 1 byte-equality. |
| `self-recursive-method` | HIGH | An `override fun X(...)` whose body calls `X(...)` unqualified with the same arity. **Real bugs found:** `LrGrammar.kt` `expectedTokensFromStates` (infinite recursion → process crash) and `simulateReduce`. |
| `sealed-tostring-shadow` | MEDIUM | A `sealed class` declares `override fun toString() = fmt()`, but at least one of its `data class` subclasses does not override `toString()`. The data class auto-generated `Variant(prop=…)` silently shadows the parent's. **Real bugs found:** broke many parser tests until each subclass got an explicit override. |
| `suppress-annotation` | HIGH | `@Suppress(...)` is forbidden by CLAUDE.md goal #5. |
| `jvm-import` | HIGH | `import kotlin.jvm.*` / `import java.*` / `import javax.*` are forbidden in a KMP project. |
| `todo-marker` | HIGH | `TODO` / `FIXME` / `XXX` comments and `TODO()` calls are forbidden by CLAUDE.md goal #5 ("no fix it later comments"). |
| `rust-source-citation` | LOW | KDoc / comments with porter phrasing (`Mirrors upstream`, `Renamed from`, `lrgrammar.rs:NNN`, `the Kotlin port`, `Direct port of upstream`). Translate Rust docs word-for-word; do not add Rust-vs-Kotlin commentary. |
| `snake-case-identifier` | MEDIUM | Underscore in a `fun`/`val`/`var` name. Allowed only in the four `SCREAMING_SNAKE_CASE` contexts (consts, immutable top-level/object vals, enum entries). |

## Why this and not ast_distance

`ast_distance` uses cosine similarity over identifier and AST-shape
vectors. A faithful Kotlin port using stdlib idioms can score low because
identifier overlap is the dominant signal — and `ast_distance` cannot see
the kinds of bugs `port_lint` catches:

| Bug | ast_distance | port_lint |
|---|---|---|
| `rust(out, "}") // ... rust(out, "}") // ...` collapse | identifier set unchanged → 0 signal | `collapsed-emit-comment` HIGH |
| `override fun X() { return X() }` recursion | call graph not visible to it | `self-recursive-method` HIGH |
| Data-class subclass shadowing parent's `toString` | tree shape unchanged → 0 signal | `sealed-tostring-shadow` MEDIUM |

`port_lint` complements `ast_distance`. The two tools answer different
questions: `ast_distance` measures coverage and surfaces missing symbols;
`port_lint` flags specific drift patterns observed to cause real bugs.

## Adding a rule

A rule is a `def check_<rule_name>(path: str, lines: list[str]) -> Iterable[Finding]`
function added to the `ALL_RULES` list in `port_lint.py`. Findings carry
`(file, line, col, rule_id, severity, message)`. Keep rules deterministic
(no scores, no fuzzy thresholds).

When adding a rule, prefer to:

- Match patterns that produced **real** observed bugs.
- Make the rule conservative — false positives erode trust.
- Pick a severity: HIGH for things that block (forbidden constructs,
  observed-bug patterns), MEDIUM for likely-but-not-certain divergence,
  LOW for doc-fidelity / style.
