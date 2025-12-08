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

import com.smushytaco.event_library.internal.invokers.event.InstanceEventInvoker
import java.lang.ref.WeakReference

/**
 * Internal representation of an instance-based event handler.
 *
 * An [InstanceEventHandlerEntry] binds a discovered handler method to a specific
 * subscriber object. The subscriber is referenced via a [WeakReference],
 * allowing it to be garbage-collected without requiring an explicit
 * unsubscribe call. When the target is collected, the handler is
 * automatically pruned during normal event dispatch or cache maintenance.
 *
 * @property target a weak reference to the subscriber object that owns
 *                  the handler method. If this reference is cleared, the
 *                  handler is treated as invalid and skipped.
 * @property invoker a precompiled [InstanceEventInvoker] that performs the
 *                   actual call to the instance handler method on [target].
 * @property priority execution ordering hint used when multiple handlers
 *                    listen to the same event type; higher values are invoked first.
 */
internal data class InstanceEventHandlerEntry(
    val target: WeakReference<Any>,
    override val invoker: InstanceEventInvoker,
    override val priority: Int
) : EventHandlerEntry
