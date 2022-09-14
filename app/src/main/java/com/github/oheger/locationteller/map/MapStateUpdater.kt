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

import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.duration.TickerService.Companion.createTickerService
import com.github.oheger.locationteller.track.LocationRetrieverFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import kotlin.coroutines.CoroutineContext

/**
 * A class that handles periodic updates of the [LocationFileState] from the server.
 *
 * The class uses a [com.github.oheger.locationteller.duration.TickerService] to be triggered periodically. When the
 * configured [updateInterval] is reached, a [MapStateLoader] is called to obtain the newest state from the server.
 * The new state is then propagated via an update function. In addition, a count-down is generated, so that a UI can
 * display the time until the next update happens.
 *
 * _Implementation note:_ This class is not thread-safe. All interactions must be done in the main thread.
 */
class MapStateUpdater internal constructor(
    /** The update interval in seconds. */
    val updateInterval: Int,

    /**
     * A function returning the [MapStateLoader] to use for updates. The loader could be replaced, e.g. when there are
     * configuration changes. Therefore, it is obtained via this function every time an update takes place.
     */
    val loaderProvider: () -> MapStateLoader,

    /** A function to be invoked when a new state was obtained from the server. */
    val updateState: (LocationFileState) -> Unit,

    /** A function to be invoked with the current value of the count-down timer. */
    val countDown: (Int) -> Unit,

    /** A function to be invoked when the own location has been retried. */
    val updateLocation: (Location?) -> Unit,

    /** The factory for creating a new location retriever instance. */
    val locationRetrieverFactory: LocationRetrieverFactory,

    /** The context for launching new coroutines. */
    override val coroutineContext: CoroutineContext
) : CoroutineScope, AutoCloseable {
    companion object {
        /**
         * Create a new [MapStateUpdater] instance that performs updates in the given [updateInterval] (in seconds).
         * The [MapStateLoader] needed for updates is obtained via the [loaderProvider] function. The new state is
         * propagated via the [updateState] function, every second the [countDown] function is invoked with the time
         * in seconds until the next update. Via the [updateLocation] function, the result of a query for the own
         * location is propagated.
         */
        fun create(
            updateInterval: Int,
            loaderProvider: () -> MapStateLoader,
            updateState: (LocationFileState) -> Unit,
            countDown: (Int) -> Unit,
            updateLocation: (Location?) -> Unit,
        ): MapStateUpdater =
            MapStateUpdater(
                updateInterval,
                loaderProvider,
                updateState,
                countDown,
                updateLocation,
                LocationRetrieverFactory(),
                Dispatchers.Main
            )
    }

    /** The service for receiving tick events periodically. */
    private val tickerService = createTickerService(notify = this::tick)

    /** Stores the last state that was loaded from the server. */
    private var lastState = LocationFileState.EMPTY

    /** The current value of the count down. */
    private var countDownValue = 0

    init {
        loadAndUpdateState()
    }

    /**
     * Free all resources used by this object. Afterwards, no updates are done any more.
     */
    override fun close() {
        tickerService.cancel()
    }

    /**
     * Trigger an immediate update. This is equivalent to setting the count-down to 1 and then simulating a tick event.
     * New state is fetched from the server, and corresponding data is published.
     */
    fun update() {
        countDownValue = 1
        tick()
    }

    /**
     * Trigger a request for the current location. Use [context] to obtain a corresponding retriever object. The
     * request is done asynchronously. The result is passed via the [updateLocation] function.
     */
    fun queryLocation(context: Context) {
        val trackConfig = ConfigManager.getInstance().trackConfig(context)
        val retriever = locationRetrieverFactory.createRetriever(context, trackConfig, validating = false)

        launch {
            val location = retriever.fetchLocation()
            updateLocation(location)
        }
    }

    /**
     * Handle notifications from the ticker service. Decrement the count-down. On reaching zero, load the newest
     * state from the server.
     */
    internal fun tick() {
        if (countDownValue > 0) {
            updateCountDown(countDownValue - 1)
            if (countDownValue == 0) {
                loadAndUpdateState()
            }
        }
    }

    /**
     * Invoke the loader to fetch updated state. Propagate the result to the update function.
     */
    private fun loadAndUpdateState() {
        launch {
            lastState = loaderProvider().loadMapState(lastState)
            updateState(lastState)
            updateCountDown(updateInterval)
        }
    }

    /**
     * Set the current count-down timer to [newValue] and also invoke the count-down update function.
     */
    private fun updateCountDown(newValue: Int) {
        countDownValue = newValue
        countDown(newValue)
    }
}
