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
import android.util.Log
import android.view.*
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapUpdater
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.track.PreferencesHandler
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MapFragment : androidx.fragment.app.Fragment(), OnMapReadyCallback, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private val logTag = "MapFragment"

    /** The map element. */
    private var map: GoogleMap? = null

    /** The current state of location data.*/
    private var state: LocationFileState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    override fun onMapReady(map: GoogleMap?) {
        Log.i(logTag, "Map is ready")
        this.map = map
        val prefHandler = PreferencesHandler.create(context!!)
        val config = prefHandler.createServerConfig()
        if (config != null && map != null) {
            launch {
                val currentState = MapUpdater.updateMap(
                    config, map, LocationFileState(emptyList(), emptyMap()),
                    MarkerFactory.create(context!!), System.currentTimeMillis()
                )
                MapUpdater.zoomToAllMarkers(map, currentState)
                MapUpdater.centerRecentMarker(map, currentState)
                state = currentState
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
}
