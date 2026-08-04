# SPDX-License-Identifier: Apache-2.0
"""RV-P1.5 (grammar 0.12, RV-31/RV-32) — the meaning of `searchable` + `method`.

Python port of the TS ``@tatrman/semantics`` ``search-method.ts`` and the Kotlin
``SearchMethod.kt``; the three must agree message for message, because the
``fuzzy`` deprecation rides the portable cross-target conformance subset.

The parser is mechanical (name as authored, raw argument). This module owns the
closed EXACT/TYPOS/TOKENS vocabulary, the arity rule, the RV-32 default of
``TYPOS(1)``, and the mapping of the deprecated ``fuzzy`` boolean.

Resolution NEVER fails: a rejected method degrades to the default so consumers
keep working on a model whose diagnostics nobody has fixed yet.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from ..diagnostics import DiagnosticCode
from ..model import SearchHintsValue

#: RV-32: the default edit distance for an included carrier that declares none.
DEFAULT_TYPOS_DISTANCE = 1

MatchMethodName = Literal["EXACT", "TYPOS", "TOKENS"]
MatchMethodOrigin = Literal["authored", "legacy-fuzzy", "default"]

_KNOWN: frozenset[str] = frozenset({"EXACT", "TYPOS", "TOKENS"})


@dataclass(frozen=True, slots=True)
class EffectiveMatchMethod:
    name: MatchMethodName
    #: Maximum edit distance — TYPOS only.
    max_distance: int | None
    origin: MatchMethodOrigin


_DEFAULT_METHOD = EffectiveMatchMethod("TYPOS", DEFAULT_TYPOS_DISTANCE, "default")


def _is_valid_distance(d: float | None) -> bool:
    return d is None or (d > 0 and float(d).is_integer())


def format_argument(argument: float | None) -> str:
    """Render a raw numeric argument the way TS/Kotlin do (``2``, not ``2.0``)."""
    if argument is None:
        return "None"
    return str(int(argument)) if float(argument).is_integer() else str(argument)


def effective_match_method(
    search: SearchHintsValue | None,
) -> EffectiveMatchMethod | None:
    """The method a carrier's content is matched with, or ``None`` when the
    carrier is not included in the lexicon.

    Precedence: an authored ``method:`` wins; then the deprecated ``fuzzy``
    boolean (``true`` -> ``TYPOS(1)``, ``false`` -> ``EXACT`` — the authored "no
    fuzzy" intent survives the bump); then the RV-32 default.
    """
    if search is None or not search.searchable:
        return None

    authored = search.method
    if authored is not None:
        name = authored.name.upper()
        if name not in _KNOWN:
            return _DEFAULT_METHOD
        if name == "TYPOS":
            if not _is_valid_distance(authored.argument):
                return _DEFAULT_METHOD
            distance = (
                DEFAULT_TYPOS_DISTANCE
                if authored.argument is None
                else int(authored.argument)
            )
            return EffectiveMatchMethod("TYPOS", distance, "authored")
        # EXACT / TOKENS: a stray argument is diagnosed, not obeyed.
        return EffectiveMatchMethod(name, None, "authored")  # type: ignore[arg-type]

    if search.fuzzy_authored:
        if search.fuzzy:
            return EffectiveMatchMethod("TYPOS", DEFAULT_TYPOS_DISTANCE, "legacy-fuzzy")
        return EffectiveMatchMethod("EXACT", None, "legacy-fuzzy")

    return _DEFAULT_METHOD


def fuzzy_deprecation_message(fuzzy: bool) -> str:
    """The message text is a cross-target contract — keep TS/Kotlin in step."""
    replacement = f"TYPOS({DEFAULT_TYPOS_DISTANCE})" if fuzzy else "EXACT"
    return (
        f"'fuzzy: {'true' if fuzzy else 'false'}' is deprecated (grammar 0.12) — "
        f"replace it with 'searchable method: {replacement}'"
    )


def validate_search_method(
    search: SearchHintsValue,
) -> list[tuple[DiagnosticCode, str, bool]]:
    """Validate one search block. Returns ``(code, message, is_error)`` triples."""
    out: list[tuple[DiagnosticCode, str, bool]] = []
    authored = search.method
    if authored is not None:
        name = authored.name.upper()
        if name not in _KNOWN:
            out.append(
                (
                    DiagnosticCode.UNKNOWN_MATCH_METHOD,
                    f"unknown match method '{authored.name}' — expected EXACT, "
                    f"TYPOS(n) or TOKENS; falling back to "
                    f"TYPOS({DEFAULT_TYPOS_DISTANCE})",
                    True,
                )
            )
        elif name == "TYPOS":
            if not _is_valid_distance(authored.argument):
                out.append(
                    (
                        DiagnosticCode.INVALID_MATCH_METHOD_ARGUMENT,
                        f"match method 'TYPOS' takes a positive whole-number edit "
                        f"distance; got '{format_argument(authored.argument)}' — "
                        f"falling back to TYPOS({DEFAULT_TYPOS_DISTANCE})",
                        True,
                    )
                )
        elif authored.argument is not None:
            out.append(
                (
                    DiagnosticCode.INVALID_MATCH_METHOD_ARGUMENT,
                    f"match method '{name}' takes no argument",
                    True,
                )
            )

    # The deprecation fires on the authored `fuzzy` whether or not a `method`
    # overrides it — the property is going away either way.
    if search.fuzzy_authored:
        out.append(
            (
                DiagnosticCode.SEARCH_FUZZY_DEPRECATED,
                fuzzy_deprecation_message(search.fuzzy),
                False,
            )
        )
    return out
