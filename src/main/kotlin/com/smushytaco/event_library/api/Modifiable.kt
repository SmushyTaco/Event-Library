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

import com.smushytaco.event_library.api.Modifiable.Companion.invoke
import com.smushytaco.event_library.internal.ModifiableImpl

/**
 * Represents an object whose state can be marked as modified.
 *
 * This interface is commonly used by events or data carriers that wish to
 * indicate that some handler has altered their contents. Systems receiving the
 * event may use this flag to perform conditional logic—such as updating caches
 * or propagating changes only when necessary.
 *
 * A default implementation is provided through the companion object's [invoke] operator.
 */
interface Modifiable {
    /**
     * Factory for creating the default [Modifiable] implementation.
     *
     * Calling `Modifiable()` returns a new [ModifiableImpl] instance, allowing
     * users to obtain a modifiable object without referencing internal types.
     */
    companion object {
        /**
         * Creates a new [Modifiable] instance backed by the library's internal implementation.
         *
         * @return a fresh, unmodified [Modifiable] instance.
         */
        @JvmStatic
        @JvmName("create")
        operator fun invoke(): Modifiable = ModifiableImpl()
    }
    /**
     * Indicates whether this object has been marked as modified.
     *
     * The modified state is typically set by calling [markModified], and is
     * irreversible for the lifetime of the instance.
     */
    val modified: Boolean
    /**
     * Marks this object as modified.
     *
     * Once invoked, the [modified] flag becomes `true`. This can be used by
     * event handlers or other processing systems to signal that changes have occurred.
     */
    fun markModified()
}
