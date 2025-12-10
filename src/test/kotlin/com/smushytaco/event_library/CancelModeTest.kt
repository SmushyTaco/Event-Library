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

import com.smushytaco.event_library.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// ────────────────────────────────────────────────────────────────────────────────
// Events used in cancel mode tests
// ────────────────────────────────────────────────────────────────────────────────

private class PlainEvent : Event

private class TestCancelableEvent :
    Event,
    Cancelable by Cancelable()

// ────────────────────────────────────────────────────────────────────────────────
// Subscribers
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Non-cancelable handlers; CancelMode should have no effect here.
 */
private class NonCancelableSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 10)
    fun first(event: PlainEvent) {
        calls += "first"
    }

    @EventHandler(priority = 0)
    fun second(event: PlainEvent) {
        calls += "second"
    }
}

/**
 * Dynamic cancellation: first handler cancels, others react differently
 * based on runIfCanceled and CancelMode.
 */
private class CancelFlowSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 20)
    fun canceller(event: TestCancelableEvent) {
        calls += "canceller"
        event.markCanceled()
    }

    @EventHandler(priority = 10)
    fun normal(event: TestCancelableEvent) {
        calls += "normal"
    }

    @EventHandler(priority = 0, runIfCanceled = true)
    fun ignoresCanceled(event: TestCancelableEvent) {
        calls += "ignoresCanceled"
    }
}

/**
 * Pre-canceled event: different handlers opt in or out via runIfCanceled.
 */
private class PreCanceledSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 20, runIfCanceled = true)
    fun firstIgnored(event: TestCancelableEvent) {
        calls += "firstIgnored"
    }

    @EventHandler(priority = 10)
    fun secondNormal(event: TestCancelableEvent) {
        calls += "secondNormal"
    }

    @EventHandler(priority = 0, runIfCanceled = true)
    fun thirdIgnored(event: TestCancelableEvent) {
        calls += "thirdIgnored"
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Tests
// ────────────────────────────────────────────────────────────────────────────────

class CancelModeTest {

    @Test
    fun `non-cancelable events are unaffected by cancel mode`() {
        val bus = Bus()
        val subscriber = NonCancelableSubscriber()
        val event = PlainEvent()

        bus.subscribe(subscriber)

        bus.post(event, CancelMode.IGNORE)
        bus.post(event, CancelMode.RESPECT)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            listOf(
                "first", "second", // IGNORE
                "first", "second", // RESPECT
                "first", "second"  // ENFORCE
            ),
            subscriber.calls,
            "All handlers for non-cancelable events should run for every CancelMode"
        )
    }

    @Test
    fun `CancelMode IGNORE runs all handlers even after cancellation`() {
        val bus = Bus()
        val subscriber = CancelFlowSubscriber()
        val event = TestCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.IGNORE)

        assertEquals(
            listOf("canceller", "normal", "ignoresCanceled"),
            subscriber.calls,
            "All handlers should run in priority order when CancelMode=IGNORE"
        )
        assertTrue(event.canceled, "Event should still be marked as canceled by the handler")
    }

    @Test
    fun `CancelMode RESPECT skips handlers that do not runIfCanceled after cancellation`() {
        val bus = Bus()
        val subscriber = CancelFlowSubscriber()
        val event = TestCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.RESPECT)

        assertEquals(
            listOf("canceller", "ignoresCanceled"),
            subscriber.calls,
            "Handlers that do not set runIfCanceled should be skipped after cancellation in RESPECT mode"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `default cancel mode behaves like RESPECT`() {
        val bus = Bus()
        val subscriber = CancelFlowSubscriber()
        val event = TestCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event) // default argument

        assertEquals(
            listOf("canceller", "ignoresCanceled"),
            subscriber.calls,
            "Default Bus.post should behave like CancelMode.RESPECT"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `CancelMode ENFORCE stops dispatch after first cancellation`() {
        val bus = Bus()
        val subscriber = CancelFlowSubscriber()
        val event = TestCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            listOf("canceller"),
            subscriber.calls,
            "No handlers after the first cancellation should run in ENFORCE mode, even if runIfCanceled=true"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `CancelMode ENFORCE does nothing when event is already canceled before dispatch`() {
        val bus = Bus()
        val subscriber = CancelFlowSubscriber()
        val event = TestCancelableEvent().also { it.markCanceled() }

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            emptyList<String>(),
            subscriber.calls,
            "Pre-canceled events should not dispatch to any handlers in ENFORCE mode"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `pre-canceled event with CancelMode IGNORE runs all handlers`() {
        val bus = Bus()
        val subscriber = PreCanceledSubscriber()
        val event = TestCancelableEvent().also { it.markCanceled() }

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.IGNORE)

        assertEquals(
            listOf("firstIgnored", "secondNormal", "thirdIgnored"),
            subscriber.calls,
            "In IGNORE mode, pre-canceled events should still run all handlers"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `pre-canceled event with CancelMode RESPECT only runs handlers that runIfCanceled`() {
        val bus = Bus()
        val subscriber = PreCanceledSubscriber()
        val event = TestCancelableEvent().also { it.markCanceled() }

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.RESPECT)

        assertEquals(
            listOf("firstIgnored", "thirdIgnored"),
            subscriber.calls,
            "In RESPECT mode, only handlers with runIfCanceled=true should run for pre-canceled events"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `pre-canceled event with CancelMode ENFORCE runs no handlers`() {
        val bus = Bus()
        val subscriber = PreCanceledSubscriber()
        val event = TestCancelableEvent().also { it.markCanceled() }

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            emptyList<String>(),
            subscriber.calls,
            "In ENFORCE mode, pre-canceled events should be short-circuited before any handler is invoked"
        )
        assertTrue(event.canceled)
    }
}
