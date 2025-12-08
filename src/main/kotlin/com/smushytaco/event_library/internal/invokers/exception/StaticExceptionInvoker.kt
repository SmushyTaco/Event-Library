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

package com.smushytaco.event_library.internal.invokers.exception

import com.smushytaco.event_library.api.Event

/**
 * Invoker for static exception handler methods that declare both an event and
 * a throwable parameter:
 *
 *     fun onFailure(event: E, t: T)
 *
 * This handler is selected when the event being processed is an instance of
 * `E` (or any subtype) and the thrown exception is an instance of `T`
 * (or any subtype).
 *
 * Static exception handlers behave analogously to static event handlers and
 * are registered via `subscribeStatic`.
 */
internal fun interface StaticExceptionInvoker : StaticExceptionInvokers {
    /**
     * Invokes the static exception handler method.
     *
     * @param event the event being processed when the exception occurred.
     * @param throwable the exception thrown by the event handler.
     */
    operator fun invoke(event: Event, throwable: Throwable)
}
