// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream

/**
 * PL-P5.S2 (H-6, contracts §8) — OpenPGP **detached-signature** verification for emit-plugin artifacts.
 * The publisher ships a `<jar>.asc` (the Maven convention) beside the jar; this verifies it against a
 * trusted publisher keyring. BouncyCastle's *lightweight* (`Bc*`) operators are used, so no JCA
 * `Security` provider has to be registered by the host.
 *
 * This is deliberately a pure function of `(artifact, ascBytes, keyringBytes)` — the file-presence /
 * policy decisions live in [EmitPluginLoader.verifySignature] so the crypto is unit-testable on its own.
 */
object PluginSignature {
    /**
     * True iff [ascBytes] is a valid detached signature over [artifact] made by a key present in
     * [keyringBytes]. Returns false (never throws) when the signature cannot be parsed, its issuing key
     * is not in the ring, or the bytes simply do not verify. Both binary and ASCII-armored inputs are
     * accepted ([PGPUtil.getDecoderStream] sniffs the armor).
     */
    fun verifyDetached(
        artifact: ByteArray,
        ascBytes: ByteArray,
        keyringBytes: ByteArray,
    ): Boolean =
        try {
            val signature = readDetachedSignature(ascBytes)
            when {
                signature == null -> false
                // Defense in depth: only a document (data) signature may certify an artifact — a key
                // certification or other signature class issued by a trusted key must never count.
                signature.signatureType != PGPSignature.BINARY_DOCUMENT &&
                    signature.signatureType != PGPSignature.CANONICAL_TEXT_DOCUMENT -> false
                else -> {
                    val keyRings =
                        PGPPublicKeyRingCollection(
                            PGPUtil.getDecoderStream(ByteArrayInputStream(keyringBytes)),
                            BcKeyFingerprintCalculator(),
                        )
                    val publicKey = keyRings.getPublicKey(signature.keyID)
                    if (publicKey == null) {
                        false
                    } else {
                        signature.init(BcPGPContentVerifierBuilderProvider(), publicKey)
                        signature.update(artifact)
                        signature.verify()
                    }
                }
            }
        } catch (_: Exception) {
            // A malformed keyring / `.asc`, or any verification error, means trust cannot be established —
            // fail CLOSED (refuse), honoring the "returns false, never throws" contract above.
            false
        }

    /** Read the first [PGPSignature] out of a detached `.asc` (unwrapping one compression layer if present). */
    private fun readDetachedSignature(ascBytes: ByteArray): PGPSignature? {
        var factory =
            BcPGPObjectFactory(PGPUtil.getDecoderStream(ByteArrayInputStream(ascBytes)))
        var obj = factory.nextObject()
        while (obj != null) {
            when (obj) {
                is PGPSignatureList -> return if (obj.isEmpty) null else obj[0]
                is PGPCompressedData -> factory = BcPGPObjectFactory(obj.dataStream)
                else -> {}
            }
            obj = factory.nextObject()
        }
        return null
    }
}
