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

package com.smushytaco.event_library.internal.invokers.exception

/**
 * Sealed marker interface for all static exception invokers.
 *
 * Implementations of this interface represent compiled call sites for static
 * `@ExceptionHandler` methods, including the supported shapes:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(event: Event)`
 *  - `(throwable: Throwable)`
 *
 * Static exception handlers are typically registered via `subscribeStatic`
 * and are not tied to any particular subscriber instance. The sealed
 * hierarchy allows the dispatcher to treat all static exception invokers
 * uniformly while still enabling precise pattern matching when needed.
 */
internal sealed interface StaticExceptionInvokers: ExceptionInvoker
