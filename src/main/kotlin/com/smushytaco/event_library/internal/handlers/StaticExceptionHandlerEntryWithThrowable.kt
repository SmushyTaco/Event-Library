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

import com.smushytaco.event_library.internal.invokers.exception.StaticExceptionInvokers

/**
 * Internal representation of a static exception handler that declares a
 * throwable parameter.
 *
 * This entry corresponds to `@ExceptionHandler` methods declared as `static`
 * (in Java) or `@JvmStatic` (in Kotlin) with one of the following shapes:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(throwable: Throwable)`
 *
 * Unlike instance-bound handlers, static handlers are not tied to a specific
 * subscriber object and therefore do not use weak references. They remain
 * active until explicitly unregistered by the event bus.
 *
 * The [throwableType] property records the declared throwable parameter type
 * (e.g. `IOException`, `Exception`, `Throwable`). During dispatch, the event
 * system only invokes this handler when:
 *
 *  - the event type is compatible (based on the event-method cache key and
 *    hierarchy traversal), and
 *  - `throwableType.isInstance(thrown)` is `true`.
 *
 * @property owner the declaring class that owns the static exception handler
 *                 method. Used to group and unregister static handlers from
 *                 the same class.
 * @property invoker the compiled [StaticExceptionInvokers] strategy used to
 *                   invoke the underlying handler method.
 * @property priority execution ordering hint; higher values are invoked before
 *                    lower values when multiple handlers match the same failure.
 * @property throwableType the declared throwable parameter type used to filter
 *                         which exceptions this handler should observe.
 */
internal data class StaticExceptionHandlerEntryWithThrowable(
    val owner: Class<*>,
    override val invoker: StaticExceptionInvokers,
    override val priority: Int,
    override val throwableType: Class<out Throwable>
) : ExceptionHandlerEntryWithThrowable
