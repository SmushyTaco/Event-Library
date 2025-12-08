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
 * Invoker for instance-bound exception handler methods that declare only
 * a throwable parameter:
 *
 *     fun onFailure(t: T)
 *
 * A handler using this signature is selected when the thrown exception is
 * an instance of `T` **or any subtype**, regardless of which event was being
 * processed at the time.
 *
 * These handlers are useful for global or type-specific exception handling,
 * such as catching all `IOException` instances across all event types.
 */
internal fun interface InstanceExceptionThrowableOnlyInvoker : InstanceExceptionInvokers {
    /**
     * Invokes the exception handler method.
     *
     * @param target the subscriber instance declaring the handler.
     * @param throwable the exception thrown by an event handler.
     */
    operator fun invoke(target: Any, throwable: Throwable)
}
