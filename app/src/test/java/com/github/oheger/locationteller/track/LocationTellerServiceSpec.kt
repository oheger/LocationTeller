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

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat
import com.github.oheger.locationteller.server.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
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
            val crScope = mockk<CoroutineScope>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            val prefHandler = preparePreferences()
            mockkStatic("com.github.oheger.locationteller.track.LocationUpdaterKt")
            every { locationUpdaterActor(any(), crScope) } answers {
                val uploadController = arg<UploadController>(0)
                val service = uploadController.trackService
                service.davClient.config shouldBe defServerConfig
                uploadController.prefHandler shouldBe prefHandler
                uploadController.trackConfig shouldBe defTrackConfig
                uploadController.offlineStorage.capacity shouldBe defTrackConfig.offlineStorageSize
                uploadController.offlineStorage.minTrackInterval shouldBe defTrackConfig.minTrackInterval * 1000
                uploadController.timeService shouldBe CurrentTimeService
                actor
            }
            val factory = UpdaterActorFactory()

            factory.createActor(prefHandler, defTrackConfig, crScope) shouldBe actor
        }

        "UpdateActorFactory should return null if no server config is defined" {
            val prefHandler = preparePreferences(svrConf = null)
            val factory = UpdaterActorFactory()

            factory.createActor(prefHandler, defTrackConfig, mockk()) shouldBe null
        }

        "LocationProcessorFactory should create a correct retriever object" {
            val context = mockk<Context>()
            val actor = mockk<SendChannel<LocationUpdate>>()
            val locationClient = mockk<FusedLocationProviderClient>()
            mockkStatic(LocationServices::class)
            every { LocationServices.getFusedLocationProviderClient(context) } returns locationClient
            val factory = LocationProcessorFactory()

            val retriever = factory.createProcessor(context, actor, defTrackConfig)
            retriever.locationClient shouldBe locationClient
            retriever.locationUpdateActor shouldBe actor
            retriever.timeService shouldBe CurrentTimeService
            retriever.trackConfig shouldBe defTrackConfig
        }

        "LocationTellerService should create default dependencies" {
            val service = LocationTellerService()

            // this test is not really meaningful; the relevant part is the
            // invocation of the secondary constructor
            service.processorFactory shouldNotBe null
            service.updaterFactory shouldNotBe null
            service.timeService shouldBe CurrentTimeService
        }

        "LocationTellerService should return null in onBind()" {
            val helper = TellerServiceTestHelper()

            helper.service.onBind(null) shouldBe null
        }

        "LocationTellerService should stop itself if tracking is disabled" {
            val helper = TellerServiceTestHelper(trackingEnabled = false)

            helper.sendStartCommand()
                .verifyServiceStopped()
                .verifyNoNextExecutionScheduled()
        }

        "LocationTellerService should trigger a location update if all criteria are fulfilled" {
            val helper = TellerServiceTestHelper()

            helper.sendStartCommand()
                .verifyLocationUpdateTriggered()
                .verifyNextExecutionScheduled()
        }

        "LocationTellerService should stop itself if no updater actor could be created" {
            val helper = TellerServiceTestHelper(actorCanBeCreated = false)

            helper.sendStartCommand()
                .verifyServiceStopped()
                .verifyNoNextExecutionScheduled()
        }

        "LocationTellerService should start itself as foreground service" {
            val helper = TellerServiceTestHelper()

            helper.sendStartCommand()
                .verifyForegroundServiceStarted()
        }

        "LocationTellerService should calculate a correct time for the next update" {
            val expTime = nextUpdateInterval * 1000L + elapsedTime
            val helper = TellerServiceTestHelper()

            helper.service.calculateNextUpdateTime(nextUpdateInterval) shouldBe expTime
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
            locationValidity = 1000, intervalIncrementOnIdle = 50,
            locationUpdateThreshold = 22, gpsTimeout = 10, retryOnErrorTime = 4,
            autoResetStats = false, offlineStorageSize = 8, maxOfflineStorageSyncTime = 20,
            multiUploadChunkSize = 4
        )

        /** Constant for the next update interval returned by the retriever.*/
        const val nextUpdateInterval = 777

        /** The elapsed time to be returned by the time service mock.*/
        const val elapsedTime = 20190107192211L

        /**
         * Installs a mock preferences manager that returns shared preferences
         * initialized with the test configurations.
         * @param svrConf the server config to initialize preferences
         * @param trackConf the track config to initialize preferences
         * @param trackingEnabled flag whether tracking should be enabled
         * @return the mock for the preferences handler
         */
        private fun preparePreferences(
            svrConf: ServerConfig? = defServerConfig,
            trackConf: TrackConfig = defTrackConfig,
            trackingEnabled: Boolean = true
        ): PreferencesHandler {
            val handler = mockk<PreferencesHandler>()
            every { handler.createTrackConfig() } returns trackConf
            every { handler.createServerConfig() } returns svrConf
            every { handler.isTrackingEnabled() } returns trackingEnabled
            every { handler.checkCount() } returns 42
            every { handler.updateCount() } returns 11
            every { handler.errorCount() } returns 1
            every { handler.totalDistance() } returns 100
            return handler
        }

        /**
         * A test helper class managing a service instance and its dependencies.
         * @param trackingEnabled flag whether tracking is enabled
         * @param actorCanBeCreated flag whether actor creation succeeds
         */
        class TellerServiceTestHelper(
            private val trackingEnabled: Boolean = true,
            private val actorCanBeCreated: Boolean = true
        ) {
            /** The timeout for verifications of asynchronous actions.*/
            private val timeout = 1000L

            /** Mock for the location retriever.*/
            private val locationProcessor = mockk<LocationProcessor>()

            /** Mock for the alarm manager.*/
            private val alarmManager = createAlarmManager()

            /** Mock for the pending intent.*/
            private val pendingIntent = mockk<PendingIntent>()

            /** Mock for the notification produced by the mocked builder.*/
            private val notification = mockk<Notification>()

            /** The service to be tested.*/
            val service = createService()

            /**
             * Sends a start command to the test service.
             * @return this test helper
             */
            fun sendStartCommand(): TellerServiceTestHelper {
                service.onStartCommand(null, 0, 0) shouldBe Service.START_STICKY
                return this
            }

            /**
             * Verifies that the test service has stopped itself.
             * @return this test helper
             */
            fun verifyServiceStopped(): TellerServiceTestHelper {
                verify(timeout = timeout) {
                    service.stopSelf()
                }
                return this
            }

            /**
             * Verifies that no future service execution has been scheduled.
             * @return this test helper
             */
            fun verifyNoNextExecutionScheduled(): TellerServiceTestHelper {
                verify(exactly = 0) {
                    alarmManager.setAlarmClock(any(), any())
                }
                return this
            }

            /**
             * Verifies that a next service execution has been scheduled at the
             * expected time using the alarm manager.
             * @return this test helper
             */
            fun verifyNextExecutionScheduled(): TellerServiceTestHelper {
                verify(exactly = 1, timeout = timeout) {
                    alarmManager.setAlarmClock(any(), any())
                }
                return this
            }

            /**
             * Verifies that the retriever was called to trigger a location
             * update.
             * @return this test helper
             */
            fun verifyLocationUpdateTriggered(): TellerServiceTestHelper {
                coVerify(timeout = timeout) {
                    locationProcessor.retrieveAndUpdateLocation()
                }
                return this
            }

            /**
             * Verifies that the service starts itself as foreground service.
             * @return this test helper
             */
            fun verifyForegroundServiceStarted(): TellerServiceTestHelper {
                verify { service.startForeground(any(), notification) }
                return this
            }

            /**
             * Creates a mock for the alarm manager. The mock is prepared to
             * expect schedules for another alarm.
             * @return the mock alarm manager
             */
            private fun createAlarmManager(): AlarmManager {
                val alarmManager = mockk<AlarmManager>()
                every { alarmManager.setAlarmClock(any(), any()) } just runs
                return alarmManager
            }

            /**
             * Creates the service instance to be tested. Its _onCreate()_
             * callback is already called.
             * @return the test service instance
             */
            private fun createService(): LocationTellerService {
                val updaterFactory = mockk<UpdaterActorFactory>()
                val retrieverFactory = mockk<LocationProcessorFactory>()
                val timeService = mockk<TimeService>()
                val service = LocationTellerService(updaterFactory, retrieverFactory, timeService)
                mockkStatic(PendingIntent::class)
                every { PendingIntent.getService(any(), 0, any(), 0) } returns pendingIntent
                val prefs = preparePreferences(trackingEnabled = trackingEnabled)

                val actor = if (actorCanBeCreated) mockk<SendChannel<LocationUpdate>>() else null
                every { updaterFactory.createActor(prefs, defTrackConfig, any()) } returns actor
                if (actor != null) {
                    every { retrieverFactory.createProcessor(any(), actor, defTrackConfig) } returns locationProcessor
                }
                coEvery { locationProcessor.retrieveAndUpdateLocation() } answers {
                    nextUpdateInterval
                }
                every { timeService.currentTime() } returns TimeData(elapsedTime)

                val notificationBuilder = mockk<NotificationCompat.Builder>()
                every { notificationBuilder.setSmallIcon(any()) } returns notificationBuilder
                every { notificationBuilder.setContentTitle(any()) } returns notificationBuilder
                every { notificationBuilder.build() } returns notification

                val serviceSpy = spyk(service)
                every { serviceSpy.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
                every { serviceSpy.notificationBuilder() } returns notificationBuilder
                every { serviceSpy.createPreferencesHandler() } returns prefs
                serviceSpy.onCreate()
                return serviceSpy
            }
        }
    }
}
