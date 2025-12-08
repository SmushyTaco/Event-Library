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

package com.smushytaco.event_library.internal.invokers.event

import com.smushytaco.event_library.api.Event

/**
 * Invocation strategy for instance-based event handler methods.
 *
 * An [InstanceEventInvoker] represents a compiled or reflective call target
 * for a handler method that operates on a specific subscriber instance.
 * It is typically created by the event system using [LambdaMetafactory][java.lang.invoke.LambdaMetafactory]
 * for optimal performance, with reflective fallback used when lambda
 * generation fails or is not permitted.
 */
internal fun interface InstanceEventInvoker : EventInvoker {
    /**
     * Invokes the underlying instance handler method.
     *
     * @param target the subscriber object on which the handler method is invoked.
     * @param event the event instance being dispatched to the handler.
     */
    operator fun invoke(target: Any, event: Event)
}
