#!/usr/bin/env python
"""
Generalized batch-rename of unused Kotlin parameters to a `_` prefix where
the Rust source already underscores them.

Reads each Kotlin file's `// port-lint: source <relative-path>` header to
find the corresponding Rust source. For codex-kotlin the Rust root is
`codex-rs/`, and the header path is relative to it.

Usage:
    python tools/fix_unused_params.py <kotlin-source-root> <rust-root>

Example:
    python tools/fix_unused_params.py src/nativeMain/kotlin codex-rs
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LINT_BIN = ROOT / "tools" / "ast_distance" / "ast_distance"

LINT_RE = re.compile(
    r"^(?P<file>[^:\n]+):(?P<line>\d+): unused_param: Unused parameter '(?P<param>[^']+)' in function '(?P<fn>[^']+)'",
    re.MULTILINE,
)
PORT_LINT_RE = re.compile(r"//\s*port-lint:\s*source\s+(\S+)")


def header_to_rust_path(kt_text: str, rust_root: Path) -> Path | None:
    m = PORT_LINT_RE.search(kt_text[:2000])
    if not m:
        return None
    return rust_root / m.group(1)


def rust_has_underscored(rust_text: str, param: str) -> bool:
    return re.search(rf"\b_{re.escape(param)}\b", rust_text) is not None


def fix_one(kt_path: Path, line_num: int, param: str, rust_root: Path) -> bool:
    text = kt_path.read_text()
    rs_path = header_to_rust_path(text, rust_root)
    if not rs_path or not rs_path.exists():
        return False
    rs_text = rs_path.read_text()
    if not rust_has_underscored(rs_text, param):
        return False

    lines = text.splitlines(keepends=True)
    if line_num - 1 >= len(lines):
        return False

    pat = re.compile(rf"\b{re.escape(param)}\s*:")
    end = min(len(lines), line_num - 1 + 60)
    paren_depth = 0
    saw_paren = False
    for i in range(line_num - 1, end):
        line = lines[i]
        for ch in line:
            if ch == "(":
                paren_depth += 1
                saw_paren = True
            elif ch == ")":
                paren_depth -= 1
        m = pat.search(line)
        if m:
            new = pat.sub(f"_{param}:", line, count=1)
            if new != line:
                lines[i] = new
                kt_path.write_text("".join(lines))
                return True
        if saw_paren and paren_depth == 0 and "{" in line:
            break
    return False


def main():
    kotlin_root = sys.argv[1] if len(sys.argv) > 1 else "src/commonMain/kotlin"
    rust_root_arg = sys.argv[2] if len(sys.argv) > 2 else "codex-rs"
    rust_root = ROOT / rust_root_arg

    out = subprocess.run(
        [str(LINT_BIN), "--lint", kotlin_root],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    fixed = 0
    skipped = 0
    seen = set()
    for m in LINT_RE.finditer(out.stdout):
        file = ROOT / m.group("file")
        line = int(m.group("line"))
        param = m.group("param")
        key = (file, line, param)
        if key in seen:
            continue
        seen.add(key)
        if fix_one(file, line, param, rust_root):
            fixed += 1
        else:
            skipped += 1
    print(f"fixed={fixed}  skipped={skipped}")


if __name__ == "__main__":
    main()
