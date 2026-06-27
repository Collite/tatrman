"""test_source_location.py — port of SourceLocationSpec.

Mirrors `packages/kotlin/ttr-parser/src/test/kotlin/.../model/SourceLocationSpec.kt`.

Contracts §2.4 (the multi-token-span invariant):
- `line` / `end_line` — 1-indexed.
- `column` / `end_column` — 0-indexed; `end_column` is **one past the last
  character of the last token in the span**.
- `offset_start` / `offset_end` — 0-indexed byte offsets; `offset_end` is
  **exclusive**.

The critical invariant:
    end_column == stop_token.column + len(stop_token.text)

NOT:
    end_column == start_column + (end_offset - start_offset)   # wrong!
"""

from __future__ import annotations

import ttr_parser
from ttr_parser import SourceLocation  # noqa: F821 — will exist once stage 2.2 lands


def _loc_of_first_def(text: str) -> SourceLocation:
    r = ttr_parser.parse_string(text)
    assert r.ok
    assert len(r.definitions) == 1
    return r.definitions[0].source


def test_single_token_single_line_span() -> None:
    text = "def project M {}"
    loc = _loc_of_first_def(text)
    assert loc.line == 1
    assert loc.end_line == 1
    assert loc.column == 0
    assert loc.end_column == len(text)
    # Offsets bracket the whole string.
    assert text.encode("utf-8")[loc.offset_start : loc.offset_end] == text.encode("utf-8")


def test_multi_line_span() -> None:
    text = 'def entity X {\n    labelPlural: "xs"\n}\n'
    loc = _loc_of_first_def(text)
    assert loc.line == 1
    assert loc.end_line == 3
    src_bytes = text.encode("utf-8")
    # The DefinitionContext's stop token is the closing `}`; the trailing
    # newline after `}` is not part of the span (ANTLR includes it as WS,
    # which is skipped to the hidden channel).
    expected_span = src_bytes[: src_bytes.index(b"}\n") + 1]
    assert loc.offset_end - loc.offset_start == len(expected_span)
    assert src_bytes[loc.offset_start : loc.offset_end] == expected_span


def test_multi_token_span_invariant_end_column_equals_stop_token_column_plus_length() -> None:
    """The load-bearing invariant from `makeSourceLocation` (walker.ts:1847).

    The closing `}` is at column 0 of line 3 with text length 1, so:
        end_column = 0 + 1 = 1

    A naive `start_column + span_length` formula would yield a much larger
    number (sum of every character in between). This test is the regression
    guard for that bug.
    """
    text = 'def entity X {\n    labelPlural: "xs"\n}\n'
    loc = _loc_of_first_def(text)
    assert loc.end_line == 3
    # The `}` is at column 0 of line 3 with length 1 → end_column = 1.
    assert loc.end_column == 1


def test_offset_end_is_exclusive() -> None:
    text = "def project M {}"
    loc = _loc_of_first_def(text)
    # Exclusive end means slice(offset_start, offset_end) == the full source.
    src_bytes = text.encode("utf-8")
    assert src_bytes[loc.offset_start : loc.offset_end] == src_bytes


def test_zero_length_span_for_empty_definition_body() -> None:
    text = "def project M {}"
    loc = _loc_of_first_def(text)
    # The whole definition span — start to stop token (the closing `}`).
    assert loc.offset_end > loc.offset_start


def test_unknown_sentinel_has_negative_fields() -> None:
    """SourceLocation.UNKNOWN is the sentinel used by Reference.of(path)."""
    assert SourceLocation.UNKNOWN.line == -1
    assert SourceLocation.UNKNOWN.column == -1
    assert SourceLocation.UNKNOWN.end_line == -1
    assert SourceLocation.UNKNOWN.end_column == -1
    assert SourceLocation.UNKNOWN.offset_start == -1
    assert SourceLocation.UNKNOWN.offset_end == -1
