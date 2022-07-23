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
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.track.LocationTellerService
import com.github.oheger.locationteller.track.TrackStorage

private const val TAG = "TrackViewModel"

/** The number of seconds per hour.*/
private const val SECS_PER_HOUR = 60 * 60

/**
 * An interface defining the contract of the view model for the tracking UI.
 *
 * The purpose of this interface is to support alternative implementations that can be used for instance if previews.
 */
interface TrackViewModel {
    /** A property with the statistics of the current tracking operation. */
    val trackStatistics: TrackStatsState

    /** A flag whether tracking is currently enabled or not. */
    val trackingEnabled: Boolean

    /** The current tracking configuration. */
    val trackConfig: TrackConfig

    /**
     * Set the tracking state to [enabled].
     */
    fun updateTrackingState(enabled: Boolean)

    /**
     * Set the tracking configuration to [config]. This function is called when there is a change in the configuration.
     */
    fun updateTrackConfig(config: TrackConfig)
}

/**
 * A class serving as view model for the tracking UI.
 *
 * This class manages a [TrackStatsState] instance and keeps it up-to-date by listening on changes on the shared
 * preferences that impact the tracking statistics.
 *
 * It further keeps track on the state whether tracking is currently enabled. The state is *false* when the application
 * starts. When the user changes the state in the UI, this model is notified, and it then invokes the tracking service
 * accordingly.
 */
class TrackViewModelImpl(
    /** The storage for accessing tracking-related properties. */
    val trackStorage: TrackStorage,

    /** The central [Application]. */
    application: Application
) : AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener, TrackViewModel {
    /**
     * Create an instance from the given [application]. Use [application] to construct a [TrackStorage].
     */
    constructor(application: Application) : this(createTrackStorage(application), application)

    /** Holds the statistics of the current tracking operation. */
    override val trackStatistics = TrackStatsState()

    /** The formatter for statistics data. */
    private val formatter = TrackStatsFormatter.create()

    /** Holds the current tracking state. */
    private val trackEnabledState = mutableStateOf(false)

    /** Stores the managed [TrackConfig]. */
    private var currentTrackConfig: TrackConfig

    init {
        trackStorage.setTrackingEnabled(false)
        trackStorage.preferencesHandler.registerListener(this)

        currentTrackConfig = TrackConfig.fromPreferences(trackStorage.preferencesHandler)

        initializeFromSharedPreferences()
    }

    /**
     * A flag whether tracking is currently enabled or not.
     */
    override val trackingEnabled: Boolean
        get() = trackEnabledState.value

    /**
     * Allows read-only access to the current track configuration.
     */
    override val trackConfig: TrackConfig
        get() = currentTrackConfig

    /**
     * Set the tracking state to [enabled]. This causes the tracking service to be updated accordingly.
     */
    override fun updateTrackingState(enabled: Boolean) {
        if (enabled != trackingEnabled) {
            trackEnabledState.value = enabled

            trackStorage.setTrackingEnabled(enabled)
            if (enabled) {
                if (trackConfig.autoResetStats) {
                    trackStorage.resetStatistics()
                }
                trackStorage.recordTrackingStart(formatter.timeService.currentTime())
            } else {
                trackStorage.recordTrackingEnd(formatter.timeService.currentTime())
            }

            getApplication<Application>().startService(serviceIntent())
        }
    }

    override fun updateTrackConfig(config: TrackConfig) {
        currentTrackConfig = config
    }

    /**
     * React on a change in the application's [preferences] by updating this model if it is affected by this
     * [property].
     */
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, property: String) {
        propertyChanged(property)
    }

    /**
     * Remove the change listener at the application's [SharedPreferences] if this model is no longer used.
     */
    override fun onCleared() {
        Log.i(TAG, "TrackViewModel is cleared.")
        trackStorage.preferencesHandler.preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * Return the [Intent] to start the tracking service.
     */
    internal fun serviceIntent(): Intent = Intent(getApplication(), LocationTellerService::class.java)

    /**
     * React on a change of a shared preferences [property].
     */
    private fun propertyChanged(property: String) {
        Log.d(TAG, "Property change event for '$property'.")

        when (property) {
            TrackStorage.PROP_TRACKING_START -> {
                trackStatistics.startTime = formatter.formatDate(trackStorage.trackingStartDate())
                updateElapsedTime()
                updateAverageSpeed()
            }
            TrackStorage.PROP_TRACKING_END -> {
                trackStatistics.endTime = formatter.formatDate(trackStorage.trackingEndDate())
                updateElapsedTime()
                updateAverageSpeed()
            }
            TrackStorage.PROP_LAST_CHECK ->
                trackStatistics.lastCheckTime = formatter.formatDate(trackStorage.lastCheck())
            TrackStorage.PROP_LAST_UPDATE ->
                trackStatistics.lastUpdateTime = formatter.formatDate(trackStorage.lastUpdate())
            TrackStorage.PROP_LAST_ERROR ->
                trackStatistics.lastErrorTime = formatter.formatDate(trackStorage.lastError())
            TrackStorage.PROP_CHECK_COUNT ->
                trackStatistics.numberOfChecks = trackStorage.checkCount().toString()
            TrackStorage.PROP_UPDATE_COUNT ->
                trackStatistics.numberOfUpdates = trackStorage.updateCount().toString()
            TrackStorage.PROP_ERROR_COUNT ->
                trackStatistics.numberOfErrors = trackStorage.errorCount().toString()
            TrackStorage.PROP_LAST_DISTANCE ->
                trackStatistics.lastDistance = trackStorage.lastDistance().toString()
            TrackStorage.PROP_TOTAL_DISTANCE -> {
                trackStatistics.totalDistance = trackStorage.totalDistance().toString()
                updateAverageSpeed()
            }
        }
    }

    /**
     * Initialize the properties managed by this class from the shared preferences.
     */
    private fun initializeFromSharedPreferences() {
        listOf(
            TrackStorage.PROP_CHECK_COUNT,
            TrackStorage.PROP_ERROR_COUNT,
            TrackStorage.PROP_LAST_CHECK,
            TrackStorage.PROP_LAST_DISTANCE,
            TrackStorage.PROP_LAST_ERROR,
            TrackStorage.PROP_LAST_UPDATE,
            TrackStorage.PROP_TOTAL_DISTANCE,
            TrackStorage.PROP_TRACKING_START,
            TrackStorage.PROP_TRACKING_END,
            TrackStorage.PROP_UPDATE_COUNT,
        ).filter { it in trackStorage.preferencesHandler.preferences }
            .forEach(this::propertyChanged)
    }

    /**
     * Update the elapsed time property if there was a change in the track start or end time.
     */
    private fun updateElapsedTime() {
        trackStatistics.elapsedTime = trackTimeInMillis()?.let { formatter.formatDuration(it) }
    }

    /**
     * Update the average tracking speed property if there was a change in a property that affects this value.
     */
    private fun updateAverageSpeed() {
        trackStatistics.averageSpeed = trackTimeInMillis()?.let { trackTime ->
            val distance = trackStorage.totalDistance().toDouble()
            val speed = distance / trackTime * SECS_PER_HOUR
            formatter.formatNumber(speed)
        }
    }

    /**
     * Compute the tracking time in milliseconds. The return value is defined if tracking is ongoing or has been
     * stopped. It is *null* if no start time is recorded.
     */
    private fun trackTimeInMillis(): Long? =
        trackStorage.trackingStartDate()?.let { startTime ->
            val endTime = trackStorage.trackingEndDate()?.time
                ?: formatter.timeService.currentTime().currentTime
            endTime - startTime.time
        }?.takeIf { it > 0 }
}

/**
 * Create the [TrackStorage] for accessing tracking-related properties from the given [application].
 */
private fun createTrackStorage(application: Application): TrackStorage {
    val preferencesHandler = PreferencesHandler.getInstance(application)
    return TrackStorage(preferencesHandler)
}
