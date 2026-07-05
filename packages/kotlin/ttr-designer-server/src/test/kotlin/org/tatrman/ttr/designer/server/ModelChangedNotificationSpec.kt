package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A registry swap fans a `ttrm/modelChanged` notification out to every registered
 * session (architecture §5). Also proves the broadcaster drops a session whose
 * `send` throws, without failing the others.
 */
class ModelChangedNotificationSpec :
    StringSpec({
        "registry swap notifies all live sessions with ttrm/modelChanged" {
            val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
            val frames = CopyOnWriteArrayList<String>()
            deps.broadcaster.register { frames += it }

            val snap = deps.registry.read()!!
            deps.registry.swap(snap.model, snap.graph, emptyList())

            frames.size shouldBe 1
            val obj = Json.parseToJsonElement(frames.first()).jsonObject
            obj["method"]!!.jsonPrimitive.content shouldBe "ttrm/modelChanged"
            frames.first() shouldContain "modelVersion"
        }

        "touching a .ttrm file → refresh → modelChanged with a new modelVersion (end-to-end, no NIO)" {
            // Copy the fixture to a temp repo so the edit doesn't corrupt the shared fixture.
            val temp = Files.createTempDirectory("ttrm-repo")
            copyDir(MetadataFixtures.erpProjectRoot(), temp)

            val deps = DesignerServerDeps.forRepo(temp)
            val before =
                deps.registry
                    .read()!!
                    .model.version.value
            val frames = CopyOnWriteArrayList<String>()
            deps.broadcaster.register { frames += it }

            // Simulate what the watcher's debounced trigger does: mutate a model file, then refresh.
            val dbFile = temp.resolve("models/erp/db.ttrm")
            dbFile.writeText(dbFile.readText() + "\n// touched\n")
            runBlocking { deps.refresher.refresh(sourceId = "", force = false) }

            frames.size shouldBe 1
            val obj = Json.parseToJsonElement(frames.first()).jsonObject
            obj["method"]!!.jsonPrimitive.content shouldBe "ttrm/modelChanged"
            val after = obj["params"]!!.jsonObject["modelVersion"]!!.jsonPrimitive.content
            after shouldNotBe before
        }

        "a dead session is dropped and the survivor still receives" {
            runTest {
                val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
                val delivered = CopyOnWriteArrayList<String>()
                deps.broadcaster.register { error("dead socket") }
                deps.broadcaster.register { delivered += it }

                deps.broadcaster.notifyAll("ttrm/modelChanged", buildJsonObject { put("modelVersion", "v1") })
                deps.broadcaster.notifyAll("ttrm/modelChanged", buildJsonObject { put("modelVersion", "v2") })

                // Both notifications reach the survivor; the dead one was pruned after the first failure.
                delivered.size shouldBe 2
            }
        }
    })

private fun copyDir(
    src: Path,
    dst: Path,
) {
    Files.walk(src).use { stream ->
        stream.forEach { path ->
            val target = dst.resolve(src.relativize(path).toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(target)
            } else {
                Files.createDirectories(target.parent)
                path.copyTo(target, overwrite = true)
            }
        }
    }
}
