# sig_diff

Dump and compare function signatures between a Rust source file and its
Kotlin port. Read-only; does not mutate either file.

Designed for the LALRPOP-generated `LrGrammar.kt` ↔ `lrgrammar.rs` pair
(40k lines each, ~1500 functions), but works on any single-file
Rust→Kotlin port.

## Usage

```bash
python tools/sig_diff/sig_diff.py <rust_file> <kotlin_file> [--out DIR]
```

Default output dir is `tools/sig_diff/output/<kotlin_basename>/`.

Example — the headline pair:

```bash
python tools/sig_diff/sig_diff.py \
  tmp/lalrpop-rs/lalrpop/src/parser/lrgrammar.rs \
  src/commonMain/kotlin/io/github/kotlinmania/lalrpop/parser/LrGrammar.kt
```

## Output

| File | Contents |
|---|---|
| `summary.txt` | Totals, family rollup, name divergence (also printed to stdout) |
| `rust_sigs.txt` | `<line> <signature>` for every `fn` in the Rust file, in source order |
| `kotlin_sigs.txt` | `<line> <signature>` for every `fun` in the Kotlin file, in source order |
| `rust_name_counts.txt` | `<count> <name>` per Rust function name |
| `kotlin_name_counts.txt` | `<count> <name>` per Kotlin function name |
| `rust_only_names.txt` | Rust names with no snake→camel match in Kotlin |
| `kotlin_only_names.txt` | Kotlin names with no Rust counterpart |

## Family rollup

Common parser-generator function families are bucketed so a port can be
checked for full coverage at a glance:

| Family | What it is |
|---|---|
| `action` | LALRPOP-emitted action functions (`___action_42` / `action42`) |
| `reduce` | Reduce dispatch handlers (`___reduce_42` / `reduce42`) |
| `popVariant` | Stack-pop helpers per symbol-variant (`___pop_Variant1` / `popVariant1`) |
| `accepts` | Acceptance check (`___accepts` / `accepts`) |
| `simulateReduce` | LR(1) simulation helper (`___simulate_reduce` / `simulateReduce`) |
| `tokenToInteger` | Token → state-machine index mapping |
| `tokenToSymbol` | Token → variant-stack-payload mapping |
| `expectedTokens` | Diagnostic helper (`___expected_tokens*` / `expectedTokens*`) |

Counts in the rollup are **occurrences**, not unique names — a faithful
port should match Rust on every family count exactly. A row where the
two columns differ is a regression to investigate.

## Name divergence

Both lists are snake_case ↔ camelCase aware. A Rust `fn parse_grammar()`
matches a Kotlin `fun parseGrammar()`. Triple-underscore Rust prefixes
(`___action_*`) are stripped before matching.

Anything in `rust_only_names.txt` is a Rust function with no Kotlin
counterpart. Anything in `kotlin_only_names.txt` is a Kotlin function
that the porter introduced (a helper, a refactor, or — sometimes — an
invention that should be inlined). Both files should be very small for
a faithful port; a long `kotlin_only_names.txt` is a smell.

## What this tool is not

`sig_diff` does **not** verify the bodies. It can only confirm that
every Rust function name has a Kotlin counterpart with the same arity
class. Two functions with matching names can have completely different
behavior — that's what the ported test suite (`./gradlew test`) is for.
This tool is the cheap structural check that runs in seconds and
catches missing or invented names.
