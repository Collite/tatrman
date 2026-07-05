package org.tatrman.ttr.designer.server.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces a burst of filesystem events into a single `refresh` call. Each
 * [poke] restarts a [debounceMillis] timer; the action fires once the fs goes
 * quiet. Runs on the injected [scope]/clock so tests drive it with virtual time
 * (`runTest`), no wall-clock sleeps (DebouncedRefreshTriggerSpec).
 */
class DebouncedRefreshTrigger(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 200,
    private val action: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var pending: Job? = null

    /** Signal that something changed; schedules (or reschedules) the debounced [action]. */
    suspend fun poke() {
        mutex.withLock {
            pending?.cancel()
            pending =
                scope.launch {
                    delay(debounceMillis)
                    action()
                }
        }
    }
}
