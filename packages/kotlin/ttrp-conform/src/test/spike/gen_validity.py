#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
#
# RJ-P0 / Stage 0.2 — derive the canonical validity specs + per-engine domain verdicts.
#
# Each canonical domain (R-A3-β: "near-PG, simplified where PG is irregular") is encoded here
# ONCE as a Python predicate + its declarative form (regex / trim / bounds / tokens). From that
# single source this script:
#   * classifies every real 0.1 probe as canonical accept/reject  -> the YAML corpus is drawn from
#     real probes, never hand-picked, so it can't drift from the stated domain;
#   * cross-tabs canonical vs pg_cast / pg_input_is_valid / polars(strict=False) to derive the
#     manifest `domain` verdict per engine (canonical | wider | narrower | divergent);
#   * writes ttrp-frontend/src/main/resources/ttrp/validity/*.yaml (the normative specs) and
#     results/verdicts.md + results/canonical.csv (the evidence for Bora's sign-off).
#
# The Python predicate is the spike's model of the domain; RJ-P1 re-implements it in the Kotlin
# catalogue and `ttrp conform` (RJ-P5) proves the two agree on this same corpus. Regexes are kept
# RE2-safe (no backrefs / lookaround) so PG `~` and Polars `str.contains` can both run them.
#
# Run:  python3 packages/kotlin/ttrp-conform/src/test/spike/gen_validity.py

import csv
import datetime as dt
import json
import pathlib
import re
from decimal import Decimal, InvalidOperation

import yaml

HERE = pathlib.Path(__file__).resolve().parent
RESULTS = HERE / "results"
REPO = HERE.parents[5]  # spike/ -> test -> src -> ttrp-conform -> kotlin -> packages -> tatrman
VALIDITY_DIR = REPO / "packages/kotlin/ttrp-frontend/src/main/resources/ttrp/validity"
NULL_TOKEN = "<<NULL>>"

ASCII_WS = " \t\n\r\f\v"
INT64_MIN, INT64_MAX = -(2**63), 2**63 - 1


def trim(s: str) -> str:
    return s.strip(ASCII_WS)


# --- canonical predicates (return True = in the canonical acceptance domain) ---------------

INT_RE = re.compile(r"[+-]?[0-9]+")
DEC_RE = re.compile(r"[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?")
FLOAT_SPECIAL = {"inf", "+inf", "-inf", "infinity", "+infinity", "-infinity", "nan", "+nan", "-nan"}
DATE_RE = re.compile(r"[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}")
TS_RE = re.compile(r"([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})[T ]([0-9]{1,2}):([0-9]{2}):([0-9]{2})(\.[0-9]+)?Z?")
BOOL_TOKENS = {"true", "false", "t", "f", "yes", "no", "y", "n", "on", "off", "1", "0"}


def can_int64(s):
    t = trim(s)
    if not INT_RE.fullmatch(t):
        return False
    return INT64_MIN <= int(t) <= INT64_MAX


def can_decimal(s):
    t = trim(s)
    if not DEC_RE.fullmatch(t):
        return False
    try:
        v = Decimal(t)
    except InvalidOperation:
        return False
    if not v.is_finite():
        return False
    v = v.quantize(Decimal("0.0001"))  # scale 4 (silent round, like PG/Polars)
    return abs(v) < Decimal(10) ** 14  # 18 precision - 4 scale = 14 integer digits


def can_float64(s):
    t = trim(s)
    if t.lower() in FLOAT_SPECIAL:
        return True
    if not DEC_RE.fullmatch(t):
        return False
    try:
        f = float(t)
    except ValueError:
        return False
    import math

    return math.isfinite(f)  # overflow -> inf -> reject; underflow -> 0.0 -> accept


def can_date(s):
    t = trim(s)
    if not DATE_RE.fullmatch(t):
        return False
    y, m, d = (int(x) for x in t.split("-"))
    try:
        dt.date(y, m, d)
        return True
    except ValueError:
        return False


def can_timestamp(s):
    t = trim(s)
    mm = TS_RE.fullmatch(t)
    if not mm:
        return False
    y, mo, d, hh, mi, ss = (int(mm.group(i)) for i in range(1, 7))
    try:
        dt.date(y, mo, d)
    except ValueError:
        return False
    return 0 <= hh <= 23 and 0 <= mi <= 59 and 0 <= ss <= 59  # reject 24:00, 25:00, :61


def can_bool(s):
    return trim(s).lower() in BOOL_TOKENS


# --- spec table: one entry per RS-1 cast pair (op.div + datetime parse authored separately) ---

CAST_SPECS = [
    {
        "code": "TTRP-RJ-001", "typePair": "text->int64", "predicate": can_int64,
        "domain": {"kind": "regex+bounds", "regex": r"^[+-]?[0-9]+$", "trim": "ascii-ws", "bounds": "int64"},
        "deviations": [
            "PG accepts radix-prefixed and grouped integers (0x1F->31, 0o17, 0b101, 1_234); canonical rejects them — non-portable PG lexer quirks.",
            "Trim is ASCII-whitespace only; PG likewise never trims Unicode spaces (NBSP/thin/ideographic stay rejected).",
        ],
    },
    {
        "code": "TTRP-RJ-002", "typePair": "text->decimal(18,4)", "predicate": can_decimal,
        "domain": {"kind": "regex+bounds", "regex": r"^[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?$", "trim": "ascii-ws", "bounds": "decimal(18,4)"},
        "deviations": [
            "PG accepts NaN and 0x1F for numeric; canonical rejects both (a finite base-10 literal only).",
            "Fractional digits beyond scale 4 are silently rounded (12.00005 -> 12.0001), matching PG and Polars — accepted, not rejected.",
        ],
    },
    {
        "code": "TTRP-RJ-003", "typePair": "text->float64", "predicate": can_float64,
        "domain": {"kind": "regex+bounds", "regex": r"^([+-]?([0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?|[+-]?(inf|infinity)|[+-]?nan)$", "trim": "ascii-ws", "bounds": "float64-finite", "caseInsensitive": True},
        "deviations": [
            "Explicit inf/-inf/infinity/nan tokens are accepted (both engines do). A finite literal is required otherwise: overflow (1e309 -> inf) is REJECTED (near-PG); underflow (1e-400 -> 0.0) is accepted as a representable value (PG rejects it — a PG irregularity we drop).",
            "PG accepts 0x1F for float; canonical rejects it.",
        ],
    },
    {
        "code": "TTRP-RJ-004", "typePair": "text->date", "predicate": can_date,
        "domain": {"kind": "regex+bounds", "regex": r"^[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}$", "trim": "ascii-ws", "bounds": "valid-gregorian-date"},
        "deviations": [
            "Format-anchored to dash-separated ISO Y-M-D (1-2 digit month/day allowed — both engines accept 2024-1-5). PG's locale forms (01/15/2024, Jan 15 2024), compact 20240115, datetime forms, and the NON-DETERMINISTIC keywords today/epoch/infinity are all rejected — determinism + purity (R-C2-b).",
        ],
    },
    {
        "code": "TTRP-RJ-005", "typePair": "text->timestamp", "predicate": can_timestamp,
        "domain": {"kind": "regex+bounds", "regex": r"^[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}[T ][0-9]{1,2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z?$", "trim": "ascii-ws", "bounds": "valid-datetime"},
        "deviations": [
            "ISO date+time with a 'T' OR space separator, optional fractional seconds, optional trailing Z; a time component is REQUIRED (PG's date-only-as-midnight is rejected).",
            "PG's 24:00:00 roll-over and the keywords now/epoch/infinity are rejected (out-of-range time / non-deterministic). Polars' default cast takes only the 'T' form — space-separated needs an explicit format in emit.",
        ],
    },
    {
        "code": "TTRP-RJ-006", "typePair": "text->bool", "predicate": can_bool,
        "domain": {"kind": "enum-ci", "tokens": sorted(BOOL_TOKENS), "trim": "ascii-ws"},
        "deviations": [
            "A fixed case-insensitive token set (PG's set, minus its ambiguous unique-prefix matching — 'tru' is rejected).",
            "Polars has NO string->Boolean cast (it errors on every value): RJ-P4 must emit a bespoke parse (lowercased is_in over the token set), not a cast.",
        ],
    },
]


def load_engine(name, col):
    with (RESULTS / name).open(encoding="utf-8") as fh:
        return {(r["type"], r["probe_json"]): r[col] for r in csv.DictReader(fh)}


def state(verdict):
    return "Y" if verdict == "accept" else ("-" if verdict == "null" else "N")


def relation(canon: set, engine: set) -> str:
    """canonical vs engine acceptance sets over comparable (non-null) probes."""
    if canon == engine:
        return "canonical"
    if canon < engine:
        return "wider"
    if engine < canon:
        return "narrower"
    return "divergent"  # incomparable (each accepts something the other rejects)


def yaml_list(items):
    return "[" + ", ".join(json.dumps(x, ensure_ascii=True) for x in items) + "]"


def main():
    corpus = yaml.safe_load((HERE / "corpus.yaml").read_text(encoding="utf-8"))
    pg_cast = load_engine("pg.csv", "pg_cast")
    pg_valid = load_engine("pg.csv", "pg_input_is_valid")
    pl_ns = load_engine("polars.csv", "polars_nonstrict")

    type_of = {"text->int64": "int64", "text->decimal(18,4)": "decimal(18,4)",
               "text->float64": "float64", "text->date": "date",
               "text->timestamp": "timestamp", "text->bool": "bool"}

    VALIDITY_DIR.mkdir(parents=True, exist_ok=True)
    verdict_rows = []
    canon_csv = []

    for spec in CAST_SPECS:
        tname = type_of[spec["typePair"]]
        probes = [NULL_TOKEN if p is None else str(p) for p in corpus["types"][tname]["probes"]]
        accept, reject = [], []
        canon_set, pg_set, pgv_set, pl_set = set(), set(), set(), set()
        for raw in probes:
            key = (tname, json.dumps(raw, ensure_ascii=True))
            if raw == NULL_TOKEN:
                continue  # 3VL: NULL is success on every engine, not part of the domain corpus
            c = spec["predicate"](raw)
            (accept if c else reject).append(raw)
            if c:
                canon_set.add(raw)
            if state(pg_cast.get(key, "")) == "Y":
                pg_set.add(raw)
            if state(pg_valid.get(key, "")) == "Y":
                pgv_set.add(raw)
            if state(pl_ns.get(key, "")) == "Y":
                pl_set.add(raw)
            canon_csv.append([tname, key[1], "Y" if c else "N",
                              state(pg_cast.get(key, "")), state(pl_ns.get(key, ""))])

        rel_pg = relation(canon_set, pg_set)
        rel_pgv = relation(canon_set, pgv_set)
        rel_pl = relation(canon_set, pl_set)
        # nativeForm is emit-usable ONLY when the engine form's domain == canonical exactly.
        # Corpus agreement is necessary but NOT sufficient for the manifest claim (small corpus,
        # architecture §9 top risk) — a corpus-canonical relation is a *candidate*, not proof.
        pg_native = "pg_input_is_valid" if rel_pgv == "canonical" else None
        pl_native = "str-cast (candidate)" if rel_pl == "canonical" else None
        verdict_rows.append({
            "code": spec["code"], "typePair": spec["typePair"],
            "pg_cast": rel_pg, "pg_input_is_valid": rel_pgv, "polars": rel_pl,
            "pg_nativeForm": pg_native, "pl_nativeForm": pl_native,
            "n_accept": len(accept), "n_reject": len(reject),
        })
        write_yaml(spec, accept, reject)

    write_noncast_yamls()
    write_verdicts(verdict_rows)

    with (RESULTS / "canonical.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["type", "probe_json", "canonical", "pg_cast", "polars_nonstrict"])
        w.writerows(canon_csv)

    print(f"[gen] wrote {len(CAST_SPECS)} cast + 3 non-cast YAMLs -> {VALIDITY_DIR}")
    print(f"[gen] verdicts -> results/verdicts.md ; canonical.csv ({len(canon_csv)} rows)")
    for v in verdict_rows:
        print(f"       {v['code']} {v['typePair']:22s} pg_cast={v['pg_cast']:9s} "
              f"pg_is_valid={v['pg_input_is_valid']:9s} polars={v['polars']}")


def write_yaml(spec, accept, reject):
    d = spec["domain"]
    lines = [f"# {spec['code']} — canonical validity for cast {spec['typePair']} (RJ-P0, R-A3-β).",
             "# Corpus rows are the real 0.1 divergence probes, partitioned by the domain below.",
             "# Deviations from raw PG-cast (canonical simplifies where PG is irregular):"]
    for dev in spec["deviations"]:
        lines += ["#   - " + dev]
    lines += ["function: cast", f"typePair: {spec['typePair']}", f"code: {spec['code']}",
              "pure: true", "domain:", f"  kind: {d['kind']}"]
    if "regex" in d:
        lines.append(f"  regex: {json.dumps(d['regex'])}")
    if "tokens" in d:
        lines.append(f"  tokens: {yaml_list(d['tokens'])}")
    if "caseInsensitive" in d:
        lines.append("  caseInsensitive: true")
    lines.append(f"  trim: {d['trim']}")
    if "bounds" in d:
        lines.append(f"  bounds: {d['bounds']}")
    lines += ["corpus:", f"  accept: {yaml_list(accept)}", f"  reject: {yaml_list(reject)}",
              "  null_is_success: true", ""]
    fname = f"cast.{spec['typePair'].replace('->', '-').replace('(', '').replace(')', '').replace(',', '_')}.yaml"
    (VALIDITY_DIR / fname).write_text("\n".join(lines), encoding="utf-8")


def write_noncast_yamls():
    # op.div — trivial domain on the *evaluated* denominator (not a text form; no regex).
    (VALIDITY_DIR / "op.div.yaml").write_text(
        "# TTRP-RJ-007 — canonical validity for op.div (RJ-P0). Guards the EVALUATED denominator,\n"
        "# not a text form: no regex, no trim. Corpus values are evaluated numerics for conform.\n"
        "function: op.div\n"
        "typePair: numeric,numeric->numeric\n"
        "code: TTRP-RJ-007\n"
        "pure: true\n"
        "domain:\n"
        "  kind: predicate\n"
        "  predicate: denominator != 0\n"
        "corpus:\n"
        '  accept: ["1", "-3", "0.5", "9223372036854775807"]\n'
        '  reject: ["0", "0.0", "-0", "0.0000"]\n'
        "  null_is_success: true\n",
        encoding="utf-8",
    )
    # fn.to_date / fn.to_timestamp — format-anchored parse (format is a call argument). The domain
    # is "parseable under the supplied strptime pattern"; the corpus below is for the canonical ISO
    # pattern and reuses the date/timestamp probes.
    (VALIDITY_DIR / "fn.to_date.yaml").write_text(
        "# TTRP-RJ-008 — canonical validity for fn.to_date(text, fmt) (RJ-P0). Domain is\n"
        "# format-anchored: the text must match the supplied fmt exactly. Corpus shown for fmt\n"
        "# '%Y-%m-%d' (ISO); other patterns anchor their own domain at author time.\n"
        "function: fn.to_date\n"
        "typePair: text->date\n"
        "code: TTRP-RJ-008\n"
        "pure: true\n"
        "domain:\n"
        "  kind: format-anchored\n"
        "  format: strptime\n"
        "  exampleFormat: '%Y-%m-%d'\n"
        "  trim: none\n"
        "corpus:\n"
        '  accept: ["2024-01-15", "2024-02-29", "0001-01-01", "9999-12-31"]\n'
        '  reject: ["2023-02-29", "2024-13-01", "01/15/2024", "20240115", "today", "", "abc"]\n'
        "  null_is_success: true\n",
        encoding="utf-8",
    )
    (VALIDITY_DIR / "fn.to_timestamp.yaml").write_text(
        "# TTRP-RJ-009 — canonical validity for fn.to_timestamp(text, fmt) (RJ-P0). Format-anchored\n"
        "# like fn.to_date; corpus shown for fmt '%Y-%m-%d %H:%M:%S'.\n"
        "function: fn.to_timestamp\n"
        "typePair: text->timestamp\n"
        "code: TTRP-RJ-009\n"
        "pure: true\n"
        "domain:\n"
        "  kind: format-anchored\n"
        "  format: strptime\n"
        "  exampleFormat: '%Y-%m-%d %H:%M:%S'\n"
        "  trim: none\n"
        "corpus:\n"
        '  accept: ["2024-01-15 12:30:00", "2024-02-29 00:00:00", "1969-12-31 23:59:59"]\n'
        '  reject: ["2024-01-15 24:00:00", "2024-01-15 25:00:00", "2024-01-15", "now", "epoch", "abc", ""]\n'
        "  null_is_success: true\n",
        encoding="utf-8",
    )


def write_verdicts(rows):
    L = ["# RJ-P0 manifest domain verdicts (contracts §3)\n",
         "> Per engine × validity entry: the `domain` relation between the engine's native "
         "acceptance and the **canonical** domain (contracts §2), derived from `canonical.csv`. "
         "`nativeForm` is emit-usable **only** where the relation is `canonical` (exact match); "
         "otherwise the emitter lowers the canonical guard from the YAML.\n",
         "Relation: `canonical` = identical • `wider` = engine accepts a superset • "
         "`narrower` = engine accepts a subset • `divergent` = incomparable (each accepts something "
         "the other rejects).\n",
         "\n## postgres-16\n",
         "| code | pair | pg_cast vs canon | pg_input_is_valid vs canon | nativeForm | verdict |",
         "|---|---|---|---|---|---|"]
    for v in rows:
        native = v["pg_nativeForm"] or "— (none)"
        emit = "guard (from YAML)" if not v["pg_nativeForm"] else f"native `{v['pg_nativeForm']}`"
        L.append(f"| {v['code']} | {v['typePair']} | {v['pg_cast']} | {v['pg_input_is_valid']} | {native} | {emit} |")
    L += ["\n> **PG headline:** `pg_input_is_valid` is never exactly canonical (it inherits every "
          "PG lexer irregularity — hex/underscore ints, NaN numerics, locale/keyword dates). So "
          "**no PG entry uses a nativeForm**; every reject site emits the canonical regex/bounds "
          "guard. `pg_input_is_valid` is still valuable as a *fast pre-check* but is not the "
          "canonical oracle.\n",
          "\n## polars (>= 1.42)\n",
          "| code | pair | polars(strict=False) vs canon | nativeForm | verdict |",
          "|---|---|---|---|---|"]
    for v in rows:
        native = v["pl_nativeForm"] or "— (none)"
        emit = "guard (from YAML)" if not v["pl_nativeForm"] else "guard (default; native candidate — see note)"
        L.append(f"| {v['code']} | {v['typePair']} | {v['polars']} | {native} | {emit} |")
    L += ["\n> **Polars headline:** numeric casts are `narrower` (no whitespace trim); `float64` is "
          "`divergent` (narrower on trim, wider on overflow→inf); `bool` accepts **nothing** (no "
          "string→Boolean cast) so its guard must parse via `is_in`, not cast; `timestamp` is "
          "`narrower` and `date` is **corpus-`canonical`**. Temporal string casts are **deprecated** "
          "in Polars (removed in 2.0) — emit `str.to_date`/`str.to_datetime`, not `cast`.\n",
          "> `text->date` matches canonical on all its date probes, so a Polars native form is a "
          "*candidate* — but that corpus does not prove domain equality for all inputs "
          "(architecture §9 top risk), so the safe default stays **guard**; promote to native only "
          "with a wider corpus. Pin the manifest `minVersion` to **1.42**.\n",
          "\n## mssql-2022\n",
          "Parked — no local MSSQL env (R-Q10). Every entry stays `domain: unknown`, `nativeForm: "
          "null` until the container runs (RJ-P3.3); `unknown` ⇒ canonical guard, so PG+Polars seal "
          "the feature without it.\n"]
    (RESULTS / "verdicts.md").write_text("\n".join(L) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
