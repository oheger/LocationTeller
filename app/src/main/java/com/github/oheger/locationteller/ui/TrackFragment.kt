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

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.databinding.FragmentTrackBinding
import com.github.oheger.locationteller.track.TrackStorage

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

    /** The object to access persistent tracking-related properties. */
    private lateinit var trackStorage: TrackStorage

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
        trackStorage = createTrackStorage()

        binding.trackingStats.setContent {
            TrackUi()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_track, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.item_track_reset_stats -> {
                trackStorage.resetStatistics()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Create the [TrackStorage] object for accessing persistent properties related to the current tracking
     * operation. This function is *protected*, so that it can be overridden by tests.
     */
    protected open fun createTrackStorage(): TrackStorage {
        val preferencesHandler = PreferencesHandler.getInstance(requireContext())
        return TrackStorage(preferencesHandler)
    }
}
