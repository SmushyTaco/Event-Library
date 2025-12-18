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

package com.smushytaco.event_library

import com.smushytaco.event_library.api.Bus
import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.api.EventHandler
import com.smushytaco.event_library.api.ExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

// ────────────────────────────────────────────────────────────────────────────────
// Events for negative tests
// ────────────────────────────────────────────────────────────────────────────────

private class NegSimpleEvent : Event
private class NegOtherEvent : Event
private class NegStaticEvent : Event
private class NegErrorEvent : Event

// ────────────────────────────────────────────────────────────────────────────────
// Subscribers with invalid / mismatched @ExceptionHandler definitions
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Has multiple invalid @ExceptionHandler signatures which should all be ignored.
 */
private class InvalidSignatureSubscriber {
    var eventCalls = 0
    var exceptionCalls = 0

    @EventHandler
    fun on(event: NegSimpleEvent) {
        eventCalls++
        throw IllegalStateException("boom")
    }

    // 0 parameters – invalid
    @ExceptionHandler
    fun noParams() {
        exceptionCalls++
    }

    // 3 parameters – invalid
    @ExceptionHandler
    fun threeParams(event: NegSimpleEvent, throwable: Throwable, extra: String) {
        exceptionCalls++
    }

    // Non Event/Throwable parameter – invalid
    @ExceptionHandler
    fun wrongTypes(x: String) {
        exceptionCalls++
    }

    // Reversed order (Throwable, Event) – invalid
    @ExceptionHandler
    fun reversed(throwable: Throwable, event: NegSimpleEvent) {
        exceptionCalls++
    }
}

/**
 * Exception handler is bound to a *different* event type than the one that throws.
 * It must not run for the wrong event.
 */
private class MismatchedEventExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun onFirst(event: NegSimpleEvent) {
        calls += "first"
        throw IllegalStateException("boom")
    }

    @EventHandler
    fun onOther(event: NegOtherEvent) {
        calls += "other"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun handleOther(event: NegOtherEvent, throwable: Throwable) {
        calls += "handlerOther"
    }
}

/**
 * Verifies that throwable-only handlers are filtered by throwable type and
 * are not invoked when the thrown exception is incompatible.
 */
private class ThrowableFilterNegativeSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun on(event: NegSimpleEvent) {
        calls += "event"
        throw IllegalStateException("boom")
    }

    // Should only match IllegalArgumentException, not IllegalStateException.
    @ExceptionHandler
    fun throwableOnly(throwable: IllegalArgumentException) {
        calls += "throwableOnly"
    }

    // Generic catch-all for this event; should still be invoked.
    @ExceptionHandler
    fun eventAndThrowable(event: NegSimpleEvent, throwable: Throwable) {
        calls += "eventAndThrowable"
    }
}

/**
 * Exception handler exists, but the event handler never throws.
 * The exception handler must not be invoked.
 */
private class NoThrowExceptionHandlerSubscriber {
    var eventCalls = 0
    var exceptionCalls = 0

    @EventHandler
    fun on(event: NegSimpleEvent) {
        eventCalls++
        // no exception thrown
    }

    @ExceptionHandler
    fun handle(event: NegSimpleEvent, throwable: Throwable) {
        exceptionCalls++
    }
}

/**
 * Has a static @ExceptionHandler, but we only call bus.subscribe(instance).
 * Static exception handler must *not* be registered through the instance path.
 */
private class StaticExceptionOnInstanceSubscriber {
    companion object {
        var exceptionCalls: Int = 0

        @JvmStatic
        @ExceptionHandler
        fun staticHandler(event: NegSimpleEvent, throwable: Throwable) {
            exceptionCalls++
        }

        fun reset() {
            exceptionCalls = 0
        }
    }

    @EventHandler
    fun on(event: NegSimpleEvent) {
        throw IllegalStateException("boom")
    }
}

/**
 * Has an instance @ExceptionHandler, but we only call bus.subscribeStatic().
 * Instance exception handler must *not* be registered through the static path.
 */
private class InstanceExceptionOnStaticSubscriber {
    companion object {
        var staticHandlerCalls: Int = 0
        var instanceExceptionCalls: Int = 0

        @JvmStatic
        @EventHandler
        fun staticHandler(event: NegStaticEvent) {
            staticHandlerCalls++
            throw IllegalStateException("boom")
        }

        fun reset() {
            staticHandlerCalls = 0
            instanceExceptionCalls = 0
        }
    }

    @ExceptionHandler
    fun instanceException(event: NegStaticEvent, throwable: Throwable) {
        // If the bus *incorrectly* wired this handler via subscribeStatic(),
        // it would increment this counter.
        instanceExceptionCalls++
    }
}

/**
 * Throws an Error; without a matching exception handler the Error must be rethrown
 * and not swallowed/logged only.
 */
private class ErrorThrowingSubscriber {
    @EventHandler
    fun on(event: NegErrorEvent) {
        throw TestError("boom")
    }
}

private class TestError(message: String) : Error(message)

// ────────────────────────────────────────────────────────────────────────────────
// Tests
// ────────────────────────────────────────────────────────────────────────────────

class ExceptionHandlerNegativeTest {

    @Test
    fun `invalid exception handler method signatures are ignored`() {
        val bus = Bus()
        val subscriber = InvalidSignatureSubscriber()

        bus.subscribe(subscriber)

        assertDoesNotThrow("Bus should swallow exceptions when there are no valid exception handlers") {
            bus.post(NegSimpleEvent())
        }

        assertEquals(
            1,
            subscriber.eventCalls,
            "Event handler should still run exactly once"
        )
        assertEquals(
            0,
            subscriber.exceptionCalls,
            "All invalid @ExceptionHandler methods must be ignored and never invoked"
        )
    }

    @Test
    fun `exception handlers declared for different event types are not invoked`() {
        val bus = Bus()
        val subscriber = MismatchedEventExceptionHandlerSubscriber()

        bus.subscribe(subscriber)

        assertDoesNotThrow {
            bus.post(NegSimpleEvent())
        }

        assertEquals(
            listOf("first"),
            subscriber.calls,
            "Exception handler bound to NegOtherEvent must not run for NegSimpleEvent failures"
        )
    }

    @Test
    fun `throwable-only handlers are skipped when throwable type does not match`() {
        val bus = Bus()
        val subscriber = ThrowableFilterNegativeSubscriber()

        bus.subscribe(subscriber)

        assertDoesNotThrow {
            bus.post(NegSimpleEvent())
        }

        assertEquals(
            listOf("event", "eventAndThrowable"),
            subscriber.calls,
            "Throwable-only handler for IllegalArgumentException must not run for IllegalStateException"
        )
    }

    @Test
    fun `event-only exception handlers are not invoked when no exception occurs`() {
        val bus = Bus()
        val subscriber = NoThrowExceptionHandlerSubscriber()

        bus.subscribe(subscriber)
        bus.post(NegSimpleEvent())

        assertEquals(
            1,
            subscriber.eventCalls,
            "Event handler should be invoked normally"
        )
        assertEquals(
            0,
            subscriber.exceptionCalls,
            "@ExceptionHandler must not run when the event handler does not throw"
        )
    }

    @Test
    fun `static exception handlers are not registered via instance subscribe`() {
        val bus = Bus()
        val subscriber = StaticExceptionOnInstanceSubscriber()

        StaticExceptionOnInstanceSubscriber.reset()

        bus.subscribe(subscriber)

        assertDoesNotThrow {
            bus.post(NegSimpleEvent())
        }

        assertEquals(
            0,
            StaticExceptionOnInstanceSubscriber.exceptionCalls,
            "Static @ExceptionHandler must not be wired when using bus.subscribe(instance)"
        )
    }

    @Test
    fun `instance exception handlers are not registered via subscribeStatic`() {
        val bus = Bus()

        InstanceExceptionOnStaticSubscriber.reset()

        bus.subscribeStatic(InstanceExceptionOnStaticSubscriber::class.java)

        assertDoesNotThrow {
            bus.post(NegStaticEvent())
        }

        assertEquals(
            1,
            InstanceExceptionOnStaticSubscriber.staticHandlerCalls,
            "Static @EventHandler should be invoked exactly once"
        )
        assertEquals(
            0,
            InstanceExceptionOnStaticSubscriber.instanceExceptionCalls,
            "Instance @ExceptionHandler must not be wired when using bus.subscribeStatic"
        )
    }

    @Test
    fun `errors without matching exception handlers are rethrown`() {
        val bus = Bus()
        val subscriber = ErrorThrowingSubscriber()

        bus.subscribe(subscriber)

        val thrown = assertThrows(TestError::class.java) {
            bus.post(NegErrorEvent())
        }

        assertEquals(
            "boom",
            thrown.message,
            "Errors should be propagated when no @ExceptionHandler handles them"
        )
    }
}
