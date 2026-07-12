# SPDX-License-Identifier: Apache-2.0
"""Triple-string dedent (contracts §2.9).

Mirrors the Kotlin `Dedent.applyTextwrapDedent` and the TS
`walker.ts` `dedentWithIndent`. The dedent is *similar to* `textwrap.dedent`,
but with two differences that matter:

1. The leading newline immediately after the opening triple-quote is dropped,
   so triple-quoted authoring reads naturally (the body starts on its own line).
2. Blank lines (whitespace-only) are normalised to empty strings — they do
   not contribute to the longest common prefix and have trailing whitespace
   stripped.

The walker uses `dedent_with_indent` internally to populate
`TaggedBlockValue.indent_width`; consumers that don't care about that can
import `dedent` directly.

Algorithm (3 steps):
1. Drop a leading newline immediately after the opening triple-quote.
2. Compute the longest common leading-whitespace prefix across all non-blank
   lines.
3. Strip that prefix; normalise blank lines to empty.
"""

from __future__ import annotations

from collections.abc import Iterable

__all__ = ["dedent", "dedent_with_indent", "DedentResult"]


class DedentResult:
    """Output of `dedent_with_indent`.

    `value` is the dedented body string; `indent_width` is the length of the
    common leading-whitespace prefix that was stripped (= 0 when no common
    prefix existed). The walker surfaces the width on `TaggedBlockValue` so
    the embedded-language source map can offset each line uniformly.
    """

    __slots__ = ("value", "indent_width")

    def __init__(self, value: str, indent_width: int) -> None:
        self.value = value
        self.indent_width = indent_width

    def __repr__(self) -> str:
        return f"DedentResult(value={self.value!r}, indent_width={self.indent_width})"


def _leading_whitespace(line: str) -> str:
    """Return the leading `[ \t]*` run of `line`. Mirrors Kotlin `takeWhile`."""
    end = 0
    limit = len(line)
    while end < limit and line[end] in (" ", "\t"):
        end += 1
    return line[:end]


def _is_blank(line: str) -> bool:
    """Whitespace-only per Kotlin `isBlank()` semantics (includes the empty string)."""
    return line.strip() == ""


def _longest_common_prefix(a: str, b: str) -> str:
    """Character-wise longest common prefix of two strings."""
    limit = min(len(a), len(b))
    i = 0
    while i < limit and a[i] == b[i]:
        i += 1
    return a[:i]


def dedent_with_indent(text: str) -> DedentResult:
    """Dedent `text` and return `(value, indent_width)`.

    Step 1 — drop a leading newline if present (lets authors write the body
    on the next line).
    Step 2 — find the longest common leading-whitespace prefix across all
    non-blank lines. Blank lines are excluded from the prefix calculation.
    Step 3 — strip that prefix from each non-blank line that begins with it;
    blank lines are normalised to empty (trailing whitespace stripped).
    """
    body = text[1:] if text.startswith("\n") else text
    lines: list[str] = body.split("\n")

    common_prefix: str | None = None
    for line in lines:
        if _is_blank(line):
            continue
        leading = _leading_whitespace(line)
        common_prefix = leading if common_prefix is None else _longest_common_prefix(common_prefix, leading)
        if not common_prefix:
            break

    prefix = common_prefix or ""
    if not prefix:
        return DedentResult(body, 0)

    stripped: list[str] = []
    for line in lines:
        if _is_blank(line):
            stripped.append("")  # normalise: trim trailing whitespace
        elif line.startswith(prefix):
            stripped.append(line[len(prefix):])
        else:
            stripped.append(line)
    return DedentResult("\n".join(stripped), len(prefix))


def dedent(text: str) -> str:
    """Dedent a triple-string body. Convenience wrapper around `dedent_with_indent`."""
    return dedent_with_indent(text).value


def common_indent_width(lines: Iterable[str]) -> int:
    """Public helper: return the common leading-whitespace width across `lines`.

    Useful for tests and downstream tools that want the raw width without
    performing the full dedent. Returns 0 when no non-blank line exists.
    """
    prefix: str | None = None
    for line in lines:
        if _is_blank(line):
            continue
        leading = _leading_whitespace(line)
        prefix = leading if prefix is None else _longest_common_prefix(prefix, leading)
        if not prefix:
            return 0
    return len(prefix or "")
