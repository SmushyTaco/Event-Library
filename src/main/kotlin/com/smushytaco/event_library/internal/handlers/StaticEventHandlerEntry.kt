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

import com.smushytaco.event_library.internal.invokers.event.StaticEventInvoker

/**
 * Internal representation of a static event handler.
 *
 * A [StaticEventHandlerEntry] describes a handler method that is declared as `static`
 * (in Java) or as a `@JvmStatic` member on a Kotlin object/companion. Unlike
 * [InstanceEventHandlerEntry], it is not tied to a specific subscriber instance and
 * therefore does not rely on weak references for lifecycle management.
 *
 * Static handlers remain active until they are explicitly unregistered by
 * the event bus (e.g. via a static unsubscribe API).
 *
 * @property owner the declaring class that owns the static handler method.
 *                 This is used to group and unregister static handlers that
 *                 originate from the same class.
 * @property invoker a precompiled [StaticEventInvoker] that performs the
 *                   actual call to the underlying handler method.
 * @property priority execution ordering hint used when multiple handlers
 *                    listen to the same event type; higher values are invoked first.
 */
internal data class StaticEventHandlerEntry(
    val owner: Class<*>,
    override val invoker: StaticEventInvoker,
    override val priority: Int,
    override val runIfCanceled: Boolean
) : EventHandlerEntry
