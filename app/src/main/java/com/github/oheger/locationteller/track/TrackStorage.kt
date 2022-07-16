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
package com.github.oheger.locationteller.track

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.server.TimeData

import java.util.Date

/**
 * A class responsible for persisting data during a tracking operation.
 *
 * In order to correctly update and aggregate some tracking-related statistics, some data needs to be persisted. This
 * class takes of this. It is used to update data when new location updates are available. It also plays a role to
 * keep the UI information on the tracking screen in sync with the current tracking operation.
 */
class TrackStorage(
    /**
     * References to the [PreferencesHandler]. This object is used to persist information.
     */
    val preferencesHandler: PreferencesHandler
) {
    companion object {
        /** Shared preferences property for the latest update of location data. */
        const val PROP_LAST_UPDATE = "lastUpdate"

        /** Shared preferences property for the distance of the last update. */
        const val PROP_LAST_DISTANCE = "lastDistance"

        /** Shared preferences property for the latest error that occurred.*/
        const val PROP_LAST_ERROR = "lastError"

        /** Shared preferences property for the last check for an update. */
        const val PROP_LAST_CHECK = "lastCheck"

        /** Shared preferences property for the last tracking start time. */
        const val PROP_TRACKING_START = "trackingStart"

        /** Shared preferences property for the last time tracking was stopped. */
        const val PROP_TRACKING_END = "trackingEnd"

        /** Shared preferences property for the number of errors encountered.*/
        const val PROP_ERROR_COUNT = "errorCount"

        /** Shared preferences property for the number of checks. */
        const val PROP_CHECK_COUNT = "checkCount"

        /** Shared preferences property for the number of updates. */
        const val PROP_UPDATE_COUNT = "updateCount"

        /** Shared preferences property for the accumulated distance (in meters). */
        const val PROP_TOTAL_DISTANCE = "totalDistance"

        /** Shared preferences property for the tracking state.*/
        const val PROP_TRACK_STATE = "trackEnabled"

        /**
         * A set with the names of all the properties that need to be removed when statistics are reset.
         */
        private val RESET_PROPS = setOf(
            PROP_TOTAL_DISTANCE,
            PROP_ERROR_COUNT,
            PROP_LAST_CHECK,
            PROP_LAST_DISTANCE,
            PROP_LAST_UPDATE,
            PROP_LAST_ERROR,
            PROP_CHECK_COUNT,
            PROP_UPDATE_COUNT
        )
    }

    /**
     * Record an error during the current tracking operation that happened at [errorTime] and the total [count] of
     * errors.
     */
    fun recordError(errorTime: Long, count: Int) {
        preferencesHandler.update {
            putLong(PROP_LAST_ERROR, errorTime)
            putInt(PROP_ERROR_COUNT, count)
        }
    }

    /**
     * Record a location update and related properties for the current tracking operation. The update happened at
     * [updateTime], there are now [count] updates. Record the [distance] to the last position and the [totalDistance]
     * tracked during this operation.
     */
    fun recordUpdate(updateTime: Long, count: Int, distance: Int, totalDistance: Long) {
        preferencesHandler.update {
            putLong(PROP_LAST_UPDATE, updateTime)
            putInt(PROP_UPDATE_COUNT, count)
            putInt(PROP_LAST_DISTANCE, distance)
            putLong(PROP_TOTAL_DISTANCE, totalDistance)
        }
    }

    /**
     * Record a location check during the current tracking operation that happened at [checkTime] and also update
     * the current [count] of checks.
     */
    fun recordCheck(checkTime: Long, count: Int) {
        preferencesHandler.update {
            putLong(PROP_LAST_CHECK, checkTime)
            putInt(PROP_CHECK_COUNT, count)
        }
    }

    /**
     * Record that tracking started at the given [startTime]. Also reset the tracking end property to reflect that a
     * tracking operation is currently ongoing.
     */
    fun recordTrackingStart(startTime: TimeData) {
        preferencesHandler.update {
            putLong(PROP_TRACKING_START, startTime.currentTime)
            remove(PROP_TRACKING_END)
        }
    }

    /**
     * Record that tracking ended at the given [endTime].
     */
    fun recordTrackingEnd(endTime: TimeData) {
        preferencesHandler.update {
            putLong(PROP_TRACKING_END, endTime.currentTime)
        }
    }

    /**
     * Return the [Date] when the last tracking error happened or *null* if no errors have been recorded so far.
     */
    fun lastError(): Date? = preferencesHandler.getDate(PROP_LAST_ERROR)

    /**
     * Return the number of errors that have been encountered since the statistics have been reset.
     */
    fun errorCount(): Int = preferencesHandler.preferences.getInt(PROP_ERROR_COUNT, 0)

    /**
     * Return the number of checks that have been performed since the statistics have been reset.
     */
    fun checkCount(): Int = preferencesHandler.preferences.getInt(PROP_CHECK_COUNT, 0)

    /**
     * Return the number of updates that have been performed since the statistics have been reset.
     */
    fun updateCount(): Int = preferencesHandler.preferences.getInt(PROP_UPDATE_COUNT, 0)

    /**
     * Return the [Date] when the last update took place or *null* if no update has been recorded so far.
     */
    fun lastUpdate(): Date? = preferencesHandler.getDate(PROP_LAST_UPDATE)

    /**
     * Return the [Date] when the last check for a location update took place or *null* if no check time has been
     * recorded so far. The last check time if updated always, no matter whether a location change was detected or not.
     */
    fun lastCheck(): Date? = preferencesHandler.getDate(PROP_LAST_CHECK)

    /**
     * Return the distance of the last location update in meters.
     */
    fun lastDistance(): Int = preferencesHandler.preferences.getInt(PROP_LAST_DISTANCE, 0)

    /**
     * Return the total distance of all checks that have been recorded for the current tracking operation (in meters).
     */
    fun totalDistance(): Long = preferencesHandler.preferences.getLong(PROP_TOTAL_DISTANCE, 0)

    /**
     * Return the last recorded start date of a tracking operation or *null* if tracking has never been started.
     */
    fun trackingStartDate(): Date? = preferencesHandler.getDate(PROP_TRACKING_START)

    /**
     * Return the recorded end date of the current tracking process or *null* if tracking is currently active.
     */
    fun trackingEndDate(): Date? = preferencesHandler.getDate(PROP_TRACKING_END)

    /**
     * Reset (i.e. remove) the properties from the storage that contain data about the tracking statistics. This
     * means that a new statistic is created.
     */
    fun resetStatistics() {
        preferencesHandler.update {
            RESET_PROPS.forEach { remove(it) }
        }
    }

    /**
     * Check whether tracking is currently active by querying the property dedicated for this purpose.
     */
    fun isTrackingEnabled(): Boolean = preferencesHandler.preferences.getBoolean(PROP_TRACK_STATE, false)

    /**
     * Update and persist the tracking state according to [flag].
     */
    fun setTrackingEnabled(flag: Boolean) {
        preferencesHandler.update {
            putBoolean(PROP_TRACK_STATE, flag)
        }
    }
}
