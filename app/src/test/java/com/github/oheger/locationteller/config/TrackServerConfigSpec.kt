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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

/**
 * Test class for [TrackServerConfig].
 */
class TrackServerConfigSpec : WordSpec({
    "fromPreferences" should {
        "create a new instance" {
            val preferencesHandler = mockk<PreferencesHandler>()
            every { preferencesHandler.getString(TrackServerConfig.PROP_SERVER_URI) } returns TEST_CONFIG.serverUri
            every { preferencesHandler.getString(TrackServerConfig.PROP_BASE_PATH) } returns TEST_CONFIG.basePath
            every { preferencesHandler.getString(TrackServerConfig.PROP_USER) } returns TEST_CONFIG.user
            every { preferencesHandler.getString(TrackServerConfig.PROP_PASSWORD) } returns TEST_CONFIG.password

            val config = TrackServerConfig.fromPreferences(preferencesHandler)

            config shouldBe TEST_CONFIG
        }

        "handle undefined properties" {
            val preferencesHandler = mockk<PreferencesHandler>()
            every { preferencesHandler.getString(any()) } returns ""

            val config = TrackServerConfig.fromPreferences(preferencesHandler)

            config shouldBe TrackServerConfig.EMPTY
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

    "save" should {
        "store the configuration in preferences" {
            val handler = mockk<PreferencesHandler>(relaxed = true)
            val editor = mockk<SharedPreferences.Editor>()
            val slotUpdater = slot<SharedPreferences.Editor.() -> Unit>()
            every { handler.update(capture(slotUpdater)) } just runs
            every { editor.putString(any(), any()) } returns editor

            TEST_CONFIG.save(handler)
            slotUpdater.captured(editor)

            verify {
                editor.putString(TrackServerConfig.PROP_SERVER_URI, TEST_CONFIG.serverUri)
                editor.putString(TrackServerConfig.PROP_BASE_PATH, TEST_CONFIG.basePath)
                editor.putString(TrackServerConfig.PROP_USER, TEST_CONFIG.user)
                editor.putString(TrackServerConfig.PROP_PASSWORD, TEST_CONFIG.password)
            }
        }
    }

    "the EMPTY instance" should {
        "not be defined" {
            TrackServerConfig.EMPTY.isDefined() shouldBe false
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
