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

import com.smushytaco.event_library.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// ────────────────────────────────────────────────────────────────────────────────
// Events and error types used in @ExceptionHandler tests
// ────────────────────────────────────────────────────────────────────────────────

private class ExceptionSimpleEvent : Event

private class ExceptionCancelableEvent :
    Event,
    Cancelable by Cancelable()

private open class ExceptionBaseEvent : Event
private class ExceptionChildEvent : ExceptionBaseEvent()

private class ExceptionTestError(message: String) : Error(message)

// ────────────────────────────────────────────────────────────────────────────────
// Instance-based subscribers with @ExceptionHandler
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Basic instance subscriber with (event, throwable) exception handler.
 */
private class InstanceExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()
    var lastEvent: ExceptionSimpleEvent? = null
    var lastThrowable: Throwable? = null

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
        calls += "exception"
        lastEvent = event
        lastThrowable = throwable
    }
}

/**
 * Instance subscriber with event-only exception handler.
 */
private class EventOnlyExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()
    var lastEvent: ExceptionSimpleEvent? = null

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun onException(event: ExceptionSimpleEvent) {
        calls += "eventOnly"
        lastEvent = event
    }
}

/**
 * Instance subscriber with throwable-only exception handler.
 */
private class ThrowableOnlyExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()
    var lastThrowable: Throwable? = null

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalArgumentException("bad")
    }

    @ExceptionHandler
    fun onThrowable(throwable: IllegalArgumentException) {
        calls += "throwableOnly"
        lastThrowable = throwable
    }
}

/**
 * Subscriber used to verify throwable-type filtering with isInstance.
 */
private class ThrowableFilteringSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalArgumentException("boom")
    }

    @ExceptionHandler
    fun onIllegalState(event: ExceptionSimpleEvent, throwable: IllegalStateException) {
        calls += "illegalState"
    }

    @ExceptionHandler
    fun onRuntime(throwable: RuntimeException) {
        calls += "runtime"
    }
}

/**
 * Subscriber that exposes all three exception handler shapes to test specificity
 * ordering at equal priority.
 */
private class SpecificityOrderingSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    // Most specific: event + specific throwable
    @ExceptionHandler
    fun mostSpecific(event: ExceptionSimpleEvent, throwable: IllegalStateException) {
        calls += "event+throwable-specific"
    }

    // Middle: event only
    @ExceptionHandler
    fun eventOnly(event: ExceptionSimpleEvent) {
        calls += "eventOnly"
    }

    // Least specific: throwable only
    @ExceptionHandler
    fun throwableOnly(throwable: Throwable) {
        calls += "throwableOnly"
    }
}

/**
 * Subscriber used to verify that priority beats specificity.
 */
private class PriorityBeatsSpecificitySubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler(priority = 0)
    fun lowSpecific(event: ExceptionSimpleEvent, throwable: IllegalStateException) {
        calls += "lowSpecific"
    }

    @ExceptionHandler(priority = 5)
    fun highEventOnly(event: ExceptionSimpleEvent) {
        calls += "highEventOnly"
    }
}

/**
 * Subscriber whose exception handlers are registered for both base and child
 * event types to verify hierarchy traversal.
 */
private class HierarchyExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun onChild(event: ExceptionChildEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun onBase(event: ExceptionBaseEvent, throwable: Throwable) {
        calls += "base"
    }

    @ExceptionHandler
    fun onChild(event: ExceptionChildEvent, throwable: Throwable) {
        calls += "child"
    }
}

/**
 * Subscriber where multiple event handlers throw to verify exception handlers
 * are invoked per-throw in priority order.
 */
private class MultipleThrowingHandlersSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 10)
    fun first(event: ExceptionSimpleEvent) {
        calls += "first"
        throw IllegalStateException("first")
    }

    @EventHandler(priority = 0)
    fun second(event: ExceptionSimpleEvent) {
        calls += "second"
        throw IllegalArgumentException("second")
    }

    @ExceptionHandler
    fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
        calls += "exception:${throwable::class.simpleName}"
    }
}

/**
 * Subscriber containing a mix of valid and invalid @ExceptionHandler signatures.
 */
private class InvalidExceptionHandlerSubscriber {
    var validCalled = false
    var invalidCalled = false

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        throw IllegalStateException("boom")
    }

    // Valid (event + throwable)
    @ExceptionHandler
    fun valid(event: ExceptionSimpleEvent, throwable: Throwable) {
        validCalled = true
    }

    // Invalid: no parameters
    @ExceptionHandler
    fun invalidNoParams() {
        invalidCalled = true
    }

    // Invalid: too many parameters
    @ExceptionHandler
    fun invalidTooMany(event: ExceptionSimpleEvent, throwable: Throwable, extra: String) {
        invalidCalled = true
    }

    // Invalid: wrong single parameter type
    @ExceptionHandler
    fun invalidWrongTypes(x: String) {
        invalidCalled = true
    }

    // Invalid: parameters in wrong order
    @ExceptionHandler
    fun invalidParamOrder(throwable: Throwable, event: ExceptionSimpleEvent) {
        invalidCalled = true
    }
}

/**
 * Subscriber with a private exception handler to ensure accessibility tweaks
 * work as expected.
 */
private class PrivateExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    private fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
        calls += "privateException"
    }
}

/**
 * Subscriber that combines cancellation with a throwing handler to ensure
 * dispatch mode controls whether the throwing handler (and its exception
 * handlers) run.
 */
private class CancelingExceptionHandlerSubscriber {
    val calls = mutableListOf<String>()
    var exceptionCalls = 0

    @EventHandler(priority = 10)
    fun cancelFirst(event: ExceptionCancelableEvent) {
        calls += "cancelFirst"
        event.markCanceled()
    }

    @EventHandler(priority = 0)
    fun throwing(event: ExceptionCancelableEvent) {
        calls += "throwing"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun onException(event: ExceptionCancelableEvent, throwable: Throwable) {
        exceptionCalls++
    }
}

/**
 * Subscriber used to verify that unsubscribe() removes both event and exception
 * handlers.
 */
private class UnsubscribeExceptionHandlerSubscriber {
    var eventCalls = 0
    var exceptionCalls = 0

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        eventCalls++
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
        exceptionCalls++
    }
}

/**
 * Subscriber used to verify Error is rethrown when no exception handlers exist.
 */
private class ErrorRethrownSubscriber {
    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        throw ExceptionTestError("boom")
    }
}

/**
 * Subscriber used to verify Error is handled and not rethrown when an
 * @ExceptionHandler is present.
 */
private class ErrorHandledByExceptionHandlerSubscriber {
    var exceptionHandled = false

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        throw ExceptionTestError("boom")
    }

    @ExceptionHandler
    fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
        exceptionHandled = true
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Static subscribers with @ExceptionHandler
// ────────────────────────────────────────────────────────────────────────────────

/**
 * Static-only subscriber with an event handler and an exception handler.
 */
private class StaticExceptionHandlerSubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()
        var lastEvent: ExceptionSimpleEvent? = null
        var lastThrowable: Throwable? = null

        @JvmStatic
        @EventHandler
        fun on(event: ExceptionSimpleEvent) {
            calls += "handler"
            throw IllegalStateException("boom")
        }

        @JvmStatic
        @ExceptionHandler
        fun onException(event: ExceptionSimpleEvent, throwable: Throwable) {
            calls += "exception"
            lastEvent = event
            lastThrowable = throwable
        }

        @JvmStatic
        fun reset() {
            calls.clear()
            lastEvent = null
            lastThrowable = null
        }
    }
}

/**
 * Subscriber that mixes static and instance exception handlers to verify
 * shared priority ordering.
 */
private class MixedStaticInstanceExceptionHandlerSubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @ExceptionHandler(priority = 5)
        fun staticHandler(event: ExceptionSimpleEvent, throwable: Throwable) {
            calls += "static"
        }

        @JvmStatic
        fun reset() {
            calls.clear()
        }
    }

    @EventHandler
    fun on(event: ExceptionSimpleEvent) {
        calls += "handler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler(priority = 10)
    fun instanceHigh(event: ExceptionSimpleEvent, throwable: Throwable) {
        calls += "instanceHigh"
    }

    @ExceptionHandler(priority = 0)
    fun instanceLow(throwable: Throwable) {
        calls += "instanceLow"
    }
}

/**
 * Subscriber used to verify that unsubscribeStatic() removes only static
 * exception handlers, leaving instance ones intact.
 */
private class StaticInstanceExceptionIsolationSubscriber {
    companion object {
        val staticCalls: MutableList<String> = mutableListOf()

        @JvmStatic
        @ExceptionHandler
        fun staticException(event: ExceptionSimpleEvent, throwable: Throwable) {
            staticCalls += "staticException"
        }

        @JvmStatic
        fun resetStatic() {
            staticCalls.clear()
        }
    }

    val instanceCalls: MutableList<String> = mutableListOf()

    @EventHandler
    fun instanceHandler(event: ExceptionSimpleEvent) {
        instanceCalls += "instanceHandler"
        throw IllegalStateException("boom")
    }

    @ExceptionHandler
    fun instanceException(event: ExceptionSimpleEvent, throwable: Throwable) {
        instanceCalls += "instanceException"
    }

    fun resetInstance() {
        instanceCalls.clear()
    }
}

/**
 * Global static exception sink that catches all Event+Throwable combinations.
 */
private class GlobalStaticExceptionSink {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @ExceptionHandler
        fun onAny(event: Event, throwable: Throwable) {
            calls += "${event::class.simpleName}:${throwable::class.simpleName}"
        }

        @JvmStatic
        fun reset() {
            calls.clear()
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Tests
// ────────────────────────────────────────────────────────────────────────────────

class ExceptionHandlerTest {

    @Test
    fun `instance exception handler receives event and throwable`() {
        val bus = Bus()
        val subscriber = InstanceExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)

        assertDoesNotThrow { bus.post(event) }

        assertEquals(listOf("handler", "exception"), subscriber.calls)
        assertSame(event, subscriber.lastEvent)
        assertTrue(subscriber.lastThrowable is IllegalStateException)
    }

    @Test
    fun `event-only exception handler is invoked when handler throws`() {
        val bus = Bus()
        val subscriber = EventOnlyExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(listOf("handler", "eventOnly"), subscriber.calls)
        assertSame(event, subscriber.lastEvent)
    }

    @Test
    fun `throwable-only exception handler is invoked for matching throwable`() {
        val bus = Bus()
        val subscriber = ThrowableOnlyExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(listOf("handler", "throwableOnly"), subscriber.calls)
        assertTrue(subscriber.lastThrowable is IllegalArgumentException)
    }

    @Test
    fun `throwable-only exception handler is skipped for non matching throwable`() {
        val bus = Bus()
        val subscriber = ThrowableFilteringSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        // IllegalArgumentException should not trigger the IllegalState handler
        assertEquals(
            listOf("handler", "runtime"),
            subscriber.calls,
            "Only the runtime throwable-only handler should be invoked"
        )
    }

    @Test
    fun `specific exception handlers are ordered by specificity within same priority`() {
        val bus = Bus()
        val subscriber = SpecificityOrderingSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf(
                "handler",
                "event+throwable-specific",
                "eventOnly",
                "throwableOnly"
            ),
            subscriber.calls,
            "Handlers should be ordered by priority, then by specificity (event+throwable, event-only, throwable-only)"
        )
    }

    @Test
    fun `exception handler priority overrides specificity`() {
        val bus = Bus()
        val subscriber = PriorityBeatsSpecificitySubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf("handler", "highEventOnly", "lowSpecific"),
            subscriber.calls,
            "Higher-priority handler should run before more specific lower-priority handler"
        )
    }

    @Test
    fun `exception handlers run for both base and derived events`() {
        val bus = Bus()
        val subscriber = HierarchyExceptionHandlerSubscriber()
        val event = ExceptionChildEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals("handler", subscriber.calls.first())
        assertEquals(
            setOf("child", "base"),
            subscriber.calls.drop(1).toSet(),
            "Both base and child exception handlers should be invoked for child events"
        )
        assertEquals(3, subscriber.calls.size)
    }

    @Test
    fun `exception handlers are invoked once per throwing handler`() {
        val bus = Bus()
        val subscriber = MultipleThrowingHandlersSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf(
                "first",
                "exception:IllegalStateException",
                "second",
                "exception:IllegalArgumentException"
            ),
            subscriber.calls,
            "Exception handler should run once per throwing event handler in priority order"
        )
    }

    @Test
    fun `invalid ExceptionHandler signatures are ignored`() {
        val bus = Bus()
        val subscriber = InvalidExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertTrue(subscriber.validCalled, "Valid exception handler should be invoked")
        assertFalse(
            subscriber.invalidCalled,
            "Handlers with invalid @ExceptionHandler signatures must not be invoked"
        )
    }

    @Test
    fun `private exception handlers are discovered and invoked`() {
        val bus = Bus()
        val subscriber = PrivateExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf("handler", "privateException"),
            subscriber.calls,
            "Private @ExceptionHandler should be made accessible and invoked"
        )
    }

    @Test
    fun `cancellation prevents throwing handler and exception handlers when cancelMode is ENFORCE`() {
        val bus = Bus()
        val subscriber = CancelingExceptionHandlerSubscriber()
        val event = ExceptionCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            listOf("cancelFirst"),
            subscriber.calls,
            "Second handler should not run once event is canceled with cancelMode=ENFORCE"
        )
        assertEquals(
            0,
            subscriber.exceptionCalls,
            "Exception handler should not be invoked when the throwing handler is skipped"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `cancellation is ignored when cancelMode is IGNORE and exception handler runs`() {
        val bus = Bus()
        val subscriber = CancelingExceptionHandlerSubscriber()
        val event = ExceptionCancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.IGNORE)

        assertEquals(
            listOf("cancelFirst", "throwing"),
            subscriber.calls,
            "Both handlers should run when cancelMode=IGNORE"
        )
        assertEquals(
            1,
            subscriber.exceptionCalls,
            "Exception handler should run when the throwing handler executes"
        )
        assertTrue(event.canceled)
    }

    @Test
    fun `unsubscribe removes both event and exception handlers`() {
        val bus = Bus()
        val subscriber = UnsubscribeExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)

        // First dispatch: both event and exception handlers should run
        bus.post(event)
        // Second dispatch after unsubscribe: neither should run
        bus.unsubscribe(subscriber)
        bus.post(event)

        assertEquals(
            1,
            subscriber.eventCalls,
            "Event handler should run only before unsubscribe"
        )
        assertEquals(
            1,
            subscriber.exceptionCalls,
            "Exception handler should run only before unsubscribe"
        )
    }

    @Test
    fun `Error is rethrown when no exception handlers exist`() {
        val bus = Bus()
        val subscriber = ErrorRethrownSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)

        assertThrows(ExceptionTestError::class.java) {
            bus.post(event)
        }
    }

    @Test
    fun `Error is handled and not rethrown when exception handler is present`() {
        val bus = Bus()
        val subscriber = ErrorHandledByExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        bus.subscribe(subscriber)

        assertDoesNotThrow {
            bus.post(event)
        }
        assertTrue(
            subscriber.exceptionHandled,
            "Exception handler should intercept the Error and prevent it from propagating"
        )
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Static @ExceptionHandler tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `static exception handler receives event and throwable using Class overload`() {
        val bus = Bus()
        val event = ExceptionSimpleEvent()

        StaticExceptionHandlerSubscriber.reset()

        bus.subscribeStatic(StaticExceptionHandlerSubscriber::class.java)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf("handler", "exception"),
            StaticExceptionHandlerSubscriber.calls,
            "Static exception handler should be invoked after the throwing static event handler"
        )
        assertSame(event, StaticExceptionHandlerSubscriber.lastEvent)
        assertTrue(StaticExceptionHandlerSubscriber.lastThrowable is IllegalStateException)
    }

    @Test
    fun `static exception handler receives event and throwable using KClass overload`() {
        val bus = Bus()
        val event = ExceptionSimpleEvent()

        StaticExceptionHandlerSubscriber.reset()

        bus.subscribeStatic(StaticExceptionHandlerSubscriber::class)
        assertDoesNotThrow { bus.post(event) }

        assertEquals(
            listOf("handler", "exception"),
            StaticExceptionHandlerSubscriber.calls,
            "Static exception handler should also work when registered via KClass overload"
        )
    }

    @Test
    fun `unsubscribeStatic removes static exception handlers`() {
        val bus = Bus()
        val event = ExceptionSimpleEvent()

        StaticExceptionHandlerSubscriber.reset()

        bus.subscribeStatic(StaticExceptionHandlerSubscriber::class.java)
        bus.post(event)
        // First time should call both handler and exception
        assertEquals(
            listOf("handler", "exception"),
            StaticExceptionHandlerSubscriber.calls
        )

        // After unsubscribeStatic, no static event or exception handlers should run
        bus.unsubscribeStatic(StaticExceptionHandlerSubscriber::class.java)
        StaticExceptionHandlerSubscriber.reset()

        bus.post(event)
        assertEquals(
            emptyList<String>(),
            StaticExceptionHandlerSubscriber.calls,
            "Static event and exception handlers should not run after unsubscribeStatic"
        )
    }

    @Test
    fun `static and instance exception handlers share priority ordering`() {
        val bus = Bus()
        val subscriber = MixedStaticInstanceExceptionHandlerSubscriber()
        val event = ExceptionSimpleEvent()

        MixedStaticInstanceExceptionHandlerSubscriber.reset()

        bus.subscribe(subscriber)
        bus.subscribeStatic(MixedStaticInstanceExceptionHandlerSubscriber::class.java)

        bus.post(event)

        assertEquals(
            listOf("handler", "instanceHigh", "static", "instanceLow"),
            MixedStaticInstanceExceptionHandlerSubscriber.calls,
            "Exception handlers should be invoked in priority order across static and instance handlers"
        )
    }

    @Test
    fun `unsubscribeStatic does not affect instance exception handlers`() {
        val bus = Bus()
        val subscriber = StaticInstanceExceptionIsolationSubscriber()
        val event = ExceptionSimpleEvent()

        StaticInstanceExceptionIsolationSubscriber.resetStatic()
        subscriber.resetInstance()

        bus.subscribe(subscriber)
        bus.subscribeStatic(StaticInstanceExceptionIsolationSubscriber::class.java)

        // First dispatch: both static and instance exception handlers should run
        bus.post(event)

        assertEquals(
            listOf("instanceHandler", "instanceException"),
            subscriber.instanceCalls,
            "Instance event+exception should run before unsubscribeStatic"
        )
        assertEquals(
            listOf("staticException"),
            StaticInstanceExceptionIsolationSubscriber.staticCalls,
            "Static exception handler should also run before unsubscribeStatic"
        )

        // After unsubscribeStatic: only instance exception handlers should run
        StaticInstanceExceptionIsolationSubscriber.resetStatic()
        subscriber.resetInstance()
        bus.unsubscribeStatic(StaticInstanceExceptionIsolationSubscriber::class.java)

        bus.post(event)

        assertEquals(
            listOf("instanceHandler", "instanceException"),
            subscriber.instanceCalls,
            "Instance exception handlers should still run after unsubscribeStatic"
        )
        assertEquals(
            emptyList<String>(),
            StaticInstanceExceptionIsolationSubscriber.staticCalls,
            "Static exception handlers should no longer run after unsubscribeStatic"
        )
    }

    @Test
    fun `global static exception handler receives exceptions for any event type`() {
        val bus = Bus()
        val localSubscriber = InstanceExceptionHandlerSubscriber() // any throwing subscriber
        val event = ExceptionSimpleEvent()

        GlobalStaticExceptionSink.reset()

        bus.subscribe(localSubscriber)
        bus.subscribeStatic(GlobalStaticExceptionSink::class.java)

        bus.post(event)

        assertTrue(
            GlobalStaticExceptionSink.calls.single()
                .startsWith("ExceptionSimpleEvent:"),
            "Global static exception handler should see the event and throwable type"
        )
    }
}
