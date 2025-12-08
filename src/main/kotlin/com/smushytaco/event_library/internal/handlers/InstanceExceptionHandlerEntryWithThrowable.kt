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

import com.smushytaco.event_library.internal.invokers.exception.InstanceExceptionInvokers
import java.lang.ref.WeakReference

/**
 * Internal representation of an instance-bound exception handler that declares
 * a throwable parameter.
 *
 * This entry corresponds to `@ExceptionHandler` methods on subscriber
 * instances with one of the following shapes:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(throwable: Throwable)`
 *
 * The subscriber instance is held via a [WeakReference], allowing it to be
 * garbage-collected without requiring an explicit unsubscribe call. When the
 * reference clears, the entry is treated as invalid and can be removed during
 * cache maintenance.
 *
 * The [throwableType] property records the declared throwable parameter type
 * (e.g. `IOException`, `RuntimeException`, `Throwable`). During dispatch, the
 * event system only invokes this handler when:
 *
 *  - the event type is compatible (based on the event-method cache key and
 *    hierarchy traversal), and
 *  - `throwableType.isInstance(thrown)` is `true`.
 *
 * @property target a weak reference to the subscriber instance that owns
 *                  the exception handler method.
 * @property invoker the compiled [InstanceExceptionInvokers] strategy used to
 *                   invoke the handler on [target].
 * @property priority execution ordering hint; higher values are invoked before
 *                    lower values when multiple handlers match the same failure.
 * @property throwableType the declared throwable parameter type used to filter
 *                         which exceptions this handler should receive.
 */
internal data class InstanceExceptionHandlerEntryWithThrowable(
    val target: WeakReference<Any>,
    override val invoker: InstanceExceptionInvokers,
    override val priority: Int,
    override val throwableType: Class<out Throwable>
) : ExceptionHandlerEntryWithThrowable
