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

import com.smushytaco.event_library.api.Bus
import com.smushytaco.event_library.api.Cancelable
import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.api.EventHandler
import org.slf4j.LoggerFactory
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import kotlin.collections.ArrayDeque

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
         * - Non-static,
         * - Returns `void`,
         * - Has exactly one parameter,
         * - Parameter type is assignable to [Event].
         *
         * Used during subscriber introspection.
         *
         * @receiver the reflective method being checked
         */
        private fun Method.isValid(): Boolean {
            if (!isAnnotationPresent(EventHandler::class.java)) return false
            if (Modifier.isStatic(modifiers)) return false
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
            return try {
                val callerLookup = MethodHandles.lookup()
                val lookup = try {
                    MethodHandles.privateLookupIn(method.declaringClass, callerLookup)
                } catch (_: IllegalAccessException) {
                    callerLookup
                }
                val handle = lookup.unreflect(method)
                val samMethodType = MethodType.methodType(Void.TYPE, Any::class.java, Event::class.java)
                val invokedType = MethodType.methodType(EventInvoker::class.java)
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
                factory.invokeExact() as EventInvoker
            } catch (t: Throwable) {
                logger.error("Failed to create lambda invoker for ${method.declaringClass.name}#${method.name}, falling back to reflection.", t)
                EventInvoker { target, event ->
                    method.invoke(target, event)
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
    private val objectEventMap: MutableMap<Any, MutableList<Class<out Event>>> = WeakHashMap()
    /**
     * Cache of fully resolved handler lists for each event class.
     *
     * Resolved lists include handlers for superclasses and interfaces of
     * the event type, allowing polymorphic event dispatch.
     */
    private val resolvedCache: MutableMap<Class<out Event>, List<Handler>> = mutableMapOf()

    override fun subscribe(any: Any) {
        synchronized(lock) {
            if (objectEventMap.containsKey(any)) return

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
                @Suppress("UNCHECKED_CAST")
                val eventClass = method.parameterTypes[0] as Class<out Event>
                val priority = method.getAnnotation(EventHandler::class.java).priority
                val handler = Handler(WeakReference(any), createInvoker(method), priority)

                val list = methodCache.getOrPut(eventClass) { mutableListOf() }

                // insert sorted by priority (highest first)
                val index = list.indexOfFirst { it.priority < priority }
                if (index == -1) {
                    list.add(handler)
                } else {
                    list.add(index, handler)
                }

                objectEventMap.getOrPut(any) { mutableListOf() }.add(eventClass)
            }
            resolvedCache.clear()
        }
    }

    override fun unsubscribe(any: Any) {
        synchronized(lock) {
            objectEventMap[any]?.forEach { eventClass ->
                methodCache[eventClass]?.removeIf {
                    val target = it.target.get()
                    target == null || target === any
                }
            }
            objectEventMap.remove(any)
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
                val target = handler.target.get() ?: continue
                handler.invoker(target, event)
            } catch (e: Exception) {
                logger.error("Failed to invoke handler for event: ${event::class.simpleName}", e)
            }
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
            val handlers = methodCache[current as Class<out Event>]
            if (handlers != null) {
                handlers.removeIf { it.target.get() == null }
                result.addAll(handlers)
            }

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
