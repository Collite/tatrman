# SPDX-License-Identifier: Apache-2.0
"""test_tagged_block.py — port of TaggedBlockSpec (TS + Kotlin).

Mirrors `packages/parser/src/__tests__/tagged-block.test.ts` and
`packages/kotlin/ttr-parser/src/test/kotlin/.../walker/TaggedBlockSpec.kt`.

Contracts §2.6 (`TaggedBlockValue`):
- `tag` — raw text after the triple-quote (e.g. "sql").
- `language` — resolved language kind (e.g. "SQL").
- `dialect` — optional dialect (e.g. "postgres", None).
- `value` — fence-stripped + dedented body.
- `tag_source` — SourceLocation of the tag token (NOT the value).
- `value_source` — SourceLocation of the dedented body.
- `indent_width` — int (prefix length the dedent stripped).
- `source` — the whole triple-quoted literal's SourceLocation.

Tag registry (mirror of `tag-registry.ts`):
  sql → SQL (dialect=null)
  ms-sql, tsql, mssql → SQL / tsql
  postgres, postgresql, pg → SQL / postgres
  duckdb → SQL / duckdb
  mysql → SQL / mysql
  bigquery, bq → SQL / bigquery
  transform → TRANSFORMATION_DSL
  dataframe → DATAFRAME_DSL
  relnode → REL_NODE
"""

from __future__ import annotations

import pytest

import ttr_parser
from ttr_parser import ParseResult, TaggedBlockValue  # noqa: F821 — will exist once stages 2.2/2.3 land


def _query_source_text(text: str) -> TaggedBlockValue:
    r: ParseResult = ttr_parser.parse_string(text)
    assert r.ok, f"parse failed: {r.errors}"
    q = r.definitions[0]
    assert q.kind == "query"
    block = q.source_text  # type: ignore[attr-defined]
    assert isinstance(block, TaggedBlockValue), f"expected TaggedBlockValue, got {type(block)}"
    return block


# C1 — bare `sql`, clean body
def test_tagged_block_sql_clean_body() -> None:
    src = 'def query c1 {\n  sourceText: """sql\nSELECT 1\n"""\n}\n'
    v = _query_source_text(src)
    assert v.tag == "sql"
    assert v.language == "SQL"
    assert v.dialect is None
    assert v.value == "SELECT 1"


# C2 — `ms-sql` tag → dialect tsql
def test_tagged_block_ms_sql_tag_resolves_to_tsql_dialect() -> None:
    src = 'def query c2 {\n  sourceText: """ms-sql\nSELECT 1\n"""\n}\n'
    v = _query_source_text(src)
    assert v.tag == "ms-sql"
    assert v.language == "SQL"
    assert v.dialect == "tsql"
    assert v.value == "SELECT 1"


# C3 — uniform 2-indent + trailing whitespace after tag
def test_tagged_block_uniform_two_indent_stripped_with_indent_width() -> None:
    # Note "sql  " with two trailing spaces — the walker parses up to the
    # first newline after the tag, so this is identical to a plain `sql` tag.
    src = 'def query c3 {\n  sourceText: """sql  \n  SELECT 1\n  """\n}\n'
    v = _query_source_text(src)
    assert v.tag == "sql"
    assert v.value == "SELECT 1"
    assert v.indent_width == 2


# C4 — ragged indent (2 vs 6) — common 2 stripped
def test_tagged_block_ragged_indent_uses_smallest_common_prefix() -> None:
    src = 'def query c4 {\n  sourceText: """sql\n  SELECT a,\n      b\n  """\n}\n'
    v = _query_source_text(src)
    assert v.value == "SELECT a,\n    b"


# C7 — one-line `"""sql"""` is NOT a tagged block — it is a plain triple-string
def test_tagged_block_one_line_plain_string_falls_back() -> None:
    src = 'def query c7 {\n  sourceText: """sql"""\n}\n'
    r = ttr_parser.parse_string(src)
    assert r.ok
    q = r.definitions[0]
    block = q.source_text  # type: ignore[attr-defined]
    # Either a TripleStringValue with value="sql", or a warning+fallback;
    # the binding contract is that one-line is plain, not a tagged block.
    assert not isinstance(block, TaggedBlockValue)


# C9 — empty body
def test_tagged_block_empty_body() -> None:
    src = 'def query c9 {\n  sourceText: """sql\n"""\n}\n'
    v = _query_source_text(src)
    assert v.value == ""


# C10 — backtick-quoted id preserved verbatim (no grammar change in dedent)
def test_tagged_block_backtick_id_preserved() -> None:
    src = 'def query c10 {\n  sourceText: """mysql\nSELECT `id`\n"""\n}\n'
    v = _query_source_text(src)
    assert v.tag == "mysql"
    assert v.dialect == "mysql"
    assert v.value == "SELECT `id`"


# C11 — internal blank line kept; only the close-fence newline stripped
def test_tagged_block_internal_blank_line_kept() -> None:
    src = 'def query c11 {\n  sourceText: """sql\nSELECT 1\n\nFROM t\n"""\n}\n'
    v = _query_source_text(src)
    assert v.value == "SELECT 1\n\nFROM t"


# 1.3.4 — tagSource underlines exactly the tag
def test_tagged_block_tag_source_covers_exactly_the_tag_token() -> None:
    src = (
        'def query q {\n'
        '  sourceText: """ms-sql\n'
        'SELECT 1\n'
        '"""}\n'
    )
    r = ttr_parser.parse_string(src)
    assert r.ok
    q = r.definitions[0]
    v = q.source_text  # type: ignore[attr-defined]
    assert isinstance(v, TaggedBlockValue)
    file_text = src.encode("utf-8")
    tag_bytes = v.tag.encode("utf-8")
    sliced = file_text[v.tag_source.offset_start : v.tag_source.offset_end]
    assert sliced == tag_bytes
    assert v.tag_source.line == 2


# Unknown tag → fallback to plain triple-string with a warning
def test_tagged_block_unknown_tag_falls_back_to_triple_string_with_warning() -> None:
    src = 'def query c12 {\n  sourceText: """some-unknown-tag\nSELECT 1\n"""\n}\n'
    r = ttr_parser.parse_string(src)
    assert r.ok
    q = r.definitions[0]
    # Must NOT be a TaggedBlockValue when the tag is unknown.
    block = q.source_text  # type: ignore[attr-defined]
    assert not isinstance(block, TaggedBlockValue)
    # A warning about the unknown tag should be present (no parse error).
    assert any("tag" in w.message.lower() or "language" in w.message.lower() for w in r.warnings)


# Parametrised dialect-resolution coverage for the registry
@pytest.mark.parametrize(
    ("tag", "expected_language", "expected_dialect"),
    [
        ("sql", "SQL", None),
        ("tsql", "SQL", "tsql"),
        ("mssql", "SQL", "tsql"),
        ("postgres", "SQL", "postgres"),
        ("postgresql", "SQL", "postgres"),
        ("pg", "SQL", "postgres"),
        ("duckdb", "SQL", "duckdb"),
        ("mysql", "SQL", "mysql"),
        ("bigquery", "SQL", "bigquery"),
        ("bq", "SQL", "bigquery"),
        ("transform", "TRANSFORMATION_DSL", None),
        ("dataframe", "DATAFRAME_DSL", None),
        ("relnode", "REL_NODE", None),
    ],
)
def test_tag_registry_resolves_every_supported_tag(
    tag: str, expected_language: str, expected_dialect: str | None
) -> None:
    src = f'def query c {{\n  sourceText: """{tag}\nSELECT 1\n"""\n}}\n'
    r = ttr_parser.parse_string(src)
    assert r.ok
    q = r.definitions[0]
    v = q.source_text  # type: ignore[attr-defined]
    assert isinstance(v, TaggedBlockValue)
    assert v.tag == tag
    assert v.language == expected_language
    assert v.dialect == expected_dialect
