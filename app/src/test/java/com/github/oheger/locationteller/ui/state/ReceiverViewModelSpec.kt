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
package com.github.oheger.locationteller.ui.state

import android.app.Application

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.track.TrackTestHelper

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

/**
 * Test class for [ReceiverViewModel] and its default implementation.
 */
class ReceiverViewModelSpec : WordSpec() {
    /** The mock preferences handler. */
    private lateinit var preferencesHandler: PreferencesHandler

    /** The mock application context. */
    private lateinit var application: Application

    /** The mock for the config manager. */
    private lateinit var configManager: ConfigManager

    override suspend fun beforeAny(testCase: TestCase) {
        mockkObject(PreferencesHandler, ReceiverConfig)
        application = createApplicationMock()
        preferencesHandler = createPreferencesHandlerMock()
        configManager = createConfigManager()

        every { PreferencesHandler.getInstance(application) } returns preferencesHandler
    }

    init {
        "receiverConfig" should {
            "return a configuration initialized from preferences" {
                val model = createModel()

                model.receiverConfig shouldBe RECEIVER_CONFIG
            }
        }

        "updateReceiverConfig" should {
            "pass the new configuration to the config manager" {
                val newConfig = ReceiverConfig(
                    updateInterval = 600,
                    fadeOutEnabled = true,
                    centerNewPosition = false,
                    fastFadeOut = true,
                    strongFadeOut = false
                )
                every { configManager.updateReceiverConfig(application, newConfig) } just runs
                val model = createModel()

                model.updateReceiverConfig(newConfig)

                verify { configManager.updateReceiverConfig(application, newConfig) }
            }

            "react on change notifications" {
                val newConfig = RECEIVER_CONFIG.copy(updateInterval = 222)
                val model = createModel()

                receiverConfigChangeNotification(newConfig)

                model.receiverConfig shouldBe newConfig
            }
        }

        "the markerFactory" should {
            "be configured with a disabled alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(fadeOutEnabled = false))

                model.markerFactory.alphaCalculator shouldBe DisabledFadeOutAlphaCalculator
            }

            "be configured with a strong and slow alpha calculator" {
                val model = createModel()

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_SLOW_STRONG
            }

            "be configured with a normal and slow alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(strongFadeOut = false))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_SLOW
            }

            "be configured with a normal and fast alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(strongFadeOut = false, fastFadeOut = true))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_FAST
            }

            "be configured with a strong and fast alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(fastFadeOut = true))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_FAST_STRONG
            }

            "be configured with a correct delta formatter" {
                val model = createModel()

                val formatter = model.markerFactory.deltaFormatter

                formatter.unitDay shouldBe UNIT_DAY
                formatter.unitHour shouldBe UNIT_HOUR
                formatter.unitMin shouldBe UNIT_MINUTE
                formatter.unitSec shouldBe UNIT_SECOND
            }
        }
    }

    /**
     * Trigger a notification that the [ReceiverConfig] was changed to [newConfig].
     */
    private fun receiverConfigChangeNotification(newConfig: ReceiverConfig) {
        val slotListener = slot<(ReceiverConfig) -> Unit>()
        verify { configManager.addReceiverConfigChangeListener(capture(slotListener)) }
        slotListener.captured(newConfig)
    }

    /**
     * Create a mock for the [ConfigManager] that is already prepared for some expected interactions.
     */
    private fun createConfigManager(): ConfigManager {
        val configManagerMock = TrackTestHelper.prepareConfigManager(application, receiverConfig = RECEIVER_CONFIG)
        every { configManagerMock.addReceiverConfigChangeListener(any()) } just runs

        return configManagerMock
    }

    /**
     * Create a new test instance of [ReceiverViewModelImpl].
     */
    private fun createModel(): ReceiverViewModelImpl = ReceiverViewModelImpl(application)
}

/** A test configuration. */
private val RECEIVER_CONFIG = ReceiverConfig(
    updateInterval = 500,
    fadeOutEnabled = true,
    fastFadeOut = false,
    strongFadeOut = true,
    centerNewPosition = true
)

/** The unit to display for days. */
private const val UNIT_DAY = "dayStr"

/** The unit to display for hours. */
private const val UNIT_HOUR = "hourStr"

/** The unit to display for minutes. */
private const val UNIT_MINUTE = "minuteStr"

/** The unit to display for seconds. */
private const val UNIT_SECOND = "secondStr"

/**
 * Create a mock for the [PreferencesHandler] that is prepared to expect updates.
 */
private fun createPreferencesHandlerMock(): PreferencesHandler =
    mockk<PreferencesHandler>().apply {
        every { update(any()) } just runs
    }

/**
 * Create a mock for the [Application] that is prepared to be queried for the required string resources.
 */
private fun createApplicationMock(): Application =
    mockk<Application>().apply {
        every { getString(R.string.time_secs) } returns UNIT_SECOND
        every { getString(R.string.time_minutes) } returns UNIT_MINUTE
        every { getString(R.string.time_hours) } returns UNIT_HOUR
        every { getString(R.string.time_days) } returns UNIT_DAY
    }
