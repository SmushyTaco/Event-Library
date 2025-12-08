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
 * Sealed marker interface for all instance-bound exception invokers.
 *
 * Implementations of this interface correspond to the different supported
 * `@ExceptionHandler` method shapes on subscriber instances, such as:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(event: Event)`
 *  - `(throwable: Throwable)`
 *
 * Each concrete invoker encapsulates the compiled call target for a specific
 * handler method, using either a [LambdaMetafactory][java.lang.invoke.LambdaMetafactory] backed implementation or
 * a reflective fallback. The event system treats these invokers uniformly at
 * the type level while still allowing dispatch code to distinguish them via
 * sealed hierarchy pattern matching when needed.
 */
internal sealed interface InstanceExceptionInvokers: ExceptionInvoker
