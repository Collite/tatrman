package org.tatrman.ttrp.emit.transfer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.GoldenSupport
import org.tatrman.ttrp.emit.TtrpEmitException

/** T3.2.4 — generated ADBC transfer scripts (Arrow-IPC staging), MOV/RLS diagnostics. */
class TransferScriptEmitterTest :
    FunSpec({
        val emitter = TransferScriptEmitter()

        val pgToStaging =
            TransferSpec(
                edge = "accounts",
                direction = TransferDirection.ENGINE_TO_STAGING,
                columns = listOf("account_id", "branch_code", "region"),
                connectionEnv = "TTR_CONN_ERP_PG",
                table = "ttrp_staging.accounts",
            )
        val stagingToPg =
            TransferSpec(
                edge = "low_regions",
                direction = TransferDirection.STAGING_TO_ENGINE,
                columns = listOf("region", "total"),
                connectionEnv = "TTR_CONN_ERP_PG",
                table = "public.low_regions",
            )

        test("pg → staging golden") {
            GoldenSupport.assertMatchesGolden(emitter.emit(pgToStaging).text, "transfers/pg_to_staging.py")
        }

        test("staging → pg golden") {
            GoldenSupport.assertMatchesGolden(emitter.emit(stagingToPg).text, "transfers/staging_to_pg.py")
        }

        test("MOV-002 when a transfer endpoint has no named connection") {
            val ex = shouldThrow<TtrpEmitException> { emitter.emit(pgToStaging.copy(connectionEnv = "")) }
            ex.id shouldBe EmitDiagnosticId.MOV_NO_CONNECTION
        }

        test("RLS-001 egress tripwire — warn severity surfaces a warning, error severity throws") {
            val warned = emitter.emit(pgToStaging.copy(sourceRls = true, rlsEgress = RlsEgress.WARN))
            warned.warnings.map { it.id } shouldBe listOf(EmitDiagnosticId.RLS_EGRESS)

            val ex =
                shouldThrow<TtrpEmitException> {
                    emitter.emit(pgToStaging.copy(sourceRls = true, rlsEgress = RlsEgress.ERROR))
                }
            ex.id shouldBe EmitDiagnosticId.RLS_EGRESS

            // Non-RLS transfer is silent.
            emitter.emit(pgToStaging).warnings.shouldBeEmpty()
        }
    })
