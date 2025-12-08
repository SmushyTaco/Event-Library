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

package com.smushytaco.event_library.internal.handlers.exception

import com.smushytaco.event_library.internal.invokers.exception.ExceptionInvoker

/**
 * Base type for all registered exception handler entries.
 *
 * An [ExceptionHandlerEntry] represents a single `@ExceptionHandler` method
 * that has been discovered and compiled into an [ExceptionInvoker]. Concrete
 * implementations distinguish between:
 *
 *  - [InstanceExceptionHandlerEntry] — handlers bound to a specific
 *    subscriber instance and referenced via a WeakReference.
 *  - [StaticExceptionHandlerEntry] — handlers backed by static methods on
 *    a class.
 *
 * Exception handler entries are stored and managed by the event system to
 * route exceptions thrown during normal event handler invocation.
 *
 * @property invoker the strategy object responsible for invoking the
 *                   underlying exception handler method.
 * @property priority execution ordering hint; entries with higher values
 *                    are dispatched earlier than those with lower values.
 */
internal sealed interface ExceptionHandlerEntry {
    val invoker: ExceptionInvoker
    val priority: Int
}
