"""test_validator.py — port of the portable validator subset (Stage 4.1.6).

Mirrors the Kotlin `ValidatorSpec.kt` (itself the port of `validator.test.ts`),
targeting the Python API of contracts §3.6:

    Validator().validate(results, symbols) -> tuple[ValidationDiagnostic, ...]

`validate` runs the **portable subset** — `validate_document` +
`validate_references` + `validate_project` + `validate_imports` — over all
parsed documents and returns the aggregated diagnostics. Each diagnostic carries
`code` (`DiagnosticCode`), `severity` (`DiagnosticSeverity`), `source`, `message`.

Scope note: the canon portable subset (Kotlin `Validator.kt`) has **no**
cardinality validator — cardinality/target-shape/type-alias/drill_map checks the
task list mentions are TS-only and excluded to keep the §5.1 semantics
conformance byte-identical. So this suite asserts only the codes the portable
validator actually emits (the TS-only validators are not ported).

Tests-first: red until 4.2–4.4.
"""

from __future__ import annotations

from ttr_parser.semantics import SymbolTable, ValidationDiagnostic, Validator

from ttr_parser import DiagnosticCode, DiagnosticSeverity, parse_string


def _validate(*docs: tuple[str, str]) -> tuple[ValidationDiagnostic, ...]:
    """docs: (uri, src) pairs — upsert each, then validate the whole set."""
    results = []
    symbols = SymbolTable()
    for uri, src in docs:
        r = parse_string(src, uri)
        symbols.upsert_document(uri, r, package_name=r.package_name or "")
        results.append(r)
    return Validator().validate(results, symbols)


def _codes(diags: tuple[ValidationDiagnostic, ...]) -> set[DiagnosticCode]:
    return {d.code for d in diags}


def test_required_property_missing_on_attributeless_entity() -> None:
    diags = _validate(
        ("er.ttr", 'schema er namespace entity\ndef entity empty { description: "no attrs" }')
    )
    assert DiagnosticCode.REQUIRED_PROPERTY_MISSING in _codes(diags)


def test_entity_attribute_not_found_for_bad_name_attribute() -> None:
    diags = _validate(
        (
            "er.ttr",
            "schema er namespace entity\n"
            "def entity artikl { attributes: [def attribute id { type: int }] nameAttribute: ghost }",
        )
    )
    assert DiagnosticCode.ENTITY_ATTRIBUTE_NOT_FOUND in _codes(diags)


def test_primary_key_column_not_found() -> None:
    diags = _validate(
        (
            "db.ttr",
            "schema db namespace dbo\n"
            'def table orders { columns: [def column id { type: int }] primaryKey: ["bogus"] }',
        )
    )
    assert DiagnosticCode.PRIMARY_KEY_COLUMN_NOT_FOUND in _codes(diags)


def test_clean_entity_has_no_diagnostics() -> None:
    diags = _validate(
        (
            "er.ttr",
            "schema er namespace entity\n"
            "def entity artikl { attributes: [def attribute id { type: int }] }",
        )
    )
    assert diags == ()


def test_unresolved_reference_is_a_warning_by_default() -> None:
    diags = _validate(
        (
            "er.ttr",
            "schema er namespace entity\n"
            "def entity artikl { attributes: [def attribute id { type: int }] }\n"
            "def er2cnc_role x { entity: er.entity.nope role: fact }",
        )
    )
    unresolved = [d for d in diags if d.code == DiagnosticCode.UNRESOLVED_REFERENCE]
    assert unresolved
    assert all(d.severity == DiagnosticSeverity.WARNING for d in unresolved)


def test_duplicate_definition_across_two_documents() -> None:
    twin = (
        "schema er namespace entity\n"
        "def entity twin { attributes: [def attribute id { type: int }] }"
    )
    diags = _validate(("a.ttr", twin), ("b.ttr", twin))
    dups = [d for d in diags if d.code == DiagnosticCode.DUPLICATE_DEFINITION]
    # the entity AND its duplicated `id` attribute each yield a diagnostic
    assert len(dups) >= 2


def test_fuzzy_without_searchable_warns() -> None:
    diags = _validate(
        (
            "test.ttr",
            "def entity E { attributes: [def attribute A { type: text, search { fuzzy: true } }] }",
        )
    )
    assert any(
        d.code == DiagnosticCode.FUZZY_WITHOUT_SEARCHABLE
        and d.severity == DiagnosticSeverity.WARNING
        for d in diags
    )


def test_no_fuzzy_warning_when_searchable_true() -> None:
    diags = _validate(
        ("test.ttr", "def entity E { search { searchable: true, fuzzy: true } }")
    )
    assert DiagnosticCode.FUZZY_WITHOUT_SEARCHABLE not in _codes(diags)
