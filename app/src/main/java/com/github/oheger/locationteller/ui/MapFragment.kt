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
package com.github.oheger.locationteller.ui

import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.*
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapUpdater
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.TimeDeltaFormatter
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.track.PreferencesHandler
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * A fragment class to display the location information read from the server
 * on a maps view.
 */
class MapFragment : androidx.fragment.app.Fragment(), OnMapReadyCallback, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    /** The handler for scheduling delayed tasks.*/
    private lateinit var handler: Handler

    /** The handler for accessing preferences.*/
    private lateinit var preferencesHandler: PreferencesHandler

    /** The formatter for time delta values.*/
    private lateinit var deltaFormatter: TimeDeltaFormatter

    /** The factory for markers on the map. */
    private lateinit var markerFactory: MarkerFactory

    /** The map element. */
    private var map: GoogleMap? = null

    /** The current server configuration. */
    private var serverConfig: ServerConfig? = null

    /** The update interval for location data. */
    private var updateInterval: Long = 0

    /**
     * A flag that indicates whether automatic updates are possible. This flag
     * is *true* when all prerequisites for querying the server are met.
     */
    private var canUpdate: Boolean = false

    /** The current state of location data.*/
    private var state: LocationFileState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(logTag, "onCreate()")
        setHasOptionsMenu(true)
        handler = Handler()
        deltaFormatter = TimeDeltaFormatter.create(requireContext())
        markerFactory = MarkerFactory(deltaFormatter)
        preferencesHandler = PreferencesHandler.create(requireContext())
        serverConfig = preferencesHandler.createServerConfig()
        val trackConfig = preferencesHandler.createTrackConfig()
        updateInterval = (trackConfig?.minTrackInterval ?: defaultUpdateInterval) * 1000L
        Log.i(logTag, "Set update interval to $updateInterval ms.")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        val mapFragment = childFragmentManager.findFragmentById(R.id.googleMap) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.item_center -> {
                centerToRecentMarker()
                true
            }
            R.id.item_zoomArea -> {
                zoomToTrackedArea()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        cancelPendingUpdates()
        super.onPause()
    }

    /**
     * Notifies this object that the map is now ready. If possible, the first
     * update operation is started.
     */
    override fun onMapReady(map: GoogleMap?) {
        Log.i(logTag, "Map is ready")
        this.map = map
        canUpdate = serverConfig != null && map != null
        Log.i(logTag, "Location updates possible: $canUpdate.")
        updateState(true)
    }

    /**
     * Updates the location state by fetching new location data from the server
     * and updating the map view if necessary. The boolean parameter indicates
     * whether the view should be initialized, i.e. a meaningful zoom level
     * and position should be set. Note that this method is called only if all
     * prerequisites for an update operation are fulfilled; therefore, the
     * !! operator can be used to state that fields are not *null*.
     * @param initView flag whether the view should be initialized
     */
    private fun updateState(initView: Boolean) {
        if (canUpdate) {
            Log.i(logTag, "Triggering update operation.")
            val currentMap = map!!
            launch {
                val currentState = MapUpdater.updateMap(
                    serverConfig!!, currentMap, LocationFileState(emptyList(), emptyMap()),
                    markerFactory, System.currentTimeMillis()
                )
                if (initView) {
                    MapUpdater.zoomToAllMarkers(currentMap, currentState)
                    MapUpdater.centerRecentMarker(currentMap, currentState)
                }
                state = currentState

                handler.postAtTime(
                    { updateState(false) }, updateToken,
                    SystemClock.uptimeMillis() + updateInterval
                )
            }
        }
    }

    /**
     * Changes the map view, so that the most recent marker is in the center.
     */
    private fun centerToRecentMarker() {
        val currentMap = map
        val currentState = state
        if (currentMap != null && currentState != null) {
            MapUpdater.centerRecentMarker(currentMap, currentState)
        }
    }

    /**
     * Changes the map view to a zoom level, so that all markers available are
     * visible.
     */
    private fun zoomToTrackedArea() {
        val currentMap = map
        val currentState = state
        if (currentMap != null && currentState != null) {
            MapUpdater.zoomToAllMarkers(currentMap, currentState)
        }
    }

    /**
     * Cancels pending update operations that might have been scheduled using
     * this fragment's handler.
     */
    private fun cancelPendingUpdates() {
        handler.removeCallbacksAndMessages(updateToken)
    }

    companion object {
        /** Tag for log operations.*/
        private const val logTag = "MapFragment"

        /**
         * An update interval (in seconds) that is used when the corresponding
         * value in the tracking configuration is undefined.
         */
        private const val defaultUpdateInterval = 120

        /**
         * A token used when posting delayed update operations using the
         * fragment's handler. With this token tasks can be removed again when
         * the fragment becomes inactive or an update is requested manually.
         */
        private const val updateToken = "MAP_UPDATES"
    }
}
