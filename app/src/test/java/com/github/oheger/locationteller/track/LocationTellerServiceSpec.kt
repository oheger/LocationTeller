/*
 * Copyright 2019 The Developers.
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
import android.preference.PreferenceManager
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for [LocationTellerService].
 */
@ObsoleteCoroutinesApi
class LocationTellerServiceSpec : StringSpec() {
    init {
        "UpdaterActorFactory should create a correct actor" {
            val context = mockk<Context>()
            val crScope = mockk<CoroutineScope>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            mockkStatic("com.github.oheger.locationteller.track.LocationUpdaterKt")
            every { locationUpdaterActor(any(), defTrackConfig, crScope) } answers {
                val service = arg<TrackService>(0)
                service.davClient.config shouldBe defServerConfig
                actor
            }
            preparePreferences(context)
            val factory = UpdaterActorFactory()

            factory.createActor(context, crScope) shouldBe actor
        }

        "UpdateActorFactory should return null if no server URI is defined" {
            checkActorCreationWithMissingProperties(svrConf = defServerConfig.copy(serverUri = ""))
        }

        "UpdateActorFactory should return null if no base path is defined" {
            checkActorCreationWithMissingProperties(svrConf = defServerConfig.copy(basePath = ""))
        }

        "UpdateActorFactory should return null if no user name is defined" {
            checkActorCreationWithMissingProperties(svrConf = defServerConfig.copy(user = ""))
        }

        "UpdateActorFactory should return null if no password is defined" {
            checkActorCreationWithMissingProperties(svrConf = defServerConfig.copy(password = ""))
        }

        "UpdateActorFactory should return null if no min interval is defined" {
            checkActorCreationWithMissingProperties(trackConf = defTrackConfig.copy(minTrackInterval = -1))
        }

        "UpdateActorFactory should return null if no max interval is defined" {
            checkActorCreationWithMissingProperties(trackConf = defTrackConfig.copy(maxTrackInterval = -1))
        }

        "UpdateActorFactory should return null if no increment is defined" {
            checkActorCreationWithMissingProperties(trackConf = defTrackConfig.copy(intervalIncrementOnIdle = -1))
        }

        "UpdateActorFactory should return null if no validity is defined" {
            checkActorCreationWithMissingProperties(trackConf = defTrackConfig.copy(locationValidity = -1))
        }

        "LocationRetrieverFactory should create a correct retriever object" {
            val context = mockk<Context>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            val locationClient = mockk<FusedLocationProviderClient>()
            mockkStatic(LocationServices::class)
            every { LocationServices.getFusedLocationProviderClient(context) } returns locationClient
            val factory = LocationRetrieverFactory()

            val retriever = factory.createRetriever(context, actor)
            retriever.locationClient shouldBe locationClient
            retriever.locationUpdateActor shouldBe actor
            retriever.timeService shouldBe CurrentTimeService
        }
    }

    companion object {
        /** A default test server configuration.*/
        private val defServerConfig = ServerConfig(
            serverUri = "https://track-server.tst",
            basePath = "/my-tracks", user = "scott", password = "tiger"
        )

        /** A default test track configuration.*/
        private val defTrackConfig = TrackConfig(
            minTrackInterval = 42, maxTrackInterval = 727,
            locationValidity = 1000, intervalIncrementOnIdle = 50
        )

        /**
         * Installs a mock preferences manager that returns shared preferences
         * initialized with the test configurations.
         * @param context the context
         * @param svrConf the server config to initialize preferences
         * @param trackConf the track config to initialize preferences
         * @return the mock for the preferences
         */
        private fun preparePreferences(
            context: Context, svrConf: ServerConfig = defServerConfig,
            trackConf: TrackConfig = defTrackConfig
        ): SharedPreferences {
            mockkStatic(PreferenceManager::class)
            val pref = createPreferencesMock(svrConf, trackConf)
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
            return pref
        }

        /**
         * Creates a mock for a preferences object that is configured to return
         * the properties for the test configurations.
         * @param svrConf the server config to initialize preferences
         * @param trackConf the track config to initialize preferences
         * @return the mock preferences object
         */
        private fun createPreferencesMock(
            svrConf: ServerConfig = defServerConfig,
            trackConf: TrackConfig = defTrackConfig
        ): SharedPreferences {
            val pref = mockk<SharedPreferences>()
            initProperty(pref, "trackServerUri", svrConf.serverUri)
            initProperty(pref, "trackRelativePath", svrConf.basePath)
            initProperty(pref, "userName", svrConf.user)
            initProperty(pref, "password", svrConf.password)
            initProperty(pref, "minTrackInterval", trackConf.minTrackInterval)
            initProperty(pref, "maxTrackInterval", trackConf.maxTrackInterval)
            initProperty(pref, "intervalIncrementOnIdle", trackConf.intervalIncrementOnIdle)
            initProperty(pref, "locationValidity", trackConf.locationValidity)
            return pref
        }

        /**
         * Helper method to mock a string property of a preferences object.
         * @param pref the preferences object
         * @param key the key of the property
         * @param value the property value (an empty string yields a null value)
         */
        private fun initProperty(pref: SharedPreferences, key: String, value: String) {
            val prefValue = if (value.isEmpty()) null else value
            every { pref.getString(key, null) } returns prefValue
        }

        /**
         * Helper method to mock an int property of a preferences object.
         * @param pref the preferences object
         * @param key the key of the property
         * @param value the property value
         */
        private fun initProperty(pref: SharedPreferences, key: String, value: Int) {
            every { pref.getInt(key, -1) } returns value
        }

        /**
         * Checks a creation of the update actor if mandatory properties are
         * missing. In this case, no actor can be created.
         * @param svrConf the server config to initialize preferences
         * @param trackConf the track config to initialize preferences
         */
        private fun checkActorCreationWithMissingProperties(
            svrConf: ServerConfig = defServerConfig,
            trackConf: TrackConfig = defTrackConfig
        ) {
            val context = mockk<Context>()
            preparePreferences(context, svrConf, trackConf)
            val factory = UpdaterActorFactory()
            factory.createActor(context, mockk()) shouldBe null
        }
    }
}