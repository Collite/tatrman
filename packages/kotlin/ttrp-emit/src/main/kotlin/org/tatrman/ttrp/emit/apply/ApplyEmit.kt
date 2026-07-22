// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

import java.sql.Types

/*
 * EN-P4 apply-plan emit (contracts §5.1, ⚑EN-1(a)) — the emitted representation of an EN-P3 lowered
 * plan: an ordered list of parameterized PG SQL-DML statements + a typed positional bind manifest, with
 * a state-read prefix. This is the artefact the FO-P2 `ApplyDoor` runs (the EN-P0 spike's `SqlStep`/
 * `Bind`/`SqlType` shape). Values are NEVER interpolated inline — placeholders (`?`) only; identifiers
 * are explicitly quoted in exact md case (F4). JDBC binding stays platform-side, so a [Bind] carries a
 * type + a source, not engine coupling.
 */

/** The JDBC type a bind is set with (§5.1 wave set) — its [jdbc] is a `java.sql.Types` code (F4). */
enum class SqlType(
    val jdbc: Int,
) {
    TEXT(Types.VARCHAR),
    BIGINT(Types.BIGINT),
    DATE(Types.DATE),
}

/** One positional `?` bind. Its value is concrete (from the batch/a literal) or resolved at run (state/F3). */
sealed interface Bind {
    val type: SqlType

    /** A concrete value from the batch or a literal, rendered to a string the interpreter coerces by [type]. */
    data class Value(
        val value: String?,
        override val type: SqlType,
    ) : Bind

    /** A value produced by a named state read in this proposal's prefix (§5.1). */
    data class StateRef(
        val read: String,
        override val type: SqlType,
    ) : Bind

    /** An F3 derived id (`<base>-rev<n>`/`-rep<n>`) computed at run from [counterRead] (§5, ⚑EN-2). TEXT. */
    data class DerivedIdRef(
        val role: String,
        val base: Bind,
        val counterRead: String,
    ) : Bind {
        override val type: SqlType get() = SqlType.TEXT
    }
}

/** A state read run first on the door connection (§5.1): its result binds later steps. */
enum class ReadKind { ROW, COUNT }

data class EmittedRead(
    val name: String,
    val sql: String,
    val binds: List<Bind>,
    val kind: ReadKind,
)

/** One parameterized DML statement + its positional binds. Placeholder count must equal `binds.size`. */
data class EmittedStep(
    val sql: String,
    val binds: List<Bind>,
    /** Which §6 effect counter this step increments (inserted/updated/closed/reversed). */
    val effect: Effect,
)

enum class Effect { INSERTED, UPDATED, CLOSED, REVERSED, NONE }

/** §10 optimistic guard: the [read] result must equal [expected] (baseRowVersion), else the row is a STALE reject. */
data class EmittedGuard(
    val read: String,
    val expected: Bind,
)

data class EmittedProposal(
    val row: Int,
    val reads: List<EmittedRead>,
    val guard: EmittedGuard?,
    val steps: List<EmittedStep>,
)

/** A deploy-stamped reference to the standing apply program (FO §6 entry record). */
data class ApplyProgramRef(
    val qname: String,
    val version: String,
)

/** The emitted apply plan the door runs — ordered, parameterized, typed (⚑EN-1(a)). */
data class EmittedApplyPlan(
    val target: String,
    val verb: String,
    val semantics: String,
    val applyProgram: ApplyProgramRef,
    val proposals: List<EmittedProposal>,
)
