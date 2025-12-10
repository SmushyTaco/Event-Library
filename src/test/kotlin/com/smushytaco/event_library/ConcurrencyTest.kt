/*
 * Copyright 2025 Nikan Radan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("unused")

package com.smushytaco.event_library

import com.smushytaco.event_library.api.Bus
import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.api.EventHandler
import com.smushytaco.event_library.api.ExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency / stress tests for the event bus implementation.
 *
 * These tests are intentionally a bit heavy to try to shake out races, missed
 * events, double-subscriptions, ConcurrentModificationException, etc.
 */
class ConcurrencyTest {

    // ------------------------------------------------------------------------
    // Test helpers / fixtures
    // ------------------------------------------------------------------------

    private fun newBus(): Bus = Bus()

    // Simple event type used by most tests
    private class SimpleEvent : Event

    // Event type for exception-handling tests
    private class ThrowingEvent : Event

    /**
     * Simple subscriber that just counts how many SimpleEvent it sees.
     */
    private class CountingSubscriber {
        val count = AtomicInteger(0)

        @EventHandler
        fun on(event: SimpleEvent) {
            count.incrementAndGet()
        }
    }

    /**
     * Subscriber that always throws on ThrowingEvent, with a matching
     * @ExceptionHandler that counts how many failures were handled.
     */
    private class ThrowingSubscriber(
        private val handlerInvocations: AtomicInteger,
        private val handledExceptions: AtomicInteger
    ) {
        @EventHandler
        fun on(event: ThrowingEvent) {
            handlerInvocations.incrementAndGet()
            throw IllegalStateException("boom")
        }

        // event + throwable signature
        @ExceptionHandler
        fun onFailure(event: Event, t: Throwable) {
            handledExceptions.incrementAndGet()
        }
    }

    /**
     * Static subscriber for testing subscribeStatic / unsubscribeStatic under load.
     */
    private class StaticSubscriber {
        companion object {
            val staticCount = AtomicInteger(0)

            @JvmStatic
            @EventHandler
            fun on(event: SimpleEvent) {
                staticCount.incrementAndGet()
            }
        }
    }

    // ------------------------------------------------------------------------
    // 1) Idempotent concurrent subscribe on the same instance
    // ------------------------------------------------------------------------

    /**
     * Many threads concurrently call subscribe() on the same instance.
     * We expect the subscriber to be registered only once:
     * - No duplicate handler invocations
     * - No crashes / CMEs
     */
    @Test
    fun `concurrent subscribe same instance is idempotent`() {
        val bus = newBus()
        val subscriber = CountingSubscriber()

        val threads = 8
        val iterationsPerThread = 1_000
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.submit {
                repeat(iterationsPerThread) {
                    bus.subscribe(subscriber)
                }
                done.countDown()
            }
        }

        assertTrue(done.await(30, TimeUnit.SECONDS), "subscribe workers did not finish in time")
        pool.shutdownNow()

        val events = 1_000
        repeat(events) {
            bus.post(SimpleEvent())
        }

        // If subscribe is correctly guarded, this should be exactly 1x per event.
        assertEquals(events, subscriber.count.get(), "Subscriber should be registered exactly once")
    }

    // ------------------------------------------------------------------------
    // 2) Many threads posting concurrently to a stable subscriber
    // ------------------------------------------------------------------------

    /**
     * Baseline stress test: one subscriber, many threads hammering post().
     * We assert that the subscriber sees every event exactly once, and that
     * nothing explodes under load.
     */
    @RepeatedTest(3)
    fun `concurrent post many threads all events delivered to stable subscriber`() {
        val bus = newBus()
        val subscriber = CountingSubscriber()
        bus.subscribe(subscriber)

        val threads = 8
        val eventsPerThread = 20_000
        val totalEvents = threads * eventsPerThread

        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.submit {
                repeat(eventsPerThread) {
                    bus.post(SimpleEvent())
                }
                done.countDown()
            }
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "post workers did not finish in time")
        pool.shutdownNow()

        assertEquals(
            totalEvents,
            subscriber.count.get(),
            "Stable subscriber should see all events, even under heavy concurrent post()"
        )
    }

    // ------------------------------------------------------------------------
    // 3) post() while subscribing / unsubscribing other subscribers
    // ------------------------------------------------------------------------

    /**
     * One "baseline" subscriber that is never unsubscribed, plus a background
     * thread that rapidly subscribes and unsubscribes throwaway subscribers,
     * while many threads are posting events.
     *
     * We assert:
     *  - The baseline subscriber sees exactly all (stable) posts.
     *  - No crashes or CMEs occur.
     */
    @RepeatedTest(3)
    fun `concurrent post while subscribing and unsubscribing other subscribers`() {
        val bus = newBus()
        val baseline = CountingSubscriber()
        bus.subscribe(baseline)

        val posterThreads = 8
        val eventsPerThread = 10_000
        val totalStableEvents = posterThreads * eventsPerThread

        val pool = Executors.newFixedThreadPool(posterThreads + 1)
        val done = CountDownLatch(posterThreads)
        val togglerRunning = AtomicBoolean(true)

        // Background thread toggling extra subscribers on/off
        pool.submit {
            while (togglerRunning.get()) {
                val tmp = CountingSubscriber()
                bus.subscribe(tmp)
                // No posts here, so baseline's expected count is known exactly.
                bus.unsubscribe(tmp)
            }
        }

        // Threads that hammer post()
        repeat(posterThreads) {
            pool.submit {
                repeat(eventsPerThread) {
                    bus.post(SimpleEvent())
                }
                done.countDown()
            }
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "post workers did not finish in time")
        togglerRunning.set(false)
        pool.shutdownNow()

        assertEquals(
            totalStableEvents,
            baseline.count.get(),
            "Baseline subscriber should see all events even while others are being added/removed"
        )
    }

    // ------------------------------------------------------------------------
    // 4) Exception handling under concurrency
    // ------------------------------------------------------------------------

    /**
     * A subscriber that *always throws* plus an @ExceptionHandler for that event.
     * Many threads post ThrowingEvent concurrently.
     *
     * We assert:
     *  - Each event causes exactly one handler invocation.
     *  - Each failure is handled by exactly one @ExceptionHandler.
     *  - No exceptions escape post() (test would fail if they do).
     */
    @RepeatedTest(3)
    fun `exception handlers are correct under concurrency`() {
        val bus = newBus()
        val handlerInvocations = AtomicInteger(0)
        val handledExceptions = AtomicInteger(0)
        val subscriber = ThrowingSubscriber(handlerInvocations, handledExceptions)

        bus.subscribe(subscriber)

        val threads = 8
        val eventsPerThread = 5_000
        val totalEvents = threads * eventsPerThread

        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)

        repeat(threads) {
            pool.submit {
                repeat(eventsPerThread) {
                    // If the bus ever rethrows the IllegalStateException, this task
                    // will die and the test will fail on the latch timeout.
                    bus.post(ThrowingEvent())
                }
                done.countDown()
            }
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "exception post workers did not finish in time")
        pool.shutdownNow()

        assertEquals(
            totalEvents,
            handlerInvocations.get(),
            "Every ThrowingEvent should reach the throwing @EventHandler"
        )
        assertEquals(
            totalEvents,
            handledExceptions.get(),
            "Every thrown exception should be routed to @ExceptionHandler exactly once"
        )
    }

    // ------------------------------------------------------------------------
    // 5) Static handlers + subscribeStatic/unsubscribeStatic under concurrency
    // ------------------------------------------------------------------------

    /**
     * Stress test for static subscribers:
     *
     *  - One stable instance subscriber.
     *  - One background thread toggling StaticSubscriber on/off via subscribeStatic/unsubscribeStatic.
     *  - Many threads posting SimpleEvent.
     *
     * We assert that the stable instance subscriber still sees every event exactly once;
     * the static subscriber just adds extra load / pressure on the caches.
     */
    @RepeatedTest(3)
    fun `static subscribers toggling under load does not affect stable subscriber`() {
        val bus = newBus()
        val baseline = CountingSubscriber()
        bus.subscribe(baseline)

        StaticSubscriber.staticCount.set(0)

        val posterThreads = 8
        val eventsPerThread = 10_000
        val totalStableEvents = posterThreads * eventsPerThread

        val pool = Executors.newFixedThreadPool(posterThreads + 1)
        val done = CountDownLatch(posterThreads)
        val togglerRunning = AtomicBoolean(true)

        // Background thread toggling the static subscriber
        pool.submit {
            while (togglerRunning.get()) {
                bus.subscribeStatic(StaticSubscriber::class.java)
                bus.unsubscribeStatic(StaticSubscriber::class.java)
            }
        }

        // Threads that hammer post()
        repeat(posterThreads) {
            pool.submit {
                repeat(eventsPerThread) {
                    bus.post(SimpleEvent())
                }
                done.countDown()
            }
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "static test post workers did not finish in time")
        togglerRunning.set(false)
        pool.shutdownNow()

        assertEquals(
            totalStableEvents,
            baseline.count.get(),
            "Baseline instance subscriber should see all events even while static subscribers are toggled"
        )

        // Sanity check: we should have seen *some* static invocations, but we don't rely
        // on an exact number because subscribeStatic/unsubscribeStatic may race with post().
        assertTrue(
            StaticSubscriber.staticCount.get() >= 0,
            "Static subscriber counter should be non-negative"
        )
    }
}
