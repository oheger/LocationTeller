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

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.track.PreferencesHandler
import kotlinx.android.synthetic.main.fragment_track.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * A fragment that allows enabling or disabling the tracking functionality.
 */
class TrackFragment : androidx.fragment.app.Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val logTag = "TrackFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(logTag, "Creating TrackFragment")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_track, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        switchTrackEnabled.setOnCheckedChangeListener { _, checked ->
            Log.i(logTag, "Set track enabled state to $checked.")
            fetchPreferences().setTrackingEnabled(checked)
        }
    }

    override fun onResume() {
        super.onResume()
        initUI()
        PreferencesHandler.registerListener(context!!, this)
    }

    override fun onPause() {
        PreferencesHandler.unregisterListener(context!!, this)
        super.onPause()
    }

    /**
     * Reacts on changes on preferences keys. If the key affected impacts the
     * UI of this fragment, it is updated.
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (PreferencesHandler.propLastUpdate == key || PreferencesHandler.propLastError == key ||
            PreferencesHandler.propLastCheck == key || PreferencesHandler.propTrackState == key ||
            PreferencesHandler.propLastDistance == key
        ) {
            initUI()
        }
    }

    /**
     * Returns a handler for the current preferences.
     * @return the _PreferencesHandler_
     */
    private fun fetchPreferences(): PreferencesHandler = PreferencesHandler.create(context!!)

    /**
     * Initializes the UI of this fragment based on the current preferences
     * values.
     */
    private fun initUI() {
        val prefHandler = fetchPreferences()
        switchTrackEnabled.isChecked = prefHandler.isTrackingEnabled()

        val formatter = SimpleDateFormat("kk:mm:ss", Locale.getDefault())
        initTimeComponent(labError, txtLastErrorTime, formatter, prefHandler.lastError())
        initTimeComponent(labCheck, txtLastCheckTime, formatter, prefHandler.lastCheck())
        if (initTimeComponent(textView2, txtLastUpdateTime, formatter, prefHandler.lastUpdate())) {
            txtDistance.visibility = View.VISIBLE
            txtDistance.text = getString(R.string.lab_last_distance, prefHandler.lastDistance())
        } else {
            txtDistance.visibility = View.GONE
        }
    }

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
