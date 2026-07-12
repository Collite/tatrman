// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * Watches a repo subtree for `.ttr*` changes via the JVM NIO [WatchService] and
 * pokes a [DebouncedRefreshTrigger]. The host wires the trigger's action to
 * `MetadataRefresher.refresh` → `registry.swap` → `modelChanged`. Watch is a host
 * concern (MD5); the library is never asked to poll.
 */
class NioRepoWatcher(
    private val root: Path,
    private val scope: CoroutineScope,
    private val trigger: DebouncedRefreshTrigger,
) {
    private val log = LoggerFactory.getLogger(NioRepoWatcher::class.java)
    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val keys = mutableMapOf<WatchKey, Path>()
    private var job: Job? = null

    fun start() {
        registerAll(root)
        job =
            scope.launch {
                while (isActive) {
                    val key = watchService.take()
                    val dir = keys[key] ?: continue
                    var relevant = false
                    for (event in key.pollEvents()) {
                        val name = event.context() as? Path ?: continue
                        val child = dir.resolve(name)
                        if (event.kind() == ENTRY_CREATE && Files.isDirectory(child)) {
                            registerAll(child)
                        }
                        if (isModelFile(child) || Files.isDirectory(child)) relevant = true
                    }
                    if (relevant) trigger.poke()
                    if (!key.reset()) {
                        keys.remove(key)
                        if (keys.isEmpty()) break
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        runCatching { watchService.close() }
    }

    private fun registerAll(start: Path) {
        if (!Files.isDirectory(start)) return
        Files.walk(start).use { stream ->
            stream.filter { Files.isDirectory(it) }.forEach { dir ->
                runCatching {
                    val key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                    keys[key] = dir
                }.onFailure { log.debug("could not watch {}", dir, it) }
            }
        }
    }

    private fun isModelFile(p: Path): Boolean {
        val n = p.fileName?.toString() ?: return false
        return n.endsWith(".ttr") || n.endsWith(".ttrm") || n.endsWith(".ttrg")
    }
}
