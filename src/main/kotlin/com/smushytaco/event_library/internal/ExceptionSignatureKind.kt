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

package com.smushytaco.event_library.internal

import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.internal.ExceptionSignatureKind.Companion.exceptionSignatureKind
import java.lang.reflect.Method

/**
 * Enumerates the supported method signatures for `@ExceptionHandler` methods.
 *
 * Each enum entry corresponds to one of the valid shapes an exception
 * handler method may take. Signatures are matched polymorphically: a
 * parameter typed as a supertype of the actual event or exception is still
 * considered compatible.
 */
internal enum class ExceptionSignatureKind {
    /**
     * Represents an exception handler method with two parameters:
     *
     *     fun onFailure(event: E, throwable: T)
     *
     * The first parameter must be an `Event` (or subtype), and the second
     * must be a `Throwable` (or subtype).
     *
     * This is the **most specific** form of exception handler. It is invoked
     * only when *both* the event and the thrown exception match the declared
     * parameter types.
     */
    EVENT_AND_THROWABLE,
    /**
     * Represents an exception handler method with a single event parameter:
     *
     *     fun onFailure(event: E)
     *
     * The parameter must be an `Event` (or subtype).
     *
     * These handlers are invoked whenever an exception occurs while
     * processing any event of the declared type, regardless of what
     * exception was thrown.
     */
    EVENT_ONLY,
    /**
     * Represents an exception handler method with a single throwable parameter:
     *
     *     fun onFailure(throwable: T)
     *
     * The parameter must be a `Throwable` (or subtype).
     *
     * These handlers match purely on exception type and are invoked for
     * any event when the thrown exception is compatible with the declared
     * throwable parameter.
     */
    THROWABLE_ONLY;
    /**
     * Resolves the logical event type associated with this exception-handler signature.
     *
     * For signatures that declare an event parameter:
     *
     *  - [EVENT_AND_THROWABLE] — `(event: Event, throwable: Throwable)`
     *  - [EVENT_ONLY]         — `(event: Event)`
     *
     * this method returns the runtime class of the event parameter (`method.parameterTypes[0]`)
     * cast to `Class<out Event>`.
     *
     * For [THROWABLE_ONLY] handlers — `(throwable: Throwable)` — there is no explicit
     * event parameter, so this method returns [Event] as the key under which the handler
     * should be registered. This allows such handlers to be treated as applicable to
     * **all** events, with polymorphic matching still applied at dispatch time.
     *
     * The resulting class is used as the key into exception-handler caches
     * (e.g. `exceptionMethodCache`) and participates in the normal event-type
     * hierarchy traversal performed by the dispatcher.
     *
     * @param method the reflective method whose signature is being interpreted.
     * @return the event type under which handlers of this signature kind should be registered.
     */
    fun getEventClass(method: Method): Class<out Event> = when (this) {
        EVENT_AND_THROWABLE, EVENT_ONLY ->
            @Suppress("UNCHECKED_CAST")
            method.parameterTypes[0] as Class<out Event>
        THROWABLE_ONLY -> Event::class.java
    }
    /**
     * Extracts the declared throwable parameter type from an exception–handler method,
     * when applicable.
     *
     * Exception handlers may declare one of the following supported signatures:
     *
     *  - `(event: Event, throwable: Throwable)` → [EVENT_AND_THROWABLE]
     *  - `(throwable: Throwable)`               → [THROWABLE_ONLY]
     *  - `(event: Event)`                       → [EVENT_ONLY]
     *
     * This function inspects the reflective [method] and returns:
     *
     *  - the method’s **second parameter** (the throwable type) for
     *    [EVENT_AND_THROWABLE],
     *  - the method’s **single parameter** for [THROWABLE_ONLY],
     *  - `null` for [EVENT_ONLY], since these handlers do not declare a throwable.
     *
     * The returned type is always a `Class<out Throwable>`, and is used during
     * exception dispatch to determine whether a handler is compatible with a
     * particular thrown exception:
     *
     * ```
     * handler.throwableType.isInstance(actualThrowable)
     * ```
     *
     * @param method the reflective method from which to extract the throwable type.
     * @return the declared throwable parameter type, or `null` if this signature
     *         kind does not include a throwable.
     */
    fun getThrowableType(method: Method): Class<out Throwable>? = when (this) {
            EVENT_AND_THROWABLE, THROWABLE_ONLY ->
                @Suppress("UNCHECKED_CAST")
                method.parameterTypes.last() as Class<out Throwable>
            EVENT_ONLY -> null
        }
    /**
     * Companion object providing the extension that inspects a reflective
     * [Method] and determines whether it represents a supported exception handler
     * signature.
     *
     * The extension property [Method.exceptionSignatureKind] analyzes:
     *
     *  - the number of parameters,
     *  - their types (checking for `Event` and `Throwable`),
     *  - and their ordering.
     *
     * If the method matches one of the supported shapes, a corresponding
     * [ExceptionSignatureKind] value is returned; otherwise `null` indicates
     * that the signature is not a valid exception handler.
     */
    companion object {
        /**
         * Determines which [ExceptionSignatureKind], if any, the receiver method
         * conforms to.
         *
         * A method annotated with `@ExceptionHandler` is only considered valid if
         * it matches one of the expected shapes:
         *
         *  - `(event: Event)` → [EVENT_ONLY]
         *  - `(throwable: Throwable)` → [THROWABLE_ONLY]
         *  - `(event: Event, throwable: Throwable)` → [EVENT_AND_THROWABLE]
         *
         * Parameter matching is **polymorphic**: a parameter typed as a supertype
         * of the actual event or exception (e.g. `Event`, `Throwable`, `Exception`)
         * is still considered compatible.
         *
         * @receiver the reflective [Method] being inspected.
         * @return the recognized signature kind, or `null` if the method does not
         *         match any supported exception-handler shape.
         */
        val Method.exceptionSignatureKind
            get(): ExceptionSignatureKind? {
                val params = parameterTypes
                return when (params.size) {
                    1 -> {
                        val p0 = params[0]
                        when {
                            Event::class.java.isAssignableFrom(p0) -> EVENT_ONLY
                            Throwable::class.java.isAssignableFrom(p0) -> THROWABLE_ONLY
                            else -> null
                        }
                    }

                    2 -> {
                        val p0 = params[0]
                        val p1 = params[1]
                        if (Event::class.java.isAssignableFrom(p0) && Throwable::class.java.isAssignableFrom(p1)) {
                            EVENT_AND_THROWABLE
                        } else {
                            null
                        }
                    }

                    else -> null
                }
            }
    }
}
