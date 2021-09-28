/*
 * Copyright 2019-2021 The Developers.
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
import android.widget.ListView
import android.widget.Switch
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.track.PreferencesHandler
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TrackFragmentSpec {
    /**
     * Executes a test with the list view in a started fragment.
     * @param block the action that does something with the list view
     */
    private fun doWithListView(block: (ListView) -> Unit) {
        val action = object : ViewAction {
            override fun getDescription(): String = "testAction"

            override fun getConstraints(): Matcher<View> = isEnabled()

            override fun perform(uiController: UiController?, view: View?) {
                val listView = view as ListView
                block(listView)
            }

        }
        onView(withId(R.id.trackingStats)).perform(action)
    }

    /**
     * Convenience function to fetch the tracking enabled switch control to
     * interact with it in an espresso test.
     * @return the _ViewInteraction_ for the tracking enabled switch
     */
    private fun trackingSwitch() = onView(withId(R.id.switchTrackEnabled))

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
    fun testAdapterIsInitialized() {
        launchFragmentInContainer<TrackFragment>()

        doWithListView {
            assertTrue("Wrong adapter", it.adapter is TrackingStatsListAdapter)
        }
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
    fun testStepsOnActivation() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        scenario.onFragment { fragment ->
            verify {
                fragment.mockAdapter.activate()
            }
        }
    }

    @Test
    fun testStepsOnDeactivation() {
        val refFrame = AtomicReference<TrackFragmentTestImpl>()
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()
        scenario.onFragment { refFrame.set(it) }

        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertNotNull("No frame reference", refFrame.get())
        verify {
            refFrame.get().mockAdapter.deactivate()
        }
    }

    @Test
    fun testPrefHandlerIsUpdatedWhenTrackingIsStarted() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        setTrackingSwitch(true)
        scenario.onFragment { fragment ->
            verify {
                fragment.mockPrefHandler.setTrackingEnabled(true)
            }
        }
    }

    @Test
    fun testPrefHandlerIsUpdatedWhenTrackingIsStopped() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingActive>()

        setTrackingSwitch(false)
        scenario.onFragment { fragment ->
            verify {
                fragment.mockPrefHandler.setTrackingEnabled(false)
            }
        }
    }

    @Test
    fun testStatisticsAreResetWhenTrackingStarts() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithAutoResetStats>()

        trackingSwitch().perform(click())
        scenario.onFragment { fragment ->
            verify {
                fragment.mockPrefHandler.resetStatistics()
            }
        }
    }

    @Test
    fun testStatisticsAreOnlyResetIfTheAutoResetFlagIsSet() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingInactive>()

        trackingSwitch().perform(click())
        scenario.onFragment { fragment ->
            verify(exactly = 0) {
                fragment.mockPrefHandler.resetStatistics()
            }
        }
    }

    @Test
    fun testStatisticsAreOnlyResetIfTrackingIsStarted() {
        val scenario = launchFragmentInContainer<TrackFragmentTestImplWithTrackingActiveAndAutoResetStats>()

        setTrackingSwitch(false)
        scenario.onFragment { fragment ->
            verify {
                fragment.mockPrefHandler.setTrackingEnabled(false)
            }
            verify(exactly = 0) {
                fragment.mockPrefHandler.resetStatistics()
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
                fragment.mockPrefHandler.resetStatistics()
            }
        }
    }
}

/**
 * A test implementation of _TrackFragment_ that uses mock objects for the
 * _PreferencesHandler_ and the _TrackingStatsListAdapter_.
 *
 * @param trackingEnabled the tracking state to be reported by the preferences
 * handler
 * @param resetStats flag whether statistics should be reset when starting
 * tracking anew
 */
open class TrackFragmentTestImpl(trackingEnabled: Boolean, resetStats: Boolean = false) : TrackFragment() {
    /** Initialized mock for the preference handler. */
    val mockPrefHandler = createPrefHandler(trackingEnabled, resetStats)

    /** A spy allowing to check interactions with the list adapter. */
    lateinit var mockAdapter: TrackingStatsListAdapter

    override fun createPreferencesHandler(): PreferencesHandler = mockPrefHandler

    override fun createTrackingStatsAdapter(prefHandler: PreferencesHandler): TrackingStatsListAdapter {
        assertEquals("Wrong preferences handler", mockPrefHandler, prefHandler)
        mockAdapter = spyk(super.createTrackingStatsAdapter(prefHandler))
        return mockAdapter
    }

    /**
     * Creates a mock for the preferences handler and prepares it to return
     * the tracking state specified.
     * @param trackingEnabled flag whether tracking should be reported as
     * active
     * @param resetStats the auto reset statistics flag
     * @return the mock for the preferences handler
     */
    private fun createPrefHandler(trackingEnabled: Boolean, resetStats: Boolean): PreferencesHandler {
        val handler = mockk<PreferencesHandler>(relaxed = true)
        every { handler.isTrackingEnabled() } returns trackingEnabled
        every { handler.isAutoResetStats() } returns resetStats
        return handler
    }
}

/**
 * A test implementation of _TrackFragment_ that sets the initial tracking
 * state to *true*.
 */
class TrackFragmentTestImplWithTrackingActive : TrackFragmentTestImpl(trackingEnabled = true)

/**
 * A test implementation of _TrackFragment_ that sets the initial tracking
 * state to *false*.
 */
class TrackFragmentTestImplWithTrackingInactive : TrackFragmentTestImpl(trackingEnabled = false)

/**
 * A test implementation of _TrackFragment_ that sets the flag to auto reset
 * tracking statistics to *true*.
 */
class TrackFragmentTestImplWithAutoResetStats : TrackFragmentTestImpl(trackingEnabled = false, resetStats = true)

/**
 * A test implementation of _TrackFragment_ that sets the initial tracking
 * state to *true* and has also the auto reset flag set.
 */
class TrackFragmentTestImplWithTrackingActiveAndAutoResetStats : TrackFragmentTestImpl(
    trackingEnabled = true,
    resetStats = true
)
