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

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage

import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackFragmentSpec {
    @Test
    fun testResetStatisticsMenuItemIsHandled() {
        val item = mockk<MenuItem>()
        every { item.itemId } returns R.id.item_track_reset_stats
        val scenario = launchFragmentInContainer<TrackFragmentTestImpl>()

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
class TrackFragmentTestImpl : TrackFragment() {
    val mockStorage = createMockTrackStorage()

    override fun createTrackStorage(): TrackStorage = mockStorage

    /**
     * Create a mock to replace the [TrackStorage] used by the fragment.
     */
    private fun createMockTrackStorage(): TrackStorage {
        val handler = mockk<PreferencesHandler>()

        val storage = mockk<TrackStorage>(relaxed = true)
        every { storage.preferencesHandler } returns handler

        return storage
    }
}
