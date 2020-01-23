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
package com.github.oheger.locationteller.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.PreferencesHandler
import java.lang.StringBuilder
import java.text.DateFormat
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
 * @param timeService the service to access the current time
 */
class TrackingStatsListAdapter private constructor(
    private val layoutInflater: LayoutInflater,
    private val prefHandler: PreferencesHandler,
    val timeService: TimeService
) : BaseAdapter() {
    /** An array with definitions to compute the statistics values. */
    private val statistics = arrayOf(
        StatData(R.string.stats_tracking_started, this::trackingStartStat),
        StatData(R.string.stats_tracking_stopped, this::trackingEndStat),
        StatData(R.string.stats_tracking_time, this::trackingTimeStat),
        StatData(R.string.stats_tracking_total_distance, (TrackingStatsListAdapter)::totalDistanceStat),
        StatData(R.string.stats_tracking_last_distance, (TrackingStatsListAdapter)::lastDistanceStat),
        StatData(R.string.stats_tracking_last_check, this::lastCheckStat),
        StatData(R.string.stats_tracking_last_update, this::lastUpdateStat),
        StatData(R.string.stats_tracking_error_count, (TrackingStatsListAdapter)::errorCountStat),
        StatData(R.string.stats_tracking_last_error, this::lastErrorStat)
    )

    /** An object to format dates.*/
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)

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
    private fun trackingTimeStat(prefHandler: PreferencesHandler): String {
        val startTime = prefHandler.trackingStartDate()?.time ?: return ""
        val endTime = prefHandler.trackingEndDate()?.time ?: timeService.currentTime().currentTime
        return formatDuration(endTime - startTime)
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
    private fun Date.format(): String = dateFormat.format(this)

    companion object {
        /** The number of milliseconds in a second.*/
        private const val MILLIS_PER_SEC = 1000

        /** The number of seconds per minute.*/
        private const val SECS_PER_MINUTE = 60

        /** The number of seconds per hour.*/
        private const val SECS_PER_HOUR = SECS_PER_MINUTE * 60

        /** The number of seconds per day.*/
        private const val SECS_PER_DAY = 24 * SECS_PER_HOUR

        /**
         * Creates a new instance of _TrackingStatsListAdapter_ with the given
         * properties.
         * @param context the Android context
         * @param prefHandler the preferences handler
         * @param timeService an optional _TimeService_ reference
         */
        fun create(context: Context, prefHandler: PreferencesHandler, timeService: TimeService? = null):
                TrackingStatsListAdapter {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            return TrackingStatsListAdapter(
                inflater, prefHandler,
                timeService ?: CurrentTimeService
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
         * Formats a duration to a string.
         * @param deltaMillis the duration in millis
         * @return the formatted duration string
         */
        private fun formatDuration(deltaMillis: Long): String {
            val buf = StringBuilder(11)
            val deltaSecs = deltaMillis / MILLIS_PER_SEC
            val days = deltaSecs / SECS_PER_DAY
            formatTimeComponent(buf, days, force = false, withSeparator = true)
            val hours = (deltaSecs % SECS_PER_DAY)
            formatTimeComponent(buf, hours / SECS_PER_HOUR, force = false, withSeparator = true)
            val minutes = hours % SECS_PER_HOUR
            formatTimeComponent(buf, minutes / SECS_PER_MINUTE, force = true, withSeparator = true)
            val secs = minutes % SECS_PER_MINUTE
            formatTimeComponent(buf, secs, force = true, withSeparator = false)
            return buf.toString()
        }

        /**
         * Adds a formatted time component to the given buffer. The component
         * is only added if necessary (if not 0, or other components already
         * exists, or the _force_ flag is set). The value must be in the range
         * between 0 and 59.
         * @param buf the buffer to add the text
         * @param time the time component to be formatted
         * @param force flag whether this component needs to be added
         * @param withSeparator flag whether a trailing separator needs to be
         * added
         */
        private fun formatTimeComponent(buf: StringBuilder, time: Long, force: Boolean, withSeparator: Boolean) {
            val existing = buf.isNotEmpty()
            if (force || existing || time > 0) {
                if (existing && time < 10) {
                    buf.append(0)
                }
                buf.append(time)
                if (withSeparator) {
                    buf.append(':')
                }
            }
        }

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
    }
}
