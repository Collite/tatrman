package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.tatrman.ttr.designer.server.watch.DebouncedRefreshTrigger
import java.util.concurrent.atomic.AtomicInteger

/**
 * The debounce coalesces a burst of pokes into a single action, and fires again
 * for a later, separated burst. Driven by virtual time (`runTest`) — no wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedRefreshTriggerSpec :
    StringSpec({
        "a burst of pokes fires the action exactly once after the window" {
            runTest {
                val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
                val count = AtomicInteger(0)
                val trigger = DebouncedRefreshTrigger(scope = scope, debounceMillis = 200) { count.incrementAndGet() }

                trigger.poke()
                advanceTimeBy(50)
                trigger.poke()
                advanceTimeBy(50)
                trigger.poke()

                advanceTimeBy(199)
                count.get() shouldBe 0 // window keeps resetting; not yet quiet

                advanceUntilIdle()
                count.get() shouldBe 1
            }
        }

        "two separated bursts fire the action twice" {
            runTest {
                val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
                val count = AtomicInteger(0)
                val trigger = DebouncedRefreshTrigger(scope = scope, debounceMillis = 200) { count.incrementAndGet() }

                trigger.poke()
                advanceUntilIdle()
                count.get() shouldBe 1

                trigger.poke()
                advanceUntilIdle()
                count.get() shouldBe 2
            }
        }
    })
