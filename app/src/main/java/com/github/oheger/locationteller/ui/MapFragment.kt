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
import com.github.oheger.locationteller.track.PreferencesHandler
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.android.synthetic.main.fragment_map.*
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

    /** The object to interact with and update the map. */
    private var mapUpdater: MapUpdater? = null

    /** The update interval for location data. */
    private var updateInterval: Long = 0

    /**
     * A flag that indicates whether automatic updates are possible. This flag
     * is *true* when all prerequisites for querying the server are met.
     */
    private var canUpdate: Boolean = false

    /** The current state of location data.*/
    private var state: LocationFileState? = null

    /**
     * Flag whether the map should be centered automatically to the most recent
     * marker when a new state is received. This is associated with a menu
     * item.
     */
    private var autoCenter = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(logTag, "onCreate()")
        setHasOptionsMenu(true)
        handler = Handler()
        deltaFormatter = TimeDeltaFormatter.create(requireContext())
        markerFactory = MarkerFactory(deltaFormatter)
        preferencesHandler = PreferencesHandler.create(requireContext())
        val serverConfig = preferencesHandler.createServerConfig()
        mapUpdater = serverConfig?.let { MapUpdater(it) }
        val trackConfig = preferencesHandler.createTrackConfig()
        updateInterval = trackConfig.minTrackInterval * 1000L
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
            R.id.item_updateMap -> {
                cancelPendingUpdates()
                updateState(initView = false)
                true
            }
            R.id.item_autoCenter -> {
                autoCenter = !item.isChecked
                item.isChecked = autoCenter
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        Log.i(logTag, "onPause()")
        cancelPendingUpdates()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.i(logTag, "onResume()")
        updateState(false)
    }

    /**
     * Notifies this object that the map is now ready. If possible, the first
     * update operation is started.
     */
    override fun onMapReady(map: GoogleMap?) {
        Log.i(logTag, "Map is ready")
        this.map = map
        canUpdate = mapUpdater != null && map != null
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
        val currentMap = map
        if (canUpdate && currentMap != null) {
            Log.i(logTag, "Triggering update operation.")
            updateInProgress()
            launch {
                val currentState = mapUpdater?.updateMap(
                    currentMap, emptyState, markerFactory, System.currentTimeMillis()
                ) ?: emptyState
                if (initView) {
                    mapUpdater?.zoomToAllMarkers(currentMap, currentState)
                }
                if (initView || (autoCenter && currentState != state)) {
                    mapUpdater?.centerRecentMarker(currentMap, currentState)
                }
                newStateArrived(currentState)

                handler.postAtTime(
                    { updateState(false) }, updateToken,
                    SystemClock.uptimeMillis() + updateInterval
                )
            }
        }
    }

    /**
     * Updates the UI when an update operation starts.
     */
    private fun updateInProgress() {
        mapProgressBar.visibility = View.VISIBLE
        mapStatusLine.text = getString(R.string.map_status_updating)
    }

    /**
     * Updates the state after it has been retrieved from the server.
     * @param newState the new state
     */
    private fun newStateArrived(newState: LocationFileState) {
        Log.i(logTag, "Got new state.")
        state = newState
        mapProgressBar.visibility = View.INVISIBLE
        val statusText = if (newState.files.isEmpty()) getString(R.string.map_status_empty)
        else getString(R.string.map_status, newState.files.size, recentMarkerTime(newState))
        mapStatusLine.text = statusText
    }

    /**
     * Returns a string for the age of the most recent marker in the given
     * state. This is used to update the status line after new state data has
     * been retrieved.
     * @param newState the updated state
     */
    private fun recentMarkerTime(newState: LocationFileState): String =
        deltaFormatter.formatTimeDelta(
            System.currentTimeMillis() -
                    (newState.recentMarker()?.locationData?.time?.currentTime ?: 0)
        )

    /**
     * Changes the map view, so that the most recent marker is in the center.
     */
    private fun centerToRecentMarker() {
        val currentMap = map
        val currentState = state
        if (currentMap != null && currentState != null) {
            mapUpdater?.centerRecentMarker(currentMap, currentState)
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
            mapUpdater?.zoomToAllMarkers(currentMap, currentState)
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
         * A token used when posting delayed update operations using the
         * fragment's handler. With this token tasks can be removed again when
         * the fragment becomes inactive or an update is requested manually.
         */
        private const val updateToken = "MAP_UPDATES"

        /**
         * Constant for a special, empty _LocationFileState_. This state is
         * used as initial state and if no updater can be created.
         */
        private val emptyState = LocationFileState(emptyList(), emptyMap())
    }
}
