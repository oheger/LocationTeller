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
                    PreferencesHandler.propTrackState,
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
            every { editor.putBoolean(PreferencesHandler.propTrackState, true) } returns editor
            every { editor.putLong(PreferencesHandler.propTrackingStart, any()) } returns editor
            every { editor.remove(PreferencesHandler.propTrackingEnd) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setTrackingEnabled(true)
            verify {
                editor.putBoolean(PreferencesHandler.propTrackState, true)
                editor.putLong(PreferencesHandler.propTrackingStart, capture(slotStartTime))
                editor.remove(PreferencesHandler.propTrackingEnd)
                editor.apply()
            }
            assertCurrentTime(slotStartTime.captured)
        }

        "PreferencesHandler should allow setting the tracking state to false" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            val slotEndTime = slot<Long>()
            every { pref.edit() } returns editor
            every { editor.putBoolean(PreferencesHandler.propTrackState, false) } returns editor
            every { editor.putLong(PreferencesHandler.propTrackingEnd, any()) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setTrackingEnabled(false)
            verify {
                editor.putBoolean(PreferencesHandler.propTrackState, false)
                editor.putLong(PreferencesHandler.propTrackingEnd, capture(slotEndTime))
                editor.apply()
            }
            assertCurrentTime(slotEndTime.captured)
        }

        "PreferencesHandler should identify configuration properties" {
            val configProps = listOf(
                PreferencesHandler.propBasePath, PreferencesHandler.propIdleIncrement,
                PreferencesHandler.propLocationValidity, PreferencesHandler.propMaxTrackInterval,
                PreferencesHandler.propMinTrackInterval, PreferencesHandler.propPassword,
                PreferencesHandler.propUser, PreferencesHandler.propServerUri,
                PreferencesHandler.propLocationUpdateThreshold, PreferencesHandler.propRetryOnErrorTime,
                PreferencesHandler.propGpsTimeout
            )

            configProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe true }
        }

        "PreferencesHandler should identify non-configuration properties" {
            val nonConfigProps = listOf(PreferencesHandler.propTrackState, "foo", "bar")

            nonConfigProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe false }
        }

        "PreferencesHandler should record an error" {
            val errorTime = 20190704213348L
            val errorCount = 12
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.propLastError, errorTime) } returns editor
            every { editor.putInt(PreferencesHandler.propErrorCount, errorCount) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordError(errorTime, errorCount)
            verify {
                editor.putLong(PreferencesHandler.propLastError, errorTime)
                editor.putInt(PreferencesHandler.propErrorCount, errorCount)
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
            every { editor.putLong(PreferencesHandler.propLastUpdate, updateTime) } returns editor
            every { editor.putInt(PreferencesHandler.propUpdateCount, updateCount) } returns editor
            every { editor.putInt(PreferencesHandler.propLastDistance, distance) } returns editor
            every { editor.putLong(PreferencesHandler.propTotalDistance, totalDistance) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordUpdate(updateTime, updateCount, distance, totalDistance)
            verify {
                editor.putLong(PreferencesHandler.propLastUpdate, updateTime)
                editor.putInt(PreferencesHandler.propUpdateCount, updateCount)
                editor.putInt(PreferencesHandler.propLastDistance, distance)
                editor.putLong(PreferencesHandler.propTotalDistance, totalDistance)
                editor.apply()
            }
        }

        "PreferencesHandler should record a check" {
            val checkTime = 20190711222122L
            val checkCount = 128
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.propLastCheck, checkTime) } returns editor
            every { editor.putInt(PreferencesHandler.propCheckCount, checkCount) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordCheck(checkTime, checkCount)
            verify {
                editor.putLong(PreferencesHandler.propLastCheck, checkTime)
                editor.putInt(PreferencesHandler.propCheckCount, checkCount)
                editor.apply()
            }
        }

        "PreferencesHandler should return the last error time" {
            val errorTime = 20190705180422L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propLastError, errorTime)
            val handler = PreferencesHandler(pref)

            val errorDate = handler.lastError()
            errorDate!!.time shouldBe errorTime
        }

        "PreferencesHandler should return null for the last error if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastError) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastError() shouldBe null
        }

        "PreferencesHandler should return the last update time" {
            val updateTime = 20190705181104L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propLastUpdate, updateTime)
            val handler = PreferencesHandler(pref)

            val updateDate = handler.lastUpdate()
            updateDate!!.time shouldBe updateTime
        }

        "PreferencesHandler should return null for the last update if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastUpdate) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastUpdate() shouldBe null
        }

        "PreferencesHandler should return the last check time" {
            val checkTime = 20190711222611L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propLastCheck, checkTime)
            val handler = PreferencesHandler(pref)

            val checkDate = handler.lastCheck()
            checkDate!!.time shouldBe checkTime
        }

        "PreferencesHandler should handle a date in the long past" {
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propLastCheck, 1000)
            val handler = PreferencesHandler(pref)

            val checkDate = handler.lastCheck()
            checkDate shouldBe null
        }

        "PreferencesHandler should return null for the last check if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastCheck) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastCheck() shouldBe null
        }

        "PreferencesHandler should return the distance of the last location update" {
            val distance = 157
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.propLastDistance, 0) } returns distance
            val handler = PreferencesHandler(pref)

            handler.lastDistance() shouldBe distance
        }

        "PreferencesHandler should return the tracking start time" {
            val startTime = 20200117215143L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propTrackingStart, startTime)
            val handler = PreferencesHandler(pref)

            val startDate = handler.trackingStartDate()
            startDate!!.time shouldBe startTime
        }

        "PreferencesHandler should return the tracking stop time" {
            val endTime = 20200117220450L
            val pref = mockk<SharedPreferences>()
            expectDatePropertyAccess(pref, PreferencesHandler.propTrackingEnd, endTime)
            val handler = PreferencesHandler(pref)

            val endDate = handler.trackingEndDate()
            endDate!!.time shouldBe endTime
        }

        "PreferencesHandler should return the total distance" {
            val distance = 20200118160148L
            val pref = mockk<SharedPreferences>()
            every { pref.getLong(PreferencesHandler.propTotalDistance, 0) } returns distance
            val handler = PreferencesHandler(pref)

            val totalDistance = handler.totalDistance()
            totalDistance shouldBe distance
        }

        "PreferencesHandler should return the error count" {
            val count = 61
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.propErrorCount, 0) } returns count
            val handler = PreferencesHandler(pref)

            val errorCount = handler.errorCount()
            errorCount shouldBe count
        }

        "PreferencesHandler should return the check count" {
            val count = 77
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.propCheckCount, 0) } returns count
            val handler = PreferencesHandler(pref)

            val checkCount = handler.checkCount()
            checkCount shouldBe count
        }

        "PreferencesHandler should return the update count" {
            val count = 99
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.propUpdateCount, 0) } returns count
            val handler = PreferencesHandler(pref)

            val updateCount = handler.updateCount()
            updateCount shouldBe count
        }

        "PreferencesHandler should allow resetting statistics data" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.remove(PreferencesHandler.propErrorCount) } returns editor
            every { editor.remove(PreferencesHandler.propTotalDistance) } returns editor
            every { editor.remove(PreferencesHandler.propLastDistance) } returns editor
            every { editor.remove(PreferencesHandler.propLastCheck) } returns editor
            every { editor.remove(PreferencesHandler.propLastError) } returns editor
            every { editor.remove(PreferencesHandler.propLastUpdate) } returns editor
            every { editor.remove(PreferencesHandler.propCheckCount) } returns editor
            every { editor.remove(PreferencesHandler.propUpdateCount) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.resetStatistics()
            verify {
                editor.remove(PreferencesHandler.propErrorCount)
                editor.remove(PreferencesHandler.propTotalDistance)
                editor.remove(PreferencesHandler.propLastDistance)
                editor.remove(PreferencesHandler.propLastError)
                editor.remove(PreferencesHandler.propLastCheck)
                editor.remove(PreferencesHandler.propLastUpdate)
                editor.remove(PreferencesHandler.propCheckCount)
                editor.remove(PreferencesHandler.propUpdateCount)
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
                16, 30, 2, true
            )
            val pref = preferencesFromTrackConfig(trackConfig)
            val handler = PreferencesHandler(pref)

            handler.createTrackConfig() shouldBe trackConfig
        }

        "PreferencesHandler should return default values for a TrackConfig if properties are undefined" {
            val pref = mockk<SharedPreferences>()
            listOf(
                PreferencesHandler.propMaxTrackInterval, PreferencesHandler.propMinTrackInterval,
                PreferencesHandler.propIdleIncrement, PreferencesHandler.propLocationValidity,
                PreferencesHandler.propLocationUpdateThreshold, PreferencesHandler.propRetryOnErrorTime,
                PreferencesHandler.propGpsTimeout, PreferencesHandler.propOfflineStorageSize,
                PreferencesHandler.propOfflineStorageSyncTime, PreferencesHandler.propMultiUploadChunkSize
            ).forEach {
                initProperty(pref, it, -1)
            }
            every { pref.getBoolean(PreferencesHandler.propAutoResetStats, false) } returns false
            val handler = PreferencesHandler(pref)

            val config = handler.createTrackConfig()
            config.minTrackInterval shouldBe PreferencesHandler.defaultMinTrackInterval
            config.maxTrackInterval shouldBe PreferencesHandler.defaultMaxTrackInterval
            config.intervalIncrementOnIdle shouldBe PreferencesHandler.defaultIdleIncrement
            config.locationValidity shouldBe PreferencesHandler.defaultLocationValidity
            config.locationUpdateThreshold shouldBe PreferencesHandler.defaultLocationUpdateThreshold
            config.retryOnErrorTime shouldBe PreferencesHandler.defaultRetryOnErrorTime
            config.gpsTimeout shouldBe PreferencesHandler.defaultGpsTimeout
            config.offlineStorageSize shouldBe PreferencesHandler.defaultOfflineStorageSize
            config.maxOfflineStorageSyncTime shouldBe PreferencesHandler.defaultOfflineStorageSyncTime
            config.multiUploadChunkSize shouldBe PreferencesHandler.defaultMultiUploadChunkSize
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
                    PreferencesHandler.propMinTrackInterval,
                    (PreferencesHandler.defaultMinTrackInterval / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.propMaxTrackInterval,
                    (PreferencesHandler.defaultMaxTrackInterval / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.propIdleIncrement,
                    (PreferencesHandler.defaultIdleIncrement / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.propLocationValidity,
                    (PreferencesHandler.defaultLocationValidity / 60).toString()
                )
                editor.putString(
                    PreferencesHandler.propLocationUpdateThreshold,
                    PreferencesHandler.defaultLocationUpdateThreshold.toString()
                )
                editor.putString(
                    PreferencesHandler.propRetryOnErrorTime,
                    PreferencesHandler.defaultRetryOnErrorTime.toString()
                )
                editor.putString(
                    PreferencesHandler.propGpsTimeout,
                    PreferencesHandler.defaultGpsTimeout.toString()
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
        private fun initProperty(pref: SharedPreferences, property: String, value: Int) {
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
            initProperty(pref, PreferencesHandler.propMinTrackInterval, trackConfig.minTrackInterval / 60)
            initProperty(pref, PreferencesHandler.propMaxTrackInterval, trackConfig.maxTrackInterval / 60)
            initProperty(pref, PreferencesHandler.propIdleIncrement, trackConfig.intervalIncrementOnIdle / 60)
            initProperty(pref, PreferencesHandler.propLocationValidity, trackConfig.locationValidity / 60)
            initProperty(pref, PreferencesHandler.propLocationUpdateThreshold, trackConfig.locationUpdateThreshold)
            initProperty(pref, PreferencesHandler.propRetryOnErrorTime, trackConfig.retryOnErrorTime)
            initProperty(pref, PreferencesHandler.propGpsTimeout, trackConfig.gpsTimeout)
            initProperty(pref, PreferencesHandler.propOfflineStorageSize, trackConfig.offlineStorageSize)
            initProperty(pref, PreferencesHandler.propOfflineStorageSyncTime, trackConfig.maxOfflineStorageSyncTime)
            initProperty(pref, PreferencesHandler.propMultiUploadChunkSize, trackConfig.multiUploadChunkSize)
            every { pref.getBoolean(PreferencesHandler.propAutoResetStats, false) } returns trackConfig.autoResetStats
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
            initProperty(pref, PreferencesHandler.propServerUri, serverConfig.serverUri)
            initProperty(pref, PreferencesHandler.propBasePath, serverConfig.basePath)
            initProperty(pref, PreferencesHandler.propUser, serverConfig.user)
            initProperty(pref, PreferencesHandler.propPassword, serverConfig.password)
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
