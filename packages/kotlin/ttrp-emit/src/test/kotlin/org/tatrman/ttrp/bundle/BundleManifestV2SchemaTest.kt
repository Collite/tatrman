// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty

/**
 * PL-P0.S1 (T1) — the E-5 bundle-manifest **v2** graduation (platform contracts §6). Validates the
 * hand-authored hero fixture against the published JSON Schema (draft 2020-12), and pins the two
 * load-bearing rules of the contract: the schema *permits* `params`/`onFailureOf` from day one (the
 * toolchain only *emits* them from PL-P2), and it carries **no `provenance` block** — provenance rides
 * the envelope + compile-record sidecar (§5/§13), never the artifact, or hard parity (B-3) breaks.
 */
class BundleManifestV2SchemaTest :
    FunSpec({
        val mapper = ObjectMapper()

        fun schema(): JsonSchema {
            val stream =
                requireNotNull(javaClass.getResourceAsStream("/schemas/bundle-manifest-v2.schema.json")) {
                    "bundle-manifest-v2.schema.json missing from ttrp-emit main resources (PL-P0.S1.T2)"
                }
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(stream)
        }

        fun fixture(name: String): JsonNode =
            mapper.readTree(
                requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "fixture $name missing" },
            )

        test("the hero v2 manifest validates against the published schema") {
            schema().validate(fixture("manifest-v2-hero.json")).shouldBeEmpty()
        }

        test("schemaVersion is pinned to const 2 (the E-5 version key)") {
            val v1shaped = fixture("manifest-v2-hero.json").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            v1shaped.put("schemaVersion", 1)
            schema().validate(v1shaped).shouldNotBeEmpty()
        }

        test("a provenance block is rejected — provenance rides the envelope, never the manifest (§6 rule)") {
            val withProvenance =
                fixture("manifest-v2-hero.json").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            withProvenance.set<JsonNode>("provenance", mapper.createObjectNode().put("objectsRead", "x"))
            schema().validate(withProvenance).shouldNotBeEmpty()
        }

        test("an unknown transform tag outside the vocabulary is rejected (⚑ transform-tag enum)") {
            val node = fixture("manifest-v2-hero.json").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            (node.at("/lineage/columns/0") as com.fasterxml.jackson.databind.node.ObjectNode)
                .put("transform", "teleport")
            schema().validate(node).shouldNotBeEmpty()
        }
    })
