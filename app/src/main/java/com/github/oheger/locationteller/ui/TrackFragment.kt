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
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.databinding.FragmentTrackBinding
import com.github.oheger.locationteller.config.PreferencesHandler
import java.text.DateFormat
import java.util.Date

/**
 * A fragment that allows enabling or disabling the tracking functionality.
 */
open class TrackFragment : androidx.fragment.app.Fragment() {
    private val logTag = "TrackFragment"

    /** Holds the binding of this fragment. */
    private var _binding: FragmentTrackBinding? = null

    /**
     * A property for the convenient access to the binding, as long as this
     * fragment is active.
     */
    private val binding get() = _binding!!

    /** The object to access preferences. */
    private lateinit var prefHandler: PreferencesHandler

    /** The adapter for the statistics list. */
    private lateinit var statisticsAdapter: TrackingStatsListAdapter

    /**
     * The action that handles the permission request for querying locations.
     */
    private lateinit var locationPermAction: LocationPermAction

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(logTag, "Creating TrackFragment")
        _binding = FragmentTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        prefHandler = createPreferencesHandler()
        binding.switchTrackEnabled.isChecked = prefHandler.isTrackingEnabled()
        binding.switchTrackEnabled.setOnCheckedChangeListener { _, checked ->
            Log.i(logTag, "Set track enabled state to $checked.")
            if (checked) {
                locationPermAction.execute()
            } else {
                prefHandler.setTrackingEnabled(checked)
            }
        }

        statisticsAdapter = createTrackingStatsAdapter(prefHandler)
        binding.trackingStats.adapter = statisticsAdapter

        locationPermAction = LocationPermAction.create(
            this,
            this::enableTracking
        ) { binding.switchTrackEnabled.isChecked = false }
    }

    private fun enableTracking() {
        if(prefHandler.isAutoResetStats()) {
            prefHandler.resetStatistics()
        }
        prefHandler.setTrackingEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_track, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.item_track_reset_stats -> {
                prefHandler.resetStatistics()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        statisticsAdapter.activate()
    }

    override fun onPause() {
        statisticsAdapter.deactivate()
        super.onPause()
    }

    /**
     * Creates the _PreferencesHandler_ used by this fragment. (Protected to be
     * overridden in tests.)
     * @return the _PreferencesHandler_
     */
    protected open fun createPreferencesHandler(): PreferencesHandler =
        PreferencesHandler.getInstance(requireContext())

    /**
     * Creates the adapter for the list view with tracking statistics.
     * (Protected to be overridden in tests.)
     * @param prefHandler the preferences handler
     * @return the adapter for the tracking statistics list view
     */
    protected open fun createTrackingStatsAdapter(prefHandler: PreferencesHandler): TrackingStatsListAdapter =
        TrackingStatsListAdapter.create(requireContext(), prefHandler)

    /**
     * Initializes a time component with a nullable time. If the time is
     * defined, it is set for the field, and the field is made visible.
     * Otherwise, the field is removed.
     * @param label the label view
     * @param field the field view
     * @param formatter the formatter object
     * @param date the date to be displayed
     * @return a flag whether the field is visible
     */
    private fun initTimeComponent(label: View, field: TextView, formatter: DateFormat, date: Date?): Boolean {
        return if (date != null) {
            label.visibility = View.VISIBLE
            field.visibility = View.VISIBLE
            field.text = formatter.format(date)
            true
        } else {
            label.visibility = View.GONE
            field.visibility = View.GONE
            false
        }
    }
}
