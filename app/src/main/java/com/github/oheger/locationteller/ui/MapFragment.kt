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
package com.github.oheger.locationteller.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.databinding.FragmentMapBinding
import com.github.oheger.locationteller.map.AlphaRange
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapMarkerState
import com.github.oheger.locationteller.map.MapUpdater
import com.github.oheger.locationteller.map.MarkerData
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.RangeTimeDeltaAlphaCalculator
import com.github.oheger.locationteller.map.TimeDeltaAlphaCalculator
import com.github.oheger.locationteller.duration.TimeDeltaFormatter
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.LocationRetriever
import com.github.oheger.locationteller.track.LocationRetrieverFactory
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.config.TrackServerConfig
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * A fragment class to display the location information read from the server
 * on a maps view.
 */
open class MapFragment : androidx.fragment.app.Fragment(), OnMapReadyCallback, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    /** Holds the binding of this fragment. */
    private var _binding: FragmentMapBinding? = null

    /**
     * A property for the convenient access to the binding, as long as this
     * fragment is active.
     */
    private val binding get() = _binding!!

    /** The handler for scheduling delayed tasks.*/
    private lateinit var handler: Handler

    /** The action handling the permission to request the location. */
    private lateinit var locationPermAction: LocationPermAction

    /**
     * The handler for accessing preferences. This is lazy because the handler
     * is accessed by multiple methods during initialization whose order should
     * not be relevant.
     */
    private val preferencesHandler: PreferencesHandler by lazy(LazyThreadSafetyMode.NONE) {
        createPreferencesHandler()
    }

    /** The formatter for time delta values.*/
    private lateinit var deltaFormatter: TimeDeltaFormatter

    /** The factory for markers on the map. */
    private lateinit var markerFactory: MarkerFactory

    /** The service for querying the current time. */
    private lateinit var timeService: TimeService

    /** The object to retrieve the location of this device. */
    private lateinit var locationRetriever: LocationRetriever

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
    private var state = emptyState

    /** Stores the marker for the own location. */
    private var ownMarker: MarkerData? = null

    /**
     * Flag whether the map should be centered automatically to the most recent
     * marker when a new state is received. This is associated with a menu
     * item.
     */
    private var autoCenter = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(LOG_TAG, "onCreate()")
        setHasOptionsMenu(true)
        handler = createHandler()
        deltaFormatter = TimeDeltaFormatter(
            unitDay = requireContext().getString(R.string.time_days),
            unitHour = requireContext().getString(R.string.time_hours),
            unitMin = requireContext().getString(R.string.time_minutes),
            unitSec = requireContext().getString(R.string.time_secs)
        )
        markerFactory = createMarkerFactoryForCalculatorId(preferencesHandler.getFadingMode())
        timeService = createTimeService()
        val serverConfig = TrackServerConfig.fromPreferences(preferencesHandler)
            .takeIf { it.isDefined() }?.let { c -> ServerConfig(c.serverUri, c.basePath, c.user, c.password) }
        mapUpdater = serverConfig?.let(::createMapUpdater)
        val trackConfig = TrackConfig.fromPreferences(preferencesHandler)
        updateInterval = trackConfig.minTrackInterval * 1000L
        Log.i(LOG_TAG, "Set update interval to $updateInterval ms.")

        val retrieverFactory = createLocationRetrieverFactory()
        locationRetriever = retrieverFactory.createRetriever(requireContext(), trackConfig, false)

        locationPermAction = LocationPermAction.create(this, { map?.let(this::showOwnLocation) })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val mapFragment = childFragmentManager.findFragmentById(R.id.googleMap) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_map, menu)
        super.onCreateOptionsMenu(menu, inflater)
        selectFadingModeItem(menu)
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
            R.id.item_own_location -> {
                if (map != null) {
                    locationPermAction.execute()
                }
                true
            }
            R.id.item_center_own_location -> {
                centerToOwnLocation()
                true
            }
            R.id.item_fade_none,
            R.id.item_fade_slow,
            R.id.item_fade_fast,
            R.id.item_fade_slow_strong,
            R.id.item_fade_fast_strong -> {
                changeFadingMode(item)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * This implementation changes the enabled states of some menu items that
     * depend on the availability of data.
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        val mapAvailable = map != null && mapUpdater != null
        val locationsPresent = state.locations.files.isNotEmpty()
        menu.findItem(R.id.item_updateMap).isEnabled = mapAvailable
        menu.findItem(R.id.item_center).isEnabled = locationsPresent
        menu.findItem(R.id.item_zoomArea).isEnabled = locationsPresent
        menu.findItem(R.id.item_own_location).isEnabled = mapAvailable
        menu.findItem(R.id.item_center_own_location).isEnabled = ownMarker != null
    }

    override fun onPause() {
        Log.i(LOG_TAG, "onPause()")
        cancelPendingUpdates()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.i(LOG_TAG, "onResume()")
        updateState(false)
    }

    /**
     * Notifies this object that the map is now ready. If possible, the first
     * update operation is started.
     */
    override fun onMapReady(map: GoogleMap) {
        Log.i(LOG_TAG, "Map is ready")
        this.map = map
        canUpdate = mapUpdater != null
        Log.i(LOG_TAG, "Location updates possible: $canUpdate.")
        updateState(true)
    }

    /**
     * Creates the _PreferencesHandler_ used by this fragment.
     * @return the _PreferencesHandler_ instance
     */
    protected open fun createPreferencesHandler(): PreferencesHandler =
        PreferencesHandler.getInstance(requireContext())

    /**
     * Creates the _MapUpdater_ to be used by this fragment.
     * @return the _MapUpdater_ instance
     */
    protected open fun createMapUpdater(serverConfig: ServerConfig): MapUpdater =
        MapUpdater(serverConfig, getString(R.string.map_distance))

    /**
     * Creates the _TimeService_ to be used by this fragment.
     * @return the _TimeService_
     */
    protected open fun createTimeService(): TimeService = CurrentTimeService

    /**
     * Creates the loop handler to be used by this fragment.
     * @return the _Handler_
     */
    protected open fun createHandler(): Handler = Handler(Looper.getMainLooper())

    /**
     * Creates the factory to create a _LocationRetriever_ object.
     * @return the factory for a _LocationRetriever_
     */
    protected open fun createLocationRetrieverFactory(): LocationRetrieverFactory =
        LocationRetrieverFactory()

    /**
     * Creates the [MarkerFactory] for populating the map view.
     * @param deltaFormatter the formatter for time deltas
     * @param calculator the calculator for alpha values
     * @return the [MarkerFactory]
     */
    protected open fun createMarkerFactory(deltaFormatter: TimeDeltaFormatter, calculator: TimeDeltaAlphaCalculator):
            MarkerFactory = MarkerFactory(deltaFormatter, calculator)

    /**
     * Returns the [TimeDeltaAlphaCalculator] to be used for the menu item
     * specified.
     * @param itemId the ID of the menu item determining the calculator
     * @return the [TimeDeltaAlphaCalculator] for this menu item
     */
    internal fun alphaCalculatorFor(itemId: Int): TimeDeltaAlphaCalculator =
        alphaCalculators[itemId] ?: DisabledFadeOutAlphaCalculator

    /**
     * Updates the location state by fetching new location data from the server
     * and updating the map view if necessary. The boolean parameter indicates
     * whether the view should be initialized, i.e. a meaningful zoom level
     * and position should be set.
     * @param initView flag whether the view should be initialized
     * @param forceUpdate flag whether an update should always be done
     */
    private fun updateState(initView: Boolean, forceUpdate: Boolean = false) {
        val currentMap = map
        if (canUpdate && currentMap != null) {
            Log.i(LOG_TAG, "Triggering update operation.")
            updateInProgress()
            launch {
                val stateToPass = if (forceUpdate) emptyState else state
                val currentState = mapUpdater?.updateMap(
                    currentMap, stateToPass, ownMarker, markerFactory, timeService.currentTime().currentTime
                ) ?: emptyState
                if (initView) {
                    mapUpdater?.zoomToAllMarkers(currentMap, currentState.locations)
                }
                if (initView || (autoCenter && currentState != state)) {
                    mapUpdater?.centerRecentMarker(currentMap, currentState.locations)
                }
                newStateArrived(currentState)

                handler.postAtTime(
                    { updateState(false) }, UPDATE_TOKEN,
                    SystemClock.uptimeMillis() + updateInterval
                )
            }
        }
    }

    /**
     * Updates the UI when an update operation starts.
     */
    private fun updateInProgress() {
        binding.mapProgressBar.visibility = View.VISIBLE
        binding.mapStatusLine.text = getString(R.string.map_status_updating)
    }

    /**
     * Updates the state after it has been retrieved from the server.
     * @param newState the new state
     */
    private fun newStateArrived(newState: MapMarkerState) {
        Log.i(LOG_TAG, "Got new state.")
        state = newState
        binding.mapProgressBar.visibility = View.INVISIBLE
        val statusText = if (newState.locations.files.isEmpty()) getString(R.string.map_status_empty)
        else getString(R.string.map_status, newState.locations.files.size, recentMarkerTime(newState.locations))
        binding.mapStatusLine.text = statusText
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
        if (currentMap != null) {
            mapUpdater?.centerRecentMarker(currentMap, state.locations)
        }
    }

    /**
     * Changes the map view to a zoom level, so that all markers available are
     * visible.
     */
    private fun zoomToTrackedArea() {
        val currentMap = map
        if (currentMap != null) {
            mapUpdater?.zoomToAllMarkers(currentMap, state.locations)
        }
    }

    /**
     * Query the location of this device and update [currentMap] to display it.
     * This function is invoked only if the permission to request the location
     * is available.
     */
    private fun showOwnLocation(currentMap: GoogleMap) = launch {
        val marker = locationRetriever.fetchLocation()?.let {
            MarkerData(
                LocationData(it.latitude, it.longitude, timeService.currentTime()),
                LatLng(it.latitude, it.longitude)
            )
        }
        if (marker != null) {
            cancelPendingUpdates()
            state = mapUpdater?.updateMap(
                currentMap, state, marker, markerFactory,
                timeService.currentTime().currentTime
            ) ?: state
            mapUpdater?.centerMarker(currentMap, marker)
            ownMarker = marker
        } else {
            val toast = Toast.makeText(
                requireContext(), R.string.map_no_own_location,
                Toast.LENGTH_SHORT
            )
            toast.show()
        }
    }

    /**
     * Changes the map view, so that the own location marker is in the center.
     */
    private fun centerToOwnLocation() {
        map?.let { currentMap ->
            mapUpdater?.centerMarker(currentMap, ownMarker)
        }
    }

    /**
     * Cancels pending update operations that might have been scheduled using
     * this fragment's handler.
     */
    private fun cancelPendingUpdates() {
        handler.removeCallbacksAndMessages(UPDATE_TOKEN)
    }

    /**
     * A menu item setting the fading mode has been selected.
     * @param item the menu item
     */
    private fun changeFadingMode(item: MenuItem) {
        item.isChecked = true
        markerFactory = createMarkerFactoryForCalculatorId(item.itemId)
        updateState(initView = false, forceUpdate = true)
        preferencesHandler.setFadingMode(item.itemId)
    }

    /**
     * Creates a [MarkerFactory] with the alpha calculator referenced by the ID
     * provided.
     * @param id the ID of the alpha calculator
     * @return the resulting [MarkerFactory]
     */
    private fun createMarkerFactoryForCalculatorId(id: Int): MarkerFactory =
        createMarkerFactory(deltaFormatter, alphaCalculatorFor(id))

    /**
     * Selects the fading mode item of the option menu that corresponds to the
     * mode stored in the preferences.
     * @param menu the option menu
     */
    private fun selectFadingModeItem(menu: Menu) {
        val fadingMode = preferencesHandler.getFadingMode()
        val actualMode = if (alphaCalculators.containsKey(fadingMode)) fadingMode
        else R.id.item_fade_none
        menu.findItem(actualMode).isChecked = true
    }

    companion object {
        /**
         * A token used when posting delayed update operations using the
         * fragment's handler. With this token tasks can be removed again when
         * the fragment becomes inactive or an update is requested manually.
         */
        const val UPDATE_TOKEN = "MAP_UPDATES"

        /** Tag for log operations.*/
        private const val LOG_TAG = "MapFragment"

        private const val HOUR_MILLIS = 60 * 60 * 1000L

        /** Milliseconds in one day. */
        private const val DAY_MILLIS = 24 * HOUR_MILLIS

        /**
         * Constant for a special, empty _MapMarkerState. This state is
         * used as initial state and if no updater can be created.
         */
        private val emptyState = MapMarkerState(LocationFileState(emptyList(), emptyMap()), null)

        /** The alpha calculator for fast and strong fading. */
        private val calculatorFastStrong = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, HOUR_MILLIS),
                AlphaRange(0.5f, 0.2f, DAY_MILLIS)
            ), 0.1f
        )

        /** The alpha calculator for slow and strong fading. */
        private val calculatorSlowStrong = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, DAY_MILLIS),
                AlphaRange(0.5f, 0.2f, 7 * DAY_MILLIS)
            ), 0.1f
        )

        /** The alpha calculator for fast normal fading. */
        private val calculatorFast = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, HOUR_MILLIS),
                AlphaRange(0.7f, 0.5f, DAY_MILLIS)
            ), 0.4f
        )

        /** The alpha calculator for slow normal fading. */
        private val calculatorSlow = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, DAY_MILLIS),
                AlphaRange(0.7f, 0.5f, 7 * DAY_MILLIS)
            ), 0.4f
        )

        /** A map assigning calculators to menu items. */
        private val alphaCalculators = mapOf<Int, TimeDeltaAlphaCalculator>(
            R.id.item_fade_fast to calculatorFast,
            R.id.item_fade_fast_strong to calculatorFastStrong,
            R.id.item_fade_slow to calculatorSlow,
            R.id.item_fade_slow_strong to calculatorSlowStrong
        )
    }
}
