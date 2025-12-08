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

/**
 * Marker base type for exception handler entries that declare a throwable
 * parameter in their method signature.
 *
 * This is implemented by:
 *
 *  - [InstanceExceptionHandlerEntryWithThrowable] for instance-bound handlers.
 *  - [StaticExceptionHandlerEntryWithThrowable] for static handlers.
 *
 * Handlers represented by this type correspond to `@ExceptionHandler` methods
 * with one of the following shapes:
 *
 *  - `(event: Event, throwable: Throwable)`
 *  - `(throwable: Throwable)`
 *
 * The [throwableType] records the **declared** throwable parameter type
 * (e.g. `IOException`, `NullPointerException`, `Throwable`). At dispatch time,
 * the event system uses this to perform polymorphic matching:
 *
 *  - a handler is considered compatible if `throwableType.isInstance(actualThrowable)` is `true`.
 *
 * Handlers without a throwable parameter (i.e. `(event: Event)` signatures)
 * are represented by plain [ExceptionHandlerEntry] instead.
 *
 * @property throwableType the concrete throwable type declared on the handler
 *                         method. Used for filtering based on the actual
 *                         thrown exception.
 */
internal sealed interface ExceptionHandlerEntryWithThrowable: ExceptionHandlerEntry {
    val throwableType: Class<out Throwable>
}
