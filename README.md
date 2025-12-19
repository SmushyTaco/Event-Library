# Event Library
[![Maven Central](https://img.shields.io/maven-central/v/com.smushytaco/event-library.svg?label=maven%20central)](https://central.sonatype.com/artifact/com.smushytaco/event-library)
[![Dokka Docs](https://img.shields.io/badge/docs-dokka-brightgreen.svg)](https://smushytaco.github.io/Event-Library)

A lightweight, reflection‑assisted but LambdaMetafactory‑accelerated event bus for Kotlin and the JVM.  
This library focuses on **simplicity**, **performance**, and **zero boilerplate**, while supporting:

- Private, protected, internal, or public handler methods
- Handler prioritization
- Cancelable and modifiable events
- Weak subscriber references (automatic cleanup)
- Polymorphic dispatch (handlers for supertype events receive subtype events)
- High‑performance invocation using `LambdaMetafactory`, with reflective fallback
- Static handlers for both events and exceptions
- Typed, prioritized exception handling via `@ExceptionHandler`

---

## 🧠 IntelliJ IDEA Plugin (Recommended)

If you use **IntelliJ IDEA** or **Android Studio**, there is an official companion plugin I created:

👉 **[Event Library Helper Plugin](https://plugins.jetbrains.com/plugin/29413-event-library-helper)**

The plugin provides **IDE-time validation and quick fixes** for `@EventHandler` and `@ExceptionHandler` methods, helping you catch mistakes before runtime.

You can check out the code [here](https://github.com/SmushyTaco/Event-Library-Plugin).

### What the plugin does
- ✅ Validates handler method signatures
- ✅ Ensures correct parameter counts and types
- ✅ Enforces `void` / `Unit` return types
- ✅ Detects invalid `@ExceptionHandler` shapes
- 🔁 Offers quick fixes where possible (e.g. return type correction, parameter order swaps)

### Language support
- **Kotlin**
- **Java**
- **Scala (Scala 3)**

Using the plugin is strongly recommended when working with Event Library, especially in larger codebases, as it turns many runtime errors into immediate editor feedback.

## ✨ Features

### 🔍 Automatic Handler Discovery

Any method annotated with `@EventHandler` and accepting exactly one `Event` parameter is treated as a handler.

```kotlin
class ExampleSubscriber {
    @EventHandler
    private fun onExample(event: ExampleEvent) {
        println("Handled!")
    }
}
```

Handlers can be `private`, `protected`, `internal`, or `public`. The bus discovers them via reflection and turns them into fast call sites.

### 🧯 Structured Exception Handling with `@ExceptionHandler`

When an `@EventHandler` throws, the bus does **not** crash your application by default.  
Instead, it dispatches the failure to matching `@ExceptionHandler` methods, which can be:

- instance methods on subscribers, or
- static methods (`static` in Java, `@JvmStatic` in Kotlin)

Supported signatures:

```kotlin
// 1) Event + Throwable: most specific
@ExceptionHandler
fun onFailure(event: MyEvent, t: IOException) { /* ... */ }

// 2) Event only: any exception for this event type
@ExceptionHandler
fun onAnyFailure(event: MyEvent) { /* ... */ }

// 3) Throwable only: this exception type for any event
@ExceptionHandler
fun onAnyIOException(t: IOException) { /* ... */ }
```

Matching is **polymorphic** in both directions:

- Event parameter can be a supertype of the actual event (`BaseEvent` handler sees `ChildEvent` failures).
- Throwable parameter can be a supertype of the actual throwable (`Exception` handler sees `IOException`).

Ordering rules for exception handlers:

1. **Priority** (higher `priority` runs first).
2. **Specificity** at the same priority:
    - event + throwable
    - event‑only
    - throwable‑only
3. Within the same priority **and** same specificity, registration order is preserved.

If **no** exception handler ends up handling a throwable:

- `Exception` (and subclasses) → logged via SLF4J and swallowed.
- `Error` (and other non‑`Exception` throwables) → rethrown.

If an exception handler itself throws, that exception is **not** re‑handled by the bus and will propagate out of `post(...)`.

### 🚀 High‑Performance Invocation

Wherever possible, handler and exception invokers are compiled into fast lambdas using `LambdaMetafactory`.  
If this isn’t possible (module visibility, access rules, security manager, etc.), the system falls back to reflection and logs the failure, but behavior remains correct.

### 🧹 Weak Subscriber References

Instance subscribers are stored via `WeakReference`. When a subscriber becomes unreachable, its handlers are automatically pruned:

- No memory leaks from forgotten `unsubscribe` calls.
- Caches are invalidated when stale handlers are removed.

### 🛑 Cancelable Events & Cancel Modes

Events can opt into cancellation:

```kotlin
class ExampleEvent : Event, Cancelable by Cancelable()

@EventHandler
fun onExample(event: ExampleEvent) {
    // Stop further processing according to the active CancelMode
    event.markCanceled()
}
```

When posting, you choose how the bus interprets cancellation via CancelMode:

```kotlin
bus.post(event) // default: CancelMode.RESPECT

bus.post(event, cancelMode = CancelMode.IGNORE)
bus.post(event, cancelMode = CancelMode.RESPECT)
bus.post(event, cancelMode = CancelMode.ENFORCE)
```

**CancelMode semantics:**

- `IGNORE` – Cancellation is treated as purely informational.  
  All handlers run in normal priority order, regardless of `event.canceled`.  
  The `runIfCanceled` flag on handlers is ignored.

- `RESPECT` *(default)* – Cancellation acts as a **per‑handler filter**.  
  If `event` is not canceled, all handlers run.  
  If `event` *is* canceled, then:
    - handlers with `@EventHandler(runIfCanceled = true)` still run;
    - handlers with `runIfCanceled = false` are skipped.
      Dispatch never short‑circuits; all eligible handlers are invoked.

- `ENFORCE` – Cancellation acts as a **hard stop**.  
  As soon as the event is observed in a canceled state—either before posting or during handler execution—no further handlers are invoked, regardless of `runIfCanceled`.

Handlers can opt into receiving canceled events when using `CancelMode.RESPECT`:

```kotlin
class AuditSubscriber {
    @EventHandler(runIfCanceled = true, priority = -10)
    fun audit(event: ExampleEvent) {
        println("Audit log for ${event}: canceled = ${event.canceled}")
    }
}
```

In this setup:

- With `CancelMode.RESPECT`, normal handlers (default `runIfCanceled = false`) are skipped once the event is canceled, but `audit` still runs.
- With `CancelMode.IGNORE`, all handlers run regardless of cancellation.
- With `CancelMode.ENFORCE`, no handlers after the cancellation point will run at all.

### ✏️ Modifiable Events

Events can advertise that their state was modified by handlers:

```kotlin
class ExampleEvent : Event, Modifiable by Modifiable()

@EventHandler
fun onEdit(event: ExampleEvent) {
    event.markModified()
}
```

Consumers can then react conditionally:

```kotlin
bus.post(event)
if (event.modified) {
    // Persist / recompute / update caches
}
```

### 🧲 Static Handler Support (Events & Exceptions)

You can declare global, static handlers on classes or Kotlin `object` / `companion object` members using `@JvmStatic`:

```kotlin
class StaticHandlers {
    companion object {
        @JvmStatic
        @EventHandler(priority = 10)
        fun onEvent(event: SomeEvent) { /* ... */ }

        @JvmStatic
        @ExceptionHandler
        fun onFailure(event: SomeEvent, t: Throwable) { /* ... */ }
    }
}
```

Register and unregister them via `subscribeStatic` / `unsubscribeStatic`:

```kotlin
bus.subscribeStatic(StaticHandlers::class)
bus.post(SomeEvent())
bus.unsubscribeStatic(StaticHandlers::class)
```

You can also do:

```kotlin
bus.subscribeStatic<StaticHandlers>()
bus.post(SomeEvent())
bus.unsubscribeStatic<StaticHandlers>()
```

Static handlers are strongly referenced and remain active until explicitly unregistered.

---

## 📦 Installation (Gradle Kotlin DSL)

To use this with Gradle, add the following to your `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.eventLibrary)
}
```

And the following to your `gradle/libs.versions.toml`:
```toml
[versions]
# Check this on https://central.sonatype.com/artifact/com.smushytaco/event-library/
eventLibrary = "4.1.0"

[libraries]
eventLibrary = { group = "com.smushytaco", name = "event-library", version.ref = "eventLibrary" }
```

---

## 🧠 Key Concepts

### Bus

The `Bus` interface is the core dispatch system.  
You typically create an instance via the companion factory:

```kotlin
val bus = Bus() // returns an internal EventManager
```

### Events

Any class implementing `Event` qualifies:

```kotlin
class MyEvent : Event
```

Optional behaviors via delegation:

```kotlin
class RichEvent :
    Event,
    Cancelable by Cancelable(),
    Modifiable by Modifiable()
```

### Subscribers

Any object can subscribe:

```kotlin
val subscriber = MySubscriber()
bus.subscribe(subscriber)
```

Unsubscribe when you’re done (though weak references will also clean up once the object is GC’d):

```kotlin
bus.unsubscribe(subscriber)
```

### Event Handlers (`@EventHandler`)

Requirements:

- Annotated with `@EventHandler`
- Exactly one parameter, implementing `Event`
- `Unit` / `void` return type

Example:

```kotlin
class MySubscriber {
    @EventHandler(priority = 5)
    private fun onMyEvent(event: MyEvent) {
        println("Got event: $event")
    }

    @EventHandler(runIfCanceled = true, priority = -10)
    fun onCanceledMyEvent(event: MyEvent) {
        println("Observed MyEvent after cancellation: ${event.canceled}")
    }
}
```

Handlers are invoked in **descending priority** order. For event handlers, handlers with the same priority are invoked in the order they were effectively registered.

Cancellation behavior for handlers depends on both the event’s `Cancelable` state and the `CancelMode` used for `post(...)`, as described in the **Cancelable Events & Cancel Modes** section.

### Exception Handlers (`@ExceptionHandler`)

Exception handlers live on the same subscriber types as event handlers and follow the same priority rules, but with the three supported shapes:

```kotlin
class MySubscriber {

    @EventHandler
    fun onMyEvent(event: MyEvent) {
        // This might throw
        riskyOperation()
    }

    // Specific event + throwable
    @ExceptionHandler(priority = 10)
    fun onMyEventFailure(event: MyEvent, t: IllegalStateException) {
        println("MyEvent failed with illegal state: ${t.message}")
    }

    // Event-only catch-all
    @ExceptionHandler
    fun onAnyMyEventFailure(event: MyEvent) {
        println("MyEvent failed with some exception")
    }

    // Throwable-only global handler
    @ExceptionHandler(priority = -10)
    fun onAnyException(t: Exception) {
        println("Some handler somewhere threw: ${t.message}")
    }
}
```

The bus calls *all* matching exception handlers (unless one of them throws), ordered by priority and specificity.

---

## 📖 End-to-End Example

### 1. Define an event

```kotlin
class MessageEvent(var text: String) :
    Event,
    Cancelable by Cancelable(),
    Modifiable by Modifiable()
```

### 2. Define a subscriber with event + exception handlers

```kotlin
class MessageSubscriber {

    @EventHandler(priority = 10)
    fun onMessage(event: MessageEvent) {
        println("Handling message: ${event.text}")

        if (event.text.contains("stop", ignoreCase = true)) {
            // Mark the event as canceled; how this affects dispatch depends
            // on the CancelMode chosen at post time.
            event.markCanceled()
        }

        if (event.text.contains("boom", ignoreCase = true)) {
            throw IllegalArgumentException("Boom!")
        }

        event.text = "Hello!"
        event.markModified()
    }

    @EventHandler(priority = 0)
    fun after(event: MessageEvent) {
        println("Second handler: ${event.text}")
    }

    @ExceptionHandler
    fun onMessageFailure(event: MessageEvent, t: IllegalArgumentException) {
        println("Message handler failed: ${t.message}")
    }

    @ExceptionHandler
    fun onAnyMessageFailure(event: MessageEvent) {
        println("Some exception happened while handling a MessageEvent.")
    }
}
```

### 3. Wire it together

```kotlin
val bus = Bus()
val subscriber = MessageSubscriber()

bus.subscribe(subscriber)

val event = MessageEvent("Hello, boom world")

// Choose how cancellation should behave:
// - IGNORE: treat canceled as informational only.
// - RESPECT (default): only run handlers that opt in via runIfCanceled once canceled.
// - ENFORCE: stop dispatch as soon as the event is canceled.
bus.post(event, cancelMode = CancelMode.ENFORCE)

println("Canceled?  ${event.canceled}")
println("Modified? ${event.modified}")
```

Depending on the `CancelMode` you choose and how your handlers are annotated with `runIfCanceled`, you can implement:

- simple, fire‑every‑handler semantics,
- fine‑grained “some handlers still run after cancellation” pipelines, or
- strict “first handler to cancel aborts everything” behavior.

---

## ⚡ Benchmarks

This library was benchmarked against the most popular JVM event buses using **JMH (Java Microbenchmark Harness)**.
Tests were performed on JDK 17 (OpenJDK 64-Bit Server VM).

### 🚀 Throughput (Higher is Better)
*Operations per microsecond (ops/µs)*

In concurrent scenarios, `Event-Library` scales almost linearly, while competitors suffer from lock contention and degrade in performance.

| Library           | 1 Thread | 4 Threads | 8 Threads |
|:------------------|:---------|:----------|:----------|
| **Event Library** | **49.3** | **130.2** | **361.4** |
| GreenRobot        | 18.9     | 15.2      | 5.1       |
| Guava             | 11.2     | 8.4       | 7.4       |
| MBassador         | 4.9      | 2.2       | 2.1       |

> **Note:** At 8 threads, this library is **~70x faster** than GreenRobot, **~48x faster** than Guava, and **~170x faster** than MBassador.

### ⏱️ Latency (Lower is Better)
*Nanoseconds per operation (ns/op)*

Measures the time it takes to post a single event to a single subscriber.

| Benchmark          | Time (ns)    | Overhead vs Direct Call |
|:-------------------|:-------------|:------------------------|
| Direct Method Call | 1.15 ns      | —                       |
| **Event Library**  | **18.63 ns** | **~17 ns**              |
| GreenRobot         | 47.47 ns     | ~46 ns                  |
| Guava              | 87.60 ns     | ~86 ns                  |
| MBassador          | 203.31 ns    | ~202 ns                 |

> **Note:** The dispatch latency is **~2.5x lower** than GreenRobot, **~4.7x lower** than Guava, and **~10x lower** than MBassador.

### 📈 Handler Scaling
Even with a high number of subscribers, dispatch remains incredibly fast (nanoseconds).

| Handlers | Dispatch Time |
|:---------|:--------------|
| 1        | 20.5 ns       |
| 4        | 23.8 ns       |
| 16       | 33.0 ns       |
| 64       | 90.6 ns       |

---

## 🧬 Internals (High-Level)

- Uses reflection **once** at subscription time to discover handlers.
- Compiles handlers into fast lambdas using `LambdaMetafactory` when possible.
- Falls back to reflection if needed, while preserving correctness.
- Caches resolved handler lists per event type (including supertypes/interfaces) for both events and exceptions.
- Automatically prunes handlers whose subscriber instances have been garbage‑collected.

You get a simple, annotation-driven API with performance close to hand-written dispatch logic.

---

## 🏁 Summary

This event system is:

- Easy to integrate (just `Bus()`, `Event`, and annotations)
- Fast in the hot path thanks to compiled invokers
- Flexible, with:
    - cancelable and modifiable events
    - instance and static handlers
    - rich, typed exception handling via `@ExceptionHandler`
    - configurable cancellation behavior via CancelMode and per-handler `runIfCanceled`
- Memory‑friendly due to weak references and automatic handler cleanup

Great for plugins, modular architectures, game engines, and any system that benefits from decoupled, event-driven communication.

---

## 📜 License

Apache 2.0 — see the [LICENSE](LICENSE) file for details.
