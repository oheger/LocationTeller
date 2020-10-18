/*
 * Copyright 2019-2020 The Developers.
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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.oheger.locationteller.server.ServerConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.math.abs

/**
 * Test class for [PreferencesHandler].
 */
class PreferencesHandlerSpec : StringSpec() {
    init {
        "PreferencesHandler should support updates on preferences" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putString("foo", "bar") } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.update { putString("foo", "bar") }
            verifyOrder {
                pref.edit()
                editor.putString("foo", "bar")
                editor.apply()
            }
        }

        "PreferencesHandler should return the current tracking state" {
            val pref = mockk<SharedPreferences>()
            every {
                pref.getBoolean(
                    PreferencesHandler.PROP_TRACK_STATE,
                    false
                )
            } returnsMany listOf(true, false)
            val handler = PreferencesHandler(pref)

            handler.isTrackingEnabled() shouldBe true
            handler.isTrackingEnabled() shouldBe false
        }

        "PreferencesHandler should allow setting the tracking state to true" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            val slotStartTime = slot<Long>()
            every { pref.edit() } returns editor
            every { editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, true) } returns editor
            every { editor.putLong(PreferencesHandler.PROP_TRACKING_START, any()) } returns editor
            every { editor.remove(PreferencesHandler.PROP_TRACKING_END) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setTrackingEnabled(true)
            verify {
                editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, true)
                editor.putLong(PreferencesHandler.PROP_TRACKING_START, capture(slotStartTime))
                editor.remove(PreferencesHandler.PROP_TRACKING_END)
                editor.apply()
            }
            assertCurrentTime(slotStartTime.captured)
        }

        "PreferencesHandler should allow setting the tracking state to false" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            val slotEndTime = slot<Long>()
            every { pref.edit() } returns editor
            every { editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false) } returns editor
            every { editor.putLong(PreferencesHandler.PROP_TRACKING_END, any()) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setTrackingEnabled(false)
            verify {
                editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false)
                editor.putLong(PreferencesHandler.PROP_TRACKING_END, capture(slotEndTime))
                editor.apply()
            }
            assertCurrentTime(slotEndTime.captured)
        }

        "PreferencesHandler should return the correct fading mode" {
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.PROP_FADING_MODE, 0) } returnsMany listOf(1, 2)
            val handler = PreferencesHandler(pref)

            handler.getFadingMode() shouldBe 1
            handler.getFadingMode() shouldBe 2
        }

        "PreferencesHandler should allow updating the fading mode" {
            val newMode = 42
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putInt(PreferencesHandler.PROP_FADING_MODE, newMode) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setFadingMode(newMode)
            verify(ordering = Ordering.ORDERED) {
                editor.putInt(PreferencesHandler.PROP_FADING_MODE, newMode)
                editor.apply()
            }
        }

        "PreferencesHandler should identify configuration properties" {
            val configProps = listOf(
                PreferencesHandler.PROP_BASE_PATH, PreferencesHandler.PROP_IDLE_INCREMENT,
                PreferencesHandler.PROP_LOCATION_VALIDITY, PreferencesHandler.PROP_MAX_TRACK_INTERVAL,
                PreferencesHandler.PROP_MIN_TRACK_INTERVAL, PreferencesHandler.PROP_PASSWORD,
                PreferencesHandler.PROP_USER, PreferencesHandler.PROP_SERVER_URI,
                PreferencesHandler.PROP_LOCATION_UPDATE_THRESHOLD, PreferencesHandler.PROP_RETRY_ON_ERROR_TIME,
                PreferencesHandler.PROP_GPS_TIMEOUT, PreferencesHandler.PROP_OFFLINE_STORAGE_SIZE,
                PreferencesHandler.PROP_OFFLINE_STORAGE_SYNC_TIME, PreferencesHandler.PROP_MULTI_UPLOAD_CHUNK_SIZE,
                PreferencesHandler.PROP_MAX_SPEED_INCREASE, PreferencesHandler.PROP_WALKING_SPEED
            )

            configProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe true }
        }

        "PreferencesHandler should identify non-configuration properties" {
            val nonConfigProps = listOf(PreferencesHandler.PROP_TRACK_STATE, "foo", "bar")

            nonConfigProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe false }
        }

        "PreferencesHandler should record an error" {
            val errorTime = 20190704213348L
            val errorCount = 12
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.PROP_LAST_ERROR, errorTime) } returns editor
            every { editor.putInt(PreferencesHandler.PROP_ERROR_COUNT, errorCount) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordError(errorTime, errorCount)
            verify {
                editor.putLong(PreferencesHandler.PROP_LAST_ERROR, errorTime)
                editor.putInt(PreferencesHandler.PROP_ERROR_COUNT, errorCount)
                editor.apply()
            }
        }

        "PreferencesHandler should record an update" {
            val updateTime = 20190704213752L
            val updateCount = 47
            val distance = 1111
            val totalDistance = 20200118161249L
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.PROP_LAST_UPDATE, updateTime) } returns editor
            every { editor.putInt(PreferencesHandler.PROP_UPDATE_COUNT, updateCount) } returns editor
            every { editor.putInt(PreferencesHandler.PROP_LAST_DISTANCE, distance) } returns editor
            every { editor.putLong(PreferencesHandler.PROP_TOTAL_DISTANCE, totalDistance) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordUpdate(updateTime, updateCount, distance, totalDistance)
            verify {
                editor.putLong(PreferencesHandler.PROP_LAST_UPDATE, updateTime)
                editor.putInt(PreferencesHandler.PROP_UPDATE_COUNT, updateCount)
                editor.putInt(PreferencesHandler.PROP_LAST_DISTANCE, distance)
                editor.putLong(PreferencesHandler.PROP_TOTAL_DISTANCE, totalDistance)
                editor.apply()
            }
        }

        "PreferencesHandler should record a check" {
            val checkTime = 20190711222122L
            val checkCount = 128
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.PROP_LAST_CHECK, checkTime) } returns editor
            every { editor.putInt(PreferencesHandler.PROP_CHECK_COUNT, checkCount) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordCheck(checkTime, checkCount)
            verify {
                editor.putLong(PreferencesHandler.PROP_LAST_CHECK, checkTime)
                editor.putInt(PreferencesHandler.PROP_CHECK_COUNT, checkCount)
                editor.apply()
            }
        }

        "PreferencesHandler should return the last error time" {
            val errorTime = 20190705180422L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_ERROR, errorTime)
            val handler = PreferencesHandler(pref)

            val errorDate = handler.lastError()
            errorDate!!.time shouldBe errorTime
        }

        "PreferencesHandler should return null for the last error if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.PROP_LAST_ERROR) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastError() shouldBe null
        }

        "PreferencesHandler should return the last update time" {
            val updateTime = 20190705181104L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_UPDATE, updateTime)
            val handler = PreferencesHandler(pref)

            val updateDate = handler.lastUpdate()
            updateDate!!.time shouldBe updateTime
        }

        "PreferencesHandler should return null for the last update if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.PROP_LAST_UPDATE) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastUpdate() shouldBe null
        }

        "PreferencesHandler should return the last check time" {
            val checkTime = 20190711222611L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_CHECK, checkTime)
            val handler = PreferencesHandler(pref)

            val checkDate = handler.lastCheck()
            checkDate!!.time shouldBe checkTime
        }

        "PreferencesHandler should handle a date in the long past" {
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_CHECK, 1000)
            val handler = PreferencesHandler(pref)

            val checkDate = handler.lastCheck()
            checkDate shouldBe null
        }

        "PreferencesHandler should return null for the last check if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.PROP_LAST_CHECK) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastCheck() shouldBe null
        }

        "PreferencesHandler should return the distance of the last location update" {
            val distance = 157
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.PROP_LAST_DISTANCE, 0) } returns distance
            val handler = PreferencesHandler(pref)

            handler.lastDistance() shouldBe distance
        }

        "PreferencesHandler should return the tracking start time" {
            val startTime = 20200117215143L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_TRACKING_START, startTime)
            val handler = PreferencesHandler(pref)

            val startDate = handler.trackingStartDate()
            startDate!!.time shouldBe startTime
        }

        "PreferencesHandler should return the tracking stop time" {
            val endTime = 20200117220450L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.PROP_TRACKING_END, endTime)
            val handler = PreferencesHandler(pref)

            val endDate = handler.trackingEndDate()
            endDate!!.time shouldBe endTime
        }

        "PreferencesHandler should return the total distance" {
            val distance = 20200118160148L
            val pref = mockk<SharedPreferences>()
            every { pref.getLong(PreferencesHandler.PROP_TOTAL_DISTANCE, 0) } returns distance
            val handler = PreferencesHandler(pref)

            val totalDistance = handler.totalDistance()
            totalDistance shouldBe distance
        }

        "PreferencesHandler should return the error count" {
            val count = 61
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.PROP_ERROR_COUNT, 0) } returns count
            val handler = PreferencesHandler(pref)

            val errorCount = handler.errorCount()
            errorCount shouldBe count
        }

        "PreferencesHandler should return the check count" {
            val count = 77
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.PROP_CHECK_COUNT, 0) } returns count
            val handler = PreferencesHandler(pref)

            val checkCount = handler.checkCount()
            checkCount shouldBe count
        }

        "PreferencesHandler should return the update count" {
            val count = 99
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.PROP_UPDATE_COUNT, 0) } returns count
            val handler = PreferencesHandler(pref)

            val updateCount = handler.updateCount()
            updateCount shouldBe count
        }

        "PreferencesHandler should allow resetting statistics data" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.remove(PreferencesHandler.PROP_ERROR_COUNT) } returns editor
            every { editor.remove(PreferencesHandler.PROP_TOTAL_DISTANCE) } returns editor
            every { editor.remove(PreferencesHandler.PROP_LAST_DISTANCE) } returns editor
            every { editor.remove(PreferencesHandler.PROP_LAST_CHECK) } returns editor
            every { editor.remove(PreferencesHandler.PROP_LAST_ERROR) } returns editor
            every { editor.remove(PreferencesHandler.PROP_LAST_UPDATE) } returns editor
            every { editor.remove(PreferencesHandler.PROP_CHECK_COUNT) } returns editor
            every { editor.remove(PreferencesHandler.PROP_UPDATE_COUNT) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.resetStatistics()
            verify {
                editor.remove(PreferencesHandler.PROP_ERROR_COUNT)
                editor.remove(PreferencesHandler.PROP_TOTAL_DISTANCE)
                editor.remove(PreferencesHandler.PROP_LAST_DISTANCE)
                editor.remove(PreferencesHandler.PROP_LAST_ERROR)
                editor.remove(PreferencesHandler.PROP_LAST_CHECK)
                editor.remove(PreferencesHandler.PROP_LAST_UPDATE)
                editor.remove(PreferencesHandler.PROP_CHECK_COUNT)
                editor.remove(PreferencesHandler.PROP_UPDATE_COUNT)
            }
        }

        "PreferencesHandler should support the registration of change listeners" {
            mockkStatic(PreferenceManager::class)
            val pref = mockk<SharedPreferences>()
            val context = mockk<Context>()
            val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
            every { pref.registerOnSharedPreferenceChangeListener(listener) } just runs

            PreferencesHandler.registerListener(context, listener)
            verify { pref.registerOnSharedPreferenceChangeListener(listener) }
        }

        "PreferencesHandler should support removing of change listeners" {
            mockkStatic(PreferenceManager::class)
            val pref = mockk<SharedPreferences>()
            val context = mockk<Context>()
            val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
            every { pref.unregisterOnSharedPreferenceChangeListener(listener) } just runs

            PreferencesHandler.unregisterListener(context, listener)
            verify { pref.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        "PreferencesHandler should create a track configuration" {
            val trackConfig = TrackConfig(
                60, 600, 120, 3600, 15, 27, 11,
                16, 30, 2, true, 2.5, 1.111
            )
            val pref = preferencesFromTrackConfig(trackConfig)
            val handler = PreferencesHandler(pref)

            handler.createTrackConfig() shouldBe trackConfig
        }

        "PreferencesHandler should return default values for a TrackConfig if properties are undefined" {
            val pref = mockk<SharedPreferences>()
            listOf(
                PreferencesHandler.PROP_MAX_TRACK_INTERVAL, PreferencesHandler.PROP_MIN_TRACK_INTERVAL,
                PreferencesHandler.PROP_IDLE_INCREMENT, PreferencesHandler.PROP_LOCATION_VALIDITY,
                PreferencesHandler.PROP_LOCATION_UPDATE_THRESHOLD, PreferencesHandler.PROP_RETRY_ON_ERROR_TIME,
                PreferencesHandler.PROP_GPS_TIMEOUT, PreferencesHandler.PROP_OFFLINE_STORAGE_SIZE,
                PreferencesHandler.PROP_OFFLINE_STORAGE_SYNC_TIME, PreferencesHandler.PROP_MULTI_UPLOAD_CHUNK_SIZE,
                PreferencesHandler.PROP_MAX_SPEED_INCREASE, PreferencesHandler.PROP_WALKING_SPEED
            ).forEach {
                initNumProperty(pref, it, -1)
            }
            every { pref.getBoolean(PreferencesHandler.PROP_AUTO_RESET_STATS, false) } returns false
            val handler = PreferencesHandler(pref)

            val config = handler.createTrackConfig()
            config.minTrackInterval shouldBe PreferencesHandler.DEFAULT_MIN_TRACK_INTERVAL
            config.maxTrackInterval shouldBe PreferencesHandler.DEFAULT_MAX_TRACK_INTERVAL
            config.intervalIncrementOnIdle shouldBe PreferencesHandler.DEFAULT_IDLE_INCREMENT
            config.locationValidity shouldBe PreferencesHandler.DEFAULT_LOCATION_VALIDITY
            config.locationUpdateThreshold shouldBe PreferencesHandler.DEFAULT_LOCATION_UPDATE_THRESHOLD
            config.retryOnErrorTime shouldBe PreferencesHandler.DEFAULT_RETRY_ON_ERROR_TIME
            config.gpsTimeout shouldBe PreferencesHandler.DEFAULT_GPS_TIMEOUT
            config.offlineStorageSize shouldBe PreferencesHandler.DEFAULT_OFFLINE_STORAGE_SIZE
            config.maxOfflineStorageSyncTime shouldBe PreferencesHandler.DEFAULT_OFFLINE_STORAGE_SYNC_TIME
            config.multiUploadChunkSize shouldBe PreferencesHandler.DEFAULT_MULTI_UPLOAD_CHUNK_SIZE
            config.maxSpeedIncrease shouldBe PreferencesHandler.DEFAULT_MAX_SPEED_INCREASE
            config.walkingSpeed shouldBe PreferencesHandler.DEFAULT_WALKING_SPEED
            config.autoResetStats shouldBe false
        }

        "PreferencesHandler should init shared preferences with track config defaults" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { pref.contains(any()) } returns false
            every { editor.putString(any(), any()) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.initTrackConfigDefaults()
            verify {
                editor.putString(
                    PreferencesHandler.PROP_MIN_TRACK_INTERVAL,
                    (PreferencesHandler.DEFAULT_MIN_TRACK_INTERVAL / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_MAX_TRACK_INTERVAL,
                    (PreferencesHandler.DEFAULT_MAX_TRACK_INTERVAL / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_IDLE_INCREMENT,
                    (PreferencesHandler.DEFAULT_IDLE_INCREMENT / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_LOCATION_VALIDITY,
                    (PreferencesHandler.DEFAULT_LOCATION_VALIDITY / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_LOCATION_UPDATE_THRESHOLD,
                    PreferencesHandler.DEFAULT_LOCATION_UPDATE_THRESHOLD.toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_RETRY_ON_ERROR_TIME,
                    PreferencesHandler.DEFAULT_RETRY_ON_ERROR_TIME.toString()
                )
                editor.putString(
                    PreferencesHandler.PROP_GPS_TIMEOUT,
                    PreferencesHandler.DEFAULT_GPS_TIMEOUT.toString()
                )
                editor.apply()
            }
        }

        "PreferencesHandler should not override already defined properties of the track config" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(any()) } returns true
            val handler = PreferencesHandler(pref)

            handler.initTrackConfigDefaults()  // should be noop
        }

        "PreferencesHandler should create a correct server configuration" {
            val handler = PreferencesHandler(preferencesFromServerConfig(defServerConfig))

            handler.createServerConfig() shouldBe defServerConfig
        }

        "PreferencesHandler should return a null server config if the URI is missing" {
            checkUndefinedServerConfig(defServerConfig.copy(serverUri = ""))
        }

        "PreferencesHandler should return a null server config if the base path is missing" {
            checkUndefinedServerConfig(defServerConfig.copy(basePath = ""))
        }

        "PreferencesHandler should return a null server config if the user name is missing" {
            checkUndefinedServerConfig(defServerConfig.copy(user = ""))
        }

        "PreferencesHandler should return a null server config if the password is missing" {
            checkUndefinedServerConfig(defServerConfig.copy(password = ""))
        }
    }

    companion object {
        /** A default server configuration.*/
        private val defServerConfig = ServerConfig(
            "https://track.tst", "/myTracks", "scott",
            "tiger"
        )

        /**
         * Prepares the given mock for a _SharedPreferences_ object to return a
         * value for a specific numeric property.
         * @param pref the _SharedPreferences_
         * @param property the name of the property
         * @param value the value to be returned for this property
         */
        private fun initNumProperty(pref: SharedPreferences, property: String, value: Any) {
            every {
                pref.getString(property, "-1")
            } returns value.toString()
        }

        /**
         * Prepares the given mock for a _SharedPreferences_ object to return a
         * value for a specific string property. Empty strings are mapped to
         * *null* values.
         * @param pref the _SharedPreferences_
         * @param property the name of the property
         * @param value the value to be returned for this property
         */
        private fun initProperty(pref: SharedPreferences, property: String, value: String) {
            every { pref.getString(property, null) } returns if (value.isEmpty()) null else value
        }

        /**
         * Creates a mock for a _SharedPreferences_ object that is prepared to
         * return the properties from the given track configuration.
         * @param trackConfig the track configuration
         * @return the mock _SharedPreferences_
         */
        private fun preferencesFromTrackConfig(trackConfig: TrackConfig): SharedPreferences {
            val pref = mockk<SharedPreferences>()
            initNumProperty(pref, PreferencesHandler.PROP_MIN_TRACK_INTERVAL, trackConfig.minTrackInterval / 60)
            initNumProperty(pref, PreferencesHandler.PROP_MAX_TRACK_INTERVAL, trackConfig.maxTrackInterval / 60)
            initNumProperty(pref, PreferencesHandler.PROP_IDLE_INCREMENT, trackConfig.intervalIncrementOnIdle / 60)
            initNumProperty(pref, PreferencesHandler.PROP_LOCATION_VALIDITY, trackConfig.locationValidity / 60)
            initNumProperty(
                pref,
                PreferencesHandler.PROP_LOCATION_UPDATE_THRESHOLD,
                trackConfig.locationUpdateThreshold
            )
            initNumProperty(pref, PreferencesHandler.PROP_RETRY_ON_ERROR_TIME, trackConfig.retryOnErrorTime)
            initNumProperty(pref, PreferencesHandler.PROP_GPS_TIMEOUT, trackConfig.gpsTimeout)
            initNumProperty(pref, PreferencesHandler.PROP_OFFLINE_STORAGE_SIZE, trackConfig.offlineStorageSize)
            initNumProperty(
                pref,
                PreferencesHandler.PROP_OFFLINE_STORAGE_SYNC_TIME,
                trackConfig.maxOfflineStorageSyncTime
            )
            initNumProperty(pref, PreferencesHandler.PROP_MULTI_UPLOAD_CHUNK_SIZE, trackConfig.multiUploadChunkSize)
            initNumProperty(pref, PreferencesHandler.PROP_MAX_SPEED_INCREASE, trackConfig.maxSpeedIncrease)
            initNumProperty(pref, PreferencesHandler.PROP_WALKING_SPEED, trackConfig.walkingSpeed * 3.6)
            every {
                pref.getBoolean(
                    PreferencesHandler.PROP_AUTO_RESET_STATS,
                    false
                )
            } returns trackConfig.autoResetStats
            return pref
        }

        /**
         * Creates a mock for a _SharedPreferences_ object that is prepared to
         * return the properties from the given server configuration. If a
         * string property of the configuration is an empty string, the
         * property is not set.
         */
        private fun preferencesFromServerConfig(serverConfig: ServerConfig): SharedPreferences {
            val pref = mockk<SharedPreferences>()
            initProperty(pref, PreferencesHandler.PROP_SERVER_URI, serverConfig.serverUri)
            initProperty(pref, PreferencesHandler.PROP_BASE_PATH, serverConfig.basePath)
            initProperty(pref, PreferencesHandler.PROP_USER, serverConfig.user)
            initProperty(pref, PreferencesHandler.PROP_PASSWORD, serverConfig.password)
            return pref
        }

        /**
         * Tests whether a *null* server config is returned in case of missing
         * properties. Shared preferences are created based on the passed in
         * configuration. Then it is tested whether based on the preferences a
         * *null* configuration is created.
         * @param config the configuration
         */
        private fun checkUndefinedServerConfig(config: ServerConfig) {
            val handler = PreferencesHandler(preferencesFromServerConfig(config))

            handler.createServerConfig() shouldBe null
        }

        /**
         * Prepares the given mock for a preferences object to return the
         * given value when asked for a data property.
         * @param prefs the preferences mock
         * @param key the key of the property
         * @param value the value to be returned
         */
        private fun expectDatePropertyAccess(prefs: SharedPreferences, key: String, value: Long) {
            every { prefs.contains(key) } returns true
            every { prefs.getLong(key, 0) } returns value
        }

        /**
         * Helper function that checks whether a time value is close to the
         * current system time.
         * @param time the time value to be checked
         */
        private fun assertCurrentTime(time: Long) {
            abs(System.currentTimeMillis() - time) shouldBeLessThanOrEqual 3000
        }
    }
}
