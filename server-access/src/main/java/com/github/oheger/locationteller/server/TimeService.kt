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
package com.github.oheger.locationteller.server

import java.util.*

/**
 * A data class representing the current time.
 *
 * The time can be accessed as the usual number of milliseconds since the
 * start of the epoch. In addition, several string-based formats are available
 * to generate names for folders or files based on the current time.
 */
data class TimeData(val currentTime: Long) {
    /** Stores a _Calendar_ object for transforming the time to string.*/
    private val calendar: Calendar by lazy {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    /**
     * A string for the date portion of this time. This can be used for
     * instance to generate folder names.
     */
    val dateString: String by lazy {
        "${calendar.get(Calendar.YEAR)}-${formatValue(calendar.get(Calendar.MONTH) + 1)}" +
                "-${formatField(Calendar.DATE)}"
    }

    /**
     * A string for the time portion of this time. This can be used for
     * instance to generate file names.
     */
    val timeString: String by lazy {
        "${formatField(Calendar.HOUR_OF_DAY)}_${formatField(Calendar.MINUTE)}" +
                "_${formatField(Calendar.SECOND)}"
    }

    /**
     * Returns a formatted string with the value of the given calendar field.
     * This function makes sure that the resulting string has the correct
     * length, independent on the concrete field value.
     * @param field the calendar field to be formatted
     * @return the resulting formatted string
     */
    private fun formatField(field: Int): String =
        formatValue(calendar.get(field))

    /**
     * Converts the given numeric value to a string ensuring that the result
     * has 2 digits.
     * @param value the value to be formatted
     * @return the resulting formatted string
     */
    private fun formatValue(value: Int): String =
        if (value < 10) "0$value" else value.toString()
}

/**
 * An interface serving as an abstraction for querying the current time.
 *
 * The functionality to query the current time is frequently needed, for
 * instance to generate names for files and directories storing tracking
 * information. (The names of these elements are derived from the times of the
 * location data they store.) To make this functionality available in a way
 * that is easy to test and mock, this interface is introduced.
 */
interface TimeService {
    /**
     * Returns an object representing the current time.
     * @return the current time
     */
    fun currentTime(): TimeData
}

/**
 * An implementation of [TimeService] that returns the current time of the
 * system as obtained using standard Java functionality.
 */
object CurrentTimeService : TimeService {
    override fun currentTime(): TimeData = TimeData(System.currentTimeMillis())
}
