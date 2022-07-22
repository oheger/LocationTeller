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

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage

import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus

import io.kotest.inspectors.forAll

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

import java.util.Calendar
import java.util.Date

/**
 * Test class for the Tracking UI.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackUiSpec {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `Tracking statistics is displayed`() {
        val statisticTags = listOf(
            TAG_TRACK_START,
            TAG_TRACK_END,
            TAG_TRACK_SPEED,
            TAG_TRACK_DIST,
            TAG_TRACK_CHECKS,
            TAG_TRACK_LAST_CHECK,
            TAG_TRACK_ERRORS
        )

        statisticTags.forAll {
            composeTestRule.onNodeWithTag(labelTag(it)).assertExists()
        }
    }

    @Test
    fun `Changes on preferences update values in the statistics`() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val preferencesHandler = PreferencesHandler.getInstance(activity.applicationContext)
            preferencesHandler.update {
                putLong(TrackStorage.PROP_TRACKING_START, createDate(2022, Calendar.JULY, 1).time)
                putInt(TrackStorage.PROP_CHECK_COUNT, 42)
                putInt(TrackStorage.PROP_LAST_DISTANCE, 77)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag(TAG_TRACK_START).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(TAG_TRACK_CHECKS).assertTextEquals("42")
        composeTestRule.onNodeWithTag(TAG_TRACK_LAST_DIST).assertTextEquals("77")
        composeTestRule.onNodeWithTag(TAG_TRACK_START).assertTextContains("2022", substring = true)
    }

    @Test
    fun `The permission request button is displayed if no location permission is granted`() {
        val permissionState = mockk<PermissionState>()
        every { permissionState.status } returns PermissionStatus.Denied(shouldShowRationale = false)
        installTrackSwitch(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertDoesNotExist()
    }

    @Test
    fun `The permission details text is displayed if required by the permission status`() {
        val permissionState = mockk<PermissionState>()
        every { permissionState.status } returns PermissionStatus.Denied(shouldShowRationale = true)
        installTrackSwitch(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertDoesNotExist()
    }

    @Test
    fun `The enabled switch is displayed if the location permission is granted`() {
        val permissionState = mockk<PermissionState>()
        every { permissionState.status } returns PermissionStatus.Granted
        installTrackSwitch(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `Clicking the request permissions button launches the request`() {
        val permissionState = mockk<PermissionState>(relaxed = true)
        every { permissionState.status } returns PermissionStatus.Denied(shouldShowRationale = false)
        installTrackSwitch(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).performClick()

        verify {
            permissionState.launchPermissionRequest()
        }
    }

    /**
     * Create a [TrackEnabledSwitch] instance and set it as content for [composeTestRule]. Pass [permissionState] to
     * the switch UI.
     */
    private fun installTrackSwitch(permissionState: PermissionState) {
        composeTestRule.setContent {
            TrackEnabledSwitch(enabled = true, onStateChange = {}, locationPermissionState = permissionState)
        }
    }
}

/**
 * Generate a [Date] object from the given parameters.
 */
private fun createDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Date {
    val cal = Calendar.getInstance()
    cal.set(year, month, day, hour, minute, second)
    return cal.time
}
