# Event Library
[![Maven Central](https://img.shields.io/maven-central/v/com.smushytaco/event-library.svg?label=maven%20central)](https://central.sonatype.com/artifact/com.smushytaco/event-library)
[![Dokka Docs](https://img.shields.io/badge/docs-dokka-brightgreen.svg)](https://smushytaco.github.io/Event-Library)

A lightweight, reflection‑assisted but LambdaMetafactory‑accelerated event bus for Kotlin and JVM.  
This library focuses on **simplicity**, **performance**, and **zero boilerplate**, while supporting:

- Private, protected, internal, or public handler methods
- Handler prioritization
- Cancelable events
- Modifiable events
- Weak subscriber references (automatic cleanup)
- Polymorphic dispatch (handlers for supertype events receive subtype events)
- High‑performance invocation using `LambdaMetafactory`, with reflective fallback

---

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

### 🚀 High‑Performance Invocation
If possible, the library compiles handlers into fast invokedynamic lambdas.  
If the JVM blocks access (module restrictions, visibility, etc.), it automatically falls back to reflection.

### 🧹 Weak Subscriber References
Subscribers are held via `WeakReference`, meaning they are automatically removed when garbage‑collected.

### 🛑 Cancelable Events
Event propagation can be interrupted via:

```kotlin
class ExampleEvent : Event, Cancelable by Cancelable()

@EventHandler
fun onExample(event: ExampleEvent) {
    event.markCanceled()
}
```

### 🔧 Modifiable Events
Events can indicate that handlers modified their state:

```kotlin
class ExampleEvent : Event, Modifiable by Modifiable()

@EventHandler
fun onEdit(event: ExampleEvent) {
    event.markModified()
}
```

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
eventLibrary = "1.0.0"

[libraries]
eventLibrary = { group = "com.smushytaco", name = "event-library", version.ref = "eventLibrary" }
```

---

## 🧠 Key Concepts

### Bus
The `Bus` interface is the core dispatch system.  
Users simply call:

```kotlin
val bus = Bus()
```

This invokes the factory inside the companion object and returns an internal `EventManager` instance.

### Events
Any class implementing `Event` qualifies:

```kotlin
class MyEvent : Event
```

Optional behavior is available by delegation:

```kotlin
class EditableEvent :
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

Unsubscribe:

```kotlin
bus.unsubscribe(subscriber)
```

### Handler Methods
Must satisfy:

- Annotated with `@EventHandler`
- Not static
- Takes exactly **one** subtype of `Event`
- Returns `Unit`

Handlers may be:
- private
- internal
- protected
- public

The library supports all visibility levels.

### Handler Priority
Higher priority runs earlier:

```kotlin
@EventHandler(priority = 10)
fun important(event: ExampleEvent)
```

---

## 🧬 Internals

### Method Discovery
The bus scans:

- The class
- All superclasses
- All interfaces

It gathers **non-bridge**, **non-synthetic** declared methods.

### Handler Validation
A method must:

1. Be annotated with `@EventHandler`
2. Not be static
3. Return `void`/`Unit`
4. Take exactly one parameter
5. The parameter type must implement `Event`

### Invocation Strategy

#### 1. Try to create a LambdaMetafactory‑backed `EventInvoker`:

- Uses `MethodHandles.privateLookupIn` (Java 9+)
- Falls back to default lookup if private lookup fails
- If any step fails → go to fallback

#### 2. Reflection fallback
Guaranteed to work even if:

- Class is private
- Method is private
- Security manager blocks invokedynamic
- Module boundaries disallow deep access

### Handler Resolution
When an event is posted, the bus:

1. Looks up handlers for the event type
2. Walks its superclasses and interfaces
3. Combines all handlers
4. Sorts by priority
5. Caches the resulting list

Cache invalidates whenever new subscribers subscribe or unsubscribe.

---

## 📖 Example Usage

### Define event:

```kotlin
class MessageEvent(val msg: String) :
    Event, Modifiable by Modifiable()
```

### Define subscriber:

```kotlin
class MessageListener {
    @EventHandler(priority = 5)
    private fun onMessage(event: MessageEvent) {
        println("Received: ${event.msg}")
        event.markModified()
    }
}
```

### Fire event:

```kotlin
val bus = Bus()
val listener = MessageListener()

bus.subscribe(listener)

val event = MessageEvent("Hello")
bus.post(event)

println("Modified: ${event.modified}")
```

---

## 🏁 Summary

This event system is:

- Easy to use
- Fast when possible
- Fully compatible with private handlers
- Robust thanks to graceful fallback behavior
- Memory‑friendly via weak references
- Flexible due to Cancelable & Modifiable interfaces

Perfect for plugins, modular architectures, game engines, or any system requiring decoupled communication.

---

## 📜 License

Apache 2.0 — see the [LICENSE](LICENSE) file for details.

