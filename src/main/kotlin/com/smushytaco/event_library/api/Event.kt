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

/**
 * Marker interface for all events dispatched through a [Bus].
 *
 * Any class implementing this interface can be posted to the event bus and
 * received by handler functions annotated with [EventHandler]. The interface
 * itself imposes no behavior; it simply identifies a type as an event
 * participating in the event system.
 *
 * Implementations are free-form and may optionally implement additional
 * interfaces such as [Cancelable] or [Modifiable] to enable extended behavior.
 */
interface Event
