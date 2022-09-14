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
package com.github.oheger.locationteller.map

import android.content.Context
import android.location.Location

import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.track.LocationRetriever
import com.github.oheger.locationteller.track.LocationRetrieverFactory
import com.github.oheger.locationteller.track.TrackTestHelper

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Test class for [MapStateUpdater].
 */
class MapStateUpdaterSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)

    init {
        "An instance can be created using the factory function" {
            val loader = createLoaderMock()
            val loaderProvider: () -> MapStateLoader = { loader }
            val updateState: (LocationFileState) -> Unit = { }
            val countDown: (Int) -> Unit = {}
            val locationUpdate: (Location?) -> Unit = {}
            MockDispatcher.installAsMain()

            MapStateUpdater.create(UPDATE_INTERVAL, loaderProvider, updateState, countDown, locationUpdate)
                .use { updater ->
                    updater.coroutineContext shouldBe Dispatchers.Main
                    updater.updateInterval shouldBe UPDATE_INTERVAL
                    updater.updateState shouldBe updateState
                    updater.countDown shouldBe countDown
                    updater.updateLocation shouldBe locationUpdate
                    updater.loaderProvider shouldBe loaderProvider

                    coVerify {
                        loader.loadMapState(LocationFileState.EMPTY)
                    }
                }
        }

        "The current state should be loaded immediately" {
            val state = LocationTestHelper.createState(1..2)
            val helper = UpdaterTestHelper().apply {
                prepareLoader(state)
            }

            helper.shouldHaveUpdated()

            helper.runJobs()
            helper.shouldHaveUpdated(state)
        }

        "Count-down events should be published" {
            val helper = UpdaterTestHelper()

            helper.countDownValue shouldBe 0

            helper.runJobs()
            helper.countDownValue shouldBe UPDATE_INTERVAL

            withTimeout(2000) {
                while (helper.countDownValue != UPDATE_INTERVAL - 1) {
                    helper.runJobs()
                    delay(200)
                }
            }
        }

        "The ticker service should be canceled on close" {
            val helper = UpdaterTestHelper()

            helper.closeUpdater()
            helper.runJobs()
            val count = helper.countDownValue

            delay(1500)
            helper.runJobs()
            helper.countDownValue shouldBe count
        }

        "The count-down value should not become negative" {
            val helper = UpdaterTestHelper().apply {
                runJobs()
                closeUpdater()
            }

            helper.sendTicks(UPDATE_INTERVAL + 1)

            helper.countDownValue shouldBe 0
        }

        "Updates should be performed when the counter reaches zero" {
            val state1 = LocationTestHelper.createState(1..2)
            val state2 = LocationTestHelper.createState(2..4)
            val helper = UpdaterTestHelper().apply {
                runJobs()
                closeUpdater()
                prepareLoader(state1, state2)
            }

            helper.sendTicks(UPDATE_INTERVAL)
            helper.runJobs()
            helper.shouldHaveUpdated(LocationFileState.EMPTY, state1)

            helper.sendTicks(UPDATE_INTERVAL)
            helper.runJobs()
            helper.shouldHaveUpdated(LocationFileState.EMPTY, state1, state2)
        }

        "Updates can be triggered on demand" {
            val state = LocationTestHelper.createState(1..4)
            val helper = UpdaterTestHelper().apply {
                runJobs()
                closeUpdater()
                prepareLoader(state)
            }

            helper.triggerUpdate()
            helper.runJobs()

            helper.shouldHaveUpdated(LocationFileState.EMPTY, state)
        }

        "The own location can be queried" {
            mockkObject(ConfigManager)
            val context = mockk<Context>()
            val location = mockk<Location>()
            val configManager = mockk<ConfigManager>()
            every { ConfigManager.getInstance() } returns configManager
            every { configManager.trackConfig(context) } returns TrackTestHelper.DEFAULT_TRACK_CONFIG

            val helper = UpdaterTestHelper()
            val retriever = helper.prepareLocationRetrieverFactory(context)
            coEvery { retriever.fetchLocation() } returns location

            helper.queryLocation(context)
            helper.runJobs()

            helper.locationValue shouldBe location
        }
    }
}

/** The interval in seconds when updates should be performed. */
private const val UPDATE_INTERVAL = 8

/**
 * A test helper class managing an instance to be tested and its dependencies.
 */
private class UpdaterTestHelper {
    /** The mock dispatcher passed to the test updater. */
    private val dispatcher = MockDispatcher.installAsMain(directExecution = false)

    /** The mock map state loader. */
    private val mapStateLoader = createLoaderMock()

    /** The mock for the factory to create the location retriever. */
    private val locationRetrieverFactory = mockk<LocationRetrieverFactory>()

    /** A list that stores the data passed to the update function. */
    private val fileStates = mutableListOf<LocationFileState>()

    /** Stores the latest value passed to the count down function. */
    var countDownValue: Int = 0

    /** Stores the latest value passed to the location update function. */
    var locationValue: Location? = null

    /** The updater to be tested. */
    private val updater = createUpdater()

    /**
     * Prepare the mock for the loader to return the given sequence of [states].
     */
    fun prepareLoader(vararg states: LocationFileState) {
        val statesWithInitial = listOf(LocationFileState.EMPTY) + states
        val statesWithNextState = statesWithInitial.zipWithNext()
        statesWithNextState.forEach { (current, next) ->
            coEvery { mapStateLoader.loadMapState(current) } returns next
        }
    }

    /**
     * Create a mock for a [LocationRetriever] and prepare the mock [LocationRetrieverFactory] to return it when it is
     * invoked with the given [context].
     */
    fun prepareLocationRetrieverFactory(context: Context): LocationRetriever {
        val retriever = mockk<LocationRetriever>()
        every {
            locationRetrieverFactory.createRetriever(
                context,
                TrackTestHelper.DEFAULT_TRACK_CONFIG,
                validating = false
            )
        } returns retriever

        return retriever
    }

    /**
     * Trigger the test updater to request the current location.
     */
    fun queryLocation(context: Context) {
        updater.queryLocation(context)
    }

    /**
     * Run the jobs that have been scheduled via the _launch()_ function.
     */
    fun runJobs() {
        dispatcher.executeBlocks()
    }

    /**
     * Invoke the [MapStateUpdater.close] function of the test instance.
     */
    fun closeUpdater() {
        updater.close()
    }

    /**
     * Check whether the updater has produced the given [states] as updates.
     */
    fun shouldHaveUpdated(vararg states: LocationFileState) {
        fileStates shouldContainExactly states.asList()
    }

    /**
     * Send [count] ticker notifications to the test updater.
     */
    fun sendTicks(count: Int = 1) {
        (1..count).forEach { _ -> updater.tick() }
    }

    /**
     * Trigger the test updater to perform an update immediately.
     */
    fun triggerUpdate() {
        updater.update()
    }

    /**
     * Create the updater to be tested.
     */
    private fun createUpdater(): MapStateUpdater = MapStateUpdater(
        updateInterval = UPDATE_INTERVAL,
        loaderProvider = { mapStateLoader },
        updateState = fileStates::add,
        countDown = { countDownValue = it },
        updateLocation = { locationValue = it },
        locationRetrieverFactory = locationRetrieverFactory,
        coroutineContext = dispatcher
    )
}

/**
 * Create a mock for the [MapStateLoader] and prepare it to return an empty state in an initial load operation.
 */
private fun createLoaderMock(): MapStateLoader = mockk<MapStateLoader>().apply {
    coEvery { loadMapState(LocationFileState.EMPTY) } returns LocationFileState.EMPTY
}
