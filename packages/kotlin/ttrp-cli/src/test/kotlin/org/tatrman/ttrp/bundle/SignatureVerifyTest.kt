// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPKeyPair
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.util.Date

/**
 * PL-P5.S2.T3/T4 — OpenPGP detached-signature verification for emit plugins (H-6, contracts §8). The crypto
 * ([PluginSignature.verifyDetached]) is exercised against real in-test keypairs; the file-presence/policy
 * layer ([EmitPluginLoader.verifySignature]) is exercised for each of the four H-6 outcomes:
 *   signed+verifies · unsigned+verify-if-signed (warn, load) · unsigned+require-signed (refuse) · tampered (refuse).
 */
class SignatureVerifyTest :
    StringSpec({

        // --- test PGP material (a "publisher" key + a detached signer over arbitrary bytes) -------------------

        fun newPublisherKey(): PGPKeyPair {
            val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            return JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, rsa, Date())
        }

        /** The publisher's public keyring (a real user-id'd, self-signed ring) as bytes — the trust root. */
        fun publicKeyring(
            key: PGPKeyPair,
            userId: String = "Test Publisher <ci@tatrman.org>",
        ): ByteArray {
            val sha1 = JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
            val ringGen =
                PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION,
                    key,
                    userId,
                    sha1,
                    null,
                    null,
                    JcaPGPContentSignerBuilder(key.publicKey.algorithm, HashAlgorithmTags.SHA256),
                    null,
                )
            return ringGen.generatePublicKeyRing().encoded
        }

        /** An armored detached signature (`.asc`) over [artifact], made by [key]. */
        fun detachedSig(
            key: PGPKeyPair,
            artifact: ByteArray,
        ): ByteArray {
            val gen =
                PGPSignatureGenerator(
                    JcaPGPContentSignerBuilder(key.publicKey.algorithm, HashAlgorithmTags.SHA256),
                    key.publicKey,
                )
            gen.init(PGPSignature.BINARY_DOCUMENT, key.privateKey)
            gen.update(artifact)
            val sig = gen.generate()
            val out = ByteArrayOutputStream()
            ArmoredOutputStream(out).use { armored -> sig.encode(BCPGOutputStream(armored)) }
            return out.toByteArray()
        }

        val jar = "the-plugin-jar-bytes".toByteArray()

        // --- PluginSignature.verifyDetached (the crypto) ------------------------------------------------------

        "a PGP-signed artifact verifies against its publisher key" {
            val key = newPublisherKey()
            PluginSignature.verifyDetached(jar, detachedSig(key, jar), publicKeyring(key)).shouldBeTrue()
        }

        "a signature by a key ABSENT from the trusted keyring does not verify" {
            val signer = newPublisherKey()
            val other = newPublisherKey() // trusted ring holds a DIFFERENT key
            PluginSignature.verifyDetached(jar, detachedSig(signer, jar), publicKeyring(other)).shouldBeFalse()
        }

        "a tampered artifact does not verify against a good signature" {
            val key = newPublisherKey()
            val asc = detachedSig(key, jar)
            PluginSignature.verifyDetached("TAMPERED".toByteArray(), asc, publicKeyring(key)).shouldBeFalse()
        }

        // --- EmitPluginLoader.verifySignature (the H-6 policy layer) ------------------------------------------

        "signed + verifies → loads silently under either policy" {
            val key = newPublisherKey()
            val asc = detachedSig(key, jar)
            val ring = publicKeyring(key)
            for (policy in SignaturePolicy.entries) {
                shouldNotThrowAny {
                    EmitPluginLoader.verifySignature(
                        "g:a",
                        jar,
                        asc,
                        ring,
                        policy,
                    ) { error("no warning expected: $it") }
                }
            }
        }

        "unsigned + verify-if-signed → loads with a recorded warning" {
            val warnings = mutableListOf<String>()
            EmitPluginLoader.verifySignature(
                "org.tatrman:ttr-emit-x",
                jar,
                ascBytes = null,
                keyringBytes = null,
                policy = SignaturePolicy.VERIFY_IF_SIGNED,
            ) { warnings += it }
            warnings shouldHaveSize 1
            warnings.single() shouldContain "unsigned"
            warnings.single() shouldContain "require-signed-plugins"
        }

        "unsigned + require-signed → refused, names the knob" {
            val ex =
                shouldThrow<PluginTrustException> {
                    EmitPluginLoader.verifySignature(
                        "org.tatrman:ttr-emit-x",
                        jar,
                        ascBytes = null,
                        keyringBytes = null,
                        policy = SignaturePolicy.REQUIRE_SIGNED,
                    )
                }
            ex.message!! shouldContain "TTRP-LCK-012"
            ex.message!! shouldContain "require-signed-plugins"
        }

        "tampered jar → refused regardless of policy (TTRP-LCK-013)" {
            val key = newPublisherKey()
            val asc = detachedSig(key, jar) // signature is over `jar`…
            val ring = publicKeyring(key)
            val tampered = "the-plugin-jar-bytes-EVIL".toByteArray() // …but the artifact was swapped.
            for (policy in SignaturePolicy.entries) {
                val ex =
                    shouldThrow<PluginTrustException> {
                        EmitPluginLoader.verifySignature("g:a", tampered, asc, ring, policy)
                    }
                ex.message!! shouldContain "TTRP-LCK-013"
            }
        }

        "signed but no trusted keyring configured → refused (TTRP-LCK-014)" {
            val key = newPublisherKey()
            val asc = detachedSig(key, jar)
            val ex =
                shouldThrow<PluginTrustException> {
                    EmitPluginLoader.verifySignature(
                        "g:a",
                        jar,
                        asc,
                        keyringBytes = null,
                        SignaturePolicy.VERIFY_IF_SIGNED,
                    )
                }
            ex.message!! shouldContain "TTRP-LCK-014"
        }

        // --- verifyDetached fails CLOSED on malformed input (honors the "returns false, never throws" contract) -

        "malformed .asc / keyring bytes → verifyDetached returns false (fail-closed, never throws)" {
            shouldNotThrowAny {
                PluginSignature
                    .verifyDetached(jar, "not-a-signature".toByteArray(), "not-a-keyring".toByteArray())
                    .shouldBeFalse()
            }
        }

        // --- EmitPluginLoader.signaturePolicy (knob → policy; the PL-P5.S2 deferral is surfaced, not silent) ----

        "signaturePolicy: false → verify-if-signed, and emits no inert notice" {
            val notices = mutableListOf<String>()
            EmitPluginLoader.signaturePolicy(requireSignedPlugins = false) { notices += it } shouldBe
                SignaturePolicy.VERIFY_IF_SIGNED
            notices.shouldBeEmpty()
        }

        "signaturePolicy: true → require-signed, and surfaces the currently-inert notice (never silent)" {
            val notices = mutableListOf<String>()
            EmitPluginLoader.signaturePolicy(requireSignedPlugins = true) { notices += it } shouldBe
                SignaturePolicy.REQUIRE_SIGNED
            notices shouldHaveSize 1
            notices.single() shouldContain "require-signed-plugins"
            notices.single() shouldContain "inert"
        }
    })
