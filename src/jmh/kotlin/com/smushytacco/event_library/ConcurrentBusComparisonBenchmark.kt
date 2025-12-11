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
import com.google.common.eventbus.EventBus as GuavaEventBus
import com.google.common.eventbus.Subscribe as GuavaSubscribe
import net.engio.mbassy.bus.MBassador as MBassadorEventBus
import net.engio.mbassy.listener.Handler as MBassadorHandler
import org.greenrobot.eventbus.EventBus as GreenrobotEventBus
import org.greenrobot.eventbus.Subscribe as GreenrobotSubscribe

@Suppress("unused")
@State(Scope.Benchmark)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ConcurrentBusComparisonBenchmark {
    @Param("1", "4", "16")
    var handlerCount: Int = 0

    private lateinit var smushyBus: Bus
    private lateinit var guavaBus: GuavaEventBus
    private lateinit var mbassadorBus: MBassadorEventBus<Any>
    private lateinit var greenrobotBus: GreenrobotEventBus

    private val event = SimpleEvent()

    @Setup(Level.Trial)
    fun setup() {
        smushyBus = Bus()
        repeat(handlerCount) {
            smushyBus.subscribe(SmushySubscriber())
        }
        smushyBus.post(event)

        guavaBus = GuavaEventBus()
        repeat(handlerCount) {
            guavaBus.register(GuavaSubscriber())
        }
        guavaBus.post(event)

        mbassadorBus = MBassadorEventBus()
        repeat(handlerCount) {
            mbassadorBus.subscribe(MBassadorSubscriber())
        }
        mbassadorBus.post(event).now()

        greenrobotBus = GreenrobotEventBus.builder().build()
        repeat(handlerCount) {
            greenrobotBus.register(GreenrobotSubscriber())
        }
        greenrobotBus.post(event)
    }

    @Benchmark
    @Threads(1)
    fun smushyBusPost1Thread() {
        smushyBus.post(event)
    }

    @Benchmark
    @Threads(4)
    fun smushyBusPost4Threads() {
        smushyBus.post(event)
    }

    @Benchmark
    @Threads(8)
    fun smushyBusPost8Threads() {
        smushyBus.post(event)
    }

    @Benchmark
    @Threads(1)
    fun guavaEventBusPost1Thread() {
        guavaBus.post(event)
    }

    @Benchmark
    @Threads(4)
    fun guavaEventBusPost4Threads() {
        guavaBus.post(event)
    }

    @Benchmark
    @Threads(8)
    fun guavaEventBusPost8Threads() {
        guavaBus.post(event)
    }

    @Benchmark
    @Threads(1)
    fun mbassadorPost1Thread() {
        mbassadorBus.post(event).now()
    }

    @Benchmark
    @Threads(4)
    fun mbassadorPost4Threads() {
        mbassadorBus.post(event).now()
    }

    @Benchmark
    @Threads(8)
    fun mbassadorPost8Threads() {
        mbassadorBus.post(event).now()
    }

    @Benchmark
    @Threads(1)
    fun greenrobotEventBusPost1Thread() {
        greenrobotBus.post(event)
    }

    @Benchmark
    @Threads(4)
    fun greenrobotEventBusPost4Threads() {
        greenrobotBus.post(event)
    }

    @Benchmark
    @Threads(8)
    fun greenrobotEventBusPost8Threads() {
        greenrobotBus.post(event)
    }

    class SimpleEvent : Event

    class SmushySubscriber {
        private val counter = AtomicInteger(0)

        @EventHandler
        fun on(event: SimpleEvent) {
            counter.incrementAndGet()
        }
    }

    class GuavaSubscriber {
        private val counter = AtomicInteger(0)

        @GuavaSubscribe
        fun on(event: SimpleEvent) {
            counter.incrementAndGet()
        }
    }

    class MBassadorSubscriber {
        private val counter = AtomicInteger(0)

        @MBassadorHandler
        fun on(event: SimpleEvent) {
            counter.incrementAndGet()
        }
    }

    class GreenrobotSubscriber {
        private val counter = AtomicInteger(0)

        @GreenrobotSubscribe
        fun on(event: SimpleEvent) {
            counter.incrementAndGet()
        }
    }
}
