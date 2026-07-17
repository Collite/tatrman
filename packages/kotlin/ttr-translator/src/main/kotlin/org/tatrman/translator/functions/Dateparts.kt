// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import org.apache.calcite.avatica.util.TimeUnit

/**
 * Maps T-SQL datepart names and abbreviations to canonical Calcite [TimeUnit]s (tasks-dateadd.md).
 *
 * Used by the custom parser's `NormalizeDatepart` helper to rewrite a bare T-SQL datepart (parsed
 * by `TimeUnitOrName()` as a time-frame *name*, e.g. `dd`) into a `TimeUnit`-backed interval
 * qualifier. That makes it a SYMBOL operand after sql-to-rel, which unparses **bare and canonical**
 * (`DATEADD(DAY, …)`) instead of as a quoted time-frame string (`DATEADD('dd', …)`, invalid T-SQL).
 *
 * Only the families whose canonical Calcite [TimeUnit] name is itself a valid T-SQL datepart are
 * mapped here (so the round-trip stays T-SQL-valid). `dayofyear`/`weekday`/`iso_week` are *not*
 * mapped: Calcite's built-in time frames already resolve them (to DOY/DOW/ISOWEEK), so they
 * validate — but those canonical names are not T-SQL dateparts, so the unparse emits e.g.
 * `DATEPART(DOY, …)` rather than `DATEPART(dayofyear, …)`. Faithfully preserving the authored
 * spelling needs custom DATE* operators with a bare-token unparse (like `SqlCollateOperator`) and is
 * a Stage-D follow-up (no extra `TimeFrameSet` is registered — Calcite's defaults suffice for
 * validation). Lookup is case-insensitive.
 */
object Dateparts {
    private val byName: Map<String, TimeUnit> =
        buildMap {
            fun map(
                unit: TimeUnit,
                vararg names: String,
            ) = names.forEach { put(it, unit) }
            map(TimeUnit.YEAR, "year", "yy", "yyyy")
            map(TimeUnit.QUARTER, "quarter", "qq", "q")
            map(TimeUnit.MONTH, "month", "mm", "m")
            map(TimeUnit.DAY, "day", "dd", "d")
            map(TimeUnit.WEEK, "week", "wk", "ww")
            map(TimeUnit.HOUR, "hour", "hh")
            map(TimeUnit.MINUTE, "minute", "mi", "n")
            map(TimeUnit.SECOND, "second", "ss", "s")
            map(TimeUnit.MILLISECOND, "millisecond", "ms")
            map(TimeUnit.MICROSECOND, "microsecond", "mcs")
            map(TimeUnit.NANOSECOND, "nanosecond", "ns")
        }

    /** The canonical [TimeUnit] for a T-SQL datepart spelling, or null if not a mapped datepart. */
    @JvmStatic
    fun toTimeUnit(name: String): TimeUnit? = byName[name.lowercase()]
}
