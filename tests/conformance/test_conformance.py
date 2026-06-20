"""Python-side AST conformance test (contracts.md §5).

For every top-level fixture in `tests/conformance/fixtures/`, the Python
dumper (`dump.py`) writes a normalised JSON dump to `tests/conformance/out-py/`
and we assert byte-equality with the committed TS golden at
`tests/conformance/out-ts/`. Any divergence is a regression — the dump
schema is a public contract from `1.0.0` onward.

The test is gated by the same `py-vs-ts` CI job, but it runs locally too
so the harness can be developed without going through GitHub Actions.
On mismatch the test prints a unified diff of the offending fixture so
the cause is immediately readable.

Gate-failure sanity: if a walker change silently drops or renames a
property, this suite turns red. A temporary mutation in the walker
should produce a clear, fixture-level diff (verified in stage 3.1.4).
"""

from __future__ import annotations

import difflib
from collections.abc import Iterator
from pathlib import Path

import pytest
from dump import FIXTURES, OUT_PY, REPO_ROOT, dump
from dump_sem import (
    OUT_PY_SEM,
    OUT_TS_SEM,
    dir_fixtures,
    dump_sem_docs,
    single_fixtures,
)

import ttr_parser

OUT_TS = REPO_ROOT / "tests" / "conformance" / "out-ts"


def _list_fixtures() -> list[str]:
    return sorted(p.stem for p in FIXTURES.iterdir() if p.is_file() and p.suffix == ".ttr")


def _refresh() -> None:
    """Re-dump every fixture to `out-py/` from current walker output."""
    OUT_PY.mkdir(parents=True, exist_ok=True)
    for fixture in FIXTURES.iterdir():
        if not (fixture.is_file() and fixture.suffix == ".ttr"):
            continue
        result = ttr_parser.parse_file(fixture)
        if any(e.code.value == "ttr/parse-error" for e in result.errors):
            continue
        (OUT_PY / (fixture.stem + ".json")).write_text(dump(result), encoding="utf-8")


@pytest.fixture(scope="module", autouse=True)
def _dump_out_py() -> Iterator[None]:
    """Always refresh `out-py/` before the assertions.

    `dump.py` is the canonical source — re-running it keeps this test
    self-contained even if the file was edited by hand.
    """
    _refresh()
    yield


def _out_paths(stem: str) -> tuple[Path, Path]:
    return OUT_PY / f"{stem}.json", OUT_TS / f"{stem}.json"


@pytest.mark.parametrize("fixture", _list_fixtures())
def test_py_dump_matches_ts_golden(fixture: str) -> None:
    """`out-py/<fixture>.json` must equal `out-ts/<fixture>.json` byte-for-byte."""
    py_path, ts_path = _out_paths(fixture)
    assert ts_path.exists(), f"missing TS golden: {ts_path}"
    assert py_path.exists(), f"missing Python dump: {py_path}"

    py_text = py_path.read_text(encoding="utf-8")
    ts_text = ts_path.read_text(encoding="utf-8")
    if py_text == ts_text:
        return

    diff = "".join(
        difflib.unified_diff(
            ts_text.splitlines(keepends=True),
            py_text.splitlines(keepends=True),
            fromfile=f"out-ts/{fixture}.json (golden)",
            tofile=f"out-py/{fixture}.json (actual)",
            n=2,
        )
    )
    pytest.fail(
        f"AST drift in {fixture}.ttr — Python dump differs from TS golden.\n"
        f"  -- run `python tests/conformance/dump.py` to refresh out-py/\n"
        f"  -- fix the dumper (NOT the golden) if the AST shape is correct\n\n"
        f"{diff}"
    )


def test_no_unexpected_out_py_files() -> None:
    """`out-py/` must not contain files for which there is no TS golden."""
    if not OUT_PY.exists():
        return
    extras = sorted(
        p.name for p in OUT_PY.iterdir()
        if p.is_file() and p.suffix == ".json" and not (OUT_TS / p.name).exists()
    )
    assert not extras, f"extra files in out-py/ with no golden: {extras}"


def test_dump_handles_all_fixtures() -> None:
    """Every top-level `.ttr` fixture produces a non-empty `ParseResult`."""
    for fixture in FIXTURES.iterdir():
        if not (fixture.is_file() and fixture.suffix == ".ttr"):
            continue
        result = ttr_parser.parse_file(fixture)
        parse_errors = [e for e in result.errors if e.code.value == "ttr/parse-error"]
        assert not parse_errors, f"unexpected parse error in {fixture.name}: {parse_errors[0]}"


# --------------------------------------------------------------------------
# Semantics conformance (§5.1): resolution + diagnostics + symbols, byte-for-byte
# against the committed out-ts-sem/ golden, for single files AND multi-doc dirs.
# --------------------------------------------------------------------------


def _list_sem_fixtures() -> list[str]:
    """Sorted stems for single `.ttr` fixtures plus subdirectory names."""
    return sorted(
        [p.stem for p in single_fixtures()] + [d.name for d in dir_fixtures()]
    )


def _refresh_sem() -> None:
    """Re-dump every semantics fixture (single + multi-doc) to out-py-sem/."""
    OUT_PY_SEM.mkdir(parents=True, exist_ok=True)
    for fixture in single_fixtures():
        result = ttr_parser.parse_file(fixture)
        text = dump_sem_docs([(result, fixture.name)])
        (OUT_PY_SEM / (fixture.stem + ".json")).write_text(text, encoding="utf-8")
    for directory in dir_fixtures():
        docs = [
            (ttr_parser.parse_file(sub), f"{directory.name}/{sub.name}")
            for sub in sorted(directory.iterdir())
            if sub.is_file() and sub.suffix == ".ttr"
        ]
        text = dump_sem_docs(docs)
        (OUT_PY_SEM / (directory.name + ".json")).write_text(text, encoding="utf-8")


@pytest.fixture(scope="module", autouse=True)
def _dump_out_py_sem() -> Iterator[None]:
    _refresh_sem()
    yield


@pytest.mark.parametrize("fixture", _list_sem_fixtures())
def test_py_sem_dump_matches_ts_golden(fixture: str) -> None:
    """`out-py-sem/<fixture>.json` must equal `out-ts-sem/<fixture>.json` byte-for-byte."""
    py_path = OUT_PY_SEM / f"{fixture}.json"
    ts_path = OUT_TS_SEM / f"{fixture}.json"
    assert ts_path.exists(), f"missing TS semantics golden: {ts_path}"
    assert py_path.exists(), f"missing Python semantics dump: {py_path}"

    py_text = py_path.read_text(encoding="utf-8")
    ts_text = ts_path.read_text(encoding="utf-8")
    if py_text == ts_text:
        return

    diff = "".join(
        difflib.unified_diff(
            ts_text.splitlines(keepends=True),
            py_text.splitlines(keepends=True),
            fromfile=f"out-ts-sem/{fixture}.json (golden)",
            tofile=f"out-py-sem/{fixture}.json (actual)",
            n=2,
        )
    )
    pytest.fail(
        f"Resolution drift in {fixture} — Python semantics dump differs from TS golden.\n"
        f"  -- run `python tests/conformance/dump_sem.py` to refresh out-py-sem/\n"
        f"  -- fix the resolver/validator/stock (NOT the golden) if Python is wrong\n\n"
        f"{diff}"
    )


def test_no_unexpected_out_py_sem_files() -> None:
    """`out-py-sem/` must not contain files for which there is no TS golden."""
    if not OUT_PY_SEM.exists():
        return
    extras = sorted(
        p.name
        for p in OUT_PY_SEM.iterdir()
        if p.is_file() and p.suffix == ".json" and not (OUT_TS_SEM / p.name).exists()
    )
    assert not extras, f"extra files in out-py-sem/ with no golden: {extras}"
