#!/usr/bin/env python
"""
port_lint — deterministic Kotlin-port lint for the lalrpop-kotlin project.

Scans .kt files for syntactic patterns that have been observed to introduce
real bugs or doc-fidelity violations during the Rust -> Kotlin port. Each
rule fires on a textual / AST-light pattern; there is no similarity math,
no thresholds, no fuzzy matching. A rule either matches a given line or
does not.

Usage:
    port_lint.py [<root>]
    port_lint.py src/commonMain/kotlin/io/github/kotlinmania/lalrpop

Exit code:
    0  no HIGH findings
    1  one or more HIGH findings (CI-friendly)

Output:
    file:line:col  rule-id  HIGH|MEDIUM|LOW  message

Rules:
    collapsed-emit-comment     HIGH    A function call followed on the same
                                       line by a comment that itself contains
                                       another function call. Caught the
                                       InternToken.kt missing-brace bug.
    self-recursive-method      HIGH    An override whose body calls itself
                                       (i.e. its own simple name with the
                                       same parameter shape). Caught the
                                       LrGrammar.kt expectedTokensFromStates
                                       infinite-recursion bug.
    sealed-tostring-shadow     MEDIUM  A sealed class declares
                                       `override fun toString() = fmt()`
                                       but at least one of its data-class
                                       subclasses does not override
                                       toString(). The data class auto-
                                       generated toString silently shadows
                                       the parent's.
    suppress-annotation        HIGH    `@Suppress(...)` is forbidden by
                                       CLAUDE.md (warnings are errors).
    jvm-import                 HIGH    JVM-only imports are forbidden in a
                                       KMP project.
    todo-marker                HIGH    TODO / FIXME / TODO() are forbidden
                                       by CLAUDE.md.
    rust-source-citation       LOW     KDoc / comments that cite a Rust
                                       source line ("X.rs:NNN") or use
                                       porter phrases ("Mirrors upstream",
                                       "Renamed from", "the Kotlin port").
                                       Doc-fidelity rule, not a behavior bug.
    snake-case-identifier      MEDIUM  Underscore in a fun/val/var/class
                                       name (snake_case). Allowed only in
                                       the four SCREAMING_SNAKE_CASE
                                       contexts (consts, immutable top-
                                       level/object vals, enum entries).
"""

from __future__ import annotations
import os
import re
import sys
from dataclasses import dataclass
from typing import Iterable


# --------------------------------------------------------------------------
# Finding type
# --------------------------------------------------------------------------

HIGH, MEDIUM, LOW = "HIGH", "MEDIUM", "LOW"


@dataclass(frozen=True)
class Finding:
    file: str
    line: int
    col: int
    rule_id: str
    severity: str
    message: str

    def render(self) -> str:
        return f"{self.file}:{self.line}:{self.col}  {self.rule_id:<26} {self.severity:<6} {self.message}"


# --------------------------------------------------------------------------
# Rule: collapsed-emit-comment
# --------------------------------------------------------------------------
#
# Matches lines of the form:
#     <ws><name>(<args>) // <... <name>(<args>) ...>
# where the trailing line-comment contains another call-shaped expression.
# The motivating bug:
#     rust(out, "}") // function     rust(out, "}") // mod
# where the second `rust(out, "}")` was meant to be a real call but was
# written inside the comment instead.
#
# We require the call inside the comment to look like a real Kotlin call
# (identifier followed by parens). This is conservative: we do NOT match
# trivial comments like `foo() // see Bar.foo()` because Bar.foo() has a
# qualifier; we only match unqualified bareword calls inside the comment.

# A "collapsed emit" looks like:
#     rust(out, "}") // function     rust(out, "}") // mod
# Two characteristics distinguish it from a benign brace label:
#   1. The inner "call" inside the comment has at least one non-empty
#      argument (a benign brace label is `// new()` or `// fn` — empty or
#      bare).
#   2. There is meaningful whitespace between the leading call's `//` and
#      the inner call.
_COLLAPSED_RE = re.compile(
    r"""
    ^\s*
    (?P<head>[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\))         # leading call
    \s*
    //                                                    # line comment
    .*?
    \b(?P<inner>
        [A-Za-z_][A-Za-z0-9_]*                            # bareword
        \s*\(
        \s*[^)\s][^)]*                                    # at least one non-space char inside parens
        \)
    )
    """,
    re.VERBOSE,
)


def check_collapsed_emit_comment(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        m = _COLLAPSED_RE.match(line)
        if not m:
            continue
        # Skip Kotlin control-flow keywords that look like calls.
        inner_name = m.group("inner").split("(")[0].strip()
        if inner_name in {"if", "when", "for", "while", "do", "try", "fun", "val", "var", "return"}:
            continue
        col = m.start("inner") + 1
        yield Finding(
            path,
            i,
            col,
            "collapsed-emit-comment",
            HIGH,
            f"call `{m.group('inner')}` is inside a trailing comment; was it supposed to be a real call?",
        )


# --------------------------------------------------------------------------
# Rule: self-recursive-method
# --------------------------------------------------------------------------
#
# Matches an `override fun X(...)` whose body (next non-blank line) calls
# `X(...)` directly with no qualifier. This is the pattern that broke
# expectedTokensFromStates. We only flag overrides because non-override
# self-recursion is normal.

_OVERRIDE_FUN_RE = re.compile(
    r"""
    ^\s*
    (?:internal\s+|private\s+|public\s+|protected\s+)*
    override\s+fun\s+
    (?P<name>[A-Za-z_][A-Za-z0-9_]*)
    \s*\(
    """,
    re.VERBOSE,
)


def _extract_method_body(lines: list[str], start_idx: int) -> str:
    """Given the 0-based index of an override fun signature line, return the
    text of just that method's body (no signature, no neighbours).

    Handles:
      - expression body:  override fun foo() = expr           (body = `expr`)
      - block body:       override fun foo() { ... }          (body = `...`)
    Multi-line signatures (where parameters wrap onto subsequent lines)
    are followed forward until we find either `=` or `{` at the top level.
    """
    text = lines[start_idx]
    # Walk forward over the signature until we hit a top-level `=` or `{`.
    paren_depth = 0
    angle_depth = 0
    sig_end_line = start_idx
    sig_end_col: int | None = None
    sig_terminator: str | None = None
    j = start_idx
    while j < len(lines):
        line = lines[j] if j == start_idx else lines[j]
        start_col = 0
        for k in range(start_col, len(line)):
            ch = line[k]
            if ch == "(":
                paren_depth += 1
            elif ch == ")":
                paren_depth -= 1
            elif ch == "<":
                angle_depth += 1
            elif ch == ">":
                angle_depth -= 1
            elif paren_depth == 0 and angle_depth == 0:
                if ch == "=" and (k + 1 >= len(line) or line[k + 1] != "="):
                    sig_end_line = j
                    sig_end_col = k + 1
                    sig_terminator = "="
                    break
                if ch == "{":
                    sig_end_line = j
                    sig_end_col = k + 1
                    sig_terminator = "{"
                    break
        if sig_terminator is not None:
            break
        j += 1
    if sig_terminator is None:
        return ""

    # Body extends from sig_end_col on sig_end_line through either:
    #  - end of line (for expression body, conservative — multi-line `=`
    #    bodies will be partially scanned, but that's acceptable).
    #  - matching `}` (for block body).
    if sig_terminator == "=":
        return lines[sig_end_line][sig_end_col:]
    # Block body: walk braces.
    depth = 1
    out = [lines[sig_end_line][sig_end_col:]]
    j = sig_end_line + 1
    while j < len(lines) and depth > 0:
        line = lines[j]
        for ch in line:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
        out.append(line)
        j += 1
    return "\n".join(out)


def _count_top_level_args(text: str) -> int:
    """Count comma-separated top-level args in `text` (which is the contents
    inside one set of parens, possibly with nested parens, brackets, or
    angle brackets). Returns 0 for empty text."""
    text = text.strip()
    if not text:
        return 0
    depth_paren = 0
    depth_brack = 0
    depth_angle = 0
    args = 1
    for ch in text:
        if ch == "(":
            depth_paren += 1
        elif ch == ")":
            depth_paren -= 1
        elif ch == "[":
            depth_brack += 1
        elif ch == "]":
            depth_brack -= 1
        elif ch == "<":
            depth_angle += 1
        elif ch == ">":
            depth_angle -= 1
        elif ch == "," and depth_paren == 0 and depth_brack == 0 and depth_angle == 0:
            args += 1
    # A trailing comma counts as an extra arg in this naive scheme; correct
    # for it.
    if text.endswith(","):
        args -= 1
    return args


def _extract_paren_block(lines: list[str], start_line: int, start_col: int) -> str | None:
    """Starting at `lines[start_line][start_col]` (which must be `(`), return
    the text inside the matching `(...)`, or None if not balanced."""
    if start_line >= len(lines):
        return None
    line = lines[start_line]
    if start_col >= len(line) or line[start_col] != "(":
        return None
    depth = 0
    out: list[str] = []
    j = start_line
    k = start_col
    while j < len(lines):
        line = lines[j]
        while k < len(line):
            ch = line[k]
            if ch == "(":
                depth += 1
                if depth > 1:
                    out.append(ch)
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return "".join(out)
                out.append(ch)
            else:
                out.append(ch)
            k += 1
        out.append("\n")
        j += 1
        k = 0
    return None


def check_self_recursive_method(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        m = _OVERRIDE_FUN_RE.match(line)
        if not m:
            continue
        name = m.group("name")
        # Find the override's parameter list and count its arity.
        param_open_col = line.find("(", m.end("name"))
        if param_open_col < 0:
            continue
        param_text = _extract_paren_block(lines, i - 1, param_open_col)
        if param_text is None:
            continue
        override_arity = _count_top_level_args(param_text)

        # Look at the body for an unqualified `name(args)` call.
        body = _extract_method_body(lines, i - 1)
        if not body:
            continue
        # Find the first occurrence of the call.
        call_re = re.compile(rf"(?<![A-Za-z0-9_.])\b{re.escape(name)}\s*\(")
        cm = call_re.search(body)
        if not cm:
            continue
        # Get the args text and count.
        body_lines = body.split("\n")
        # Locate which line of `body` the match is on, and the column.
        offset = cm.end() - 1  # position of the `(`
        # Walk body to find (line, col) of the `(`.
        run = 0
        call_line_idx = 0
        call_col = 0
        for bl_idx, bl in enumerate(body_lines):
            if run + len(bl) >= offset:
                call_line_idx = bl_idx
                call_col = offset - run
                break
            run += len(bl) + 1  # +1 for the newline
        call_args_text = _extract_paren_block(body_lines, call_line_idx, call_col)
        if call_args_text is None:
            continue
        call_arity = _count_top_level_args(call_args_text)
        if call_arity != override_arity:
            # Different arity → resolves to a different overload, not self.
            continue
        yield Finding(
            path,
            i,
            m.start("name") + 1,
            "self-recursive-method",
            HIGH,
            f"override `{name}` calls `{name}(...)` unqualified with the same {override_arity}-arg shape — will recurse into itself",
        )


# --------------------------------------------------------------------------
# Rule: sealed-tostring-shadow
# --------------------------------------------------------------------------
#
# For each sealed/abstract class that overrides toString(), check that every
# `data class` / `data object` declared *inside* the sealed class body also
# overrides toString(). This is a coarse structural check: we count braces
# to find the class body and walk subclasses.

_SEALED_DECL_RE = re.compile(
    r"^\s*(?:internal\s+|private\s+|public\s+|protected\s+)?sealed\s+(?:class|interface)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)
_DATA_SUBCLASS_RE = re.compile(
    r"^\s*data\s+(?:class|object)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)
_TOSTRING_RE = re.compile(r"\boverride\s+fun\s+toString\s*\(")


def _find_block_end(lines: list[str], start_line_index: int) -> int:
    """Find the index (0-based) of the line that contains the closing `}`
    matching the opening `{` on or after `start_line_index`.
    Returns len(lines) if not found."""
    depth = 0
    started = False
    for i in range(start_line_index, len(lines)):
        for ch in lines[i]:
            if ch == "{":
                depth += 1
                started = True
            elif ch == "}":
                depth -= 1
                if started and depth == 0:
                    return i
    return len(lines)


def check_sealed_tostring_shadow(path: str, lines: list[str]) -> Iterable[Finding]:
    i = 0
    while i < len(lines):
        m = _SEALED_DECL_RE.match(lines[i])
        if not m:
            i += 1
            continue
        sealed_name = m.group("name")
        end = _find_block_end(lines, i)
        block = lines[i:end + 1]
        block_text = "\n".join(block)
        # Only flag if the sealed parent overrides toString().
        if not _TOSTRING_RE.search(block_text):
            i = end + 1
            continue
        # Walk the inner body looking for direct data class/object subclasses
        # that don't have an override fun toString().
        for j, sub_line in enumerate(block):
            sm = _DATA_SUBCLASS_RE.match(sub_line)
            if not sm:
                continue
            # Inner body of the data class extends from this line until the
            # matching `}` (or end-of-line if this is a one-line decl with no
            # body braces, e.g. `data class Foo(...) : X()`).
            sub_start = j
            # Determine if this subclass has a body or is bodyless.
            if "{" in sub_line:
                sub_end = _find_block_end(block, sub_start)
            else:
                # One-line decl, no body — definitely no toString override.
                sub_end = sub_start
            sub_text = "\n".join(block[sub_start : sub_end + 1])
            if _TOSTRING_RE.search(sub_text):
                continue
            yield Finding(
                path,
                i + sub_start + 1,
                sm.start("name") + 1,
                "sealed-tostring-shadow",
                MEDIUM,
                f"`{sm.group('name')}` (data subclass of sealed `{sealed_name}` whose toString is overridden) has no toString override; auto-generated `Variant(prop=…)` will shadow the parent",
            )
        i = end + 1


# --------------------------------------------------------------------------
# Rule: suppress-annotation
# --------------------------------------------------------------------------

_SUPPRESS_RE = re.compile(r"@Suppress\b")


def check_suppress_annotation(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        m = _SUPPRESS_RE.search(line)
        if m:
            yield Finding(
                path, i, m.start() + 1, "suppress-annotation", HIGH,
                "`@Suppress` is forbidden — fix the underlying warning",
            )


# --------------------------------------------------------------------------
# Rule: jvm-import
# --------------------------------------------------------------------------

_JVM_IMPORT_RE = re.compile(
    r"^\s*import\s+(?:kotlin\.jvm\.|java\.|javax\.)"
)


def check_jvm_import(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        if _JVM_IMPORT_RE.match(line):
            yield Finding(
                path, i, 1, "jvm-import", HIGH,
                "JVM-only import in a KMP project",
            )


# --------------------------------------------------------------------------
# Rule: todo-marker
# --------------------------------------------------------------------------

_TODO_RE = re.compile(r"\b(?:TODO|FIXME|XXX)\b")
_TODO_CALL_RE = re.compile(r"\bTODO\s*\(")


def check_todo_marker(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        # Comment-form TODOs: anywhere in line.
        cm = _TODO_RE.search(line)
        if cm:
            yield Finding(
                path, i, cm.start() + 1, "todo-marker", HIGH,
                f"`{cm.group(0)}` marker — CLAUDE.md forbids TODO/FIXME/XXX",
            )
            continue
        # TODO() calls.
        cc = _TODO_CALL_RE.search(line)
        if cc:
            yield Finding(
                path, i, cc.start() + 1, "todo-marker", HIGH,
                "`TODO(...)` call — CLAUDE.md forbids placeholder bodies",
            )


# --------------------------------------------------------------------------
# Rule: rust-source-citation
# --------------------------------------------------------------------------

_RUST_CITATION_RE = re.compile(
    r"""(
        \.rs:\d+                  # explicit Rust line citation
        |Mirrors\s+upstream
        |Renamed\s+from
        |[Tt]he\s+Kotlin\s+port
        |Direct\s+port\s+of\s+upstream
        |upstream\s+Rust\s+(?:uses|has|wraps|emits)
    )""",
    re.VERBOSE,
)


def check_rust_source_citation(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        # Only flag inside a comment context.
        stripped = line.lstrip()
        if not (stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*")):
            continue
        m = _RUST_CITATION_RE.search(line)
        if not m:
            continue
        yield Finding(
            path, i, m.start() + 1, "rust-source-citation", LOW,
            f"porter note `{m.group(1).strip()}` — translate Rust doc word-for-word; do not add Rust-vs-Kotlin commentary",
        )


# --------------------------------------------------------------------------
# Rule: snake-case-identifier
# --------------------------------------------------------------------------
#
# Match `fun foo_bar(...)`, `val foo_bar`, `var foo_bar`. We do NOT flag
# SCREAMING_SNAKE_CASE because that's allowed for `const val`, top-level /
# object immutable vals, and enum entries.

_SNAKE_DECL_RE = re.compile(
    r"""
    ^\s*
    (?:internal\s+|private\s+|public\s+|protected\s+|override\s+|open\s+|inline\s+|suspend\s+)*
    (?:fun|val|var)\s+
    (?P<name>[a-z_][a-z0-9_]*)        # lowercase start, allowed underscores
    \b
    """,
    re.VERBOSE,
)


def check_snake_case_identifier(path: str, lines: list[str]) -> Iterable[Finding]:
    for i, line in enumerate(lines, start=1):
        m = _SNAKE_DECL_RE.match(line)
        if not m:
            continue
        name = m.group("name")
        if "_" not in name:
            continue
        # Permit single trailing underscore (Kotlin convention to dodge keyword
        # clashes — though backticks are preferred). Don't flag those by default.
        if name.endswith("_") and "_" not in name[:-1]:
            continue
        yield Finding(
            path, i, m.start("name") + 1, "snake-case-identifier", MEDIUM,
            f"identifier `{name}` uses snake_case — Kotlin convention is camelCase",
        )


# --------------------------------------------------------------------------
# Driver
# --------------------------------------------------------------------------

ALL_RULES = [
    check_collapsed_emit_comment,
    check_self_recursive_method,
    check_sealed_tostring_shadow,
    check_suppress_annotation,
    check_jvm_import,
    check_todo_marker,
    check_rust_source_citation,
    check_snake_case_identifier,
]


def walk_kotlin_files(root: str) -> Iterable[str]:
    for dirpath, dirnames, filenames in os.walk(root):
        # Skip build / generated dirs.
        dirnames[:] = [d for d in dirnames if d not in {"build", ".gradle", "tmp", "node_modules"}]
        for f in filenames:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def lint_file(path: str) -> list[Finding]:
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    lines = text.splitlines()
    findings: list[Finding] = []
    for rule in ALL_RULES:
        findings.extend(rule(path, lines))
    return findings


def main(argv: list[str]) -> int:
    root = argv[1] if len(argv) > 1 else "src"
    if not os.path.isdir(root):
        print(f"port_lint: not a directory: {root}", file=sys.stderr)
        return 2

    all_findings: list[Finding] = []
    for path in sorted(walk_kotlin_files(root)):
        all_findings.extend(lint_file(path))

    # Print findings grouped by severity.
    by_sev: dict[str, list[Finding]] = {HIGH: [], MEDIUM: [], LOW: []}
    for f in all_findings:
        by_sev[f.severity].append(f)

    for sev in (HIGH, MEDIUM, LOW):
        for f in by_sev[sev]:
            print(f.render())

    # Summary.
    print()
    print(f"port_lint summary: {len(by_sev[HIGH])} HIGH, {len(by_sev[MEDIUM])} MEDIUM, {len(by_sev[LOW])} LOW")

    return 1 if by_sev[HIGH] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
