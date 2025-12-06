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

import com.smushytaco.event_library.api.Cancelable

/**
 * Internal implementation of the [Cancelable] interface.
 *
 * This class provides the minimal state-tracking required for cancellation,
 * storing a mutable cancellation flag that can only be set once. It is used as
 * the default instance returned by [Cancelable.invoke] and is not intended for
 * direct use outside the library.
 */
internal class CancelableImpl : Cancelable {
    override var canceled = false
        private set

    override fun markCanceled() {
        canceled = true
    }
}
