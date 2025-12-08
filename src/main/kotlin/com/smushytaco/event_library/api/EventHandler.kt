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

/**
 * Marks a function as an event handler that should be invoked when an event
 * of the appropriate type is posted to a [Bus].
 *
 * A valid event handler method must:
 *
 * - Be annotated with [EventHandler].
 * - Accept exactly one parameter whose type implements [Event].
 * - Have a `void`/`Unit` return type.
 *
 * Handlers may be either instance methods (discovered via [Bus.subscribe]) or
 * static / `@JvmStatic` methods (discovered via [Bus.subscribeStatic]).
 *
 * When an event is posted, all matching handlers are invoked in order of
 * descending [priority].
 *
 * @property priority Determines the execution order when multiple handlers
 *        receive the same event. Higher values run **earlier**.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(
    val priority: Int = 0
)
