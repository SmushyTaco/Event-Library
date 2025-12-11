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
import java.util.concurrent.atomic.AtomicInteger

@Suppress("unused")
@State(Scope.Benchmark)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ConcurrentPostBenchmark {
    @Param("1", "4", "16")
    var handlerCount: Int = 0

    private lateinit var bus: Bus
    private val event = SimpleEvent()

    @Setup(Level.Trial)
    fun setup() {
        bus = Bus()
        repeat(handlerCount) {
            bus.subscribe(CountingSubscriber())
        }
        bus.post(event)
    }

    @Benchmark
    @Threads(1)
    fun post1Thread() {
        bus.post(event)
    }

    @Benchmark
    @Threads(4)
    fun post4Threads() {
        bus.post(event)
    }

    @Benchmark
    @Threads(8)
    fun post8Threads() {
        bus.post(event)
    }

    class SimpleEvent : Event

    class CountingSubscriber {
        private val counter = AtomicInteger(0)
        @EventHandler
        fun on(event: SimpleEvent) {
            counter.incrementAndGet()
        }
    }
}
