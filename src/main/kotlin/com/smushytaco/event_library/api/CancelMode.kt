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
 * Determines how a [Bus] should interpret cancellation when dispatching events
 * that implement [Cancelable].
 *
 * A cancel mode is supplied to [Bus.post] to control whether event cancellation:
 *
 * - is **ignored entirely**,
 * - is **respected on a per-handler basis**, or
 * - should **terminate dispatch immediately**.
 *
 * The mode applies only to event-handler invocation; exception-handler dispatch
 * is unaffected.
 *
 * ## Summary of Modes
 *
 * | Mode      | Handler Runs When Event Is Canceled?                          | Stops Pipeline? |
 * |-----------|---------------------------------------------------------------|------------------|
 * | [IGNORE]  | **Yes**, all handlers always run                              | Never            |
 * | [RESPECT] | Only handlers that explicitly opt-in to receiving canceled events | Never        |
 * | [ENFORCE] | No; dispatch halts immediately on first cancellation          | Yes              |
 *
 * This design allows the caller to choose between:
 *
 * - simple "fire every handler" behavior,
 * - fine-grained handler-level control over canceled events, and
 * - strict short-circuit semantics in which cancellation acts as a hard stop.
 */
enum class CancelMode {
    /**
     * Cancellation is **completely ignored**.
     *
     * When this mode is used:
     *
     * - All handlers run in their normal priority order.
     * - Event cancellation flags are never checked.
     * - Handler-level cancellation semantics (e.g., `runIfCanceled`) are ignored.
     *
     * Use this mode when cancellation is meaningful only to your own logic and
     * should not influence the event-dispatch pipeline.
     */
    IGNORE,

    /**
     * Cancellation is **respected on a per-handler basis**, but **does not**
     * stop the event-dispatch pipeline.
     *
     * In this mode:
     *
     * - If the event is not canceled, all handlers run.
     * - If the event *is* canceled, handlers are invoked only if they explicitly
     *   allow receiving canceled events via [EventHandler.runIfCanceled].
     */
    RESPECT,

    /**
     * Cancellation is **enforced as a hard stop**.
     *
     * As soon as the event becomes canceled:
     *
     * - No further handlers are invoked.
     * - Handler-specific cancellation preferences (e.g., `runIfCanceled`) are
     *   ignored after cancellation occurs.
     *
     * This mode supports strict, short-circuit semantics where cancellation is
     * intended to abort further processing entirely.
     */
    ENFORCE
}

