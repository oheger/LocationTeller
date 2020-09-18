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
package com.github.oheger.locationteller.track

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.oheger.locationteller.server.ServerConfig
import java.util.Date

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
        val offlineStorageSize = preferences.getNumeric(
            propOfflineStorageSize,
            defaultValue = defaultOfflineStorageSize
        )
        val offlineStorageSyncTime = preferences.getNumeric(
            propOfflineStorageSyncTime,
            defaultValue = defaultOfflineStorageSyncTime
        )
        val multiUploadChunkSize = preferences.getNumeric(
            propMultiUploadChunkSize,
            defaultValue = defaultMultiUploadChunkSize
        )
        return TrackConfig(
            minTrackInterval = minTrackInterval, maxTrackInterval = maxTrackInterval,
            intervalIncrementOnIdle = intervalIncrementOnIdle, locationValidity = locationValidity,
            locationUpdateThreshold = locationUpdateThreshold, retryOnErrorTime = retryOnErrorTime,
            gpsTimeout = gpsTimeout, autoResetStats = isAutoResetStats(), offlineStorageSize = offlineStorageSize,
            maxOfflineStorageSyncTime = offlineStorageSyncTime, multiUploadChunkSize = multiUploadChunkSize
        )
    }

    /**
     * Updates a _SharedPreferences_ object. This function obtains an editor
     * from the preferences, invokes the block on it and finally applies the
     * changes.
     * @param block the lambda to update the preferences
     */
    fun update(block: SharedPreferences.Editor.() -> Unit) {
        val editor = preferences.edit()
        editor.block()
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
     * Updates the tracking enabled state in the managed preferences. This
     * causes some related properties to be updated as well.
     * @param flag the new tracking state
     */
    fun setTrackingEnabled(flag: Boolean) {
        update {
            val currentTime = System.currentTimeMillis()
            putBoolean(propTrackState, flag)
            if (flag) {
                putLong(propTrackingStart, currentTime)
                remove(propTrackingEnd)
            } else {
                putLong(propTrackingEnd, currentTime)
            }
        }
    }

    /**
     * Returns a flag whether the tracking statistics should be reset
     * automatically when starting a new track operation.
     * @return the auto reset statistics flag
     */
    fun isAutoResetStats(): Boolean = preferences.getBoolean(propAutoResetStats, false)

    /**
     * Sets the preferences property for the last error to the given timestamp
     * and also updates the total error counter.
     * @param at the time when the error happened
     * @param count the total number of errors
     */
    fun recordError(at: Long, count: Int) {
        update {
            putLong(propLastError, at)
            putInt(propErrorCount, count)
        }
    }

    /**
     * Updates the preferences properties for the last successful update with
     * regards to the passed in information.
     * @param at the time when the update happened
     * @param count the number of updates
     * @param distance the distance to the last position
     * @param totalDistance the accumulated distance
     */
    fun recordUpdate(at: Long, count: Int, distance: Int, totalDistance: Long) {
        update {
            putLong(propLastUpdate, at)
            putInt(propUpdateCount, count)
            putInt(propLastDistance, distance)
            putLong(propTotalDistance, totalDistance)
        }
    }

    /**
     * Sets the preferences property for the last check time to the given
     * timestamp and also updates the number of checks.
     * @param at the time when the last check has happened
     * @param count the number of checks
     */
    fun recordCheck(at: Long, count: Int) {
        update {
            putLong(propLastCheck, at)
            putInt(propCheckCount, count)
        }
    }

    /**
     * Returns a _Date_ with the last error that happened. Result is *null* if
     * the last update was successful.
     * @return the date of the last error
     */
    fun lastError(): Date? = preferences.getDate(propLastError)

    /**
     * Returns the number of errors that have been encountered since the
     * statistics have been reset.
     * @return the number of errors
     */
    fun errorCount(): Int = preferences.getInt(propErrorCount, 0)

    /**
     * Returns the number of checks that have been performed since the
     * statistics have been reset.
     * @return the number of checks
     */
    fun checkCount(): Int = preferences.getInt(propCheckCount, 0)

    /**
     * Returns the number of updates that have been performed since the
     * statistics have been reset.
     * @return the number of updates
     */
    fun updateCount(): Int = preferences.getInt(propUpdateCount, 0)

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
     * Returns the total distance of all checks that have been recorded (in
     * meters).
     * @return the total distance of all location updates
     */
    fun totalDistance(): Long = preferences.getLong(propTotalDistance, 0)

    /**
     * Returns the recorded start date of the current tracking process.
     * Result is *null* if tracking has never been started.
     * @return the start date of the current tracking process
     */
    fun trackingStartDate(): Date? = preferences.getDate(propTrackingStart)

    /**
     * Returns the recorded end date of the current tracking process. Result
     * is *null* if tracking is currently active.
     * @return the time tracking was stopped or *null*
     */
    fun trackingEndDate(): Date? = preferences.getDate(propTrackingEnd)

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
     * Resets all the stored values that are related to tracking statistics.
     */
    fun resetStatistics() {
        update {
            resetProps.forEach { remove(it) }
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
            if (time < minDateValue) null else Date(time)
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

        /** Shared preferences property for the offline storage size. */
        const val propOfflineStorageSize = "offlineStorageSize"

        /**
         * Shared preferences property for the sync time of the offline
         * storage.
         */
        const val propOfflineStorageSyncTime = "offlineStorageSyncTime"

        /**
         * Shared preferences property for the chunk size of a multi upload
         * operation to sync the offline storage.
         */
        const val propMultiUploadChunkSize = "multiUploadChunkSize"

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

        /** Shared preferences property for the last tracking start time. */
        const val propTrackingStart = "trackingStart"

        /** Shared preferences property for the last time tracking was stopped. */
        const val propTrackingEnd = "trackingEnd"

        /** Shared preferences property for the number of errors encountered.*/
        const val propErrorCount = "errorCount"

        /** Shared preferences property for the number of checks. */
        const val propCheckCount = "checkCount"

        /** Shared preferences property for the number of updates. */
        const val propUpdateCount = "updateCount"

        /** Shared preferences property for the accumulated distance (in meters). */
        const val propTotalDistance = "totalDistance"

        /** Shared preferences property to trigger the auto-reset of stats. */
        const val propAutoResetStats = "autoResetStats"

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

        /** A default value for the size of the offline storage. */
        const val defaultOfflineStorageSize = 32

        /** A default value for the offline storage sync time (in sec). */
        const val defaultOfflineStorageSyncTime = 30

        /** A default value for the multi-upload chunk size. */
        const val defaultMultiUploadChunkSize = 4

        /**
         * Constant for the minimum value accepted for a date (in millis).
         * This value should prevent that an undefined date property (set to 0)
         * is reported as a date in the 1970s.
         */
        const val minDateValue = 100000L

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

        /**
         * A set with the names of all the properties that need to be cleared
         * when statistics are reset.
         */
        private val resetProps = setOf(
            propTotalDistance, propErrorCount, propLastCheck, propLastDistance, propLastUpdate, propLastError,
            propCheckCount, propUpdateCount
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
