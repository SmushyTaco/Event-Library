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

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.smushytaco.event_library.api.*
import com.smushytaco.event_library.internal.ExceptionSignatureKind.Companion.exceptionSignatureKind
import com.smushytaco.event_library.internal.handlers.*
import com.smushytaco.event_library.internal.invokers.event.EventInvoker
import com.smushytaco.event_library.internal.invokers.event.InstanceEventInvoker
import com.smushytaco.event_library.internal.invokers.event.StaticEventInvoker
import com.smushytaco.event_library.internal.invokers.exception.*
import org.slf4j.LoggerFactory
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Internal implementation of the [Bus] interface responsible for:
 *
 * - Discovering and registering event handler methods (`@EventHandler`),
 * - Discovering and registering exception handler methods (`@ExceptionHandler`),
 * - Managing instance subscribers through weak references,
 * - Managing static subscribers registered by class,
 * - Dispatching events with respect to handler priority, cancellation and
 *   polymorphic event-type matching,
 * - Routing exceptions to matching exception handlers,
 * - Caching resolved handler lists for efficient repeated dispatch.
 *
 * This class is not exposed publicly; callers obtain a bus instance via
 * [Bus.invoke] rather than referencing this implementation directly.
 */
internal class EventManager : Bus {
    /**
     * Holds utilities and shared logger for the event system.
     */
    companion object {
        /**
         * Shared logger instance used for reporting reflection failures,
         * lambda generation issues, and other internal runtime diagnostics.
         *
         * This logger is intentionally private to avoid exposing implementation
         * details to API consumers while still providing meaningful debug
         * information during event processing.
         */
        private val logger = LoggerFactory.getLogger(EventManager::class.java)
        /**
         * Validates whether a method qualifies as an event handler.
         *
         * Conditions:
         * - Annotated with [EventHandler],
         * - Returns `void`,
         * - Has exactly one parameter,
         * - Parameter type is assignable to [Event].
         *
         * Used during subscriber introspection.
         *
         * @param allowStatic if true, only static methods are accepted; if false, only instance methods are accepted.
         *
         * @receiver the reflective method being checked
         */
        private fun Method.isValidEventHandler(allowStatic: Boolean = false): Boolean {
            if (!isAnnotationPresent(EventHandler::class.java)) return false
            if (allowStatic != Modifier.isStatic(modifiers)) return false
            if (returnType != Void.TYPE) return false
            if (parameterCount != 1) return false

            return Event::class.java.isAssignableFrom(parameterTypes[0])
        }
        /**
         * Determines whether this method is a valid `@ExceptionHandler` method according
         * to the event system's rules.
         *
         * A method is considered a valid exception handler if and only if:
         *
         * 1. It is annotated with `@ExceptionHandler`.
         * 2. It is static **only if** [allowStatic] is `true`, and non-static otherwise.
         * 3. It returns `void`.
         * 4. Its parameter list conforms to one of the supported exception-handler shapes:
         *
         *    - `(event: Event)`
         *    - `(throwable: Throwable)`
         *    - `(event: Event, throwable: Throwable)`
         *
         * All matching is **polymorphic**: parameter types may be supertypes of actual
         * dispatched values (e.g. `Event`, `Throwable`, or `Exception`).
         *
         * Invalid signatures are silently rejected, allowing the subscription logic to
         * skip them naturally—mirroring how normal event handlers are validated and
         * filtered.
         *
         * @param allowStatic whether static exception-handler methods are permitted.
         * @return `true` if the method is a valid exception handler; otherwise `false`.
         */
        private fun Method.isValidExceptionHandler(allowStatic: Boolean = false): Boolean {
            if (!isAnnotationPresent(ExceptionHandler::class.java)) return false
            if (allowStatic != Modifier.isStatic(modifiers)) return false
            if (returnType != Void.TYPE) return false
            return exceptionSignatureKind != null
        }
        /**
         * Retrieves all declared methods from the given class, its superclasses, and interfaces.
         *
         * Methods that are synthetic or bridge-generated are excluded. The traversal stops
         * before reaching [Any].
         *
         * @param klass the root class whose hierarchy will be scanned for declared methods.
         * @return a lazy [Sequence] of [Method] instances discovered in the class hierarchy.
         */
        private fun allDeclaredMethods(klass: Class<*>): Sequence<Method> = sequence {
            val seen = mutableSetOf<Class<*>>()
            val queue = ArrayDeque<Class<*>>()

            queue.add(klass)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()

                if (!seen.add(current)) continue
                if (current == Any::class.java) continue

                current.declaredMethods
                    .asSequence()
                    .filter { !it.isSynthetic && !it.isBridge }
                    .forEach { yield(it) }

                current.superclass?.let { queue.add(it) }

                current.interfaces.forEach { queue.add(it) }
            }
        }
        /**
         * Creates an [EventInvoker] for the given handler method.
         *
         * Uses [LambdaMetafactory] to generate a high-performance lambda when possible.
         * If generation fails, falls back to reflective invocation and logs the failure.
         *
         * @param method the handler method for which an invoker is created.
         * @return a compiled or reflective invoker that calls the handler.
         */
        private fun createEventInvoker(method: Method): EventInvoker {
            val isStatic = Modifier.isStatic(method.modifiers)
            return try {
                val callerLookup = MethodHandles.lookup()
                val lookup = try {
                    MethodHandles.privateLookupIn(method.declaringClass, callerLookup)
                } catch (_: IllegalAccessException) {
                    callerLookup
                }
                val handle = lookup.unreflect(method)
                val samMethodType = if (isStatic) {
                    MethodType.methodType(Void.TYPE, Event::class.java)
                } else {
                    MethodType.methodType(Void.TYPE, Any::class.java, Event::class.java)
                }
                val invokedType = MethodType.methodType(if (isStatic) StaticEventInvoker::class.java else InstanceEventInvoker::class.java)
                val implMethodType = handle.type()

                val callSite = LambdaMetafactory.metafactory(
                    lookup,
                    "invoke",
                    invokedType,
                    samMethodType,
                    handle,
                    implMethodType
                )

                val factory = callSite.target
                if (isStatic) {
                    factory.invokeExact() as StaticEventInvoker
                } else {
                    factory.invokeExact() as InstanceEventInvoker
                }
            } catch (t: Throwable) {
                logger.warn("Failed to create lambda invoker for ${method.declaringClass.name}#${method.name}, falling back to reflection.", t)
                if (isStatic) {
                    StaticEventInvoker { event ->
                        method.invoke(null, event)
                    }
                } else {
                    InstanceEventInvoker { target, event ->
                        method.invoke(target, event)
                    }
                }
            }
        }
        /**
         * Creates an [ExceptionInvoker] for a method annotated with `@ExceptionHandler`.
         *
         * This function inspects the reflective [method] to determine which supported
         * exception-handler signature it uses, as defined by [ExceptionSignatureKind].
         * Supported shapes are:
         *
         *  - `(event: Event, throwable: Throwable)`
         *  - `(event: Event)`
         *  - `(throwable: Throwable)`
         *
         * Methods whose parameter lists do not match one of these shapes return `null`,
         * allowing the subscriber-scanning logic to silently skip invalid handlers in a
         * manner consistent with normal event handler discovery.
         *
         * ## Invocation Strategy
         *
         * When a method is valid, the event system attempts to generate a high-performance
         * lambda via [LambdaMetafactory]. If this fails—for example due to module
         * visibility, lookup access rules, or JVM restrictions—the system logs the
         * failure and falls back to a reflective invoker.
         *
         * The returned invoker is specific to:
         *
         *  - **signature shape** (event only, throwable only, or event + throwable)
         *  - **context** (instance handler vs static handler)
         *
         * This ensures that exception dispatch can uniformly invoke handlers without
         * performing reflective analysis at runtime.
         *
         * ## Return Value
         *
         * - Returns a fully constructed [ExceptionInvoker] for valid exception handler methods.
         * - Returns `null` when the method's signature does not match any permitted
         *   `@ExceptionHandler` shape.
         *
         * @param method the reflective method to convert into an exception-handler invoker.
         * @return a compiled or reflective [ExceptionInvoker], or `null` if the method
         *         is not a valid exception handler.
         */
        private fun createExceptionInvoker(method: Method): ExceptionInvoker? {
            val isStatic = Modifier.isStatic(method.modifiers)
            val kind = method.exceptionSignatureKind ?: return null

            return try {
                val callerLookup = MethodHandles.lookup()
                val lookup = try {
                    MethodHandles.privateLookupIn(method.declaringClass, callerLookup)
                } catch (_: IllegalAccessException) {
                    callerLookup
                }

                val handle = lookup.unreflect(method)
                val implMethodType = handle.type()

                when (kind) {
                    ExceptionSignatureKind.EVENT_AND_THROWABLE -> {
                        if (isStatic) {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Event::class.java,
                                Throwable::class.java
                            )
                            val invokedType = MethodType.methodType(StaticExceptionInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as StaticExceptionInvoker
                        } else {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Any::class.java,
                                Event::class.java,
                                Throwable::class.java
                            )
                            val invokedType = MethodType.methodType(InstanceExceptionInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as InstanceExceptionInvoker
                        }
                    }

                    ExceptionSignatureKind.EVENT_ONLY -> {
                        if (isStatic) {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Event::class.java
                            )
                            val invokedType = MethodType.methodType(StaticExceptionEventOnlyInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as StaticExceptionEventOnlyInvoker
                        } else {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Any::class.java,
                                Event::class.java
                            )
                            val invokedType = MethodType.methodType(InstanceExceptionEventOnlyInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as InstanceExceptionEventOnlyInvoker
                        }
                    }

                    ExceptionSignatureKind.THROWABLE_ONLY -> {
                        if (isStatic) {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Throwable::class.java
                            )
                            val invokedType = MethodType.methodType(StaticExceptionThrowableOnlyInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as StaticExceptionThrowableOnlyInvoker
                        } else {
                            val samMethodType = MethodType.methodType(
                                Void.TYPE,
                                Any::class.java,
                                Throwable::class.java
                            )
                            val invokedType = MethodType.methodType(InstanceExceptionThrowableOnlyInvoker::class.java)

                            val callSite = LambdaMetafactory.metafactory(
                                lookup,
                                "invoke",
                                invokedType,
                                samMethodType,
                                handle,
                                implMethodType
                            )
                            val factory = callSite.target
                            factory.invokeExact() as InstanceExceptionThrowableOnlyInvoker
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.warn(
                    "Failed to create lambda exception invoker for ${method.declaringClass.name}#${method.name}, falling back to reflection.",
                    t
                )

                when (kind) {
                    ExceptionSignatureKind.EVENT_AND_THROWABLE -> {
                        if (isStatic) {
                            StaticExceptionInvoker { event, throwable ->
                                method.invoke(null, event, throwable)
                            }
                        } else {
                            InstanceExceptionInvoker { target, event, throwable ->
                                method.invoke(target, event, throwable)
                            }
                        }
                    }

                    ExceptionSignatureKind.EVENT_ONLY -> {
                        if (isStatic) {
                            StaticExceptionEventOnlyInvoker { event ->
                                method.invoke(null, event)
                            }
                        } else {
                            InstanceExceptionEventOnlyInvoker { target, event ->
                                method.invoke(target, event)
                            }
                        }
                    }

                    ExceptionSignatureKind.THROWABLE_ONLY -> {
                        if (isStatic) {
                            StaticExceptionThrowableOnlyInvoker { throwable ->
                                method.invoke(null, throwable)
                            }
                        } else {
                            InstanceExceptionThrowableOnlyInvoker { target, throwable ->
                                method.invoke(target, throwable)
                            }
                        }
                    }
                }
            }
        }
        /**
         * Shared internal logic for registering both event handlers and exception handlers.
         *
         * This method unifies the subscription flow for:
         *
         *  - instance-based handlers (event or exception),
         *  - static handlers (event or exception),
         *  - any handler type implementing [Priority].
         *
         * ## Responsibilities
         *
         * 1. **Insert the handler** into the appropriate list within [methodCache],
         *    maintaining descending priority order (higher values run first).
         *
         * 2. **Record handler ownership** in either:
         *
         *     - [objectMap] — when [any] is non-null (instance subscriber), or
         *     - [staticMap] — when [type] is non-null (static subscriber class).
         *
         *    Only one of [any] or [type] should be provided for any call.
         *
         * ## Usage
         *
         * This function is used for:
         *
         *  - normal event handler registration,
         *  - exception handler registration,
         *  - both instance and static subscriptions.
         *
         * By abstracting the storage and bookkeeping rules, the event system avoids
         * duplicated logic for events vs. exceptions.
         *
         * ## Cache Invalidation
         *
         * This function **does not** clear any resolved-handler caches.
         * Callers are responsible for invalidating:
         *
         *  - `resolvedEventCache` for event handlers, or
         *  - `exceptionResolvedCache` for exception handlers.
         *
         * after completing all registration work.
         *
         * @param T the handler entry type (e.g. [EventHandlerEntry], [ExceptionHandlerEntry]).
         * @param methodCache map from event type to the mutable list of handlers associated with it.
         * @param objectMap Caffeine cache tracking which event types an instance subscriber owns handlers for.
         * @param staticMap map tracking which event types a static subscriber class owns handlers for.
         * @param eventClass the event type under which this handler should be registered.
         * @param priority the handler’s priority; higher values are ordered earlier.
         * @param handlerEntry the fully constructed handler entry.
         * @param any instance subscriber, or `null` if registering a static handler.
         * @param type static subscriber class, or `null` if registering an instance handler.
         */
        private fun <T: Priority> sharedSubscribeLogic(
            methodCache: MutableMap<Class<out Event>,
                    MutableList<T>>,
            objectMap: Cache<Any, MutableList<Class<out Event>>>,
            staticMap: MutableMap<Class<*>, MutableList<Class<out Event>>>,
            eventClass: Class<out Event>,
            priority: Int,
            handlerEntry: T,
            any: Any? = null,
            type: Class<*>? = null
        ) {
            val list = methodCache.getOrPut(eventClass) { mutableListOf() }

            val index = list.indexOfFirst { it.priority < priority }
            if (index == -1) {
                list.add(handlerEntry)
            } else {
                list.add(index, handlerEntry)
            }
            any?.let {
                val list = objectMap.getIfPresent(it)
                    ?: mutableListOf<Class<out Event>>().also { newList ->
                        objectMap.put(it, newList)
                    }
                list.add(eventClass)
                return
            }
            type?.let {
                staticMap.getOrPut(it) { mutableListOf() }.add(eventClass)
            }
        }
        /**
         * Computes a relative "specificity" rank for this exception handler entry.
         *
         * This is used as a secondary sort key when ordering exception handlers:
         * handlers are first ordered by descending [Priority.priority], and then
         * by this rank in ascending order (lower values are considered more
         * specific).
         *
         * The ranking scheme is:
         *
         *  - `0` — handlers that declare both an event and a throwable parameter
         *           **and** have an explicit throwable type recorded via
         *           [ExceptionHandlerEntryWithThrowable], i.e. methods of the form:
         *
         *           `fun onFailure(event: E, t: T)` where `T` is a specific subtype.
         *
         *           These are the most specific, as they constrain both the event
         *           type and the throwable type.
         *
         *  - `1` — handlers that are scoped only by event (no explicit throwable
         *           parameter), i.e.:
         *
         *           `fun onFailure(event: E)`
         *
         *           These care about which event failed, but not which throwable
         *           was thrown.
         *
         *  - `2` — handlers that are scoped only by throwable, i.e.:
         *
         *           `fun onFailure(t: T)`
         *
         *           These act as global throwable observers and are the least
         *           specific with respect to the event that failed.
         *
         * This ranking is intentionally conservative: it focuses on the presence of
         * an explicit throwable type vs. event-only vs. throwable-only, and is used
         * purely as a tie-breaker within the same priority.
         *
         * @return an integer specificity rank where lower values indicate more
         *         specific handlers.
         */
        private fun ExceptionHandlerEntry.specificityRank(): Int = when {
            this is ExceptionHandlerEntryWithThrowable && (invoker is InstanceExceptionInvoker || invoker is StaticExceptionInvoker) -> 0
            invoker is InstanceExceptionThrowableOnlyInvoker || invoker is StaticExceptionThrowableOnlyInvoker -> 2
            else -> 1
        }
    }
    /**
     * Synchronization lock for all subscription and cache mutation operations.
     */
    private val lock = Any()
    /**
     * Maps each event type to its registered handler list.
     *
     * Handlers are stored in priority order (highest first).
     */
    private val eventMethodCache: MutableMap<Class<out Event>, MutableList<EventHandlerEntry>> = mutableMapOf()
    /**
     * Tracks which event types each subscriber object handles.
     *
     * Keys are weak references, allowing automatic cleanup when subscribers
     * become unreachable.
     */
    private val objectEventMap = Caffeine.newBuilder()
        .weakKeys()
        .removalListener<Any, MutableList<Class<out Event>>> { _, events, _ ->
            if (events != null) {
                synchronized(lock) {
                    var hasRemovedAnything = false
                    for (eventClass in events) {
                        eventMethodCache[eventClass]?.removeIf { handler ->
                            val condition = handler is InstanceEventHandlerEntry && handler.target.get() == null
                            if (condition) hasRemovedAnything = true
                            condition
                        }
                    }
                    if (hasRemovedAnything) resolvedEventCache.clear()
                }
            }
        }
        .build<Any, MutableList<Class<out Event>>>()
    /**
     * Tracks which event types each static subscriber class handles.
     *
     * Unlike [objectEventMap], this map stores strong references to subscriber
     * classes rather than instances. Static handlers do not participate in
     * garbage-collection–based cleanup and must be explicitly removed via
     * [unsubscribeStatic].
     *
     * Each key corresponds to a class containing one or more `static`
     * (or `@JvmStatic` in Kotlin companions) methods annotated with [EventHandler],
     * and the associated value lists all event types that the class handles.
     */
    private val staticEventMap: MutableMap<Class<*>, MutableList<Class<out Event>>> = mutableMapOf()
    /**
     * Cache of fully resolved handler lists for each event class.
     *
     * Resolved lists include handlers for superclasses and interfaces of
     * the event type, allowing polymorphic event dispatch.
     */
    private val resolvedEventCache: MutableMap<Class<out Event>, List<EventHandlerEntry>> = mutableMapOf()
    /**
     * Maps each event type to its registered exception handlers.
     *
     * The key is the event type declared on the exception handler method, or
     * [Event] itself for handlers that:
     *
     *  - only declare a throwable parameter, or
     *  - use a broad event parameter such as `Event`.
     *
     * Handlers are stored in priority order (highest first).
     */
    private val exceptionMethodCache: MutableMap<Class<out Event>, MutableList<ExceptionHandlerEntry>> = mutableMapOf()
    /**
     * Tracks which event types each subscriber object has exception handlers for.
     *
     * Keys are weak references (via Caffeine's `weakKeys`), allowing automatic
     * cleanup when subscribers become unreachable. The value is the list of
     * event types under which this subscriber's exception handlers were
     * registered in [exceptionMethodCache].
     *
     * This mirrors [objectEventMap], but for `@ExceptionHandler` methods.
     */
    private val objectExceptionMap = Caffeine.newBuilder()
        .weakKeys()
        .removalListener<Any, MutableList<Class<out Event>>> { _, events, _ ->
            if (events != null) {
                synchronized(lock) {
                    var hasRemovedAnything = false
                    for (eventClass in events) {
                        exceptionMethodCache[eventClass]?.removeIf { entry ->
                            val shouldRemove = (entry is InstanceExceptionHandlerEntry && entry.target.get() == null) ||
                                    (entry is InstanceExceptionHandlerEntryWithThrowable && entry.target.get() == null)
                            if (shouldRemove) hasRemovedAnything = true
                            shouldRemove
                        }
                    }
                    if (hasRemovedAnything) exceptionResolvedCache.clear()
                }
            }
        }
        .build<Any, MutableList<Class<out Event>>>()
    /**
     * Tracks which event types each static subscriber class has exception
     * handlers for.
     *
     * Each key corresponds to a class containing one or more static (or
     * `@JvmStatic`) methods annotated with `@ExceptionHandler`, and the
     * associated value lists all event types for which that class has
     * exception handlers registered in [exceptionMethodCache].
     *
     * This mirrors [staticEventMap], but for exception handlers.
     */
    private val staticExceptionMap: MutableMap<Class<*>, MutableList<Class<out Event>>> = mutableMapOf()
    /**
     * Cache of fully resolved exception handler lists for each event class.
     *
     * Resolved lists include handlers registered for the event type itself,
     * its superclasses, and its interfaces that implement [Event]. Throwable
     * compatibility is still checked at dispatch time.
     *
     * This mirrors [resolvedEventCache], but for `@ExceptionHandler` methods.
     */
    private val exceptionResolvedCache: MutableMap<Class<out Event>, List<ExceptionHandlerEntry>> = mutableMapOf()

    override fun subscribe(any: Any) {
        synchronized(lock) {
            if (objectEventMap.getIfPresent(any) != null || objectExceptionMap.getIfPresent(any) != null) return

            val klass = any::class.java

            val methods = allDeclaredMethods(klass)
                .onEach {
                    try {
                        it.isAccessible = true
                    } catch (e: Exception) {
                        logger.error("Failed to make ${it.name} accessible.", e)
                    }
                }
                .toList()

            for (method in methods) {
                if (!method.isValidEventHandler()) continue

                val instanceInvoker = createEventInvoker(method)
                if (instanceInvoker !is InstanceEventInvoker) continue

                @Suppress("UNCHECKED_CAST")
                val eventClass = method.parameterTypes[0] as Class<out Event>
                val eventHandlerAnnotation = method.getAnnotation(EventHandler::class.java)
                val handler = InstanceEventHandlerEntry(WeakReference(any), instanceInvoker, eventHandlerAnnotation.priority, eventHandlerAnnotation.runIfCanceled)

                sharedSubscribeLogic(eventMethodCache, objectEventMap, staticEventMap, eventClass, eventHandlerAnnotation.priority, handler, any)
            }
            resolvedEventCache.clear()

            for (method in methods) {
                if (!method.isValidExceptionHandler()) continue

                val exceptionInvoker = createExceptionInvoker(method) ?: continue
                val kind = method.exceptionSignatureKind ?: continue
                val priority = method.getAnnotation(ExceptionHandler::class.java).priority

                val eventClass = kind.getEventClass(method)
                val throwableType = kind.getThrowableType(method)

                val entry: ExceptionHandlerEntry =
                    when (exceptionInvoker) {
                        is InstanceExceptionInvokers -> {
                            if (throwableType != null) {
                                InstanceExceptionHandlerEntryWithThrowable(WeakReference(any), exceptionInvoker, priority, throwableType)
                            } else {
                                InstanceExceptionHandlerEntry(WeakReference(any), exceptionInvoker, priority)
                            }
                        }
                        is StaticExceptionInvokers -> continue
                    }

                sharedSubscribeLogic(exceptionMethodCache, objectExceptionMap, staticExceptionMap, eventClass, priority, entry, any = any)
            }
            exceptionResolvedCache.clear()
        }
    }

    override fun unsubscribe(any: Any) {
        synchronized(lock) {
            val eventClasses = objectEventMap.getIfPresent(any)
            if (eventClasses != null) {
                eventClasses.forEach { eventClass ->
                    eventMethodCache[eventClass]?.removeIf {
                        if (it !is InstanceEventHandlerEntry) return@removeIf false
                        val target = it.target.get()
                        target == null || target === any
                    }
                }
                objectEventMap.invalidate(any)
                resolvedEventCache.clear()
            }

            val exceptionEventClasses = objectExceptionMap.getIfPresent(any)
            if (exceptionEventClasses != null) {
                exceptionEventClasses.forEach { eventClass ->
                    exceptionMethodCache[eventClass]?.removeIf {
                        if (it is InstanceExceptionHandlerEntry) {
                            val target = it.target.get()
                            return@removeIf target == null || target === any
                        }
                        if (it is InstanceExceptionHandlerEntryWithThrowable) {
                            val target = it.target.get()
                            return@removeIf target == null || target === any
                        }
                        return@removeIf false
                    }
                }
                objectExceptionMap.invalidate(any)
                exceptionResolvedCache.clear()
            }
        }
    }

    override fun subscribeStatic(type: Class<*>) {
        synchronized(lock) {
            if (staticEventMap.containsKey(type) || staticExceptionMap.containsKey(type)) return

            val methods = allDeclaredMethods(type)
                .onEach {
                    try {
                        it.isAccessible = true
                    } catch (e: Exception) {
                        logger.error("Failed to make static handler ${it.name} accessible.", e)
                    }
                }
                .toList()

            for (method in methods) {
                if (!method.isValidEventHandler(allowStatic = true)) continue

                val staticInvoker = createEventInvoker(method)
                if (staticInvoker !is StaticEventInvoker) continue

                @Suppress("UNCHECKED_CAST")
                val eventClass = method.parameterTypes[0] as Class<out Event>
                val eventHandlerAnnotation = method.getAnnotation(EventHandler::class.java)

                val handler = StaticEventHandlerEntry(type, staticInvoker, eventHandlerAnnotation.priority, eventHandlerAnnotation.runIfCanceled)

                sharedSubscribeLogic(eventMethodCache, objectEventMap, staticEventMap, eventClass, eventHandlerAnnotation.priority, handler, type = type)
            }
            resolvedEventCache.clear()

            for (method in methods) {
                if (!method.isValidExceptionHandler(allowStatic = true)) continue

                val exceptionInvoker = createExceptionInvoker(method) ?: continue
                val kind = method.exceptionSignatureKind ?: continue
                val priority = method.getAnnotation(ExceptionHandler::class.java).priority

                val eventClass = kind.getEventClass(method)
                val throwableType = kind.getThrowableType(method)

                val entry: ExceptionHandlerEntry =
                    when (exceptionInvoker) {
                        is StaticExceptionInvokers -> {
                            if (throwableType != null) {
                                StaticExceptionHandlerEntryWithThrowable(type, exceptionInvoker, priority, throwableType)
                            } else {
                                StaticExceptionHandlerEntry(type, exceptionInvoker, priority)
                            }
                        }
                        is InstanceExceptionInvokers -> continue
                    }
                sharedSubscribeLogic(exceptionMethodCache, objectExceptionMap, staticExceptionMap, eventClass, priority, entry, type = type)
            }
            exceptionResolvedCache.clear()
        }
    }

    override fun unsubscribeStatic(type: Class<*>) {
        synchronized(lock) {
            val eventClasses = staticEventMap[type]
            if (eventClasses != null) {
                eventClasses.forEach { eventClass ->
                    eventMethodCache[eventClass]?.removeIf {
                        it is StaticEventHandlerEntry && it.owner == type
                    }
                }
                staticEventMap.remove(type)
                resolvedEventCache.clear()
            }

            val exceptionEventClasses = staticExceptionMap[type]
            if (exceptionEventClasses != null) {
                exceptionEventClasses.forEach { eventClass ->
                    exceptionMethodCache[eventClass]?.removeIf {
                        (it is StaticExceptionHandlerEntry && it.owner == type) ||
                                (it is StaticExceptionHandlerEntryWithThrowable && it.owner == type)
                    }
                }
                staticExceptionMap.remove(type)
                exceptionResolvedCache.clear()
            }
        }
    }

    override fun post(event: Event, cancelMode: CancelMode) {
        val cancelable = event as? Cancelable

        if (cancelMode == CancelMode.ENFORCE && cancelable?.canceled == true) return

        val handlers = synchronized(lock) {
            val eventClass = event::class.java
            resolvedEventCache[eventClass]
                ?: collectHandlersFor(eventClass, eventMethodCache)
                    .sortedByDescending { it.priority }
                    .also { resolvedEventCache[eventClass] = it }
        }
        for (handler in handlers) {
            if (cancelable?.canceled == true) {
                if (cancelMode == CancelMode.RESPECT && !handler.runIfCanceled) continue
                if (cancelMode == CancelMode.ENFORCE) break
            }
            try {
                when(handler) {
                    is InstanceEventHandlerEntry -> {
                        val target = handler.target.get() ?: continue
                        handler.invoker(target, event)
                    }
                    is StaticEventHandlerEntry -> handler.invoker(event)
                }
            } catch (t: Throwable) {
                dispatchExceptionHandlers(event, t)
            }
        }
    }
    /**
     * Collects all handlers associated with the given event class, including those
     * registered for its supertypes and interfaces that implement [Event].
     *
     * This utility performs a breadth-first traversal of the event type hierarchy:
     *
     * 1. Starts at [eventClass].
     * 2. For each visited class, looks up any handlers in [methodCache] keyed by that
     *    class and appends them to result.
     * 3. Enqueues the superclass (if it implements [Event]) and all interfaces that
     *    implement [Event], continuing until the hierarchy has been exhausted.
     *
     * The function is generic and can be used for:
     *
     *  - event handlers ([EventHandlerEntry])
     *  - exception handlers ([ExceptionHandlerEntry])
     *
     * as long as they are stored in a `Map<Class<out Event>, MutableList<T>>` keyed
     * by the event type for which they apply.
     *
     * Typically, the returned list is then sorted by priority and cached in a
     * resolved-handler cache to avoid recomputing the hierarchy traversal for
     * subsequent events of the same runtime type.
     *
     * @param T the handler entry type, such as [EventHandlerEntry] or [ExceptionHandlerEntry].
     * @param eventClass the concrete event type being dispatched.
     * @param methodCache the map from event type to handler lists from which entries
     *                    should be collected.
     * @return a combined list of handlers applicable to [eventClass] and all of its
     *         relevant supertypes and interfaces.
     */
    private fun <T> collectHandlersFor(eventClass: Class<out Event>, methodCache: Map<Class<out Event>, MutableList<T>>): List<T> {
        val result = mutableListOf<T>()
        val seen = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()

        queue.add(eventClass)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue

            @Suppress("UNCHECKED_CAST")
            val eventHandlerEntries: List<T>? = methodCache[current as Class<out Event>]
            if (eventHandlerEntries != null) result.addAll(eventHandlerEntries)

            current.superclass
                ?.takeIf { Event::class.java.isAssignableFrom(it) }
                ?.let { queue.add(it) }

            current.interfaces
                .filter { Event::class.java.isAssignableFrom(it) }
                .forEach { queue.add(it) }
        }

        return result
    }
    /**
     * Dispatches a throwable raised by an event handler to registered
     * `@ExceptionHandler` methods.
     *
     * This method is invoked from [post] whenever an event handler throws.
     * It performs the following steps:
     *
     * 1. Resolves all exception handlers that are applicable to the runtime
     *    type of [event], using [exceptionResolvedCache] to avoid repeated
     *    hierarchy walks. Resolution includes handlers registered for
     *    superclasses and interfaces of the event type.
     *
     * 2. Sorts the resolved handlers using a composite ordering:
     *
     *      - Primary: descending [Priority.priority] (higher first).
     *      - Secondary: ascending [specificityRank], so that handlers with
     *        both event and throwable parameters run before event-only, which
     *        in turn run before throwable-only handlers at the same priority.
     *
     * 3. If no handlers are found:
     *
     *      - If [throwable] is an [Exception], it is logged as an unhandled
     *        failure for the given event type and then swallowed.
     *      - Otherwise (e.g. [Error] subclasses), [throwable] is rethrown to
     *        avoid masking fatal conditions.
     *
     * 4. For each handler entry, checks throwable compatibility when the
     *    handler declares a concrete throwable type (via
     *    [ExceptionHandlerEntryWithThrowable]). Compatibility is determined
     *    using [Class.isInstance], so handlers declared for a supertype (such
     *    as `Exception`) may observe subtypes (such as `IOException`).
     *
     * 5. Invokes the underlying handler using the appropriate invoker shape:
     *
     *      - `(event: Event, throwable: Throwable)`
     *      - `(event: Event)`
     *      - `(throwable: Throwable)`
     *
     * Any exceptions thrown *by* exception handlers themselves are not caught
     * here and will propagate outward. This is deliberate: if an exception
     * handler chooses to throw, it is treated as a terminal failure rather
     * than feeding back into the bus.
     *
     * @param event the event whose handler produced [throwable].
     * @param throwable the exception thrown while dispatching [event].
     */
    private fun dispatchExceptionHandlers(event: Event, throwable: Throwable) {
        val handlers = synchronized(lock) {
            val eventClass = event::class.java
            exceptionResolvedCache[eventClass]
                ?: collectHandlersFor(eventClass, exceptionMethodCache)
                    .sortedWith(
                        compareByDescending<ExceptionHandlerEntry> { it.priority }
                            .thenBy { it.specificityRank() }
                    )
                    .also { exceptionResolvedCache[eventClass] = it }
        }


        if (handlers.isEmpty()) {
            if (throwable is Exception) {
                logger.error("Unhandled exception in handler for event: ${event::class.simpleName}", throwable)
                return
            }
            throw throwable
        }

        var handled = false

        for (entry in handlers) {
            when (entry) {
                is InstanceExceptionHandlerEntryWithThrowable -> {
                    if (!entry.throwableType.isInstance(throwable)) continue
                    val target = entry.target.get() ?: continue
                    handled = true

                    when (val invoker = entry.invoker) {
                        is InstanceExceptionInvoker -> invoker(target, event, throwable)
                        is InstanceExceptionThrowableOnlyInvoker -> invoker(target, throwable)
                        is InstanceExceptionEventOnlyInvoker -> invoker(target, event)
                    }
                }

                is InstanceExceptionHandlerEntry -> {
                    val target = entry.target.get() ?: continue
                    handled = true

                    when (val invoker = entry.invoker) {
                        is InstanceExceptionInvoker -> invoker(target, event, throwable)
                        is InstanceExceptionThrowableOnlyInvoker -> invoker(target, throwable)
                        is InstanceExceptionEventOnlyInvoker -> invoker(target, event)
                    }
                }

                is StaticExceptionHandlerEntryWithThrowable -> {
                    if (!entry.throwableType.isInstance(throwable)) continue
                    handled = true

                    when (val invoker = entry.invoker) {
                        is StaticExceptionInvoker -> invoker(event, throwable)
                        is StaticExceptionThrowableOnlyInvoker -> invoker(throwable)
                        is StaticExceptionEventOnlyInvoker -> invoker(event)
                    }
                }

                is StaticExceptionHandlerEntry -> {
                    handled = true
                    when (val invoker = entry.invoker) {
                        is StaticExceptionInvoker -> invoker(event, throwable)
                        is StaticExceptionThrowableOnlyInvoker -> invoker(throwable)
                        is StaticExceptionEventOnlyInvoker -> invoker(event)
                    }
                }
            }
        }

        if (!handled) {
            if (throwable is Exception) {
                logger.error("Unhandled exception in handler for event: ${event::class.simpleName}", throwable)
                return
            }
            throw throwable
        }
    }
}
