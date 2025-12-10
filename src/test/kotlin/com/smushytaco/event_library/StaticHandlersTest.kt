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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

// ────────────────────────────────────────────────────────────────────────────────
// Events used in static handler tests
// ────────────────────────────────────────────────────────────────────────────────

private class StaticSimpleEvent : Event

private class StaticPriorityEvent : Event

private class StaticCancelableEvent :
    Event,
    Cancelable by Cancelable()

private open class StaticBaseEvent : Event
private class StaticChildEvent : StaticBaseEvent()

private class MixedEvent : Event

private class CombinedEvent : Event

// ────────────────────────────────────────────────────────────────────────────────
/* Static-only subscribers */
// ────────────────────────────────────────────────────────────────────────────────

private class StaticSimpleSubscriber {
    companion object {
        var received: StaticSimpleEvent? = null

        @JvmStatic
        @EventHandler
        private fun onStatic(event: StaticSimpleEvent) {
            received = event
        }

        fun reset() {
            received = null
        }
    }
}

private class StaticPrioritySubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler(priority = 10)
        fun high(event: StaticPriorityEvent) {
            calls += "high"
        }

        @JvmStatic
        @EventHandler(priority = 0)
        fun low(event: StaticPriorityEvent) {
            calls += "low"
        }

        fun reset() {
            calls.clear()
        }
    }
}

private class StaticCancelingSubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler(priority = 10)
        fun first(event: StaticCancelableEvent) {
            calls += "first"
            event.markCanceled()
        }

        @JvmStatic
        @EventHandler(priority = 0)
        fun second(event: StaticCancelableEvent) {
            calls += "second"
        }

        fun reset() {
            calls.clear()
        }
    }
}

private class StaticHierarchySubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler
        fun onBase(event: StaticBaseEvent) {
            calls += "base"
        }

        @JvmStatic
        @EventHandler
        private fun onChild(event: StaticChildEvent) {
            calls += "child"
        }

        fun reset() {
            calls.clear()
        }
    }
}

private class StaticExceptionSubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler(priority = 10)
        fun throwing(event: StaticSimpleEvent) {
            calls += "throwing"
            throw IllegalStateException("boom")
        }

        @JvmStatic
        @EventHandler(priority = 0)
        fun after(event: StaticSimpleEvent) {
            calls += "after"
        }

        fun reset() {
            calls.clear()
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
/* Mixed static + instance subscribers */
// ────────────────────────────────────────────────────────────────────────────────

private class MixedSubscriber {
    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler(priority = 10)
        fun staticHigh(event: MixedEvent) {
            calls += "staticHigh"
        }

        @JvmStatic
        @EventHandler(priority = 0)
        fun staticLow(event: MixedEvent) {
            calls += "staticLow"
        }

        fun reset() {
            calls.clear()
        }
    }

    @EventHandler(priority = 5)
    fun instanceMid(event: MixedEvent) {
        calls += "instanceMid"
    }
}

private class CombinedSubscriber {
    companion object {
        val staticCalls: MutableList<String> = mutableListOf()

        @JvmStatic
        @EventHandler
        fun staticHandler(event: CombinedEvent) {
            staticCalls += "static"
        }

        fun resetStatic() {
            staticCalls.clear()
        }
    }

    val instanceCalls: MutableList<String> = mutableListOf()

    @EventHandler
    fun instanceHandler(event: CombinedEvent) {
        instanceCalls += "instance"
    }

    fun resetInstance() {
        instanceCalls.clear()
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Tests
// ────────────────────────────────────────────────────────────────────────────────

class StaticHandlersTest {

    @Test
    fun `static handler receives posted event using Class overload`() {
        val bus = Bus()
        val event = StaticSimpleEvent()

        StaticSimpleSubscriber.reset()

        bus.subscribeStatic(StaticSimpleSubscriber::class.java)
        bus.post(event)

        assertSame(
            event,
            StaticSimpleSubscriber.received,
            "Static handler should receive the exact event instance when registered via Class overload"
        )
    }

    @Test
    fun `static handler receives posted event using KClass overload`() {
        val bus = Bus()
        val event = StaticSimpleEvent()

        StaticSimpleSubscriber.reset()

        bus.subscribeStatic(StaticSimpleSubscriber::class)
        bus.post(event)

        assertSame(
            event,
            StaticSimpleSubscriber.received,
            "Static handler should receive the event when registered via KClass overload"
        )
    }

    @Test
    fun `unsubscribeStatic stops static handlers from receiving events`() {
        val bus = Bus()
        val event1 = StaticSimpleEvent()
        val event2 = StaticSimpleEvent()

        StaticSimpleSubscriber.reset()

        bus.subscribeStatic(StaticSimpleSubscriber::class.java)
        bus.post(event1)

        // Verify first event is delivered
        assertSame(
            event1,
            StaticSimpleSubscriber.received,
            "Static handler should receive events before unsubscribeStatic"
        )

        // Unsubscribe and verify no further delivery
        bus.unsubscribeStatic(StaticSimpleSubscriber::class.java)
        StaticSimpleSubscriber.reset()
        bus.post(event2)

        assertNull(
            StaticSimpleSubscriber.received,
            "Static handler should not receive events after unsubscribeStatic"
        )
    }

    @Test
    fun `calling subscribeStatic twice for same class does not register handlers twice`() {
        val bus = Bus()
        val event = StaticSimpleEvent()

        StaticSimpleSubscriber.reset()

        bus.subscribeStatic(StaticSimpleSubscriber::class.java)
        bus.subscribeStatic(StaticSimpleSubscriber::class.java) // should be ignored
        bus.post(event)

        // Handler should run exactly once
        assertSame(
            event,
            StaticSimpleSubscriber.received,
            "Static handler should only be invoked once even if subscribeStatic is called multiple times"
        )
    }

    @Test
    fun `static handlers are invoked in priority order`() {
        val bus = Bus()
        val event = StaticPriorityEvent()

        StaticPrioritySubscriber.reset()

        bus.subscribeStatic(StaticPrioritySubscriber::class.java)
        bus.post(event)

        assertEquals(
            listOf("high", "low"),
            StaticPrioritySubscriber.calls,
            "Static handlers should be invoked from highest to lowest priority"
        )
    }

    @Test
    fun `static handlers stop after cancellation when cancelMode is ENFORCE`() {
        val bus = Bus()
        val event = StaticCancelableEvent()

        StaticCancelingSubscriber.reset()

        bus.subscribeStatic(StaticCancelingSubscriber::class.java)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            listOf("first"),
            StaticCancelingSubscriber.calls,
            "Lower-priority static handler should not run once event is canceled with cancelMode=ENFORCE"
        )
        assertTrue(event.canceled, "Static cancelable event should be marked as canceled")
    }

    @Test
    fun `static handlers ignore cancellation when cancelMode is IGNORE`() {
        val bus = Bus()
        val event = StaticCancelableEvent()

        StaticCancelingSubscriber.reset()

        bus.subscribeStatic(StaticCancelingSubscriber::class.java)
        bus.post(event, CancelMode.IGNORE)

        assertEquals(
            listOf("first", "second"),
            StaticCancelingSubscriber.calls,
            "Both static handlers should run when cancelMode=IGNORE"
        )
        assertTrue(
            event.canceled,
            "Static event is still cancelable, but cancellation is not enforced by the bus in IGNORE mode"
        )
    }

    @Test
    fun `static handlers for base and derived event types are invoked`() {
        val bus = Bus()
        val event = StaticChildEvent()

        StaticHierarchySubscriber.reset()

        bus.subscribeStatic(StaticHierarchySubscriber::class.java)
        bus.post(event)

        assertEquals(
            listOf("child", "base"),
            StaticHierarchySubscriber.calls,
            "Static child-specific handler should run before static base-event handler"
        )
    }

    @Test
    fun `exceptions in static handlers do not prevent others from running`() {
        val bus = Bus()
        val event = StaticSimpleEvent()

        StaticExceptionSubscriber.reset()

        bus.subscribeStatic(StaticExceptionSubscriber::class)
        bus.post(event)

        assertEquals(
            listOf("throwing", "after"),
            StaticExceptionSubscriber.calls,
            "Second static handler should still run even if the first throws an exception"
        )
    }

    @Test
    fun `static and instance handlers share priority ordering`() {
        val bus = Bus()
        val subscriber = MixedSubscriber()
        val event = MixedEvent()

        MixedSubscriber.reset()

        bus.subscribe(subscriber)
        bus.subscribeStatic(MixedSubscriber::class.java)
        bus.post(event)

        assertEquals(
            listOf("staticHigh", "instanceMid", "staticLow"),
            MixedSubscriber.calls,
            "Static and instance handlers should be interleaved according to priority"
        )
    }

    @Test
    fun `unsubscribeStatic does not affect instance handlers`() {
        val bus = Bus()
        val subscriber = CombinedSubscriber()
        val event = CombinedEvent()

        CombinedSubscriber.resetStatic()
        subscriber.resetInstance()

        bus.subscribe(subscriber)
        bus.subscribeStatic(CombinedSubscriber::class.java)

        // First dispatch: both static and instance should receive
        bus.post(event)

        assertEquals(
            listOf("static"),
            CombinedSubscriber.staticCalls,
            "Static handler should receive events before unsubscribeStatic"
        )
        assertEquals(
            listOf("instance"),
            subscriber.instanceCalls,
            "Instance handler should also receive events before unsubscribeStatic"
        )

        // Unsubscribe static handlers
        bus.unsubscribeStatic(CombinedSubscriber::class.java)
        CombinedSubscriber.resetStatic()
        subscriber.resetInstance()

        // Second dispatch: only instance should receive
        bus.post(event)

        assertEquals(
            emptyList<String>(),
            CombinedSubscriber.staticCalls,
            "Static handlers should no longer receive events after unsubscribeStatic"
        )
        assertEquals(
            listOf("instance"),
            subscriber.instanceCalls,
            "Instance handler should still receive events after unsubscribeStatic"
        )
    }
}
