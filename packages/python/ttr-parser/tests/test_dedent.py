"""test_dedent.py — port of DedentSpec.

Mirrors `packages/kotlin/ttr-parser/src/test/kotlin/.../walker/DedentSpec.kt`.

Algorithm (contracts §2.9, walker.ts `dedentWithIndent`):
1. Drop a leading newline immediately after the triple-quote opener.
2. Compute the longest common leading-whitespace prefix across all
   non-blank lines (blank = whitespace-only, normalised to empty).
3. Strip that prefix; if a non-blank line does not start with it,
   leave the line untouched.
4. Blank lines: trim trailing whitespace only.

Returns the **value** (string). The walker internally uses a tuple
(value, indent_width) — exposed via TaggedBlockValue.indentWidth when
the value comes from a tagged block.
"""

from __future__ import annotations

import pytest

from ttr_parser.dedent import dedent  # noqa: F821 — will exist once stage 2.3 lands


@pytest.mark.parametrize(
    ("input_text", "expected"),
    [
        # Spec test 1 — no common prefix, no change.
        (
            "Hello there.\nHow are you?\nOh good, I'm glad.",
            "Hello there.\nHow are you?\nOh good, I'm glad.",
        ),
        # Spec test 2 — common leading whitespace stripped.
        (
            "  Hello there.\n  How are you?\n  Oh good, I'm glad.",
            "Hello there.\nHow are you?\nOh good, I'm glad.",
        ),
        # Spec test 3 — different indentation, smallest common prefix wins.
        (
            "  Hello there.\n    How are you?",
            "Hello there.\n  How are you?",
        ),
        # Spec test 4 — blank lines do not contribute to common prefix.
        (
            "  line one\n\n  line two",
            "line one\n\nline two",
        ),
        # Spec test 5 — leading newline dropped so triple-quoted authoring is natural.
        (
            "\n  hello\n  world",
            "hello\nworld",
        ),
        # Spec test 6 — tabs participate in the prefix as-is (no expansion).
        (
            "\tone\n\ttwo",
            "one\ntwo",
        ),
        # Spec test 7 — no leading whitespace → no change.
        (
            "no\nindent",
            "no\nindent",
        ),
    ],
    ids=[
        "no-common-prefix",
        "strips-common-2-space-prefix",
        "uses-smallest-common-prefix",
        "blank-lines-dont-count",
        "drops-leading-newline",
        "tabs-count-as-prefix",
        "no-indent-no-change",
    ],
)
def test_dedent(input_text: str, expected: str) -> None:
    assert dedent(input_text) == expected


def test_dedent_internal_indent_width_exposed_for_tagged_block() -> None:
    """When the walker uses dedent internally for a tagged block, the indent
    width is exposed on TaggedBlockValue.indentWidth. This is the load-bearing
    piece of the dedent API beyond the raw string value."""
    # This is exercised end-to-end in test_tagged_block.py once the walker
    # exists; the dedent primitive only returns a string.
    assert dedent("  SELECT 1\n  SELECT 2").startswith("SELECT 1")
