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
 * Marks a function as an exception handler for failures that occur during
 * event dispatch.
 *
 * A method annotated with `@ExceptionHandler` will be invoked whenever an
 * `@EventHandler` method throws an exception while processing an event.
 * Exception handlers allow subscribers to react to failures in a structured,
 * type-safe, and prioritized manner.
 *
 * Handlers may be either instance methods (discovered via [Bus.subscribe]) or
 * static / `@JvmStatic` methods (discovered via [Bus.subscribeStatic]).
 *
 * ## Supported method signatures
 *
 * An exception handler may declare one of the following parameter shapes:
 *
 * - **`(event: E, throwable: T)`**
 *   Handles exceptions of type `T` (or its subtypes) thrown while processing
 *   an event of type `E` (or its subtypes).
 *
 * - **`(event: E)`**
 *   Handles *any* exception thrown while processing an event of type `E`.
 *
 * - **`(throwable: T)`**
 *   Handles exceptions of type `T` thrown from *any* event.
 *
 * These signatures give fine-grained control over which failures a method
 * handles, allowing handlers to be as broad or specific as needed.
 *
 * ## Priority & ordering
 *
 * When multiple exception handlers match a thrown exception, they are all
 * invoked in order of descending [priority]. Handlers with higher `priority`
 * values run before those with lower values.
 *
 * For handlers sharing the same [priority], the bus prefers more specific
 * signatures:
 *
 * 1. `(event: E, throwable: T)` — most specific.
 * 2. `(event: E)` — event-scoped catch-alls.
 * 3. `(throwable: T)` — throwable-only observers.
 *
 * @property priority the execution priority of this exception handler.
 * Higher values run earlier. Defaults to `0`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ExceptionHandler(
    val priority: Int = 0
)
