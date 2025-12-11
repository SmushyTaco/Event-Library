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

package com.smushytacco.event_library

import com.smushytaco.event_library.api.Bus
import com.smushytaco.event_library.api.Event
import com.smushytaco.event_library.api.EventHandler
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@Suppress("unused")
@State(Scope.Thread)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class SubscribeUnsubscribeBenchmark {
    lateinit var bus: Bus

    @Setup(Level.Iteration)
    fun setup() {
        bus = Bus()
    }

    @Benchmark
    fun subscribeInstance() {
        val sub = InstanceSubscriber()
        bus.subscribe(sub)
    }

    @Benchmark
    fun subscribeThenUnsubscribeInstance() {
        val sub = InstanceSubscriber()
        bus.subscribe(sub)
        bus.unsubscribe(sub)
    }

    @Benchmark
    fun subscribeStatic() {
        bus.subscribeStatic(StaticSubscriber::class)
    }

    @Benchmark
    fun subscribeThenUnsubscribeStatic() {
        bus.subscribeStatic(StaticSubscriber::class)
        bus.unsubscribeStatic(StaticSubscriber::class)
    }

    class BenchEvent : Event

    class InstanceSubscriber {
        @EventHandler
        fun on(event: BenchEvent) {}
    }

    class StaticSubscriber {
        companion object {
            @JvmStatic
            @EventHandler
            fun on(event: BenchEvent) {}
        }
    }
}

