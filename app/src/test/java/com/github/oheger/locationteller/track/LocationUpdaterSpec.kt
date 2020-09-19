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

import android.location.Location
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 * Test class for the actor that executes location updates.
 */
@ObsoleteCoroutinesApi
class LocationUpdaterSpec : StringSpec() {
    init {
        "LocationUpdaterActor should correctly invoke the upload controller" {
            val delay = 2121
            val controller = mockk<UploadController>()
            val locationData = LocationData(3.14, 6.28, TimeData(20200215212258L))
            val location = mockk<Location>()
            val locUpdate = LocationUpdate(locationData, location, CompletableDeferred())
            coEvery { controller.handleUpload(locationData, location) } returns delay

            runActorTest(controller) { actor ->
                actor.send(locUpdate)
                locUpdate.nextTrackDelay.await() shouldBe delay
            }
        }
    }

    companion object {
        /**
         * Executes a test on an actor. The actor is obtained, and the given
         * test function is invoked with it. Finally, the actor is closed.
         * @param uploadController the mock for the upload controller
         * @param block the test function
         */
        private fun runActorTest(
            uploadController: UploadController,
            block: suspend (ch: SendChannel<LocationUpdate>) -> Unit
        ) = runBlocking {
            val actor = locationUpdaterActor(uploadController, this)
            block(actor)
            actor.close()
        }
    }
}
