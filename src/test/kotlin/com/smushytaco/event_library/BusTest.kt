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

private class SimpleEvent : Event

private class CancelableEvent : Event, Cancelable by Cancelable()

private open class BaseEvent : Event
private class ChildEvent : BaseEvent()

private class SimpleSubscriber {
    var received: SimpleEvent? = null

    @EventHandler
    private fun onSimple(event: SimpleEvent) {
        received = event
    }
}

private class UnsubscribeSubscriber {
    var count = 0

    @EventHandler
    fun onSimple(event: SimpleEvent) {
        count++
    }
}

private class PrioritySubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 10)
    fun high(event: SimpleEvent) {
        calls += "high"
    }

    @EventHandler(priority = 0)
    fun low(event: SimpleEvent) {
        calls += "low"
    }
}

private class CancelingSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 10)
    fun first(event: CancelableEvent) {
        calls += "first"
        event.markCanceled()
    }

    @EventHandler(priority = 0)
    fun second(event: CancelableEvent) {
        calls += "second"
    }
}

private class HierarchySubscriber {
    val calls = mutableListOf<String>()

    @EventHandler
    fun onBase(event: BaseEvent) {
        calls += "base"
    }

    @EventHandler
    fun onChild(event: ChildEvent) {
        calls += "child"
    }
}

private class ExceptionSubscriber {
    val calls = mutableListOf<String>()

    @EventHandler(priority = 10)
    fun throwing(event: SimpleEvent) {
        calls += "throwing"
        throw IllegalStateException("boom")
    }

    @EventHandler(priority = 0)
    fun after(event: SimpleEvent) {
        calls += "after"
    }
}

class BusTest {

    @Test
    fun `subscriber receives posted event`() {
        val bus = Bus()
        val subscriber = SimpleSubscriber()
        val event = SimpleEvent()

        bus.subscribe(subscriber)
        bus.post(event)

        assertSame(event, subscriber.received, "Subscriber should receive the exact event instance")
    }

    @Test
    fun `unsubscribe stops receiving events`() {
        val bus = Bus()
        val subscriber = UnsubscribeSubscriber()
        val event = SimpleEvent()

        bus.subscribe(subscriber)
        bus.post(event)
        bus.unsubscribe(subscriber)
        bus.post(event)

        assertEquals(1, subscriber.count, "Subscriber should only receive events before unsubscribe")
    }

    @Test
    fun `handlers are invoked in priority order`() {
        val bus = Bus()
        val subscriber = PrioritySubscriber()
        val event = SimpleEvent()

        bus.subscribe(subscriber)
        bus.post(event)

        assertEquals(
            listOf("high", "low"),
            subscriber.calls,
            "Handlers should be invoked from highest to lowest priority"
        )
    }

    @Test
    fun `cancellation stops further handlers when cancelMode is ENFORCE`() {
        val bus = Bus()
        val subscriber = CancelingSubscriber()
        val event = CancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.ENFORCE)

        assertEquals(
            listOf("first"),
            subscriber.calls,
            "Lower-priority handler should not run once event is canceled with cancelMode=ENFORCE"
        )
        assertTrue(event.canceled, "Event should be marked as canceled")
    }

    @Test
    fun `cancellation is ignored when cancelMode is IGNORE`() {
        val bus = Bus()
        val subscriber = CancelingSubscriber()
        val event = CancelableEvent()

        bus.subscribe(subscriber)
        bus.post(event, CancelMode.IGNORE)

        assertEquals(
            listOf("first", "second"),
            subscriber.calls,
            "Both handlers should run when cancelMode=IGNORE"
        )
        assertTrue(
            event.canceled,
            "Event is still cancelable, but cancellation is not enforced by the bus in IGNORE mode"
        )
    }

    @Test
    fun `handlers for base and derived event types are invoked`() {
        val bus = Bus()
        val subscriber = HierarchySubscriber()
        val event = ChildEvent()

        bus.subscribe(subscriber)
        bus.post(event)

        assertEquals(
            listOf("child", "base"),
            subscriber.calls,
            "Child-specific handler should run before base-event handler"
        )
    }

    @Test
    fun `exceptions in one handler do not prevent others from running`() {
        val bus = Bus()
        val subscriber = ExceptionSubscriber()
        val event = SimpleEvent()

        bus.subscribe(subscriber)
        bus.post(event)

        assertEquals(
            listOf("throwing", "after"),
            subscriber.calls,
            "Second handler should still run even if the first throws an exception"
        )
    }
}
