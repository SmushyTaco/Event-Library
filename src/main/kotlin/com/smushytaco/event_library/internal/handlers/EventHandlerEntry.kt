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

import com.smushytaco.event_library.internal.invokers.event.EventInvoker

/**
 * Base type for all registered event handler entries.
 *
 * An [EventHandlerEntry] represents a single method annotated with
 * `@EventHandler` that has been discovered and compiled into an
 * [EventInvoker]. Concrete implementations distinguish between:
 *
 *  - instance-bound handlers (tied to a particular subscriber object),
 *  - static handlers (declared as `static` / `@JvmStatic` on a class).
 *
 * The event system uses this abstraction to store, sort, and dispatch
 * event handlers without exposing implementation details to consumers.
 *
 * @property invoker strategy object responsible for invoking the underlying
 *                   event handler method.
 * @property priority execution ordering hint inherited from [Priority];
 *                    handlers with higher values run first.
 */
internal sealed interface EventHandlerEntry: Priority {
    val invoker: EventInvoker
}
