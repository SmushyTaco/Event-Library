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

import com.smushytaco.event_library.internal.CancelableImpl

/**
 * Represents an object that can be marked as canceled.
 *
 * This is typically used by events whose handlers may choose to
 * interrupt or prevent further processing. When an event implementing
 * [Cancelable] is posted with cancellation respected, event dispatching
 * halts as soon as the event becomes canceled.
 *
 * A default implementation is provided via the companion object's [invoke] operator.
 */
interface Cancelable {
    /**
     * Factory for obtaining the default [Cancelable] implementation.
     *
     * Calling `Cancelable()` creates a new [CancelableImpl] instance.
     * This avoids exposing internal implementation details to API users.
     */
    companion object {
        /**
         * Creates a new [Cancelable] using the default internal implementation.
         *
         * @return a fresh, uncanceled [Cancelable] instance.
         */
        operator fun invoke(): Cancelable = CancelableImpl()
    }
    /**
     * Indicates whether this object has been canceled.
     *
     * Event dispatchers or other processing systems may use this flag
     * to determine whether to continue or stop processing.
     */
    val canceled: Boolean
    /**
     * Marks this object as canceled.
     *
     * Once canceled, the state is irreversible for the lifetime of this instance.
     * Event systems may use this to interrupt further propagation.
     */
    fun markCanceled()
}
