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

import android.content.Context
import com.github.oheger.locationteller.server.CurrentTimeService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for the several factory classes.
 */
class FactoriesSpec : StringSpec() {
    init {
        "UpdaterActorFactory should create a correct actor" {
            val crScope = mockk<CoroutineScope>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            val prefHandler = TrackTestHelper.preparePreferences()
            mockkStatic("com.github.oheger.locationteller.track.LocationUpdaterKt")
            every { locationUpdaterActor(any(), crScope) } answers {
                val uploadController = arg<UploadController>(0)
                val service = uploadController.trackService
                service.davClient.config shouldBe TrackTestHelper.defServerConfig
                uploadController.prefHandler shouldBe prefHandler
                uploadController.trackConfig shouldBe TrackTestHelper.defTrackConfig
                uploadController.offlineStorage.capacity shouldBe TrackTestHelper.defTrackConfig.offlineStorageSize
                uploadController.offlineStorage.minTrackInterval shouldBe TrackTestHelper.defTrackConfig.minTrackInterval * 1000
                uploadController.timeService shouldBe CurrentTimeService
                actor
            }
            val factory = UpdaterActorFactory()

            factory.createActor(prefHandler, TrackTestHelper.defTrackConfig, crScope) shouldBe actor
        }

        "UpdateActorFactory should return null if no server config is defined" {
            val prefHandler = TrackTestHelper.preparePreferences(svrConf = null)
            val factory = UpdaterActorFactory()

            factory.createActor(prefHandler, TrackTestHelper.defTrackConfig, mockk()) shouldBe null
        }

        "LocationRetrieverFactory should create a correct retriever object" {
            val context = mockk<Context>()
            val locationClient = mockk<FusedLocationProviderClient>()
            mockkStatic(LocationServices::class)
            every { LocationServices.getFusedLocationProviderClient(context) } returns locationClient
            val factory = LocationRetrieverFactory()

            val retriever = factory.createRetriever(context, TrackTestHelper.defTrackConfig, false)
            retriever.shouldBeInstanceOf<LocationRetrieverImpl>()
            retriever.locationClient shouldBe locationClient
            retriever.timeout shouldBe TrackTestHelper.defTrackConfig.gpsTimeout * 1000
        }

        "LocationRetrieverFactory should create a validating retriever object" {
            val context = mockk<Context>()
            val locationClient = mockk<FusedLocationProviderClient>()
            mockkStatic(LocationServices::class)
            every { LocationServices.getFusedLocationProviderClient(context) } returns locationClient
            val factory = LocationRetrieverFactory()

            val retriever = factory.createRetriever(context, TrackTestHelper.defTrackConfig, true)
            retriever.shouldBeInstanceOf<ValidatingLocationRetriever>()
            val wrappedRetriever = retriever.wrappedRetriever
            wrappedRetriever.shouldBeInstanceOf<LocationRetrieverImpl>()
            wrappedRetriever.locationClient shouldBe locationClient
            wrappedRetriever.timeout shouldBe TrackTestHelper.defTrackConfig.gpsTimeout * 1000
            retriever.maxSpeedIncrease shouldBe TrackTestHelper.defTrackConfig.maxSpeedIncrease
            retriever.walkingSpeed shouldBe TrackTestHelper.defTrackConfig.walkingSpeed
            retriever.timeService shouldBe ElapsedTimeService
        }

        "LocationProcessorFactory should create a correct processor object" {
            val retriever = mockk<LocationRetriever>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            val factory = LocationProcessorFactory()

            val processor = factory.createProcessor(retriever, actor)
            processor.locationRetriever shouldBe retriever
            processor.locationUpdateActor shouldBe actor
            processor.timeService shouldBe CurrentTimeService
        }
    }
}
