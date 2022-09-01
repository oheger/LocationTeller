/*
 * Copyright 2019-2022 The Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.oheger.locationteller.duration

import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.duration.TickerService.Companion.createTickerService

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.beInRange
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.setMain

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test class for [TickerService].
 */
class TickerServiceSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)

    init {
        "TickerService should send ticks periodically" {
            val counter = AtomicInteger()

            installMainDispatcher().use {
                val ticker = createTickerService(delayMillis = 250) { counter.incrementAndGet() }
                delay(1000)

                counter.get() should beInRange(3..4)
                ticker.cancel()
            }
        }

        "TickerService can be canceled" {
            val counter = AtomicInteger()

            installMainDispatcher().use {
                val ticker = createTickerService(delayMillis = 100) { counter.incrementAndGet() }
                ticker.cancel()

                val count = counter.get()
                delay(250)
                counter.get() shouldBe count
            }
        }
    }
}

/**
 * Install a dispatcher for the main context. This is necessary, because for the test execution, no main dispatcher is
 * configured.
 */
private fun installMainDispatcher(): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        .also { Dispatchers.setMain(it) }
