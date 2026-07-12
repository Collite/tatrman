// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The WS-frame ⇄ `Content-Length`-stream bridge (T5.1.3): inbound framing is exact,
 * outbound de-framing recovers whole message bodies across arbitrary write boundaries,
 * and multi-byte UTF-8 lengths are byte-counted correctly.
 */
class WsJsonRpcBridgeSpec :
    StringSpec({

        "one inbound frame → exactly one Content-Length message readable by the launcher" {
            val bridge = WsJsonRpcBridge {}
            val payload = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
            bridge.onMessage(payload)
            bridge.closeInbound()

            // Read the framed header + body exactly as lsp4j's StreamMessageProducer would.
            val reader = BufferedReader(InputStreamReader(bridge.inputStream, Charsets.UTF_8))
            val header = reader.readLine() // "Content-Length: N"
            header shouldBe "Content-Length: ${payload.toByteArray(Charsets.UTF_8).size}"
            reader.readLine() shouldBe "" // blank separator line
            val body = CharArray(payload.length)
            reader.read(body, 0, payload.length)
            String(body) shouldBe payload
        }

        "two outbound messages split across write boundaries → two byte-identical sends" {
            val sent = CopyOnWriteArrayList<String>()
            val bridge = WsJsonRpcBridge { sent.add(it) }
            val a = """{"id":1,"result":"a"}"""
            val b = """{"id":2,"result":"bb"}"""
            val framed =
                (
                    "Content-Length: ${a.toByteArray().size}\r\n\r\n$a" +
                        "Content-Length: ${b.toByteArray().size}\r\n\r\n$b"
                ).toByteArray(Charsets.UTF_8)

            // Dribble the bytes one at a time to prove reassembly is write-boundary agnostic.
            for (byte in framed) bridge.outputStream.write(byte.toInt())

            sent shouldHaveSize 2
            sent[0] shouldBe a
            sent[1] shouldBe b
        }

        "multi-byte UTF-8 body: Content-Length is byte-counted, not char-counted" {
            val sent = CopyOnWriteArrayList<String>()
            val bridge = WsJsonRpcBridge { sent.add(it) }
            val body = """{"msg":"€ é 中"}""" // chars < bytes
            val byteLen = body.toByteArray(Charsets.UTF_8).size
            val framed = "Content-Length: $byteLen\r\n\r\n$body".toByteArray(Charsets.UTF_8)
            bridge.outputStream.write(framed)

            sent shouldHaveSize 1
            sent[0] shouldBe body
        }
    })
