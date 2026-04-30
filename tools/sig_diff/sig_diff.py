#!/usr/bin/env python
"""sig_diff — dump and compare function signatures between a Rust source
file and its Kotlin port.

Usage:
    python tools/sig_diff/sig_diff.py <rust_file> <kotlin_file> [--out DIR]

Default <out> is `tools/sig_diff/output/<kotlin_basename>/`. Writes:
    rust_sigs.txt          — line, signature (Rust)
    kotlin_sigs.txt        — line, signature (Kotlin)
    rust_name_counts.txt   — function name → occurrence count (Rust)
    kotlin_name_counts.txt — function name → occurrence count (Kotlin)
    rust_only_names.txt    — names that exist only in Rust
    kotlin_only_names.txt  — names that exist only in Kotlin
    summary.txt            — totals and family counts

Prints summary to stdout. Exit code is 0 even when divergences are
found — the divergence files are the report. This tool is read-only.

The signature dump is line-numbered and source-ordered. The "family"
buckets a parser-generator's structural functions (action / reduce /
popVariant / accepts / simulateReduce / tokenToInteger / tokenToSymbol
/ expectedTokens / expectedTokensFromStates) so a port can be checked
for full coverage without grepping by hand.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from collections import Counter

# Match a Rust fn line. Captures only the start; multi-line signatures
# are stitched in collect_signature.
RS_FN_START = re.compile(
    r"^(\s*)((?:pub(?:\([^)]+\))?\s+)?(?:async\s+)?(?:unsafe\s+)?(?:extern\s+(?:\"[^\"]+\"\s+)?)?fn\s+[A-Za-z_][A-Za-z0-9_]*)"
)

KT_FUN_MODS = (
    r"(?:public|private|internal|protected|override|open|final|inline|"
    r"infix|operator|tailrec|suspend|external|abstract)"
)
KT_FN_START = re.compile(
    r"^(\s*)((?:" + KT_FUN_MODS + r"\s+)*fun(?:\s*<[^>]+>)?\s+`?[A-Za-z_][A-Za-z0-9_]*`?)"
)

NAME_RS_RE = re.compile(r"\bfn\s+([A-Za-z_][A-Za-z0-9_]*)")
NAME_KT_RE = re.compile(r"\bfun\s+(?:<[^>]+>\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?")


def normalize(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def collect_signature(lines: list[str], start_idx: int, lang: str) -> tuple[str, int]:
    """Stitch a multi-line signature.

    Walks forward from start_idx, joining text until we see a body
    terminator at the paren/bracket-balanced top level:

    - Rust: `{` (block body) or `;` (forward declaration).
    - Kotlin: `{`, `;`, or `=` followed by anything other than `=` (the
      expression-body form, e.g. `fun foo(): T = bar.baz()`).

    Truncates at 50 lines to bound runaway parsing.
    """
    paren = 0
    bracket = 0
    out_chars: list[str] = []
    full_text: list[str] = []  # mirror of out_chars for lookahead
    for i in range(start_idx, len(lines)):
        line = lines[i]
        for j, ch in enumerate(line):
            if ch == "(":
                paren += 1
            elif ch == ")":
                paren -= 1
            elif ch == "[":
                bracket += 1
            elif ch == "]":
                bracket -= 1
            elif ch == "{" and paren == 0 and bracket == 0:
                return "".join(out_chars), i
            elif ch == ";" and paren == 0 and bracket == 0:
                out_chars.append(ch)
                return "".join(out_chars), i
            elif (
                ch == "="
                and lang == "kotlin"
                and paren == 0
                and bracket == 0
                # Skip == (equality) and >= / <= / != / += etc.
                and (j + 1 >= len(line) or line[j + 1] != "=")
                and (not out_chars or out_chars[-1] not in {"=", "!", "<", ">", "+", "-", "*", "/", "%"})
            ):
                return "".join(out_chars).rstrip(), i
            out_chars.append(ch)
        out_chars.append(" ")
        if i - start_idx > 50:
            break
    return "".join(out_chars), len(lines) - 1


def extract(path: str, start_re: re.Pattern[str], lang: str) -> list[tuple[int, str]]:
    with open(path) as f:
        lines = f.readlines()
    sigs: list[tuple[int, str]] = []
    i = 0
    while i < len(lines):
        if start_re.match(lines[i]):
            sig, end = collect_signature(lines, i, lang)
            sigs.append((i + 1, normalize(sig)))
            i = end + 1
        else:
            i += 1
    return sigs


def fn_name(sig: str, lang: str) -> str:
    m = (NAME_RS_RE if lang == "rust" else NAME_KT_RE).search(sig)
    return m.group(1) if m else "?"


def snake_to_camel(name: str) -> str:
    parts = name.split("_")
    if not parts:
        return name
    head = parts[0]
    return head + "".join(p[:1].upper() + p[1:] for p in parts[1:])


# Family extraction: strip leading underscores, lower-case the alphabetic
# prefix before any digits. Both Rust `___action_42` and Kotlin
# `action42` map to family `action`.
FAMILY_PREFIX_RE = re.compile(r"^([a-z][a-z]+?)(?:_?\d|$)")


def family_of(name: str) -> str | None:
    """Bucket common parser-generator functions by their alphabetic prefix.

    Returns None for names that aren't part of a numbered family (e.g. a
    one-off helper like `parseGrammar`). Both Rust `___pop_Variant_42`
    and Kotlin `popVariant42` should land in the same `popVariant`
    family.
    """
    base = name.lstrip("_")
    if not base:
        return None
    # Normalize snake to camel before extracting the prefix, so `pop_Variant1`
    # and `popVariant1` produce the same family.
    if "_" in base:
        base = snake_to_camel(base)
    m = re.match(r"^([A-Za-z][A-Za-z]*?)(\d+)$", base)
    if m:
        prefix = m.group(1)
        return prefix[0].lower() + prefix[1:]
    # Singletons that still belong to a family conceptually.
    for fam in ("accepts", "simulateReduce", "tokenToInteger", "tokenToSymbol"):
        if base == fam:
            return fam
    if base.startswith("expectedTokens"):
        return "expectedTokens"
    return None


def write_lines(path: str, lines: list[str]) -> None:
    with open(path, "w") as f:
        for line in lines:
            f.write(line)
            if not line.endswith("\n"):
                f.write("\n")


def main() -> int:
    ap = argparse.ArgumentParser(description="Dump and compare Rust/Kotlin function signatures.")
    ap.add_argument("rust_file", help="Path to the .rs file (the upstream oracle)")
    ap.add_argument("kotlin_file", help="Path to the .kt port")
    ap.add_argument(
        "--out",
        default=None,
        help="Output directory (default: tools/sig_diff/output/<kotlin_basename>)",
    )
    args = ap.parse_args()

    if not os.path.isfile(args.rust_file):
        print(f"error: rust file not found: {args.rust_file}", file=sys.stderr)
        return 2
    if not os.path.isfile(args.kotlin_file):
        print(f"error: kotlin file not found: {args.kotlin_file}", file=sys.stderr)
        return 2

    here = os.path.dirname(os.path.abspath(__file__))
    if args.out is None:
        kt_basename = os.path.splitext(os.path.basename(args.kotlin_file))[0]
        out = os.path.join(here, "output", kt_basename)
    else:
        out = args.out
    os.makedirs(out, exist_ok=True)

    rs = extract(args.rust_file, RS_FN_START, "rust")
    kt = extract(args.kotlin_file, KT_FN_START, "kotlin")

    write_lines(
        os.path.join(out, "rust_sigs.txt"),
        [f"{ln:6d}  {sig}" for ln, sig in rs],
    )
    write_lines(
        os.path.join(out, "kotlin_sigs.txt"),
        [f"{ln:6d}  {sig}" for ln, sig in kt],
    )

    rs_names = Counter(fn_name(s, "rust") for _, s in rs)
    kt_names = Counter(fn_name(s, "kotlin") for _, s in kt)

    write_lines(
        os.path.join(out, "rust_name_counts.txt"),
        [f"{n:5d}  {name}" for name, n in sorted(rs_names.items())],
    )
    write_lines(
        os.path.join(out, "kotlin_name_counts.txt"),
        [f"{n:5d}  {name}" for name, n in sorted(kt_names.items())],
    )

    # Family rollup
    rs_families: Counter[str] = Counter()
    kt_families: Counter[str] = Counter()
    rs_singletons = 0
    kt_singletons = 0
    for n, k in rs_names.items():
        fam = family_of(n)
        if fam is None:
            rs_singletons += k
        else:
            rs_families[fam] += k
    for n, k in kt_names.items():
        fam = family_of(n)
        if fam is None:
            kt_singletons += k
        else:
            kt_families[fam] += k

    # Name divergence (snake/camel-aware)
    rs_alt = set()
    for n in rs_names:
        rs_alt.add(n)
        rs_alt.add(snake_to_camel(n))
        rs_alt.add(snake_to_camel(n.lstrip("_")))
    kt_alt = set()
    for n in kt_names:
        kt_alt.add(n)
        kt_alt.add(snake_to_camel(n))
        kt_alt.add(snake_to_camel(n.lstrip("_")))

    rs_only = sorted(n for n in rs_names if n not in kt_alt and snake_to_camel(n) not in kt_alt and snake_to_camel(n.lstrip("_")) not in kt_alt)
    kt_only = sorted(n for n in kt_names if n not in rs_alt and snake_to_camel(n) not in rs_alt)

    write_lines(
        os.path.join(out, "rust_only_names.txt"),
        [f"{rs_names[n]:5d}  {n}" for n in rs_only],
    )
    write_lines(
        os.path.join(out, "kotlin_only_names.txt"),
        [f"{kt_names[n]:5d}  {n}" for n in kt_only],
    )

    # Summary
    summary_lines = []
    summary_lines.append(f"# sig_diff summary")
    summary_lines.append(f"")
    summary_lines.append(f"Rust   : {args.rust_file}")
    summary_lines.append(f"Kotlin : {args.kotlin_file}")
    summary_lines.append(f"")
    summary_lines.append(f"Rust   fn  signatures: {len(rs)}  ({len(rs_names)} unique names)")
    summary_lines.append(f"Kotlin fun signatures: {len(kt)}  ({len(kt_names)} unique names)")
    summary_lines.append(f"")
    summary_lines.append(f"## Family counts")
    summary_lines.append(f"")
    summary_lines.append(f"{'family':<28}  Rust   Kotlin")
    keys = sorted(set(rs_families.keys()) | set(kt_families.keys()))
    for k in keys:
        summary_lines.append(f"{k:<28}  {rs_families.get(k, 0):5d}  {kt_families.get(k, 0):5d}")
    summary_lines.append(f"{'<one-offs>':<28}  {rs_singletons:5d}  {kt_singletons:5d}")
    summary_lines.append(f"")
    summary_lines.append(f"## Divergence")
    summary_lines.append(f"")
    summary_lines.append(f"Rust-only names  : {len(rs_only)}")
    summary_lines.append(f"Kotlin-only names: {len(kt_only)}")
    if rs_only:
        summary_lines.append(f"")
        summary_lines.append(f"Rust-only:")
        for n in rs_only:
            summary_lines.append(f"  {rs_names[n]:5d}  {n}")
    if kt_only:
        summary_lines.append(f"")
        summary_lines.append(f"Kotlin-only:")
        for n in kt_only:
            summary_lines.append(f"  {kt_names[n]:5d}  {n}")

    summary_path = os.path.join(out, "summary.txt")
    write_lines(summary_path, summary_lines)

    print("\n".join(summary_lines))
    print(f"\nFull output written under: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
