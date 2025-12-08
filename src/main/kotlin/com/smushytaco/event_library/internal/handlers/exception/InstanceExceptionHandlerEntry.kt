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

import com.smushytaco.event_library.internal.invokers.exception.InstanceExceptionInvokers
import java.lang.ref.WeakReference

/**
 * Internal representation of an instance-bound exception handler.
 *
 * An [InstanceExceptionHandlerEntry] binds a discovered `@ExceptionHandler`
 * method to a specific subscriber object. The subscriber is held via a
 * [WeakReference], allowing it to be garbage-collected without requiring an
 * explicit unsubscribe call. When the target is collected, the entry is
 * treated as invalid and can be pruned during normal cache maintenance.
 *
 * @property target a weak reference to the subscriber instance that owns
 *                  the exception handler method.
 * @property invoker the compiled [InstanceExceptionInvokers] strategy used
 *                   to invoke the handler on [target].
 * @property priority execution ordering hint; higher values are invoked
 *                    before lower values when multiple handlers match the
 *                    same failure.
 */
internal data class InstanceExceptionHandlerEntry(
    val target: WeakReference<Any>,
    override val invoker: InstanceExceptionInvokers,
    override val priority: Int
) : ExceptionHandlerEntry
