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

package com.smushytaco.event_library.api

import com.smushytaco.event_library.internal.EventManager

/**
 * Represents an event bus capable of registering subscribers and dispatching events
 * to their corresponding handler methods.
 *
 * Implementations are responsible for:
 * - Scanning subscriber objects for functions annotated with [EventHandler].
 * - Routing posted [Event] instances to all matching handlers.
 * - Respecting event cancellation when applicable.
 *
 * A default implementation is provided via the companion object's [invoke] operator.
 */
interface Bus {
    /**
     * Factory for obtaining the default [Bus] implementation.
     *
     * Calling `Bus()` creates a new underlying [EventManager] instance.
     * This is a convenience for users who want a simple entry point without
     * depending directly on internal implementation classes.
     */
    companion object {
        /**
         * Creates a new [Bus] using the default implementation.
         *
         * @return a fresh [Bus] instance backed by an internal [EventManager].
         */
        operator fun invoke(): Bus = EventManager()
    }
    /**
     * Registers an object as an event subscriber.
     *
     * All functions within the object annotated with [EventHandler] and
     * accepting exactly one parameter of type [Event] (or its subtype)
     * will be discovered and registered automatically.
     *
     * The subscriber is held via a weak reference, so it will be
     * automatically removed when garbage collected.
     *
     * @param any the object containing event handler methods.
     */
    fun subscribe(any: Any)
    /**
     * Unregisters an object from receiving events.
     *
     * All event handlers previously discovered during [subscribe] will be
     * removed. If the object was not registered, this operation has no effect.
     *
     * @param any the subscriber to remove.
     */
    fun unsubscribe(any: Any)
    /**
     * Posts an event to all matching subscribers.
     *
     * Handlers are invoked in priority order as defined by [EventHandler.priority].
     * If the event implements [Cancelable] and [respectCancels] is `true`,
     * dispatching will stop as soon as the event is cancelled.
     *
     * @param event the event instance to dispatch.
     * @param respectCancels whether the bus should stop dispatching if the event is cancelled.
     */
    fun post(event: Event, respectCancels: Boolean = false)
}
