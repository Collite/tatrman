# RJ-P0 manifest domain verdicts (contracts §3)

> Per engine × validity entry: the `domain` relation between the engine's native acceptance and the **canonical** domain (contracts §2), derived from `canonical.csv`. `nativeForm` is emit-usable **only** where the relation is `canonical` (exact match); otherwise the emitter lowers the canonical guard from the YAML.

Relation: `canonical` = identical • `wider` = engine accepts a superset • `narrower` = engine accepts a subset • `divergent` = incomparable (each accepts something the other rejects).


## postgres-16

| code | pair | pg_cast vs canon | pg_input_is_valid vs canon | nativeForm | verdict |
|---|---|---|---|---|---|
| TTRP-RJ-001 | text->int64 | wider | wider | — (none) | guard (from YAML) |
| TTRP-RJ-002 | text->decimal(18,4) | wider | wider | — (none) | guard (from YAML) |
| TTRP-RJ-003 | text->float64 | divergent | divergent | — (none) | guard (from YAML) |
| TTRP-RJ-004 | text->date | wider | wider | — (none) | guard (from YAML) |
| TTRP-RJ-005 | text->timestamp | wider | wider | — (none) | guard (from YAML) |
| TTRP-RJ-006 | text->bool | wider | wider | — (none) | guard (from YAML) |

> **PG headline:** `pg_input_is_valid` is never exactly canonical (it inherits every PG lexer irregularity — hex/underscore ints, NaN numerics, locale/keyword dates). So **no PG entry uses a nativeForm**; every reject site emits the canonical regex/bounds guard. `pg_input_is_valid` is still valuable as a *fast pre-check* but is not the canonical oracle.


## polars (>= 1.42)

| code | pair | polars(strict=False) vs canon | nativeForm | verdict |
|---|---|---|---|---|
| TTRP-RJ-001 | text->int64 | narrower | — (none) | guard (from YAML) |
| TTRP-RJ-002 | text->decimal(18,4) | narrower | — (none) | guard (from YAML) |
| TTRP-RJ-003 | text->float64 | divergent | — (none) | guard (from YAML) |
| TTRP-RJ-004 | text->date | canonical | str-cast (candidate) | guard (default; native candidate — see note) |
| TTRP-RJ-005 | text->timestamp | narrower | — (none) | guard (from YAML) |
| TTRP-RJ-006 | text->bool | narrower | — (none) | guard (from YAML) |

> **Polars headline:** numeric casts are `narrower` (no whitespace trim); `float64` is `divergent` (narrower on trim, wider on overflow→inf); `bool` accepts **nothing** (no string→Boolean cast) so its guard must parse via `is_in`, not cast; `timestamp` is `narrower` and `date` is **corpus-`canonical`**. Temporal string casts are **deprecated** in Polars (removed in 2.0) — emit `str.to_date`/`str.to_datetime`, not `cast`.

> `text->date` matches canonical on all its date probes, so a Polars native form is a *candidate* — but that corpus does not prove domain equality for all inputs (architecture §9 top risk), so the safe default stays **guard**; promote to native only with a wider corpus. Pin the manifest `minVersion` to **1.42**.


## mssql-2022

Parked — no local MSSQL env (R-Q10). Every entry stays `domain: unknown`, `nativeForm: null` until the container runs (RJ-P3.3); `unknown` ⇒ canonical guard, so PG+Polars seal the feature without it.

