# SPDX-License-Identifier: Apache-2.0
"""RV-P1.5 (grammar 0.12, RV-31/RV-32) — Python parity for the match method.

Mirrors the TS ``packages/semantics/src/__tests__/search-method.test.ts`` and the
Kotlin ``SearchMethodSpec.kt``. The diagnostic MESSAGES are a cross-target
contract (the ``fuzzy`` deprecation rides the portable conformance subset), so
they are asserted verbatim here too.
"""

from __future__ import annotations

import pytest

from ttr_parser import DiagnosticCode, parse_string
from ttr_parser.model import SearchHintsValue, TableDef
from ttr_parser.semantics.search_method import (
    EffectiveMatchMethod,
    effective_match_method,
    validate_search_method,
)


def _search(body: str) -> SearchHintsValue:
    result = parse_string(f"def table T {{ search {{ {body} }} }}", "test.ttrm")
    assert result.errors == ()
    table = result.definitions[0]
    assert isinstance(table, TableDef)
    return table.search


# --------------------------------------------------------------------------
# The parser stays mechanical
# --------------------------------------------------------------------------


def test_method_name_and_argument_are_captured_as_authored() -> None:
    method = _search("searchable method: TYPOS(2)").method
    assert method is not None
    assert method.name == "TYPOS"
    assert method.argument == 2.0


def test_bare_searchable_is_the_inclusion_marker() -> None:
    search = _search("searchable")
    assert search.searchable is True
    assert search.method is None


def test_searchable_false_still_parses() -> None:
    assert _search("searchable: false").searchable is False


def test_authored_fuzzy_false_is_distinguishable_from_an_absent_fuzzy() -> None:
    assert _search("searchable").fuzzy_authored is False
    authored = _search("searchable: true, fuzzy: false")
    assert authored.fuzzy is False
    assert authored.fuzzy_authored is True


# --------------------------------------------------------------------------
# effective_match_method
# --------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("body", "expected"),
    [
        ("searchable method: TYPOS(2)", EffectiveMatchMethod("TYPOS", 2, "authored")),
        ("searchable method: TYPOS", EffectiveMatchMethod("TYPOS", 1, "authored")),
        ("searchable method: EXACT", EffectiveMatchMethod("EXACT", None, "authored")),
        ("searchable method: TOKENS", EffectiveMatchMethod("TOKENS", None, "authored")),
        ("searchable method: typos(2)", EffectiveMatchMethod("TYPOS", 2, "authored")),
        ("searchable", EffectiveMatchMethod("TYPOS", 1, "default")),
        ("searchable: true", EffectiveMatchMethod("TYPOS", 1, "default")),
        (
            "searchable: true, fuzzy: true",
            EffectiveMatchMethod("TYPOS", 1, "legacy-fuzzy"),
        ),
        (
            "searchable: true, fuzzy: false",
            EffectiveMatchMethod("EXACT", None, "legacy-fuzzy"),
        ),
        # A rejected method degrades to the default rather than failing.
        ("searchable method: TYPSO", EffectiveMatchMethod("TYPOS", 1, "default")),
        ("searchable method: TYPOS(0)", EffectiveMatchMethod("TYPOS", 1, "default")),
    ],
)
def test_effective_match_method(body: str, expected: EffectiveMatchMethod) -> None:
    assert effective_match_method(_search(body)) == expected


def test_a_carrier_that_is_not_included_has_no_method() -> None:
    assert effective_match_method(_search("searchable: false")) is None
    assert effective_match_method(_search('keywords: { cs: ["a"] }')) is None
    assert effective_match_method(None) is None


def test_an_explicit_method_wins_over_a_legacy_fuzzy() -> None:
    method = effective_match_method(_search("searchable method: TOKENS, fuzzy: true"))
    assert method == EffectiveMatchMethod("TOKENS", None, "authored")


# --------------------------------------------------------------------------
# validate_search_method
# --------------------------------------------------------------------------


def test_the_fuzzy_deprecation_message_is_the_cross_target_contract_text() -> None:
    diags = validate_search_method(_search("searchable: true, fuzzy: true"))
    assert len(diags) == 1
    code, message, is_error = diags[0]
    assert code == DiagnosticCode.SEARCH_FUZZY_DEPRECATED
    assert is_error is False
    assert message == (
        "'fuzzy: true' is deprecated (grammar 0.12) — replace it with "
        "'searchable method: TYPOS(1)'"
    )
    assert "searchable method: EXACT" in (
        validate_search_method(_search("searchable: true, fuzzy: false"))[0][1]
    )


def test_the_0_12_form_is_clean() -> None:
    assert validate_search_method(_search("searchable method: TYPOS(2)")) == []
    assert validate_search_method(_search("searchable")) == []


def test_an_unknown_method_is_an_error() -> None:
    diags = validate_search_method(_search("searchable method: TYPSO(2)"))
    assert len(diags) == 1
    code, message, is_error = diags[0]
    assert code == DiagnosticCode.UNKNOWN_MATCH_METHOD
    assert is_error is True
    assert "TOKENS" in message


@pytest.mark.parametrize(
    "body",
    ["searchable method: EXACT(2)", "searchable method: TOKENS(1)"],
)
def test_exact_and_tokens_take_no_argument(body: str) -> None:
    diags = validate_search_method(_search(body))
    assert len(diags) == 1
    assert diags[0][0] == DiagnosticCode.INVALID_MATCH_METHOD_ARGUMENT
    assert "takes no argument" in diags[0][1]


@pytest.mark.parametrize(
    "bad", ["TYPOS(0)", "TYPOS(-1)", "TYPOS(1.5)", "TYPOS(4)", "TYPOS(7)"]
)
def test_typos_distance_must_be_a_whole_number_in_1_to_3(bad: str) -> None:
    """The range is ttr-lexicon's (RG-LEX-002) — the artifact a method compiles into."""
    diags = validate_search_method(_search(f"searchable method: {bad}"))
    assert len(diags) == 1
    assert diags[0][0] == DiagnosticCode.INVALID_MATCH_METHOD_ARGUMENT
    assert "1..3" in diags[0][1]


@pytest.mark.parametrize("ok", ["TYPOS(1)", "TYPOS(2)", "TYPOS(3)"])
def test_typos_distances_in_range_are_accepted(ok: str) -> None:
    assert validate_search_method(_search(f"searchable method: {ok}")) == []


def test_a_rejected_argument_renders_like_the_other_targets() -> None:
    # `0`, not `0.0` — diagnostic text must stay byte-identical across targets.
    assert "got '0'" in validate_search_method(_search("searchable method: TYPOS(0)"))[0][1]
