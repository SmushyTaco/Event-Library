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

import com.smushytaco.event_library.api.Event

/**
 * Internal functional interface used to invoke an event handler method on a target object.
 *
 * [EventManager] uses [LambdaMetafactory][java.lang.invoke.LambdaMetafactory]
 * for performance with a reflective fallback when necessary.
 *
 * The invoker encapsulates the actual method call, allowing event dispatch to avoid
 * reflective overhead during normal operation.
 *
 * @receiver the compiled invoker instance that will call the handler method.
 */
internal fun interface EventInvoker {
    /**
     * Invokes the underlying event handler method on the provided [target].
     *
     * @param target the subscriber instance receiving the event.
     * @param event the event to be delivered to the handler method.
     */
    operator fun invoke(target: Any, event: Event)
}
