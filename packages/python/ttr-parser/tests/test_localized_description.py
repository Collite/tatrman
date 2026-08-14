# SPDX-License-Identifier: Apache-2.0
"""NLS-P10 (grammar 0.13, GXP-D7) — Python parity for the localised ``description:``.

Mirrors the TS ``localized-description.test.ts`` and the Kotlin
``LocalizedDescriptionSpec.kt``. Two carriers, never one: ``description`` keeps the
plain form and ``description_localized`` the map, and the walker never folds a map
down to one locale — that is a reader's decision (Veles' D7 fallback chain).
"""

from __future__ import annotations

from typing import Any

from ttr_parser import parse_string
from ttr_parser.model import EntityDef, RoleDef, TableDef


def _def(src: str, index: int = 0) -> Any:
    result = parse_string(src, "test.ttrm")
    assert result.errors == ()
    return result.definitions[index]


def test_plain_string_description_is_unchanged() -> None:
    e: EntityDef = _def('def entity Product { description: "the product" }')
    assert e.description == "the product"
    assert e.description_localized is None


def test_two_locale_map_lands_in_description_localized() -> None:
    e: EntityDef = _def('def entity Product { description: { en: "product name", cs: "Název produktu" } }')
    assert e.description is None
    assert dict(e.description_localized.by_language) == {
        "en": "product name",
        "cs": "Název produktu",
    }


def test_single_entry_map_is_legal() -> None:
    e: EntityDef = _def('def entity Product { description: { cs: "Produkt" } }')
    assert dict(e.description_localized.by_language) == {"cs": "Produkt"}


def test_empty_map_parses_to_an_empty_entry_set() -> None:
    # Ruling (NLS-P10 T1): the empty map is a LINT warning, not a parse error —
    # the parser stays mechanical and the D7 chain already ends at "".
    e: EntityDef = _def("def entity Product { description: {} }")
    assert e.description is None
    assert dict(e.description_localized.by_language) == {}


def test_triple_string_inside_the_map_is_dedented() -> None:
    e: EntityDef = _def('def entity Product { description: { en: """\n    a\n    b\n    """ } }')
    assert e.description_localized.by_language["en"] == "a\nb\n"


def test_map_form_on_a_table_and_its_columns() -> None:
    src = (
        "def table sales {\n"
        '  description: { en: "sales", cs: "prodeje" },\n'
        "  columns: [\n"
        '    def column amount { type: decimal, description: { en: "amount", cs: "částka" } }\n'
        "  ]\n"
        "}"
    )
    t: TableDef = _def(src)
    assert dict(t.description_localized.by_language) == {"en": "sales", "cs": "prodeje"}
    assert dict(t.columns[0].description_localized.by_language) == {"en": "amount", "cs": "částka"}


def test_map_form_on_an_entity_and_its_attributes() -> None:
    src = (
        "def entity Product {\n"
        '  description: { en: "a product", cs: "produkt" },\n'
        "  attributes: [\n"
        '    def attribute name { type: string, description: { en: "its name", cs: "jméno" } }\n'
        "  ]\n"
        "}"
    )
    e: EntityDef = _def(src)
    assert dict(e.description_localized.by_language) == {"en": "a product", "cs": "produkt"}
    assert dict(e.attributes[0].description_localized.by_language) == {"en": "its name", "cs": "jméno"}


def test_map_form_on_a_role() -> None:
    r: RoleDef = _def('def role customer { description: { en: "buyer", cs: "kupující" } }')
    assert dict(r.description_localized.by_language) == {"en": "buyer", "cs": "kupující"}


def test_both_forms_coexist_in_one_document() -> None:
    src = 'def entity A { description: "plain" }\ndef entity B { description: { en: "mapped" } }'
    a: EntityDef = _def(src, 0)
    b: EntityDef = _def(src, 1)
    assert a.description == "plain"
    assert a.description_localized is None
    assert b.description is None
    assert dict(b.description_localized.by_language) == {"en": "mapped"}
