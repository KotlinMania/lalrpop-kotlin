#!/usr/bin/env python
"""Transliterate `___reduceN` functions from lrgrammar.rs to Kotlin.

This script reads `tmp/lalrpop-rs/lalrpop/src/parser/lrgrammar.rs` and emits
Kotlin bodies for the 530 `___reduceN` helpers (the five fallible ones —
205, 206, 386, 446, 447 — are inlined into the dispatcher on the Rust side
and are not declared as free functions, so this script never sees them).

Output is appended to `src/commonMain/.../parser/LrGrammar.kt`.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RS_PATH = ROOT / 'tmp' / 'lalrpop-rs' / 'lalrpop' / 'src' / 'parser' / 'lrgrammar.rs'
KT_PATH = ROOT / 'src' / 'commonMain' / 'kotlin' / 'io' / 'github' / 'kotlinmania' / 'lalrpop_kotlin' / 'parser' / 'LrGrammar.kt'

RE_HEADER = re.compile(r'^fn ___reduce(\d+)<', re.M)
RE_COMMENT = re.compile(r'^// (.+)$', re.M)
RE_POP = re.compile(r'^let ___sym(\d+) = ___pop_Variant(\d+)\(___symbols\);$', re.M)
RE_EMPTY_START = re.compile(r'^let ___start = ___lookahead_start\.cloned\(\)\.or_else\(\|\| ___symbols\.last\(\)\.map\(\|s\| s\.2\)\)\.unwrap_or_default\(\);$', re.M)
RE_EMPTY_END = re.compile(r'^let ___end = ___start;$', re.M)
RE_START_SYM = re.compile(r'^let ___start = ___sym0\.0\.clone\(\);$', re.M)
RE_END_SYM = re.compile(r'^let ___end = ___sym(\d+)\.2\.clone\(\);$', re.M)
RE_ACTION_EMPTY = re.compile(r'^let ___nt = super::___action(\d+)::<>\(text, &___start, &___end\);$', re.M)
RE_ACTION_SYMS = re.compile(r'^let ___nt = super::___action(\d+)::<>\(text, ((?:___sym\d+(?:, )?)+)\);$', re.M)
RE_PUSH = re.compile(r'^___symbols\.push\(\(___start, ___Symbol::Variant(\d+)\(___nt\), ___end\)\);$', re.M)
RE_RETURN = re.compile(r'^\((\d+), (\d+)\)$', re.M)


def split_reduce_blocks(src: str):
    """Return list of (number, body_text) for each free `fn ___reduceN`."""
    matches = list(RE_HEADER.finditer(src))
    blocks = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else None
        block = src[start:end] if end is not None else src[start:]
        blocks.append((int(m.group(1)), block))
    return blocks


def translate(num: int, body: str) -> str:
    comment_match = RE_COMMENT.search(body)
    comment = comment_match.group(1).strip() if comment_match else ''

    pops = RE_POP.findall(body)  # list of (sym_idx, variant)
    action_empty = RE_ACTION_EMPTY.search(body)
    action_syms = RE_ACTION_SYMS.search(body)
    push = RE_PUSH.search(body)
    ret = RE_RETURN.search(body)

    if push is None or ret is None:
        raise ValueError(f'reduce{num}: could not parse push/return')

    push_variant = int(push.group(1))
    pop_states, nonterminal = int(ret.group(1)), int(ret.group(2))

    lines: list[str] = []
    lines.append(f'/** `___reduce{num}` — {comment} */')
    lines.append(f'internal fun reduce{num}(')
    lines.append('    text: String,')
    lines.append('    lookaheadStart: Int?,')
    lines.append('    symbols: MutableList<io.github.kotlinmania.lalrpop_kotlin.runtime.SymbolTriple<Int, LrSymbol>>,')
    lines.append('): Pair<Int, Int> {')

    push_expr_for_nt = f'LrSymbol.Variant{push_variant}(nt)' if push_variant != 9 else 'LrSymbol.Variant9'

    if action_empty is not None:
        action_n = int(action_empty.group(1))
        lines.append('    val start = lookaheadStart ?: (symbols.lastOrNull()?.third ?: 0)')
        lines.append('    val end = start')
        if push_variant == 9:
            lines.append(f'    action{action_n}(text, start, end)')
            lines.append('    symbols.add(Triple(start, LrSymbol.Variant9, end))')
        else:
            lines.append(f'    val nt = action{action_n}(text, start, end)')
            lines.append(f'    symbols.add(Triple(start, {push_expr_for_nt}, end))')
    else:
        if action_syms is None:
            raise ValueError(f'reduce{num}: no action call found')
        action_n = int(action_syms.group(1))
        # Pops appear in Rust in reverse order (symK-1 first, sym0 last). We emit same order.
        # Each pop: `let ___symI = ___pop_VariantJ(___symbols);`
        end_match = RE_END_SYM.search(body)
        assert end_match, f'reduce{num}: no end= sym match'
        end_sym = int(end_match.group(1))
        start_match = RE_START_SYM.search(body)
        assert start_match, f'reduce{num}: no start= sym0 match'

        # Emit in Rust order (reverse of sym indices).
        for sym_idx, variant in pops:
            lines.append(f'    val sym{sym_idx} = popVariant{variant}(symbols)')
        lines.append('    val start = sym0.first')
        lines.append(f'    val end = sym{end_sym}.third')
        sym_args = action_syms.group(2).replace('___sym', 'sym')
        if push_variant == 9:
            lines.append(f'    action{action_n}(text, {sym_args})')
            lines.append('    symbols.add(Triple(start, LrSymbol.Variant9, end))')
        else:
            lines.append(f'    val nt = action{action_n}(text, {sym_args})')
            lines.append(f'    symbols.add(Triple(start, {push_expr_for_nt}, end))')

    lines.append(f'    return Pair({pop_states}, {nonterminal})')
    lines.append('}')
    return '\n'.join(lines) + '\n'


def main() -> int:
    src = RS_PATH.read_text()
    blocks = split_reduce_blocks(src)
    print(f'Parsed {len(blocks)} reduce blocks', file=sys.stderr)

    out_chunks: list[str] = []
    out_chunks.append('\n// === lrgrammar.rs:9493-19691 — `___reduce0`..`___reduce534` ===\n')
    out_chunks.append('// Fallible reduces (205, 206, 386, 446, 447) are inlined into the\n')
    out_chunks.append('// dispatcher (see `___reduce` translation below) — they can short-circuit\n')
    out_chunks.append('// with `Some(Err(e))`, so they never appear as free functions in Rust and\n')
    out_chunks.append('// are absent here too.\n\n')

    for num, body in blocks:
        out_chunks.append(translate(num, body))
        out_chunks.append('\n')

    existing = KT_PATH.read_text()
    if '// === lrgrammar.rs:9493-19691' in existing:
        print('Reduce section already present — aborting.', file=sys.stderr)
        return 1

    KT_PATH.write_text(existing + ''.join(out_chunks))
    print('Appended reduce section.', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
