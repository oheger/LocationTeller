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