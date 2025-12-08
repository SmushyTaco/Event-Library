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
 * Common contract for internal handler descriptors that participate in
 * priority-based dispatch.
 *
 * Both event handlers and exception handlers implement [Priority] so they
 * can be sorted and executed according to their relative importance.
 * Higher [priority] values indicate that a handler should run earlier than
 * those with lower values.
 *
 * This interface is internal to the event system and is not exposed through
 * the public API.
 *
 * @property priority execution ordering hint; larger values are dispatched first.
 */
internal sealed interface Priority {
    val priority: Int
}
