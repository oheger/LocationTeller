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
            prefHandler.getNumeric(
                TrackConfig.PROP_MIN_TRACK_INTERVAL,
                defaultValue = TrackConfig.DEFAULT.minTrackInterval
            )
        } returns config.minTrackInterval
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_MAX_TRACK_INTERVAL,
                defaultValue = TrackConfig.DEFAULT.maxTrackInterval
            )
        } returns config.maxTrackInterval
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_IDLE_INCREMENT,
                defaultValue = TrackConfig.DEFAULT.intervalIncrementOnIdle
            )
        } returns config.intervalIncrementOnIdle
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_LOCATION_VALIDITY,
                defaultValue = TrackConfig.DEFAULT.locationValidity
            )
        } returns config.locationValidity
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_LOCATION_UPDATE_THRESHOLD,
                defaultValue = TrackConfig.DEFAULT.locationUpdateThreshold
            )
        } returns config.locationUpdateThreshold
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_RETRY_ON_ERROR_TIME,
                defaultValue = TrackConfig.DEFAULT.retryOnErrorTime
            )
        } returns config.retryOnErrorTime
        every {
            prefHandler.getNumeric(TrackConfig.PROP_GPS_TIMEOUT, defaultValue = TrackConfig.DEFAULT.gpsTimeout)
        } returns config.gpsTimeout
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_OFFLINE_STORAGE_SIZE,
                defaultValue = TrackConfig.DEFAULT.offlineStorageSize
            )
        } returns config.offlineStorageSize
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_OFFLINE_STORAGE_SYNC_TIME,
                defaultValue = TrackConfig.DEFAULT.maxOfflineStorageSyncTime
            )
        } returns config.maxOfflineStorageSyncTime
        every {
            prefHandler.getNumeric(
                TrackConfig.PROP_MULTI_UPLOAD_CHUNK_SIZE,
                defaultValue = TrackConfig.DEFAULT.multiUploadChunkSize
            )
        } returns config.multiUploadChunkSize
        every {
            prefHandler.getDouble(
                TrackConfig.PROP_MAX_SPEED_INCREASE,
                defaultValue = TrackConfig.DEFAULT.maxSpeedIncrease
            )
        } returns config.maxSpeedIncrease
        every {
            prefHandler.getDouble(TrackConfig.PROP_WALKING_SPEED, 1.0 / 3.6, TrackConfig.DEFAULT.walkingSpeed)
        } returns config.walkingSpeed
        every { prefs.getBoolean(TrackConfig.PROP_AUTO_RESET_STATS, false) } returns true

        val newConfig = TrackConfig.fromPreferences(prefHandler)

        newConfig shouldBe config
    }

    "Values can be saved in shared preferences" {
        val handler = mockk<PreferencesHandler>()
        val pref = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        val slotUpdater = slot<SharedPreferences.Editor.() -> Unit>()
        every { handler.preferences } returns pref
        every { handler.update(capture(slotUpdater)) } just runs
        every { pref.contains(any()) } returns false
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        val config = TrackConfig.DEFAULT.copy(minTrackInterval = 42)

        config.save(handler)
        slotUpdater.captured(editor)

        verify {
            editor.putString(TrackConfig.PROP_MIN_TRACK_INTERVAL, config.minTrackInterval.toString())
            editor.putString(TrackConfig.PROP_MAX_TRACK_INTERVAL, config.maxTrackInterval.toString())
            editor.putString(TrackConfig.PROP_IDLE_INCREMENT, config.intervalIncrementOnIdle.toString())
            editor.putString(TrackConfig.PROP_LOCATION_VALIDITY, config.locationValidity.toString())
            editor.putString(TrackConfig.PROP_LOCATION_UPDATE_THRESHOLD, config.locationUpdateThreshold.toString())
            editor.putString(TrackConfig.PROP_RETRY_ON_ERROR_TIME, config.retryOnErrorTime.toString())
            editor.putString(TrackConfig.PROP_GPS_TIMEOUT, config.gpsTimeout.toString())
            editor.putString(TrackConfig.PROP_OFFLINE_STORAGE_SIZE, config.offlineStorageSize.toString())
            editor.putString(TrackConfig.PROP_OFFLINE_STORAGE_SYNC_TIME, config.maxOfflineStorageSyncTime.toString())
            editor.putString(TrackConfig.PROP_MULTI_UPLOAD_CHUNK_SIZE, config.multiUploadChunkSize.toString())
            editor.putString(TrackConfig.PROP_MAX_SPEED_INCREASE, config.maxSpeedIncrease.toString())
            editor.putString(TrackConfig.PROP_WALKING_SPEED, config.walkingSpeed.toString())
            editor.putBoolean(TrackConfig.PROP_AUTO_RESET_STATS, config.autoResetStats)
        }
    }

    "Values can be saved without overriding existing properties" {
        val handler = mockk<PreferencesHandler>()
        val pref = mockk<SharedPreferences>()
        every { handler.preferences } returns pref
        every { pref.contains(any()) } returns true

        TrackConfig.DEFAULT.save(handler, keepExisting = true)

        verify(exactly = 0) { handler.update(any()) }
    }

    "Configuration properties can be identified" {
        val configProps = listOf(
            TrackConfig.PROP_AUTO_RESET_STATS,
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
