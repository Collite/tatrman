# SPDX-License-Identifier: Apache-2.0
"""Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block and the
document-level `security { … }` block.

Twin of the TS `semantics-block.test.ts` and the Kotlin `SemanticsBlockSpec.kt`;
case names are kept aligned for conformance triage. These two blocks were the
only AST surface the Python walker never implemented, which is what kept the
`py-vs-ts` conformance gate advisory (see `.github/workflows/conformance.yml`).
"""

from __future__ import annotations

from ttr_parser import parse_string
from ttr_parser.diagnostics import DiagnosticCode


def _entity(src: str):
    r = parse_string(src, "x.ttrm")
    return r, r.definitions[0]


def test_carries_a_nested_object_and_a_list_verbatim() -> None:
    # They are NOT rejected: the grammar's `value` has always admitted `list` and
    # `object_`, and which keys may hold structure is vocabulary knowledge that lives
    # in the semantics layer, not here.
    r, e = _entity(
        "model er\ndef entity E { semantics { role: event_date, bad: { x: 1 }, worse: [1, 2] } }"
    )
    assert [d for d in r.errors if d.code == DiagnosticCode.SEMANTICS_NON_SCALAR_VALUE] == []
    assert e.semantics is not None
    assert e.semantics.entries["role"] == "event_date"
    assert dict(e.semantics.entries["bad"]) == {"x": 1.0}
    assert e.semantics.entries["worse"] == (1.0, 2.0)


def test_carries_the_v3_mention_surface() -> None:
    # MS contracts §1.1, end to end through the walker. Both item spellings survive,
    # in DECLARED ORDER — the first measure is the default measure, so order is
    # contract, not incidental.
    _, e = _entity(
        "model er\ndef entity sales { semantics { kind: period_table, name: customer_name, "
        "code: doc_no, measures: [amount_czk, { attribute: quantity, aggregation: avg }] } }"
    )
    assert e.semantics.entries["name"] == "customer_name"
    assert e.semantics.entries["code"] == "doc_no"
    measures = e.semantics.entries["measures"]
    assert measures[0] == "amount_czk"
    assert dict(measures[1]) == {"attribute": "quantity", "aggregation": "avg"}


def test_still_rejects_a_function_call_value() -> None:
    # A functionCall has no data meaning in a semantics block at any depth, so the
    # non-scalar diagnostic survives for exactly that case rather than being retired.
    #
    # NB this loader gates definitions on walker errors (as the Kotlin one does, and
    # unlike the TS loader which keeps the AST alongside the diagnostic), so on
    # rejection `definitions` is empty; the contract verified here is the diagnostic.
    r = parse_string("model er\ndef entity E { semantics { role: event_date, bad: now() } }", "x.ttrm")
    assert len([d for d in r.errors if d.code == DiagnosticCode.SEMANTICS_NON_SCALAR_VALUE]) == 1


def test_duplicate_keys_are_recorded_at_every_depth() -> None:
    # Last-wins on the value, but the overwrite is recorded — a repeat inside a nested
    # object as the dotted/indexed path to it (review-081 F6).
    _, e = _entity(
        "model er\ndef entity E { semantics { kind: poi, kind: calendar, "
        "measures: [{ attribute: a, attribute: b }] } }"
    )
    assert "kind" in e.semantics.duplicate_properties
    assert "measures[0].attribute" in e.semantics.duplicate_properties
    assert e.semantics.entries["kind"] == "calendar"


def test_semantics_on_a_db_table_and_its_columns() -> None:
    r = parse_string(
        "model db\ndef table t { semantics { kind: period_table }, "
        "columns: [ def column c { type: date, semantics { role: period_start } } ] }",
        "x.ttrm",
    )
    table = r.definitions[0]
    assert table.semantics.entries["kind"] == "period_table"
    assert table.columns[0].semantics.entries["role"] == "period_start"


def test_security_block_carries_the_four_verbs() -> None:
    r = parse_string(
        "model db\n"
        "security {\n"
        "  own sales: team_sales\n"
        "  classify order_line.customer_email: pii\n"
        "  grant read on sales to accounting\n"
        "  mask order_line.customer_email\n"
        "}\n",
        "x.ttrm",
    )
    assert len(r.security_blocks) == 1
    sts = r.security_blocks[0].statements
    assert [s.verb for s in sts] == ["own", "classify", "grant", "mask"]
    assert (sts[0].object_ref, sts[0].owner) == ("sales", "team_sales")
    assert (sts[1].object_ref, sts[1].classification) == ("order_line.customer_email", "pii")
    # `grant <privilege> on <object> to <grantee>` — id order is privilege, object,
    # grantee, which is NOT the order the conformance dump emits them in.
    assert (sts[2].privilege, sts[2].object_ref, sts[2].grantee) == ("read", "sales", "accounting")
    assert sts[3].object_ref == "order_line.customer_email"
