// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.snapshot.SnapshotCache
import org.tatrman.ttrp.project.FetchPlanner
import org.tatrman.ttrp.project.LockPlatformWorld
import org.tatrman.ttrp.project.ResolveResponse
import org.tatrman.ttrp.project.TtrLockCodec
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.project.TtrpManifestReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ttr fetch` (contracts §3): resolve the server's current canon, download the archives it names into
 * the cache, and rewrite `ttr.lock`. A fetch is a **reviewable lock diff** — the diff is printed and
 * the operator commits it. Server URL + auth come from project config, never from the lock.
 */
class FetchCommand : CliktCommand(name = "fetch") {
    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "Resolve platform canon into the local cache and rewrite ttr.lock (a reviewable lock diff)."

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ResolveDto(
        val world: String,
        val models: Map<String, String> = emptyMap(),
        val manifests: Map<String, String> = emptyMap(),
        val platformWorld: PlatformWorldDto? = null,
    )

    @Serializable
    private data class PlatformWorldDto(
        val qname: String,
        val pin: String,
    )

    override fun run() {
        val projectDir = Path.of("").toAbsolutePath()
        val manifest = TtrpManifestReader.resolve(projectDir).manifest
        val server =
            manifest.metadataServer ?: run {
                echo("ttr fetch: no [ttrp] metadata-server configured (standalone project)", err = true)
                throw ProgramResult(2)
            }
        val worldQ =
            manifest.world ?: run {
                echo("ttr fetch: no [ttrp] world configured", err = true)
                throw ProgramResult(2)
            }
        val token = manifest.metadataToken ?: System.getenv("TTR_METADATA_TOKEN")
        val cache = SnapshotCache(cacheRoot(manifest))
        val http = HttpClient.newHttpClient()

        val resolved = resolve(http, server, worldQ, token)

        // Download every archive the resolve names that we don't already have — including the pinned
        // `[manifests]` (executor/engine-type manifest archives), so a later frozen compile that consumes
        // them finds them in the cache rather than absent.
        for (id in listOf(resolved.world) + resolved.models.values + resolved.manifests.values) {
            if (!cache.has(id)) {
                val bytes = getArchive(http, server, id, token)
                val stored = cache.put(bytes) // verifies content id
                if (stored != id) {
                    echo("ttr fetch: archive $id hashed to $stored — refusing to cache", err = true)
                    throw ProgramResult(1)
                }
            }
        }

        val lockPath = projectDir.resolve("ttr.lock")
        val current = if (Files.isRegularFile(lockPath)) TtrLockCodec.parse(Files.readString(lockPath)).lock else null
        val plan = FetchPlanner.diff(current, resolved)
        val next = FetchPlanner.apply(current, resolved, worldQ)
        Files.writeString(lockPath, TtrLockCodec.write(next))
        echo(plan.render().trimEnd())
    }

    private fun cacheRoot(m: TtrpManifest): Path =
        m.cacheDir?.let { Path.of(it) } ?: m.manifestDir.resolve(".ttr").resolve("cache")

    private fun resolve(
        http: HttpClient,
        server: String,
        world: String,
        token: String?,
    ): ResolveResponse {
        // JSON-encode the body so a world qname containing a quote/backslash/control char can't break or
        // inject into the request (string interpolation would).
        val body = json.encodeToString(JsonObject.serializer(), buildJsonObject { put("world", world) })
        val req =
            HttpRequest
                .newBuilder(URI.create("$server/v1/snapshots/resolve"))
                .header("Content-Type", "application/json")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            echo("ttr fetch: resolve failed (${resp.statusCode()})", err = true)
            throw ProgramResult(1)
        }
        val dto = json.decodeFromString(ResolveDto.serializer(), resp.body())
        return ResolveResponse(
            world = dto.world,
            models = dto.models,
            manifests = dto.manifests,
            platformWorld = dto.platformWorld?.let { LockPlatformWorld(it.qname, it.pin) },
        )
    }

    private fun getArchive(
        http: HttpClient,
        server: String,
        id: String,
        token: String?,
    ): ByteArray {
        val req =
            HttpRequest
                .newBuilder(URI.create("$server/v1/snapshots/$id"))
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .GET()
                .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (resp.statusCode() !in 200..299) {
            echo("ttr fetch: download $id failed (${resp.statusCode()})", err = true)
            throw ProgramResult(1)
        }
        return resp.body()
    }
}
