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

import android.app.Application

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

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
    fun `Tracking statistics is not displayed for undefined fields if tracking is disabled`() {
        val model = installTrackView(trackingEnabled = false)

        model.trackStatistics.startTime = null
        model.trackStatistics.endTime = null
        model.trackStatistics.averageSpeed = null
        model.trackStatistics.numberOfChecks = null
        model.trackStatistics.lastErrorTime = null
        model.trackStatistics.numberOfErrors = null

        val statisticTags = listOf(
            TAG_TRACK_START,
            TAG_TRACK_END,
            TAG_TRACK_SPEED,
            TAG_TRACK_CHECKS,
            TAG_TRACK_ERRORS,
            TAG_TRACK_LAST_ERROR
        )
        statisticTags.forAll {
            composeTestRule.onNodeWithTag(labelTag(it)).assertDoesNotExist()
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
        installTrackView(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertDoesNotExist()
    }

    @Test
    fun `The permission details text is displayed if required by the permission status`() {
        val permissionState = mockk<PermissionState>()
        every { permissionState.status } returns PermissionStatus.Denied(shouldShowRationale = true)
        installTrackView(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertDoesNotExist()
    }

    @Test
    fun `The enabled switch is displayed if the location permission is granted`() {
        val permissionState = mockk<PermissionState>()
        every { permissionState.status } returns PermissionStatus.Granted
        installTrackView(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).assertExists()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_DETAILS).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_MESSAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `Clicking the request permissions button launches the request`() {
        val permissionState = mockk<PermissionState>(relaxed = true)
        every { permissionState.status } returns PermissionStatus.Denied(shouldShowRationale = false)
        installTrackView(permissionState)

        composeTestRule.onNodeWithTag(TAG_TRACK_PERM_BUTTON).performClick()

        verify {
            permissionState.launchPermissionRequest()
        }
    }

    @Test
    fun `Undefined tracking statistics are displayed if tracking is active`() {
        installTrackView(trackingEnabled = true)

        val statisticTags = listOf(
            TAG_TRACK_START,
            TAG_TRACK_END,
            TAG_TRACK_SPEED,
            TAG_TRACK_DIST,
            TAG_TRACK_CHECKS,
            TAG_TRACK_LAST_CHECK,
        )
        statisticTags.forAll {
            composeTestRule.onNodeWithTag(labelTag(it)).assertExists()
        }
    }

    @Test
    fun `Defined statistics about errors is displayed if tracking is active`() {
        val model = installTrackView(trackingEnabled = true)

        model.trackStorage.recordError(20220728204659L, 11)

        composeTestRule.onNodeWithTag(labelTag(TAG_TRACK_ERRORS)).assertExists()
        composeTestRule.onNodeWithTag(labelTag(TAG_TRACK_LAST_ERROR)).assertExists()
    }

    @Test
    fun `Undefined statistics about errors is not displayed if tracking is active`() {
        installTrackView(trackingEnabled = true)

        composeTestRule.onNodeWithTag(labelTag(TAG_TRACK_ERRORS)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(labelTag(TAG_TRACK_LAST_ERROR)).assertDoesNotExist()
    }

    /**
     * Create an instance of the track view and set it as content for [composeTestRule]. Pass [permissionState] to
     * the switch UI. Return the underling [TrackViewModel].
     */
    private fun installTrackView(permissionState: PermissionState): TrackViewModelImpl {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = TrackViewModelImpl(application)

        composeTestRule.setContent {
            TrackView(model = model, locationPermissionState = permissionState)
        }
        return model
    }

    /**
     * Create and install an instance of the track view with active permissions and the given
     * [tracking enabled state][trackingEnabled]. Return the underlying [TrackViewModel].
     */
    private fun installTrackView(trackingEnabled: Boolean): TrackViewModelImpl {
        val permissionState = mockk<PermissionState>(relaxed = true)
        every { permissionState.status } returns PermissionStatus.Granted

        val model = installTrackView(permissionState)
        model.updateTrackingState(trackingEnabled)
        return model
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
