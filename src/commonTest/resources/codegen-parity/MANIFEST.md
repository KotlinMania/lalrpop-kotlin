# Codegen parity corpus

This directory holds the byte-for-byte parity corpus for lalrpop-kotlin's
Rust-output back-end. Each entry pairs an input grammar with the
**oracle** Rust output produced by upstream LALRPOP for the same
grammar. lalrpop-kotlin must emit a byte-identical file (modulo
deterministic whitespace) when fed the same input.

The oracle outputs are sourced from
`tmp/lalrpop-rs/target/debug/build/lalrpop-test-*/out/<name>.rs`,
which is upstream LALRPOP's own build artefact for the
`lalrpop-test/src/<name>.lalrpop` grammars. **Never** edit a file under
`expected/` to make a diff disappear: it is the contract.

## Layout

- `inputs/<name>.lalrpop` — grammar input
- `expected/<name>.expected.rs` — upstream-emitted Rust (the oracle)

## Corpus checklist

Status legend:
- `not-wired`: the harness is not yet driving this grammar through the
  Kotlin pipeline.
- `divergent`: harness runs, output diverges from oracle. Track the
  failing section in the bug list below.
- `matching`: byte-identical to oracle (modulo whitespace
  normalisation). Locked in by the parity regression test.

| Grammar                  | Bytes | Oracle lines | Status     | Notes |
|--------------------------|------:|-------------:|-----------:|-------|
| use_super_internal_tok   |    83 |          761 | matching   | smallest grammar; one terminal, one production, internal tokenizer, `use super::` rename |
| zero_length_match        |    92 |          756 | matching   | exercises zero-length match handling and `\s` Unicode whitespace expansion |
| dyn_argument             |   107 |          790 | matching   | parser argument with `dyn Trait` type |
| match_alternatives       |   142 |          834 | matching   | exercises `match { ... }` alternation |

Future additions (not yet staged):

- `lrgrammar` (40 219 lines of oracle) — parity witness for the
  bootstrapped LALRPOP grammar parser. Add only after the small corpus
  is matching, otherwise diff triage drowns in noise.
- A grammar exercising `#[inline]` rules.
- A grammar exercising parameterised macros.
- A grammar exercising precedence/associativity declarations.
- An error-recovery grammar.

## Triage discipline

When a divergence shows up:

1. Bisect the diff by section in this order: token enum, action
   functions, state table, reduction dispatch, error recovery, header.
   A wrong token enum cascades into every later section, so a
   non-matching token enum is always the first bug.
2. Fix lalrpop-kotlin, never the oracle.
3. Re-run the harness. Keep iterating until the byte-diff is empty
   (after deterministic whitespace normalisation).
4. Once a grammar matches, mark it `matching` in this manifest and
   commit the snapshot. The snapshot becomes the regression contract:
   do not regenerate it from lalrpop-kotlin (that would let drift
   hide).

## Whitespace normalisation

The harness normalises:
- Trailing whitespace stripped per line.
- Line endings normalised to `\n`.

It does **not** normalise:
- Function/symbol/state ordering.
- Renaming of generated identifiers.
- `#[allow(...)]` attribute presence or order.

Those are real divergences. If upstream and the Kotlin port disagree
on any of them, fix the Kotlin port.
