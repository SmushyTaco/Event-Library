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
 * Marker interface for all compiled exception-handler invocation strategies.
 *
 * Each method annotated with `@ExceptionHandler` is analyzed at subscription
 * time and converted into a concrete invoker that efficiently calls the
 * method. A handler's method signature determines which concrete invoker is
 * used:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(event: Event)`
 *  - `(throwable: Throwable)`
 *
 * Matching of event and throwable types is **polymorphic**: a handler whose
 * parameter type is a supertype of the actual event or exception will be
 * considered compatible.
 *
 * Invokers encapsulate the [LambdaMetafactory][java.lang.invoke.LambdaMetafactory] or reflective fallback logic
 * required to invoke the handler during exception dispatch.
 */
internal sealed interface ExceptionInvoker
