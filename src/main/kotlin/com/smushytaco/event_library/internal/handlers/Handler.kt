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

package com.smushytaco.event_library.internal.handlers

import com.smushytaco.event_library.internal.event_invokers.EventInvoker

/**
 * Sealed base type for all registered event handlers.
 *
 * Concrete implementations distinguish between:
 * - [InstanceHandler] — handlers bound to a specific subscriber instance.
 * - [StaticHandler] — handlers backed by static methods on a class.
 *
 * Each handler wraps an [EventInvoker] that knows how to invoke the
 * underlying method, along with a [priority] value used to order execution
 * when multiple handlers are applicable for a given event.
 *
 * Implementations are internal to the event system and are not exposed
 * through the public API.
 *
 * @property invoker strategy object responsible for invoking the underlying handler with the appropriate arguments.
 * @property priority execution ordering hint; higher values are dispatched earlier.
 */
internal sealed interface Handler {
    val invoker: EventInvoker
    val priority: Int
}
