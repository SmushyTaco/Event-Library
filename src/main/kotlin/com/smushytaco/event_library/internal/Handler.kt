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

import java.lang.ref.WeakReference

/**
 * Internal representation of a registered event handler method.
 *
 * A [Handler] links:
 * - the subscriber instance (held via a [WeakReference] so it can be garbage-collected),
 * - the compiled [EventInvoker] used to call the handler method,
 * - and the handler's execution [priority].
 *
 * Instances of this class are created during subscription and stored in
 * internal handler lists used by the event dispatch system.
 *
 * @property target a weak reference to the subscriber object containing the handler method.
 *                  When the subscriber is garbage-collected, this handler becomes invalid
 *                  and is automatically removed during dispatch or unsubscribe operations.
 * @property invoker a functional wrapper that performs the actual method invocation for the handler.
 *                   Implementations are typically generated via `LambdaMetafactory` for speed.
 * @property priority determines execution order relative to other handlers for the same event type.
 *                    Higher values run earlier during dispatch.
 */
internal data class Handler(val target: WeakReference<Any>, val invoker: EventInvoker, val priority: Int)
