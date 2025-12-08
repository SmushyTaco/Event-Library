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

import com.smushytaco.event_library.internal.invokers.exception.ExceptionInvoker

/**
 * Base type for all registered exception handler entries.
 *
 * An [ExceptionHandlerEntry] represents a single method annotated with
 * `@ExceptionHandler` that has been discovered and compiled into an
 * [ExceptionInvoker]. Concrete implementations distinguish between:
 *
 *  - instance-bound exception handlers, and
 *  - static exception handlers.
 *
 * Exception handler entries are stored separately from normal event
 * handlers but follow the same priority-based dispatch model.
 *
 * @property invoker strategy object responsible for invoking the underlying
 *                   exception handler method when an event handler fails.
 * @property priority execution ordering hint inherited from [Priority];
 *                    handlers with higher values are invoked earlier.
 */
internal sealed interface ExceptionHandlerEntry: Priority {
    val invoker: ExceptionInvoker
}
