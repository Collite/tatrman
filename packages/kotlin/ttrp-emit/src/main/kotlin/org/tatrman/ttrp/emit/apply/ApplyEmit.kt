// SPDX-License-Identifier: Apache-2.0
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.tatrman.ttrp.emit.apply

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
enum class SqlType(
    val jdbc: Int,
) {
    TEXT(Types.VARCHAR),
    BIGINT(Types.BIGINT),
    DATE(Types.DATE),
}

/**
 * One positional `?` bind. Its value is concrete (from the batch/a literal) or resolved at run (state/F3).
 * The `kind` discriminator ([ApplyPlanJson]) is the cross-repo contract with the platform interpreter.
 */
@Serializable
sealed interface Bind {
    val type: SqlType

    /** A concrete value from the batch or a literal, rendered to a string the interpreter coerces by [type]. */
    @Serializable
    @SerialName("value")
    data class Value(
        val value: String?,
        override val type: SqlType,
    ) : Bind

    /** A value produced by a named state read in this proposal's prefix (§5.1). */
    @Serializable
    @SerialName("state")
    data class StateRef(
        val read: String,
        override val type: SqlType,
    ) : Bind

    /** An F3 derived id (`<base>-rev<n>`/`-rep<n>`) computed at run from [counterRead] (§5, ⚑EN-2). TEXT. */
    @Serializable
    @SerialName("derivedId")
    data class DerivedIdRef(
        val role: String,
        val base: Bind,
        val counterRead: String,
    ) : Bind {
        override val type: SqlType get() = SqlType.TEXT
    }

    /**
     * ED — a `?` fed from a named [EmittedFuncEval] result (the FO-8 derived column). The door evaluates
     * the function-eval prefix, then binds this slot from the result named [read] (ED `contracts.md` §4).
     */
    @Serializable
    @SerialName("func")
    data class FuncRef(
        val read: String,
        override val type: SqlType,
    ) : Bind
}

/** A state read run first on the door connection (§5.1): its result binds later steps. */
@Serializable
enum class ReadKind { ROW, COUNT }

@Serializable
data class EmittedRead(
    val name: String,
    val sql: String,
    val binds: List<Bind>,
    val kind: ReadKind,
)

/** One parameterized DML statement + its positional binds. Placeholder count must equal `binds.size`. */
@Serializable
data class EmittedStep(
    val sql: String,
    val binds: List<Bind>,
    /** Which §6 effect counter this step increments (inserted/updated/closed/reversed). */
    val effect: Effect,
)

@Serializable
enum class Effect { INSERTED, UPDATED, CLOSED, REVERSED, NONE }

/** §10 optimistic guard: the [read] result must equal [expected] (baseRowVersion), else the row is a STALE reject. */
@Serializable
data class EmittedGuard(
    val read: String,
    val expected: Bind,
)

/**
 * ED — a prefix function evaluation on the wire (ED `contracts.md` §4/§5): the deploy-resolved [pin] is
 * evaluated door-side over [argBinds] (batch/state binds), its scalar result named [name] and consumed
 * by a [Bind.FuncRef] slot. Runs after the state reads, before the DML.
 */
@Serializable
data class EmittedFuncEval(
    val name: String,
    val pin: PluginPin,
    val argBinds: List<Bind>,
)

@Serializable
data class EmittedProposal(
    val row: Int,
    val reads: List<EmittedRead>,
    val guard: EmittedGuard?,
    val steps: List<EmittedStep>,
    // ED — the function-eval prefix. `@EncodeDefault(NEVER)`: omitted when empty so every non-derivation
    // plan's wire (and its committed JSON golden) stays byte-identical to the EN emit.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val funcs: List<EmittedFuncEval> = emptyList(),
)

/** A deploy-stamped reference to the standing apply program (FO §6 entry record). */
@Serializable
data class ApplyProgramRef(
    val qname: String,
    val version: String,
)

/**
 * A deploy-resolved plugin pin (FO §6 `pluginPins`) — the `{id, version}` a `call-fn` resolved to at
 * deploy (EN-P5). Baked into the plan so replay reads the pin, never re-resolving against the registry
 * (P-3). Empty when the program makes no `call-fn` calls.
 */
@Serializable
data class PluginPin(
    val id: String,
    val version: String,
)

/** The emitted apply plan the door runs — ordered, parameterized, typed (⚑EN-1(a)). */
@Serializable
data class EmittedApplyPlan(
    val target: String,
    val verb: String,
    val semantics: String,
    val applyProgram: ApplyProgramRef,
    val pluginPins: List<PluginPin> = emptyList(),
    val proposals: List<EmittedProposal>,
)

/**
 * The JSON codec for the emitted plan — the cross-repo wire the platform interpreter reads (RO-6: the
 * plan crosses as a resource, not a project dep). `kind`-discriminated [Bind] polymorphism, stable
 * pretty output for committed fixtures.
 */
object ApplyPlanJson {
    val json: kotlinx.serialization.json.Json =
        kotlinx.serialization.json.Json {
            classDiscriminator = "kind"
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
        }

    fun encode(plan: EmittedApplyPlan): String = json.encodeToString(EmittedApplyPlan.serializer(), plan)

    fun decode(text: String): EmittedApplyPlan = json.decodeFromString(EmittedApplyPlan.serializer(), text)
}
