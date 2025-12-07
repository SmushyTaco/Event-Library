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

import com.github.benmanes.caffeine.cache.Caffeine
import com.smushytaco.event_library.api.Bus
import com.smushytaco.event_library.api.Cancelable
import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.api.EventHandler
import com.smushytaco.event_library.internal.event_invokers.EventInvoker
import com.smushytaco.event_library.internal.event_invokers.InstanceEventInvoker
import com.smushytaco.event_library.internal.event_invokers.StaticEventInvoker
import com.smushytaco.event_library.internal.handlers.Handler
import com.smushytaco.event_library.internal.handlers.InstanceHandler
import com.smushytaco.event_library.internal.handlers.StaticHandler
import org.slf4j.LoggerFactory
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Internal implementation of the [Bus] interface responsible for:
 * - Discovering and registering event handler methods,
 * - Managing subscribers through weak references,
 * - Dispatching events with respect to handler priority and cancellation,
 * - Caching resolved handler lists for efficient repeated dispatch.
 *
 * This class is not exposed publicly; users acquire a bus instance through
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
        private fun Method.isValid(allowStatic: Boolean = false): Boolean {
            if (!isAnnotationPresent(EventHandler::class.java)) return false
            if (allowStatic != Modifier.isStatic(modifiers)) return false
            if (returnType != Void.TYPE) return false
            if (parameterCount != 1) return false

            return Event::class.java.isAssignableFrom(parameterTypes[0])
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
        private fun createInvoker(method: Method): EventInvoker {
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
    private val methodCache: MutableMap<Class<out Event>, MutableList<Handler>> = mutableMapOf()
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
                        methodCache[eventClass]?.removeIf { handler ->
                            val condition = handler is InstanceHandler && handler.target.get() == null
                            if (condition) hasRemovedAnything = true
                            condition
                        }
                    }
                    if (hasRemovedAnything) resolvedCache.clear()
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
    private val resolvedCache: MutableMap<Class<out Event>, List<Handler>> = mutableMapOf()

    override fun subscribe(any: Any) {
        synchronized(lock) {
            if (objectEventMap.getIfPresent(any) != null) return

            val klass = any::class.java

            val methods = allDeclaredMethods(klass)
                .filter { it.isValid() }
                .onEach {
                    try {
                        it.isAccessible = true
                    } catch (e: Exception) {
                        logger.error("Failed to make ${it.name} accessible.", e)
                    }
                }
                .toList()

            for (method in methods) {
                val instanceInvoker = createInvoker(method)
                if (instanceInvoker !is InstanceEventInvoker) continue
                @Suppress("UNCHECKED_CAST")
                val eventClass = method.parameterTypes[0] as Class<out Event>
                val priority = method.getAnnotation(EventHandler::class.java).priority
                val handler = InstanceHandler(WeakReference(any), instanceInvoker, priority)

                sharedSubscribeLogic(eventClass, priority, handler, any)
            }
            resolvedCache.clear()
        }
    }

    override fun unsubscribe(any: Any) {
        synchronized(lock) {
            val eventClasses = objectEventMap.getIfPresent(any) ?: return
            eventClasses.forEach { eventClass ->
                methodCache[eventClass]?.removeIf {
                    if (it !is InstanceHandler) return@removeIf false
                    val target = it.target.get()
                    target == null || target === any
                }
            }
            objectEventMap.invalidate(any)
            resolvedCache.clear()
        }
    }

    override fun subscribeStatic(type: Class<*>) {
        synchronized(lock) {
            if (staticEventMap.containsKey(type)) return

            val methods = allDeclaredMethods(type)
                .filter { it.isValid(allowStatic = true) }
                .onEach {
                    try {
                        it.isAccessible = true
                    } catch (e: Exception) {
                        logger.error("Failed to make static handler ${it.name} accessible.", e)
                    }
                }
                .toList()

            for (method in methods) {
                val staticInvoker = createInvoker(method)
                if (staticInvoker !is StaticEventInvoker) continue

                @Suppress("UNCHECKED_CAST")
                val eventClass = method.parameterTypes[0] as Class<out Event>
                val priority = method.getAnnotation(EventHandler::class.java).priority

                val handler = StaticHandler(type, staticInvoker, priority)

                sharedSubscribeLogic(eventClass, priority, handler, type = type)
            }

            resolvedCache.clear()
        }
    }

    override fun unsubscribeStatic(type: Class<*>) {
        synchronized(lock) {
            val eventClasses = staticEventMap[type] ?: return
            eventClasses.forEach { eventClass ->
                methodCache[eventClass]?.removeIf {
                    it is StaticHandler && it.owner == type
                }
            }
            staticEventMap.remove(type)
            resolvedCache.clear()
        }
    }

    override fun post(event: Event, respectCancels: Boolean) {
        val cancelable = if (respectCancels) event as? Cancelable else null

        if (cancelable?.canceled == true) return

        val handlers = synchronized(lock) {
            val eventClass = event::class.java
            resolvedCache[eventClass]
                ?: collectHandlersFor(eventClass)
                    .sortedByDescending { it.priority }
                    .also { resolvedCache[eventClass] = it }
        }
        for (handler in handlers) {
            if (cancelable?.canceled == true) break
            try {
                when(handler) {
                    is InstanceHandler -> {
                        val target = handler.target.get() ?: continue
                        handler.invoker(target, event)
                    }
                    is StaticHandler -> handler.invoker(event)
                }
            } catch (e: Exception) {
                logger.error("Failed to invoke handler for event: ${event::class.simpleName}", e)
            }
        }
    }
    /**
     * Shared internal logic for registering both instance-based and static event handlers.
     *
     * This function inserts the given [handler] into the global [methodCache] for the supplied
     * [eventClass], preserving priority ordering (higher priority first). It then records the
     * handler's ownership in either:
     *
     * - [objectEventMap], when [any] is non-null (instance subscriber), or
     * - [staticEventMap], when [type] is non-null (static subscriber class).
     *
     * Only one of [any] or [type] should be provided per invocation.
     *
     * This method does **not** invalidate [resolvedCache]; callers are responsible for clearing it
     * after completing all registration work.
     *
     * @param eventClass the event type the handler should receive.
     * @param priority the handler’s priority as defined by [EventHandler.priority].
     * @param handler the fully constructed handler instance to register.
     * @param any the subscriber instance owning the handler, or `null` for static handlers.
     * @param type the subscriber class owning static handlers, or `null` for instance handlers.
     */
    private fun sharedSubscribeLogic(eventClass: Class<out Event>, priority: Int, handler: Handler, any: Any? = null, type: Class<*>? = null) {
        val list = methodCache.getOrPut(eventClass) { mutableListOf() }

        val index = list.indexOfFirst { it.priority < priority }
        if (index == -1) {
            list.add(handler)
        } else {
            list.add(index, handler)
        }
        any?.let {
            val list = objectEventMap.getIfPresent(it)
                ?: mutableListOf<Class<out Event>>().also { newList ->
                    objectEventMap.put(it, newList)
                }
            list.add(eventClass)
            return
        }
        type?.let {
            staticEventMap.getOrPut(it) { mutableListOf() }.add(eventClass)
        }
    }
    /**
     * Collects all handlers for the given event class, including those registered
     * for its supertypes and interfaces that implement [Event].
     *
     * Handlers with garbage-collected targets are removed during traversal.
     *
     * @param eventClass the event type being dispatched.
     * @return a combined list of handlers applicable to the event.
     */
    private fun collectHandlersFor(eventClass: Class<out Event>): List<Handler> {
        val result = mutableListOf<Handler>()
        val seen = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()

        queue.add(eventClass)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue

            @Suppress("UNCHECKED_CAST")
            val handlers: List<Handler>? = methodCache[current as Class<out Event>]
            if (handlers != null) result.addAll(handlers)

            current.superclass
                ?.takeIf { Event::class.java.isAssignableFrom(it) }
                ?.let { queue.add(it) }

            current.interfaces
                .filter { Event::class.java.isAssignableFrom(it) }
                .forEach { queue.add(it) }
        }

        return result
    }
}
