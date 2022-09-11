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
package com.github.oheger.locationteller.config

import android.content.SharedPreferences

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

/**
 * Test class for [ReceiverConfig].
 */
class ReceiverConfigSpec : WordSpec({
    "fromPreferences" should {
        "create an instance from preferences" {
            val preferencesHandler = mockk<PreferencesHandler>()
            every {
                preferencesHandler.getBoolean(ReceiverConfig.PROP_FADE_OUT_ENABLED)
            } returns TEST_CONFIG.fadeOutEnabled
            every {
                preferencesHandler.getBoolean(ReceiverConfig.PROP_FADE_OUT_FAST)
            } returns TEST_CONFIG.fastFadeOut
            every {
                preferencesHandler.getBoolean(ReceiverConfig.PROP_FADE_OUT_STRONG)
            } returns TEST_CONFIG.strongFadeOut
            every {
                preferencesHandler.getBoolean(ReceiverConfig.PROP_AUTO_CENTER)
            } returns TEST_CONFIG.centerNewPosition
            every {
                preferencesHandler.getInt(ReceiverConfig.PROP_UPDATE_INTERVAL, defaultValue = 180)
            } returns TEST_CONFIG.updateInterval

            val config = ReceiverConfig.fromPreferences(preferencesHandler)

            config shouldBe TEST_CONFIG
        }
    }

    "save" should {
        "save a configuration" {
            val handler = mockk<PreferencesHandler>(relaxed = true)
            val editor = mockk<SharedPreferences.Editor>()
            val slotUpdater = slot<SharedPreferences.Editor.() -> Unit>()
            every { handler.update(capture(slotUpdater)) } just runs
            every { editor.putBoolean(any(), any()) } returns editor
            every { editor.putInt(any(), any()) } returns editor

            TEST_CONFIG.save(handler)
            slotUpdater.captured(editor)

            verify {
                editor.putInt(ReceiverConfig.PROP_UPDATE_INTERVAL, TEST_CONFIG.updateInterval)
                editor.putBoolean(ReceiverConfig.PROP_AUTO_CENTER, TEST_CONFIG.centerNewPosition)
                editor.putBoolean(ReceiverConfig.PROP_FADE_OUT_ENABLED, TEST_CONFIG.fadeOutEnabled)
                editor.putBoolean(ReceiverConfig.PROP_FADE_OUT_FAST, TEST_CONFIG.fastFadeOut)
                editor.putBoolean(ReceiverConfig.PROP_FADE_OUT_STRONG, TEST_CONFIG.strongFadeOut)
            }
        }
    }
})

/** A configuration with test settings. */
private val TEST_CONFIG = ReceiverConfig(
    updateInterval = 90,
    fadeOutEnabled = true,
    fastFadeOut = true,
    strongFadeOut = true,
    centerNewPosition = true
)
