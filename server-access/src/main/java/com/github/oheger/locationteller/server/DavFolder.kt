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
package com.github.oheger.locationteller.server

import java.lang.NumberFormatException

/** The separator for URI components.*/
const val UriSeparator = "/"

/**
 * A data class representing an element (a file or folder) that is stored on a
 * DAV server.
 * @param name the name of the element
 * @param isFolder flag whether this element is a folder
 */
data class DavElement(val name: String, val isFolder: Boolean)

/**
 * A data class representing a folder on a DAV server.
 *
 * Instances of this class are returned when querying a folder on a server.
 * An instance has the relative path of the folder and a list with the
 * elements contained in the folder.
 */
data class DavFolder(val path: String, val elements: List<DavElement>) {
    /**
     * Returns the resolved path to the given element relative to this folder.
     * The element is expected to be part of this folder. The resolved path
     * is the path of this folder with the element name appended.
     * @param elem the element to be resolved
     */
    fun resolve(elem: DavElement): String {
        val resolvedName = path + UriSeparator + elem.name
        return if (elem.isFolder) resolvedName + UriSeparator
        else resolvedName
    }
}

/**
 * A data class representing location information.
 *
 * The data hold by this class is written to the track server for each location
 * item. The class offers functionality to generate a string representation and
 * parse itself from a string.
 *
 * @param latitude the latitude of the location
 * @param longitude the longitude of the location
 * @param time a timestamp when this location was tracked
 */
data class LocationData(val latitude: Double, val longitude: Double, val time: TimeData) {
    /**
     * Generates a string representation of this instance that is compatible
     * with the format expected by the _parse()_ method of the companion
     * object.
     * @return a string representation of this instance
     */
    fun stringRepresentation(): String = "$latitude$separator$longitude"

    companion object {
        /** The separator character between latitude and longitude.*/
        private const val separator = ';'

        /**
         * Tries to create a new instance from a string representation. If
         * the representation is invalid, result is *null*.
         * @param representation the string representation
         * @param time the time information for the new instance
         */
        fun parse(representation: String, time: TimeData): LocationData? {
            val separatorPos = representation.indexOf(separator)
            if (separatorPos >= 1 && separatorPos < representation.length - 1) {
                try {
                    val latitude = representation.substring(0, separatorPos).toDouble()
                    val longitude = representation.substring(separatorPos + 1).toDouble()
                    return LocationData(latitude, longitude, time)
                } catch (e: NumberFormatException) {
                    // fall through
                }
            }
            return null
        }
    }
}
