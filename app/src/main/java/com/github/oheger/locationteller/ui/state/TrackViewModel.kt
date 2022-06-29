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
package com.github.oheger.locationteller.ui.state

import android.app.Application
import android.content.SharedPreferences

import androidx.lifecycle.AndroidViewModel

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage

/**
 * A class serving as view model for the tracking UI.
 *
 * This class manages a [TrackStatsState] instance and keeps it up-to-date by listening on changes on the shared
 * preferences that impact the tracking statistics.
 */
class TrackViewModel(application: Application) : AndroidViewModel(application),
    SharedPreferences.OnSharedPreferenceChangeListener {
    /** Holds the statistics of the current tracking operation. */
    val trackStatistics = TrackStatsState()

    /** The handler for accessing shared properties. */
    private val preferencesHandler: PreferencesHandler

    /** The formatter for statistics data. */
    private val formatter = TrackStatsFormatter.create()

    init {
        preferencesHandler = PreferencesHandler.getInstance(application)
        preferencesHandler.registerListener { _, property -> propertyChanged(property) }
    }

    /**
     * React on a change in the application's [preferences] by updating this model if it is affected by this
     * [property].
     */
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, property: String) {
        propertyChanged(property)
    }

    /**
     * React on a change of a shared preferences [property].
     */
    private fun propertyChanged(property: String) {
        when(property) {
            TrackStorage.PROP_TRACKING_START ->
                trackStatistics.startTime = formatter.formatDate(preferencesHandler.getDate(property))
            TrackStorage.PROP_TRACKING_END ->
                trackStatistics.endTime = formatter.formatDate(preferencesHandler.getDate(property))
            TrackStorage.PROP_LAST_CHECK ->
                trackStatistics.lastCheckTime = formatter.formatDate(preferencesHandler.getDate(property))
            TrackStorage.PROP_LAST_UPDATE ->
                trackStatistics.lastUpdateTime = formatter.formatDate(preferencesHandler.getDate(property))
            TrackStorage.PROP_LAST_ERROR ->
                trackStatistics.lastErrorTime = formatter.formatDate(preferencesHandler.getDate(property))
            TrackStorage.PROP_CHECK_COUNT ->
                trackStatistics.numberOfChecks = preferencesHandler.preferences.getInt(property, 0).toString()
            TrackStorage.PROP_UPDATE_COUNT ->
                trackStatistics.numberOfUpdates = preferencesHandler.preferences.getInt(property, 0).toString()
            TrackStorage.PROP_ERROR_COUNT ->
                trackStatistics.numberOfErrors = preferencesHandler.preferences.getInt(property, 0).toString()
            TrackStorage.PROP_LAST_DISTANCE ->
                trackStatistics.lastDistance = preferencesHandler.preferences.getInt(property, 0).toString()
            TrackStorage.PROP_TOTAL_DISTANCE ->
                trackStatistics.totalDistance = preferencesHandler.preferences.getLong(property, 0).toString()
        }
    }
}
