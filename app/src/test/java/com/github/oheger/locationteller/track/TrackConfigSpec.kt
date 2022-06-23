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
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

/**
 * Test class for [TrackConfig].
 */
class TrackConfigSpec : StringSpec({
    "An instance can be created from shared preferences" {
        val config = TrackConfig(
            minTrackInterval = 120,
            maxTrackInterval = 600,
            intervalIncrementOnIdle = 60,
            locationValidity = 3600,
            locationUpdateThreshold = 88,
            retryOnErrorTime = 111,
            gpsTimeout = 77,
            offlineStorageSize = 42,
            maxOfflineStorageSyncTime = 22,
            multiUploadChunkSize = 4,
            autoResetStats = true,
            maxSpeedIncrease = 3.1415,
            walkingSpeed = 2.4
        )

        val prefs = mockk<SharedPreferences>()
        val prefHandler = mockk<PreferencesHandler>()
        every { prefHandler.preferences } returns prefs
        every {
            prefHandler.getNumeric(TrackConfig.PROP_MIN_TRACK_INTERVAL, 60, TrackConfig.DEFAULT_MIN_TRACK_INTERVAL)
        } returns config.minTrackInterval
        every {
            prefHandler.getNumeric(TrackConfig.PROP_MAX_TRACK_INTERVAL, 60, TrackConfig.DEFAULT_MAX_TRACK_INTERVAL)
        } returns config.maxTrackInterval
        every {
            prefHandler.getNumeric(TrackConfig.PROP_IDLE_INCREMENT, 60, TrackConfig.DEFAULT_IDLE_INCREMENT)
        } returns config.intervalIncrementOnIdle
        every {
            prefHandler.getNumeric(TrackConfig.PROP_LOCATION_VALIDITY, 60, TrackConfig.DEFAULT_LOCATION_VALIDITY)
        } returns config.locationValidity
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_LOCATION_UPDATE_THRESHOLD,
                defaultValue = TrackConfig.DEFAULT_LOCATION_UPDATE_THRESHOLD
            )
        } returns config.locationUpdateThreshold
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_RETRY_ON_ERROR_TIME,
                defaultValue = TrackConfig.DEFAULT_RETRY_ON_ERROR_TIME
            )
        } returns config.retryOnErrorTime
        every {
            prefHandler.getNumeric(TrackConfig.PROP_GPS_TIMEOUT, defaultValue = TrackConfig.DEFAULT_GPS_TIMEOUT)
        } returns config.gpsTimeout
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_OFFLINE_STORAGE_SIZE,
                defaultValue = TrackConfig.DEFAULT_OFFLINE_STORAGE_SIZE
            )
        } returns config.offlineStorageSize
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_OFFLINE_STORAGE_SYNC_TIME,
                defaultValue = TrackConfig.DEFAULT_OFFLINE_STORAGE_SYNC_TIME
            )
        } returns config.maxOfflineStorageSyncTime
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_MULTI_UPLOAD_CHUNK_SIZE,
                defaultValue = TrackConfig.DEFAULT_MULTI_UPLOAD_CHUNK_SIZE
            )
        } returns config.multiUploadChunkSize
        every {
            prefHandler.getDouble(
                TrackConfig.PROP_MAX_SPEED_INCREASE,
                defaultValue = TrackConfig.DEFAULT_MAX_SPEED_INCREASE
            )
        } returns config.maxSpeedIncrease
        every {
            prefHandler.getDouble(TrackConfig.PROP_WALKING_SPEED, 1.0 / 3.6, TrackConfig.DEFAULT_WALKING_SPEED)
        } returns config.walkingSpeed
        every { prefs.getBoolean(TrackConfig.PROP_AUTO_RESET_STATS, false) } returns true

        val newConfig = TrackConfig.fromPreferences(prefHandler)

        newConfig shouldBe config
    }

    "Default values can be set in shared preferences" {
        val handler = mockk<PreferencesHandler>()
        val pref = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        val slotUpdater = slot<SharedPreferences.Editor.() -> Unit>()
        every { handler.preferences } returns pref
        every { handler.update(capture(slotUpdater)) } just runs
        every { pref.contains(any()) } returns false
        every { editor.putString(any(), any()) } returns editor

        TrackConfig.initDefaults(handler)
        slotUpdater.captured(editor)

        verify {
            editor.putString(
                TrackConfig.PROP_MIN_TRACK_INTERVAL,
                (TrackConfig.DEFAULT_MIN_TRACK_INTERVAL / 60).toString()
            )
            editor.putString(
                TrackConfig.PROP_MAX_TRACK_INTERVAL,
                (TrackConfig.DEFAULT_MAX_TRACK_INTERVAL / 60).toString()
            )
            editor.putString(
                TrackConfig.PROP_IDLE_INCREMENT,
                (TrackConfig.DEFAULT_IDLE_INCREMENT / 60).toString()
            )
            editor.putString(
                TrackConfig.PROP_LOCATION_VALIDITY,
                (TrackConfig.DEFAULT_LOCATION_VALIDITY / 60).toString()
            )
            editor.putString(
                TrackConfig.PROP_LOCATION_UPDATE_THRESHOLD,
                TrackConfig.DEFAULT_LOCATION_UPDATE_THRESHOLD.toString()
            )
            editor.putString(
                TrackConfig.PROP_RETRY_ON_ERROR_TIME,
                TrackConfig.DEFAULT_RETRY_ON_ERROR_TIME.toString()
            )
            editor.putString(
                TrackConfig.PROP_GPS_TIMEOUT,
                TrackConfig.DEFAULT_GPS_TIMEOUT.toString()
            )
        }
    }

    "Setting default values does not override existing properties" {
        val handler = mockk<PreferencesHandler>()
        val pref = mockk<SharedPreferences>()
        every { handler.preferences } returns pref
        every { pref.contains(any()) } returns true

        TrackConfig.initDefaults(handler)

        verify(exactly = 0) { handler.update(any()) }
    }

    "Configuration properties can be identified" {
        val configProps = listOf(
            TrackConfig.PROP_IDLE_INCREMENT,
            TrackConfig.PROP_LOCATION_VALIDITY,
            TrackConfig.PROP_MAX_TRACK_INTERVAL,
            TrackConfig.PROP_MIN_TRACK_INTERVAL,
            TrackConfig.PROP_LOCATION_UPDATE_THRESHOLD,
            TrackConfig.PROP_RETRY_ON_ERROR_TIME,
            TrackConfig.PROP_GPS_TIMEOUT,
            TrackConfig.PROP_OFFLINE_STORAGE_SIZE,
            TrackConfig.PROP_OFFLINE_STORAGE_SYNC_TIME,
            TrackConfig.PROP_MULTI_UPLOAD_CHUNK_SIZE,
            TrackConfig.PROP_MAX_SPEED_INCREASE,
            TrackConfig.PROP_WALKING_SPEED
        )

        configProps.forAll { prop -> TrackConfig.isProperty(prop) shouldBe true }
    }

    "Non-configuration properties can be identified" {
        val nonConfigProps = listOf("foo", "bar", "baz")

        nonConfigProps.forAll { prop -> TrackConfig.isProperty(prop) shouldBe false }
    }
})
