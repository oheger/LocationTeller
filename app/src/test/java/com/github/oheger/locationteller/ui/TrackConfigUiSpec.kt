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
package com.github.oheger.locationteller.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.Navigation.findNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.track.TrackTestHelper

import io.kotest.matchers.shouldBe

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

/**
 * Test class for the UI for the tracking configuration.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackConfigUiSpec {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun navigateToTrackConfig() {
        composeTestRule.activityRule.scenario.onActivity {
            findNavController(it, R.id.nav_host_fragment).navigate(R.id.trackSettingsFragment)
        }
    }

    @Test
    fun `Track configuration can be edited`() {
        fun editValue(item: String, vararg values: String) {
            composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(item)).performScrollTo()
            composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(item)).performClick()
            values.forEachIndexed { index, value ->
                with(composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForIndexedItem(item, index))) {
                    performScrollTo()
                    performTextClearance()
                    performTextInput(value)
                }
            }
            composeTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(item)).performScrollTo()
            composeTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(item)).performClick()
        }

        val testConfig = TrackTestHelper.DEFAULT_TRACK_CONFIG
        editValue(CONFIG_ITEM_TRACK_MIN_INTERVAL, testConfig.minTrackInterval.toString(), "0")
        editValue(CONFIG_ITEM_TRACK_MAX_INTERVAL, "7", "12")
        editValue(CONFIG_ITEM_TRACK_IDLE_INCREMENT, testConfig.intervalIncrementOnIdle.toString(), "0")
        editValue(CONFIG_ITEM_TRACK_LOCATION_VALIDITY, "47", "23", "18", "12")
        editValue(CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD, testConfig.locationUpdateThreshold.toString())
        editValue(CONFIG_ITEM_TRACK_GPS_TIMEOUT, "0", "1")
        editValue(CONFIG_ITEM_TRACK_RETRY_ERROR_TIME, "40", "0")

        val currentConfig =
            TrackConfig.fromPreferences(PreferencesHandler.getInstance(ApplicationProvider.getApplicationContext()))
                // TODO: The following properties are not yet supported by the UI. They will be added later.
                .copy(
                    offlineStorageSize = testConfig.offlineStorageSize,
                    maxOfflineStorageSyncTime = testConfig.maxOfflineStorageSyncTime,
                    multiUploadChunkSize = testConfig.multiUploadChunkSize,
                    maxSpeedIncrease = testConfig.maxSpeedIncrease,
                    walkingSpeed = testConfig.walkingSpeed,
                    autoResetStats = testConfig.autoResetStats
                )
        currentConfig shouldBe testConfig
    }
}
