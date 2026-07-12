// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException

/**
 * The hand-rolled JSON-RPC 2.0 dispatcher honours the envelope contract (§4):
 * success/error shapes, the reserved error codes, batch rejection, and
 * notification (no-id) drop.
 */
class JsonRpcDispatcherSpec :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }

        fun errorCode(frame: String): Int =
            json
                .parseToJsonElement(frame)
                .jsonObject["error"]!!
                .jsonObject["code"]!!
                .jsonPrimitive.int

        "success echoes id and returns result" {
            runTest {
                val d = JsonRpcDispatcher()
                d.register("ping") { JsonPrimitive("pong") }
                val resp = d.dispatch("""{"jsonrpc":"2.0","id":7,"method":"ping"}""")!!
                val obj = json.parseToJsonElement(resp).jsonObject
                obj["id"]!!.jsonPrimitive.int shouldBe 7
                obj["result"]!!.jsonPrimitive.content shouldBe "pong"
            }
        }

        "malformed json -> -32700" {
            runTest {
                errorCode(JsonRpcDispatcher().dispatch("{not json")!!) shouldBe RpcCodes.PARSE_ERROR
            }
        }

        "batch (array) -> -32600" {
            runTest {
                errorCode(JsonRpcDispatcher().dispatch("""[{"jsonrpc":"2.0","id":1,"method":"x"}]""")!!) shouldBe
                    RpcCodes.INVALID_REQUEST
            }
        }

        "unknown method -> -32601" {
            runTest {
                errorCode(JsonRpcDispatcher().dispatch("""{"jsonrpc":"2.0","id":1,"method":"nope"}""")!!) shouldBe
                    RpcCodes.METHOD_NOT_FOUND
            }
        }

        "inbound notification (no id) is dropped" {
            runTest {
                JsonRpcDispatcher().dispatch("""{"jsonrpc":"2.0","method":"ping"}""").shouldBeNull()
            }
        }

        "TtrmRpcException maps to its code and data.kind" {
            runTest {
                val d = JsonRpcDispatcher()
                d.register("boom") { throw TtrmRpcException(RpcCodes.NOT_FOUND, "not-found") }
                val resp = d.dispatch("""{"jsonrpc":"2.0","id":1,"method":"boom"}""")!!
                val error = json.parseToJsonElement(resp).jsonObject["error"]!!.jsonObject
                error["code"]!!.jsonPrimitive.int shouldBe RpcCodes.NOT_FOUND
                error["data"]!!.jsonObject["kind"]!!.jsonPrimitive.content shouldBe "not-found"
            }
        }

        "handler throwing a non-Ttrm exception -> -32603" {
            runTest {
                val d = JsonRpcDispatcher()
                d.register("boom") { throw IllegalStateException("oops") }
                errorCode(d.dispatch("""{"jsonrpc":"2.0","id":1,"method":"boom"}""")!!) shouldBe RpcCodes.INTERNAL
            }
        }
    })
