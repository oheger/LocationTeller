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
package com.github.oheger.locationteller.track

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.github.oheger.locationteller.server.ServerConfig
import java.util.*

/**
 * A class managing access to the preferences for the location teller
 * application.
 *
 * This class defines constants for all the properties supported and offers
 * some helper functions for accessing specific settings.
 *
 * @param preferences the managed _SharedPreferences_ instance
 */
class PreferencesHandler(val preferences: SharedPreferences) {
    /**
     * Creates a _ServerConfig_ object from the managed preferences. If
     * mandatory properties are missing, result is *null*.
     * @return the server configuration or *null*
     */
    fun createServerConfig(): ServerConfig? {
        val serverUri = preferences.getString(propServerUri, null)
        val basePath = preferences.getString(propBasePath, null)
        val user = preferences.getString(propUser, null)
        val password = preferences.getString(propPassword, null)
        return if (serverUri == null || basePath == null || user == null || password == null) {
            return null
        } else ServerConfig(serverUri, basePath, user, password)
    }

    /**
     * Creates a _TrackConfig_ object from the managed preferences. For missing
     * properties in the underlying shared preferences default values are set.
     * @return the track configuration
     */
    fun createTrackConfig(): TrackConfig {
        val minTrackInterval = preferences.getNumeric(
            propMinTrackInterval, factor = minute,
            defaultValue = defaultMinTrackInterval
        )
        val maxTrackInterval = preferences.getNumeric(
            propMaxTrackInterval, factor = minute,
            defaultValue = defaultMaxTrackInterval
        )
        val intervalIncrementOnIdle = preferences.getNumeric(
            propIdleIncrement, factor = minute,
            defaultValue = defaultIdleIncrement
        )
        val locationValidity = preferences.getNumeric(
            propLocationValidity, factor = minute,
            defaultValue = defaultLocationValidity
        )
        val locationUpdateThreshold = preferences.getNumeric(
            propLocationUpdateThreshold,
            defaultValue = defaultLocationUpdateThreshold
        )
        val retryOnErrorTime = preferences.getNumeric(propRetryOnErrorTime, defaultValue = defaultRetryOnErrorTime)
        val gpsTimeout = preferences.getNumeric(propGpsTimeout, defaultValue = defaultGpsTimeout)
        return TrackConfig(
            minTrackInterval = minTrackInterval, maxTrackInterval = maxTrackInterval,
            intervalIncrementOnIdle = intervalIncrementOnIdle, locationValidity = locationValidity,
            locationUpdateThreshold = locationUpdateThreshold, retryOnErrorTime = retryOnErrorTime,
            gpsTimeout = gpsTimeout
        )
    }

    /**
     * Updates a _SharedPreferences_ object. This function obtains an editor
     * from the preferences, invokes the block on it and finally applies the
     * changes.
     * @param block the lambda to update the preferences
     */
    fun update(block: SharedPreferences.(SharedPreferences.Editor) -> Unit) {
        val editor = preferences.edit()
        block.invoke(preferences, editor)
        editor.apply()
    }

    /**
     * Checks whether tracking is currently active. This is determined by a
     * special property.
     * @return a flag whether tracking is active
     */
    fun isTrackingEnabled(): Boolean =
        preferences.getBoolean(propTrackState, false)

    /**
     * Updates the tracking enabled state in the managed preferences.
     * @param flag the new tracking state
     */
    fun setTrackingEnabled(flag: Boolean) {
        update { editor -> editor.putBoolean(propTrackState, flag) }
    }

    /**
     * Sets the preferences property for the last error to the given timestamp.
     * @param at the time when the error happened
     */
    fun recordError(at: Long) {
        update { editor -> editor.putLong(propLastError, at) }
    }

    /**
     * Sets the preferences properties for the last successful update to the
     * given timestamp and distance. The last error property is cleared.
     * @param at the time when the update happened
     * @param distance the distance to the last position
     */
    fun recordUpdate(at: Long, distance: Int) {
        update { editor ->
            editor.putLong(propLastUpdate, at)
                .putInt(propLastDistance, distance)
                .remove(propLastError)
        }
    }

    /**
     * Sets the preferences property for the last check time to the given
     * timestamp.
     * @param at the time when the last check has happened
     */
    fun recordCheck(at: Long) {
        update { editor -> editor.putLong(propLastCheck, at) }
    }

    /**
     * Returns a _Date_ with the last error that happened. Result is *null* if
     * the last update was successful.
     * @return the date of the last error
     */
    fun lastError(): Date? = preferences.getDate(propLastError)

    /**
     * Returns a _Date_ when the last updated took place. Result is *null* if
     * no update has been recorded so far.
     * @return the date of the last update
     */
    fun lastUpdate(): Date? = preferences.getDate(propLastUpdate)

    /**
     * Returns a _Date_ when the last check for a location update took place.
     * This timestamp is recorded any time the service is invoked, even if
     * there was no change in the location. Result is *null* if no check time
     * has been recorded so far.
     * @return the date of the last check
     */
    fun lastCheck(): Date? = preferences.getDate(propLastCheck)

    /**
     * Returns the distance of the last location update in meters.
     * @return the distance of the last location update
     */
    fun lastDistance(): Int = preferences.getInt(propLastDistance, 0)

    /**
     * Registers the given change listener at the managed preferences.
     * @param listener the listener to be registered
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Removes the given change listener from the managed preferences.
     * @param listener the listener to be removed
     */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Sets default values for the shared preferences corresponding to options
     * of the track configuration if they are undefined. With this function it
     * can be ensured that the shared preferences are initialized with
     * meaningful value.
     */
    fun initTrackConfigDefaults() {
        val undefinedProps = configDefaults.filterNot { preferences.contains(it.key) }
        if (undefinedProps.isNotEmpty()) {
            val editor = preferences.edit()
            undefinedProps.forEach { pair ->
                editor.putString(pair.key, pair.value.toString())
            }
            editor.apply()
        }
    }

    /**
     * Extension function to query a numeric property from a preferences
     * object. From the settings screen, the properties are stored as
     * strings. Therefore, a conversion has to be done. Sometimes the config
     * UI uses a different unit than the logic. This is handled by allowing a
     * factor to be specified. A default value can be provided to deal with
     * undefined properties. Note that the factor is not applied to the default
     * value.
     * @param key the key to be queried
     * @param factor a factor to be applied to the value
     * @param defaultValue the default value to be applied
     * @return the numeric value of this key
     */
    private fun SharedPreferences.getNumeric(key: String, factor: Int = 1, defaultValue: Int = undefinedNumber): Int {
        val value = getString(key, undefinedNumberStr)?.toInt() ?: undefinedNumber
        return if (value == undefinedNumber) defaultValue else value * factor
    }

    /**
     * Extension function to query a _Date_ property from a preferences
     * object. The actual type of the property is _Long_. If it is defined, it
     * is converted to a _Date_. Otherwise, result is *null*.
     * @param key the key to be queried
     * @return the _Date_ value of the property or *null*
     */
    private fun SharedPreferences.getDate(key: String): Date? =
        if (contains(key)) {
            val time = getLong(key, 0)
            Date(time)
        } else null

    companion object {
        /** Shared preferences property for the track server URI.*/
        const val propServerUri = "trackServerUri"

        /** Shared preferences property for the base path on the server.*/
        const val propBasePath = "trackRelativePath"

        /** Shared preferences property for the user name.*/
        const val propUser = "userName"

        /** Shared preferences property for the password.*/
        const val propPassword = "password"

        /** Shared preferences property for the minimum track interval.*/
        const val propMinTrackInterval = "minTrackInterval"

        /** Shared preferences property for the maximum track interval.*/
        const val propMaxTrackInterval = "maxTrackInterval"

        /** Shared preferences property for the increment interval.*/
        const val propIdleIncrement = "intervalIncrementOnIdle"

        /** Shared preferences property for the increment interval.*/
        const val propLocationValidity = "locationValidity"

        /** Shared preferences property for the location update threshold.*/
        const val propLocationUpdateThreshold = "locationUpdateThreshold"

        /** Shared preferences property for the retry on error time. */
        const val propRetryOnErrorTime = "retryOnErrorTime"

        /** Shared preferences property for the GPS timeout. */
        const val propGpsTimeout = "gpsTimeout"

        /** Shared preferences property for the tracking state.*/
        const val propTrackState = "trackEnabled"

        /** Shared preferences property for the latest update of location data. */
        const val propLastUpdate = "lastUpdate"

        /** Shared preferences property for the distance of the last update. */
        const val propLastDistance = "lastDistance"

        /** Shared preferences property for the latest error that occurred.*/
        const val propLastError = "lastError"

        /** Shared preferences property for the last check for an update. */
        const val propLastCheck = "lastCheck"

        /** A default value for the minimum track interval (in seconds). */
        const val defaultMinTrackInterval = 180

        /** A default value for the maximum track interval (in seconds). */
        const val defaultMaxTrackInterval = 900

        /** A default value for the idle increment interval (in seconds). */
        const val defaultIdleIncrement = 120

        /** A default value for the location validity time (in seconds).*/
        const val defaultLocationValidity = 43200 // 12 hours

        /** A default value for the retry on error time (in seconds). */
        const val defaultRetryOnErrorTime = 30

        /** A default value for the GPS timeout (in seconds). */
        const val defaultGpsTimeout = 45

        /** A default value for the location update threshold property. */
        const val defaultLocationUpdateThreshold = 10

        /** Constant for an undefined numeric property.*/
        private const val undefinedNumber = -1

        /** String value of an undefined numeric property.*/
        private const val undefinedNumberStr = undefinedNumber.toString()

        /** A set with all properties related to configuration.*/
        private val configProps = setOf(
            propServerUri, propBasePath, propUser, propPassword,
            propMinTrackInterval, propMaxTrackInterval, propIdleIncrement, propLocationValidity,
            propLocationUpdateThreshold, propRetryOnErrorTime, propGpsTimeout
        )

        /**
         * A map with configuration properties and their default values. This
         * is used to initialize shared preferences.
         */
        private val configDefaults = mapOf(
            propMinTrackInterval to (defaultMinTrackInterval / 60),
            propMaxTrackInterval to (defaultMaxTrackInterval / 60),
            propIdleIncrement to (defaultIdleIncrement / 60),
            propLocationValidity to (defaultLocationValidity / 60),
            propLocationUpdateThreshold to defaultLocationUpdateThreshold,
            propRetryOnErrorTime to defaultRetryOnErrorTime,
            propGpsTimeout to defaultGpsTimeout
        )

        /** Factor to convert minutes to seconds. */
        private const val minute = 60

        /**
         * Creates a _PreferencesHandler_ object based on the given context.
         * @param context the current context
         * @return the _PreferencesHandler_
         */
        fun create(context: Context): PreferencesHandler {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            return PreferencesHandler(pref)
        }

        /**
         * Checks whether the given property is related to the configuration
         * of the application. (Other properties contain persistent application
         * state.)
         * @param prop the property in question
         * @return *true* for a configuration property; *false* otherwise
         */
        fun isConfigProperty(prop: String): Boolean = configProps.contains(prop)

        /**
         * Registers a preferences change listener at the default preferences.
         * @param context the current context
         * @param listener the listener
         */
        fun registerListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            create(context).registerListener(listener)
        }

        /**
         * Removes the given change listener from the default preferences.
         * @param context the current context
         * @param listener the listener
         */
        fun unregisterListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            create(context).unregisterListener(listener)
        }
    }
}
