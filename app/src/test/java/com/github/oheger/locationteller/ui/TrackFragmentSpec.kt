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

import android.view.MenuItem
import android.view.View
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackFragmentSpec {
    /**
     * Convenience function to fetch the tracking enabled switch control to
     * interact with it in an espresso test.
     * @return the _ViewInteraction_ for the tracking enabled switch
     */
    private fun trackingSwitch() = onView(withId(R.id.switchTrackEnabled))

    /**
     * Set the state of the checkbox that enables or disables tracking to
     * [enabled].
     */
    private fun setTrackingSwitch(enabled: Boolean) {
        val action = object : ViewAction {
            override fun getDescription(): String = "set switch state"

            override fun getConstraints(): Matcher<View> = isEnabled()

            override fun perform(uiController: UiController?, view: View?) {
                if (view is Switch) {
                    view.isChecked = enabled
                }
            }

        }
        trackingSwitch().perform(action)
    }

    @Test
    fun testInitialTrackingStateActiveIsReported() {
        launchFragmentInContainer<TrackFragmentTestImplWithTrackingActive>()

        trackingSwitch().check(matches(isChecked()))
    }

    @Test
    fun testInitialTrackingStateInactiveIsReported() {
        launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        trackingSwitch().check(matches(isNotChecked()))
    }

    @Test
    fun `location action is invoked when tracking is started`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        every { LocationPermAction.create(any(), any(), any()) } returns mockPermAction
        every { mockPermAction.execute() } just runs
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        setTrackingSwitch(true)
        scenario.onFragment { fragment ->
            verify {
                mockPermAction.execute()
            }
            verify(exactly = 0) {
                fragment.mockStorage.setTrackingEnabled(true)
            }
        }
    }

    @Test
    fun `callback of the location action enables tracking`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        val slotFragment = slot<Fragment>()
        val slotAction = slot<() -> Unit>()
        every { LocationPermAction.create(capture(slotFragment), capture(slotAction), any()) } returns mockPermAction
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        scenario.onFragment { fragment ->
            slotFragment.captured shouldBe fragment
            slotAction.captured()
            verify {
                fragment.mockStorage.setTrackingEnabled(true)
            }
        }
    }

    @Test
    fun `reject callback of the location action resets the tracking check box`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        val slotReject = slot<() -> Unit>()
        every { LocationPermAction.create(any(), any(), capture(slotReject)) } returns mockPermAction
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingActive>()

        scenario.onFragment { fragment ->
            slotReject.captured()
            verify(exactly = 0) {
                fragment.mockStorage.setTrackingEnabled(true)
            }
            trackingSwitch().check(matches(isNotChecked()))
        }
    }

    @Test
    fun testPrefHandlerIsUpdatedWhenTrackingIsStopped() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingActive>()

        setTrackingSwitch(false)
        scenario.onFragment { fragment ->
            verify {
                fragment.mockStorage.setTrackingEnabled(false)
            }
        }
    }

    @Test
    fun `statistics are reset when tracking starts`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        val slotAction = slot<() -> Unit>()
        every { LocationPermAction.create(any(), capture(slotAction), any()) } returns mockPermAction
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithAutoResetStats>()

        scenario.onFragment { fragment ->
            slotAction.captured()
            verify {
                fragment.mockStorage.resetStatistics()
            }
        }
    }

    @Test
    fun `statistics are only reset if the autoReset flag is set`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        val slotAction = slot<() -> Unit>()
        every { LocationPermAction.create(any(), capture(slotAction), any()) } returns mockPermAction
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        scenario.onFragment { fragment ->
            slotAction.captured()
            verify(exactly = 0) {
                fragment.mockStorage.resetStatistics()
            }
        }
    }

    @Test
    fun testStatisticsAreOnlyResetIfTrackingIsStarted() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingActiveAndAutoResetStats>()

        setTrackingSwitch(false)
        scenario.onFragment { fragment ->
            verify {
                fragment.mockStorage.setTrackingEnabled(false)
            }
            verify(exactly = 0) {
                fragment.mockStorage.resetStatistics()
            }
        }
    }

    @Test
    fun testResetStatisticsMenuItemIsHandled() {
        val item = mockk<MenuItem>()
        every { item.itemId } returns R.id.item_track_reset_stats
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        scenario.onFragment { fragment ->
            fragment.hasOptionsMenu() shouldBe true
            fragment.onOptionsItemSelected(item) shouldBe true
            verify {
                fragment.mockStorage.resetStatistics()
            }
        }
    }
}

/**
 * A test implementation of [TrackFragment] that uses mock objects for the [PreferencesHandler] and the
 * [TrackStorage].
 */
open class TrackFragmentTestImpl(
    /** The tracking state to be reported by the [TrackStorage]. */
    trackingEnabled: Boolean,

    /** Flag whether statistics should be reset when starting tracking anew. */
    resetStats: Boolean = false
) : TrackFragment() {
    val mockStorage = createTrackStorage(trackingEnabled, resetStats)

    override fun createTrackStorage(): TrackStorage = mockStorage

    private fun createTrackStorage(trackingEnabled: Boolean, resetStats: Boolean): TrackStorage {
        val handler = mockk<PreferencesHandler>()
        every { handler.isAutoResetStats() } returns resetStats

        val storage = mockk<TrackStorage>(relaxed = true)
        every { storage.preferencesHandler } returns handler
        every { storage.isTrackingEnabled() } returns trackingEnabled

        return storage
    }
}

/**
 * A test implementation of [TrackFragment] that sets the initial tracking state to *true*.
 */
class TrackFragmentTestImplWithTrackingActive : TrackFragmentTestImpl(trackingEnabled = true)

/**
 * A test implementation of [TrackFragment] that sets the initial tracking state to *false*.
 */
class TrackFragmentTestImplWithTrackingInactive : TrackFragmentTestImpl(trackingEnabled = false)

/**
 * A test implementation of [TrackFragment] that sets the flag to auto reset tracking statistics to *true*.
 */
class TrackFragmentTestImplWithAutoResetStats : TrackFragmentTestImpl(trackingEnabled = false, resetStats = true)

/**
 * A test implementation of [TrackFragment] that sets the initial tracking state to *true* and has also the auto
 * reset flag set.
 */
class TrackFragmentTestImplWithTrackingActiveAndAutoResetStats : TrackFragmentTestImpl(
    trackingEnabled = true,
    resetStats = true
)
