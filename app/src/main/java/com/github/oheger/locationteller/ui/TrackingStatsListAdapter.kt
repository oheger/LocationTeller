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

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import java.util.*

/**
 * A data class storing the fields of the form representing a list row in the
 * tracking statistics list. This is used to apply the view holder pattern.
 * @param txtLabel the text view with the label
 * @param txtValue the text view with the value
 */
data class TrackingFormViewHolder(val txtLabel: TextView, val txtValue: TextView)

/**
 * A class implementing the adapter for the list view with tracking statistics.
 *
 * This adapter generates a list view with a fix number of rows where each
 * row represents a statistics value. The values are directly obtained from a
 * [PreferencesHandler] object. The class also reacts on changes in the
 * preferences, so that the view can be updated.
 * @param layoutInflater the object to inflate layouts
 * @param prefHandler the preferences handler
 * @param formatter the object to format data
 */
class TrackingStatsListAdapter private constructor(
    private val layoutInflater: LayoutInflater,
    private val prefHandler: PreferencesHandler,
    val formatter: TrackStatsFormatter
) : BaseAdapter(), SharedPreferences.OnSharedPreferenceChangeListener {
    /** An array with definitions to compute the statistics values. */
    private val statistics = arrayOf(
        StatData(R.string.stats_tracking_started, this::trackingStartStat),
        StatData(R.string.stats_tracking_stopped, this::trackingEndStat),
        StatData(R.string.stats_tracking_time, this::trackingTimeStat),
        StatData(R.string.stats_tracking_total_distance, TrackingStatsListAdapter::totalDistanceStat),
        StatData(R.string.stats_tracking_speed, this::trackingSpeedStat),
        StatData(R.string.stats_tracking_last_distance, TrackingStatsListAdapter::lastDistanceStat),
        StatData(R.string.stats_tracking_check_count, TrackingStatsListAdapter::checkCountStat),
        StatData(R.string.stats_tracking_last_check, this::lastCheckStat),
        StatData(R.string.stats_tracking_update_count, TrackingStatsListAdapter::updateCountStat),
        StatData(R.string.stats_tracking_last_update, this::lastUpdateStat),
        StatData(R.string.stats_tracking_error_count, TrackingStatsListAdapter::errorCountStat),
        StatData(R.string.stats_tracking_last_error, this::lastErrorStat)
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = if (convertView == null) {
            val v = layoutInflater.inflate(R.layout.statistics_row_layout, parent, false)
            val label = v.findViewById<TextView>(R.id.stats_label)
            val text = v.findViewById<TextView>(R.id.stats_value)
            val viewHolder = TrackingFormViewHolder(label, text)
            v.tag = viewHolder
            v
        } else convertView

        val holder = view.tag as TrackingFormViewHolder
        holder.txtLabel.setText(statistics[position].labelResID)
        holder.txtValue.text = getItem(position)
        return view
    }

    /**
     * This implementation returns the statistics value at the given position.
     */
    override fun getItem(position: Int): String =
        statistics[position].valueFunc(prefHandler)

    /**
     * Item IDs are not needed by this implementation, so always 0 is returned.
     */
    override fun getItemId(position: Int): Long = 0

    override fun getCount(): Int = statistics.size

    /**
     * Reacts on changes on the shared preferences. Then an update of the UI
     * needs to be triggered.
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (statisticsProperties.contains(key)) {
            notifyDataSetChanged()
        }
    }

    /**
     * Activates this adapter. This causes a preferences listener to be
     * registered, so that the adapter can react on changes on relevant
     * properties.
     */
    fun activate() {
        prefHandler.registerListener(this)
        notifyDataSetChanged()
    }

    /**
     * Deactivates this adapter. This causes the preferences listener to be
     * removed from the shared preferences, so that updates are no longer
     * processed.
     */
    fun deactivate() {
        prefHandler.unregisterListener(this)
    }

    /**
     * Returns the statistics value for the tracking start date.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun trackingStartStat(prefHandler: PreferencesHandler): String =
        formatDate(prefHandler.trackingStartDate())

    /**
     * Returns the statistics value for the tracking end date.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun trackingEndStat(prefHandler: PreferencesHandler): String =
        formatDate(prefHandler.trackingEndDate())

    /**
     * Returns the statistics value for the last check date.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun lastCheckStat(prefHandler: PreferencesHandler): String =
        formatDate(prefHandler.lastCheck())

    /**
     * Returns the statistics value for the last update date.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun lastUpdateStat(prefHandler: PreferencesHandler): String =
        formatDate(prefHandler.lastUpdate())

    /**
     * Returns the statistics value for the last error date.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun lastErrorStat(prefHandler: PreferencesHandler): String =
        formatDate(prefHandler.lastError())

    /**
     * Returns the statistics value for the elapsed tracking time. If tracking
     * is ongoing, the current time is used as end time; otherwise the recorded
     * end time is used.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun trackingTimeStat(prefHandler: PreferencesHandler): String =
        trackingTimeMillis(prefHandler)?.let { formatter.formatDuration(it) } ?: ""

    /**
     * Returns the statistics value for the average tracking speed. This is
     * based on the total distance and the elapsed tracking time.
     * @param prefHandler the preferences handler
     * @return the statistics value for this property
     */
    private fun trackingSpeedStat(prefHandler: PreferencesHandler): String {
        val trackingTime = trackingTimeMillis(prefHandler) ?: return ""
        return if (trackingTime > 0) {
            val speed = prefHandler.totalDistance().toDouble() / trackingTime * SECS_PER_HOUR
            return formatter.formatNumber(speed)
        } else ""
    }

    /**
     * Calculates the tracking time as the delta of tracking start and end
     * time (in milliseconds). If tracking is ongoing, the current time is
     * used as end time. If no tracking start date is recorded, result is
     * *null*.
     * @param prefHandler the preferences value
     * @return the tracking time in milliseconds or *null*
     */
    private fun trackingTimeMillis(prefHandler: PreferencesHandler): Long? {
        val startTime = prefHandler.trackingStartDate()?.time
        return startTime?.let {
            val endTime = prefHandler.trackingEndDate()?.time ?: formatter.timeService.currentTime().currentTime
            endTime - it
        }
    }

    /**
     * Function to format a date object that can deal with null dates.
     * @param date the date to be formatted
     * @return the formatted date string
     */
    private fun formatDate(date: Date?): String =
        date?.format() ?: ""

    /**
     * Convenience function to format a date. This is implemented as an
     * extension function, so that *null* objects can be handled more easily
     * with a null-safe access operator.
     * @return the formatted date
     */
    private fun Date.format(): String = formatter.formatDate(this)

    companion object {

        /** The number of seconds per minute.*/
        private const val SECS_PER_MINUTE = 60

        /** The number of seconds per hour.*/
        private const val SECS_PER_HOUR = SECS_PER_MINUTE * 60

        /**
         * A set of properties affecting the statistics displayed by this
         * class. When the value of one of these properties changes the UI
         * needs to be updated.
         */
        private val statisticsProperties = setOf(
            PreferencesHandler.PROP_TRACKING_START,
            PreferencesHandler.PROP_TRACKING_END, PreferencesHandler.PROP_LAST_ERROR,
            PreferencesHandler.PROP_LAST_UPDATE, PreferencesHandler.PROP_LAST_CHECK,
            PreferencesHandler.PROP_LAST_DISTANCE
        )

        /**
         * Creates a new instance of _TrackingStatsListAdapter_ with the given
         * properties.
         * @param context the Android context
         * @param prefHandler the preferences handler
         * @param formatter an optional object for formatting data
         */
        fun create(context: Context, prefHandler: PreferencesHandler, formatter: TrackStatsFormatter? = null):
                TrackingStatsListAdapter {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            return TrackingStatsListAdapter(
                inflater, prefHandler,
                formatter ?: TrackStatsFormatter.create(null)
            )
        }

        /**
         * A data class used to define the single statistics values.
         *
         * Each value is associated with a resource ID for the label. The
         * actual value is computed based on the preferences by a function.
         *
         * @param labelResID the resource ID for the label
         * @param valueFunc the function to compute the value
         */
        private data class StatData(val labelResID: Int, val valueFunc: (PreferencesHandler) -> String)

        /**
         * Returns the statistics value for the total tracking distance.
         * @param prefHandler the preferences handler
         * @return the statistics value for this property
         */
        private fun totalDistanceStat(prefHandler: PreferencesHandler): String =
            prefHandler.totalDistance().toString()

        /**
         * Returns the statistics value for the last tracking distance.
         * @param prefHandler the preferences handler
         * @return the statistics value for this property
         */
        private fun lastDistanceStat(prefHandler: PreferencesHandler): String =
            prefHandler.lastDistance().toString()

        /**
         * Returns the statistics value for the number of errors.
         * @param prefHandler the preferences handler
         * @return the statistics value for this property
         */
        private fun errorCountStat(prefHandler: PreferencesHandler): String =
            prefHandler.errorCount().toString()

        /**
         * Returns the statistics value for the number of checks.
         * @param prefHandler the preferences handler
         * @return the statistics value for this property
         */
        private fun checkCountStat(prefHandler: PreferencesHandler): String =
            prefHandler.checkCount().toString()

        /**
         * Returns the statistics value for the number of updates.
         * @param prefHandler the preferences handler
         * @return the statistics value for this property
         */
        private fun updateCountStat(prefHandler: PreferencesHandler): String =
            prefHandler.updateCount().toString()
    }
}
