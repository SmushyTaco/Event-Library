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

package com.smushytaco.event_library.internal

import com.smushytaco.event_library.api.Modifiable

/**
 * Internal implementation of the [Modifiable] interface.
 *
 * This class provides lightweight state tracking for objects that may be
 * marked as modified. It is used as the default implementation returned by
 * [Modifiable.invoke] and is not intended for direct use outside the library.
 */
internal class ModifiableImpl : Modifiable {
    override var modified = false
        private set

    override fun markModified() {
        modified = true
    }
}
