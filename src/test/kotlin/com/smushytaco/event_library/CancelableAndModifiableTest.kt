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

package com.smushytaco.event_library

import com.smushytaco.event_library.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

private class CompositeEvent :
    Event,
    Cancelable by Cancelable(),
    Modifiable by Modifiable()

class CancelableAndModifiableTest {

    @Test
    fun `Cancelable defaults to not canceled and can be canceled`() {
        val cancelable = Cancelable()

        assertFalse(cancelable.canceled, "New Cancelable should not be canceled by default")
        cancelable.markCanceled()
        assertTrue(cancelable.canceled, "Cancelable should be marked as canceled after markCanceled()")
    }

    @Test
    fun `Modifiable defaults to not modified and can be marked modified`() {
        val modifiable = Modifiable()

        assertFalse(modifiable.modified, "New Modifiable should not be modified by default")
        modifiable.markModified()
        assertTrue(modifiable.modified, "Modifiable should be marked as modified after markModified()")
    }

    @Test
    fun `event can delegate to Cancelable and Modifiable`() {
        val event = CompositeEvent()

        // Initially neither canceled nor modified
        assertFalse(event.canceled, "Delegated Cancelable should start as not canceled")
        assertFalse(event.modified, "Delegated Modifiable should start as not modified")

        event.markCanceled()
        event.markModified()

        assertTrue(event.canceled, "Delegated Cancelable should reflect canceled state")
        assertTrue(event.modified, "Delegated Modifiable should reflect modified state")
    }
}
