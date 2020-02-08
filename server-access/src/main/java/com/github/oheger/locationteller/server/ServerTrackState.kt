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
package com.github.oheger.locationteller.server

/**
 * A data class describing a file reference on the server with location
 * information.
 *
 * Such files are organized in folders (below the base path of a track
 * configuration). Based on naming conventions, the time when the location was
 * recorded can be determined. This class contains the components to identify
 * a file. Note that not the actual names are stored, but only the portions to
 * determine the time of the data.
 */
data class FileData(val folderRef: String, val fileRef: String)

/**
 * A data class representing the location-related information stored on the
 * server.
 *
 * An instance of this class can be used to find out what location data and -
 * based on file naming conventions - from when is currently stored on the
 * server. This is needed to display this information and to update the server
 * accordingly, e.g. to remove outdated location data.
 *
 * Note that the class does not store location data directly, but only
 * references to files stored on the server. This simplifies certain update
 * operations.
 *
 * @param files a list with the data of files with location data
 */
data class ServerTrackState(val files: List<FileData>) {
    /**
     * Returns a _Pair_ with files that are outdated according to the given
     * reference time and an updated _ServerTrackState_ object that has these
     * files removed. This function can be used to find the files that need to
     * be removed from the server and in parallel generate an updated state
     * object.
     * @param refTime the reference time; older files are removed
     * @return a _Pair_ with outdated files and an updated state
     */
    fun removeOutdatedFiles(refTime: TimeData): Pair<List<FileData>, ServerTrackState> {
        val partition = files.partition { data ->
            data.folderRef < refTime.dateString ||
                    (data.folderRef == refTime.dateString && data.fileRef < refTime.timeString)
        }

        return if (partition.first.isEmpty()) Pair(emptyList(), this)
        else Pair(partition.first, ServerTrackState(partition.second))
    }

    /**
     * Returns an updated state object does not contain any of the specified
     * files.
     * @param removeFiles a set with the files to be removed
     * @return the updated state object
     */
    fun removeFiles(removeFiles: Set<FileData>): ServerTrackState {
        val nextFiles = files.filter { !removeFiles.contains(it) }
        return ServerTrackState(nextFiles)
    }

    /**
     * Returns an updated state object that contains the given _FileData_
     * object at the last position. Per default, this function expects that the
     * sort order is correctly maintained by appending this file. If this is
     * not the case, the _inOrder_ parameter must be set to *false*; then an
     * additional sort operation is executed.
     * @param file the file to be appended
     * @return the updated state object
     */
    fun appendFile(file: FileData, inOrder: Boolean = true): ServerTrackState {
        val newFiles = ArrayList<FileData>(files.size + 1)
        newFiles.addAll(files)
        newFiles.add(file)
        if (!inOrder) {
            newFiles.sortWith(compareBy({ it.folderRef }, { it.fileRef }))
        }
        return ServerTrackState(newFiles)
    }

    /**
     * Checks whether a folder with the given reference is contained in this
     * state object.
     * @param ref the reference pointing to the folder
     * @return *true* if such a folder is present; *false* otherwise
     */
    fun hasFolder(ref: String): Boolean {
        val filteredFiles = files.dropWhile { it.folderRef < ref }
        return if (filteredFiles.isEmpty()) false
        else filteredFiles.first().folderRef == ref
    }
}
