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
    ): Boolean {
        val signature = readDetachedSignature(ascBytes) ?: return false
        val keyRings =
            PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(ByteArrayInputStream(keyringBytes)),
                BcKeyFingerprintCalculator(),
            )
        val publicKey = keyRings.getPublicKey(signature.keyID) ?: return false
        signature.init(BcPGPContentVerifierBuilderProvider(), publicKey)
        signature.update(artifact)
        return signature.verify()
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
