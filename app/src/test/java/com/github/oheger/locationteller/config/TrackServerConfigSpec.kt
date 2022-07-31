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
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

/**
 * Test class for [TrackServerConfig].
 */
class TrackServerConfigSpec : WordSpec({
    "fromPreferences" should {
        "create a new instance" {
            val prefs = mockk<SharedPreferences>()
            every { prefs.getString(TrackServerConfig.PROP_SERVER_URI, null) } returns TEST_CONFIG.serverUri
            every { prefs.getString(TrackServerConfig.PROP_BASE_PATH, null) } returns TEST_CONFIG.basePath
            every { prefs.getString(TrackServerConfig.PROP_USER, null) } returns TEST_CONFIG.user
            every { prefs.getString(TrackServerConfig.PROP_PASSWORD, null) } returns TEST_CONFIG.password
            val preferencesHandler = mockk<PreferencesHandler>()
            every { preferencesHandler.preferences } returns prefs

            val config = TrackServerConfig.fromPreferences(preferencesHandler)

            config shouldBe TEST_CONFIG
        }

        "handle undefined properties" {
            val expectedConfig = TrackServerConfig("", "", "", "")
            val prefs = mockk<SharedPreferences>()
            every { prefs.getString(any(), null) } returns null
            val preferencesHandler = mockk<PreferencesHandler>()
            every { preferencesHandler.preferences } returns prefs

            val config = TrackServerConfig.fromPreferences(preferencesHandler)

            config shouldBe expectedConfig
        }
    }

    "isDefined" should {
        "return true for a fully defined configuration" {
            TEST_CONFIG.isDefined() shouldBe true
        }

        "return false if at least one mandatory property is undefined" {
            val undefinedConfigs = listOf(
                TEST_CONFIG.copy(serverUri = ""),
                TEST_CONFIG.copy(basePath = ""),
                TEST_CONFIG.copy(user = ""),
                TEST_CONFIG.copy(password = "")
            )

            undefinedConfigs.forAll {
                it.isDefined() shouldBe false
            }
        }
    }
})

/** A fully populated test configuration. */
private val TEST_CONFIG = TrackServerConfig(
    "https://track.example.org",
    "my/tracks",
    "scott",
    "tiger"
)
