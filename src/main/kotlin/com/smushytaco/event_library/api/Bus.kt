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

import com.smushytaco.event_library.api.Bus.Companion.invoke
import com.smushytaco.event_library.internal.EventManager
import kotlin.reflect.KClass

/**
 * Represents an event bus capable of registering subscribers and dispatching events.
 *
 * Implementations are responsible for:
 * - Scanning subscriber **objects** and **classes** for functions annotated with
 *   [EventHandler] and [ExceptionHandler].
 * - Routing posted [Event] instances to all matching event handlers.
 * - Routing exceptions thrown by handlers to matching `@ExceptionHandler` methods.
 * - Respecting event cancellation when applicable.
 *
 * A default implementation is provided via the companion object's [invoke] operator.
 * Library users normally obtain a bus instance by calling `Bus()` rather than
 * referencing the internal implementation directly.
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
     * Registers an object as a subscriber.
     *
     * All functions on [any] annotated with [EventHandler] or [ExceptionHandler]
     * and having a supported signature are discovered and wired into the bus:
     *
     * - `@EventHandler` methods must accept exactly one parameter whose type
     *   implements [Event] and return `void`/`Unit`.
     * - `@ExceptionHandler` methods may use any of the supported exception-handler
     *   shapes documented on [ExceptionHandler].
     *
     * The subscriber is held via a weak reference, so both its event handlers and
     * exception handlers are automatically removed when the object becomes
     * unreachable and is garbage-collected.
     *
     * Calling [subscribe] for the same instance more than once is a no-op.
     *
     * @param any the object containing handler methods.
     */
    fun subscribe(any: Any)
    /**
     * Unregisters an object from the bus.
     *
     * All event handlers and exception handlers previously discovered for [any]
     * during [subscribe] are removed. If the object was not registered (or has
     * already been garbage-collected and cleaned up), this operation has no effect.
     *
     * @param any the subscriber to remove.
     */
    fun unsubscribe(any: Any)
    /**
     * Registers a class containing **static handler methods**.
     *
     * All `static` (or `@JvmStatic`) functions on [type] annotated with
     * [EventHandler] or [ExceptionHandler] and having a supported signature are
     * discovered and registered automatically.
     *
     * Unlike instance-based subscription:
     * - Static handlers do not require an object instance.
     * - Their lifetime persists until explicitly unregistered via [unsubscribeStatic].
     * - They are not subject to weak-reference cleanup.
     *
     * Calling [subscribeStatic] more than once for the same class is a no-op.
     *
     * @param type the class containing static handler methods to register.
     */
    fun subscribeStatic(type: Class<*>)
    /**
     * Registers a class containing **static handler methods**.
     *
     * This is a convenience overload for Kotlin callers that forwards to
     * [subscribeStatic] with [KClass.java].
     *
     * @param type the Kotlin class whose static handler methods should be registered.
     */
    fun subscribeStatic(type: KClass<*>) = subscribeStatic(type.java)
    /**
     * Unregisters all static handlers declared for the given class.
     *
     * Both `@EventHandler` and `@ExceptionHandler` methods that were previously
     * registered via [subscribeStatic] are removed. If [type] was not registered,
     * this operation has no effect.
     *
     * @param type the class whose static handler methods should be unregistered.
     */
    fun unsubscribeStatic(type: Class<*>)
    /**
     * Unregisters all static handlers declared for the given Kotlin class.
     *
     * Convenience overload that forwards to [unsubscribeStatic] with
     * [KClass.java].
     *
     * @param type the Kotlin class whose static handler methods should be unregistered.
     */
    fun unsubscribeStatic(type: KClass<*>) = unsubscribeStatic(type.java)
    /**
     * Posts an [event] to all matching event handlers registered on this [Bus].
     *
     * Handlers are invoked in descending [EventHandler.priority] order. If a handler
     * throws, the failure is routed to any matching `@ExceptionHandler` methods;
     * dispatch then continues unless an exception handler itself throws.
     *
     * ## Cancellation Semantics
     *
     * If the event implements [Cancelable], its cancellation state is interpreted
     * according to the supplied [cancelMode]:
     *
     * - **[CancelMode.IGNORE]**
     *   Cancellation is completely disregarded. All handlers run in normal priority
     *   order, regardless of whether the event has been marked as canceled.
     *
     * - **[CancelMode.RESPECT]** *(default)*
     *   Cancellation acts as a **per-handler filter**.
     *   Canceled events are delivered *only* to handlers that explicitly opt in to
     *   receiving them via [EventHandler.runIfCanceled].
     *   Dispatch never short-circuits; all eligible handlers are invoked.
     *
     * - **[CancelMode.ENFORCE]**
     *   Cancellation acts as a **hard stop**.
     *   As soon as the event is marked canceled—either prior to posting or during
     *   handler execution—no further handlers are invoked, regardless of handler
     *   preferences.
     *
     * ## Exception Handling
     *
     * If an event handler throws, exception dispatch follows the rules defined by
     * `@ExceptionHandler`:
     *
     * - matching handlers are invoked in descending priority and specificity,
     * - if no handler processes the throwable:
     *     - `Exception` (and subclasses) are logged and swallowed,
     *     - non-`Exception` throwables (e.g., `Error`) are rethrown.
     *
     * Exceptions thrown *by exception handlers themselves* are not re-routed and will
     * propagate out of this function.
     *
     * @param event the event instance to dispatch.
     * @param cancelMode determines how cancellation should influence handler delivery.
     */
    fun post(event: Event, cancelMode: CancelMode = CancelMode.RESPECT)
}
