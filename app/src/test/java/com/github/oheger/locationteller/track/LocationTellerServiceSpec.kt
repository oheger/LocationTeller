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

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat
import com.github.oheger.locationteller.server.*
import com.github.oheger.locationteller.track.TrackTestHelper.prepareTrackStorage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for [LocationTellerService].
 */
class LocationTellerServiceSpec : StringSpec() {
    init {
        "LocationTellerService should create default dependencies" {
            val service = LocationTellerService()

            // this test is not really meaningful; the relevant part is the
            // invocation of the secondary constructor
            service.processorFactory shouldNotBe null
            service.updaterFactory shouldNotBe null
            service.retrieverFactory shouldNotBe null
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
        /** Constant for the next update interval returned by the retriever.*/
        const val nextUpdateInterval = 777

        /** The elapsed time to be returned by the time service mock.*/
        const val elapsedTime = 20190107192211L

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
                val retrieverFactory = mockk<LocationRetrieverFactory>()
                val processorFactory = mockk<LocationProcessorFactory>()
                val timeService = mockk<TimeService>()
                val retriever = mockk<LocationRetrieverImpl>()
                val service = LocationTellerService(updaterFactory, retrieverFactory, processorFactory, timeService)
                mockkStatic(PendingIntent::class)
                every {
                    PendingIntent.getService(any(), 0, any(), PendingIntent.FLAG_IMMUTABLE)
                } returns pendingIntent
                val trackStorage = prepareTrackStorage(trackingEnabled = trackingEnabled)

                val actor = if (actorCanBeCreated) mockk<SendChannel<LocationUpdate>>() else null
                every { updaterFactory.createActor(trackStorage, TrackTestHelper.defTrackConfig, any()) } returns actor
                every {
                    retrieverFactory.createRetriever(any(), TrackTestHelper.defTrackConfig, validating = true)
                } returns retriever
                if (actor != null) {
                    every { processorFactory.createProcessor(retriever, actor) } returns locationProcessor
                }
                coEvery { locationProcessor.retrieveAndUpdateLocation() } answers {
                    nextUpdateInterval
                }
                every { timeService.currentTime() } returns TimeData(elapsedTime)

                val notificationBuilder = mockk<NotificationCompat.Builder>()
                every { notificationBuilder.setSmallIcon(any<Int>()) } returns notificationBuilder
                every { notificationBuilder.setContentTitle(any()) } returns notificationBuilder
                every { notificationBuilder.build() } returns notification

                val serviceSpy = spyk(service)
                every { serviceSpy.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
                every { serviceSpy.notificationBuilder() } returns notificationBuilder
                every { serviceSpy.createTrackStorage() } returns trackStorage
                serviceSpy.onCreate()
                return serviceSpy
            }
        }
    }
}
