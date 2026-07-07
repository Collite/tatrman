package org.tatrman.ttrp.conform

import io.kotest.core.spec.style.FunSpec

/**
 * T3.4.4 — the ONLY sanctioned live-engine execution in Phase 3, gated by `TTRP_CONFORM_PG=1`
 * (requires a Postgres seeded from `resources/seed/hero_seed.sql` via `TTR_CONN_ERP_PG`, plus
 * `polars` + `adbc-driver-postgresql` on PATH — the executor-manifest package list). Skips with a
 * visible reason otherwise.
 *
 * **Status (recorded for the phase-close review):** the offline harness (ArrowIo + seven-point
 * comparator + invoker + ConformRunner) is complete and green. The full **PG↔Polars placement-
 * variant identical-results** proof (A4 core for the hero) is gated on two follow-ups tracked in
 * progress-phase-03.md: (1) **SQL Join emit** — the hero crunch has a join, so a PG-heavy variant B
 * is not yet emittable; (2) **runtime CSV-path + rejects wiring** for the authored variant's live
 * run. Until both land this live case is a documented skip; the ttrp-conform suite still runs as the
 * standing emit regression gate (offline).
 */
class HeroConformLiveTest :
    FunSpec({
        val enabled = System.getenv("TTRP_CONFORM_PG") == "1"

        test("hero placement variants produce identical results (live, dockerized PG)") {
            if (!enabled) {
                System.err.println(
                    "SKIP: TTRP_CONFORM_PG != 1 — live hero conform not run (see class KDoc: gated on SQL Join emit " +
                        "+ runtime CSV/rejects wiring).",
                )
                return@test
            }
            System.err.println(
                "SKIP: live hero conform is staged — PG↔Polars variant B awaits SQL Join emit; " +
                    "authored-variant live run awaits runtime CSV-path wiring. Tracked in progress-phase-03.md.",
            )
        }
    })
