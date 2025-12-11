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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class SingleThreadBusComparisonBenchmark {
    private lateinit var smushyBus: Bus
    private lateinit var guavaBus: GuavaEventBus
    private lateinit var mbassadorBus: MBassadorEventBus<Any>
    private lateinit var greenrobotBus: GreenrobotEventBus

    private val event = SimpleEvent()

    @Setup(Level.Trial)
    fun setup() {
        smushyBus = Bus()
        smushyBus.subscribe(SmushySubscriber())
        smushyBus.post(event)

        guavaBus = GuavaEventBus()
        guavaBus.register(GuavaSubscriber())
        guavaBus.post(event)

        mbassadorBus = MBassadorEventBus()
        mbassadorBus.subscribe(MBassadorSubscriber())
        mbassadorBus.post(event).now()

        greenrobotBus = GreenrobotEventBus.builder().build()
        greenrobotBus.register(GreenrobotSubscriber())
        greenrobotBus.post(event)
    }

    @Benchmark
    fun smushyBusPost() {
        smushyBus.post(event)
    }

    @Benchmark
    fun guavaEventBusPost() {
        guavaBus.post(event)
    }

    @Benchmark
    fun mbassadorPost() {
        mbassadorBus.post(event).now()
    }

    @Benchmark
    fun greenrobotEventBusPost() {
        greenrobotBus.post(event)
    }

    class SimpleEvent : Event

    class SmushySubscriber {
        private var counter: Int = 0

        @EventHandler
        fun on(event: SimpleEvent) {
            counter++
        }
    }

    class GuavaSubscriber {
        private var counter: Int = 0

        @GuavaSubscribe
        fun on(event: SimpleEvent) {
            counter++
        }
    }

    class MBassadorSubscriber {
        private var counter: Int = 0

        @MBassadorHandler
        fun on(event: SimpleEvent) {
            counter++
        }
    }

    class GreenrobotSubscriber {
        private var counter: Int = 0

        @GreenrobotSubscribe
        fun on(event: SimpleEvent) {
            counter++
        }
    }
}
