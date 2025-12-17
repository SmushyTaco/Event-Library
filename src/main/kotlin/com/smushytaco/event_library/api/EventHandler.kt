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

package com.smushytaco.event_library.api

import com.smushytaco.event_library.api.Bus.Companion.subscribeStatic

/**
 * Marks a function as an event handler that should be invoked when an event of the
 * appropriate type is posted to a [Bus].
 *
 * A valid event handler method must:
 *
 * - Be annotated with [EventHandler].
 * - Accept exactly **one** parameter whose type implements [Event].
 * - Have a `void`/`Unit` return type.
 *
 * Handlers may be:
 *
 * - **instance methods**, discovered via [Bus.subscribe], or
 * - **static methods** (`static` in Java or `@JvmStatic` in Kotlin), discovered via
 *   [Bus.subscribeStatic].
 *
 * ## Invocation Order
 *
 * When an event is posted, all matching handlers are invoked in order of descending
 * [priority]. Handlers with the same priority execute in registration order.
 *
 * ## Cancellation Interaction
 *
 * If the event implements [Cancelable], delivery of the event to this handler is
 * influenced by:
 *
 * 1. The [EventHandler.runIfCanceled] flag, **and**
 * 2. The [CancelMode] supplied to [Bus.post].
 *
 * ### `runIfCanceled`
 *
 * When `true`, this handler **will still receive** the event even if it has been
 * canceled — unless the dispatch mode is [CancelMode.ENFORCE], in which case no
 * additional handlers run after cancellation.
 *
 * When `false` (default), this handler:
 *
 * - receives the event normally when it is not canceled,
 * - receives the event *only if* the current [CancelMode] is:
 *     - [CancelMode.IGNORE], or
 *     - [CancelMode.RESPECT] **and** the event is not canceled.
 *
 * @property priority
 * Determines the execution order when multiple handlers receive the same event.
 * Higher values run **earlier**.
 *
 * @property runIfCanceled
 * Whether this handler should receive events that have already been marked as
 * canceled.
 * - When `true`, the handler receives canceled events except when cancellation is
 *   strictly enforced via [CancelMode.ENFORCE].
 * - When `false`, the handler receives canceled events only when cancellation is
 *   being ignored by the dispatch mode.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(
    val priority: Int = 0,
    val runIfCanceled: Boolean = false
)
