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

package com.smushytaco.event_library.internal.event_invokers

/**
 * Sealed marker interface for all event invocation strategies.
 *
 * The event system compiles or constructs concrete invokers that know how
 * to call specific handler methods. Two primary specializations exist:
 *
 * - [InstanceEventInvoker] for handlers that require a receiver instance.
 * - [StaticEventInvoker] for handlers implemented as static methods.
 *
 * The sealed hierarchy allows the dispatcher to pattern-match on the
 * invoker type when performing calls, while still treating all invokers
 * uniformly at the type level.
 *
 * This interface is internal and not exposed through the public API.
 */
internal sealed interface EventInvoker
