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
package com.github.oheger.locationteller.track

import android.content.SharedPreferences

import com.github.oheger.locationteller.config.PreferencesHandler

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import java.util.Date

class TrackStorageSpec : StringSpec({
    "An error can be recorded" {
        val errorTime = 20190704213348L
        val errorCount = 12

        val editor = checkPreferencesUpdate { handler ->
            TrackStorage(handler).recordError(errorTime, errorCount)
        }

        verify {
            editor.putLong(TrackStorage.PROP_LAST_ERROR, errorTime)
            editor.putInt(TrackStorage.PROP_ERROR_COUNT, errorCount)
        }
    }

    "An update can be recorded" {
        val updateTime = 20190704213752L
        val updateCount = 47
        val distance = 1111
        val totalDistance = 20200118161249L

        val editor = checkPreferencesUpdate { handler ->
            TrackStorage(handler).recordUpdate(updateTime, updateCount, distance, totalDistance)
        }

        verify {
            editor.putLong(TrackStorage.PROP_LAST_UPDATE, updateTime)
            editor.putInt(TrackStorage.PROP_UPDATE_COUNT, updateCount)
            editor.putInt(TrackStorage.PROP_LAST_DISTANCE, distance)
            editor.putLong(TrackStorage.PROP_TOTAL_DISTANCE, totalDistance)
        }
    }

    "A check can be recorded" {
        val checkTime = 20190711222122L
        val checkCount = 128

        val editor = checkPreferencesUpdate { handler ->
            TrackStorage(handler).recordCheck(checkTime, checkCount)
        }

        verify {
            editor.putLong(TrackStorage.PROP_LAST_CHECK, checkTime)
            editor.putInt(TrackStorage.PROP_CHECK_COUNT, checkCount)
        }
    }

    "The time of the last error can be queried" {
        val expectedErrorTime = Date(20190705180422L)
        val handler = mockk<PreferencesHandler>()
        every { handler.getDate(TrackStorage.PROP_LAST_ERROR) } returns expectedErrorTime

        val storage = TrackStorage(handler)
        val errorTime = storage.lastError()

        errorTime shouldBe expectedErrorTime
    }

    "The time of the last update can be queried" {
        val expectedUpdateTime = Date(20190705181104L)
        val handler = mockk<PreferencesHandler>()
        every { handler.getDate(TrackStorage.PROP_LAST_UPDATE) } returns expectedUpdateTime

        val storage = TrackStorage(handler)
        val updateTime = storage.lastUpdate()

        updateTime shouldBe expectedUpdateTime
    }

    "The time of the last check can be queried" {
        val expectedCheckTime = Date(20190711222611L)
        val handler = mockk<PreferencesHandler>()
        every { handler.getDate(TrackStorage.PROP_LAST_CHECK) } returns expectedCheckTime

        val storage = TrackStorage(handler)
        val checkTime = storage.lastCheck()

        checkTime shouldBe expectedCheckTime
    }

    "The tracking start time can be queried" {
        val expectedStartTime = Date(20200117215143L)
        val handler = mockk<PreferencesHandler>()
        every { handler.getDate(TrackStorage.PROP_TRACKING_START) } returns expectedStartTime

        val storage = TrackStorage(handler)
        val startTime = storage.trackingStartDate()

        startTime shouldBe expectedStartTime
    }

    "The tracking end time can be queried" {
        val expectedEndTime = Date(20200117220450L)
        val handler = mockk<PreferencesHandler>()
        every { handler.getDate(TrackStorage.PROP_TRACKING_END) } returns expectedEndTime

        val storage = TrackStorage(handler)
        val endTime = storage.trackingEndDate()

        endTime shouldBe expectedEndTime
    }

    "The distance of the last location update can be queried" {
        val distance = 157
        val handler = mockk<PreferencesHandler>()
        val preferences = handler.mockPreferences()
        every { preferences.getInt(TrackStorage.PROP_LAST_DISTANCE, 0) } returns distance

        val storage = TrackStorage(handler)

        storage.lastDistance() shouldBe distance
    }

    "The total tracking distance can be queried" {
        val distance = 20200118160148L
        val handler = mockk<PreferencesHandler>()
        val preferences = handler.mockPreferences()
        every { preferences.getLong(TrackStorage.PROP_TOTAL_DISTANCE, 0) } returns distance

        val storage = TrackStorage(handler)
        val totalDistance = storage.totalDistance()

        totalDistance shouldBe distance
    }

    "The error count can be queried" {
        val count = 61
        val handler = mockk<PreferencesHandler>()
        val preferences = handler.mockPreferences()
        every { preferences.getInt(TrackStorage.PROP_ERROR_COUNT, 0) } returns count

        val storage = TrackStorage(handler)
        val errorCount = storage.errorCount()

        errorCount shouldBe count
    }

    "The check count can be queried" {
        val count = 77
        val handler = mockk<PreferencesHandler>()
        val preferences = handler.mockPreferences()
        every { preferences.getInt(TrackStorage.PROP_CHECK_COUNT, 0) } returns count

        val storage = TrackStorage(handler)
        val checkCount = storage.checkCount()

        checkCount shouldBe count
    }

    "The update count can be queried" {
        val count = 99
        val handler = mockk<PreferencesHandler>()
        val preferences = handler.mockPreferences()
        every { preferences.getInt(TrackStorage.PROP_UPDATE_COUNT, 0) } returns count

        val storage = TrackStorage(handler)
        val updateCount = storage.updateCount()

        updateCount shouldBe count
    }

    "The tracking statistic can be reset" {
        val editor = checkPreferencesUpdate { handler ->
            TrackStorage(handler).resetStatistics()
        }

        verify {
            editor.remove(TrackStorage.PROP_TOTAL_DISTANCE)
            editor.remove(TrackStorage.PROP_ERROR_COUNT)
            editor.remove(TrackStorage.PROP_LAST_CHECK)
            editor.remove(TrackStorage.PROP_LAST_DISTANCE)
            editor.remove(TrackStorage.PROP_LAST_UPDATE)
            editor.remove(TrackStorage.PROP_LAST_ERROR)
            editor.remove(TrackStorage.PROP_CHECK_COUNT)
            editor.remove(TrackStorage.PROP_UPDATE_COUNT)
        }
    }
})

/**
 * Create a mock [PreferencesHandler] and prepare it to expect an _update()_ invocation; then invoke [block] to make
 * use of this mock. Return the [SharedPreferences.Editor] mock passed to the update lambda. This can now be used to
 * verify whether the expected updates were made.
 */
private fun checkPreferencesUpdate(block: (PreferencesHandler) -> Unit): SharedPreferences.Editor {
    val handler = mockk<PreferencesHandler>()
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    val slotUpdater = slot<SharedPreferences.Editor.() -> Unit>()
    every { handler.update(capture(slotUpdater)) } just runs

    block(handler)

    slotUpdater.captured(editor)
    return editor
}

/**
 * Prepare this mock of a [PreferencesHandler] to return its [SharedPreferences]. Create mock [SharedPreferences] for
 * this purpose.
 */
private fun PreferencesHandler.mockPreferences(): SharedPreferences =
    mockk<SharedPreferences>().also {
        every { preferences } returns it
    }
