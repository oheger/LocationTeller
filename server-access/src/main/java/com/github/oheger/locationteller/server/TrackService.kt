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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.util.*
import java.util.regex.Pattern

/**
 * A class providing functionality to interact with a server to retrieve
 * tracked locations or add new location data.
 *
 * This class implements the major part of the logic for managing location data
 * on a server. It uses an instance of [DavClient] to access the server and
 * manipulates files there.
 *
 * Location data is stored on the server in files following specific naming
 * conventions:
 * * For each day a folder is created whose name is derived from the date.
 * * Each track folder contains files for the single location updates that have
 * been posted. The file names are derived from the time associated with the
 * location.
 *
 * An instance of this class manages an object with the state of the server
 * (i.e. the location data that has been uploaded). This state is accessed and
 * updated when operations are executed. Note that this class is not
 * thread-safe! Callers are responsible for a correct synchronization.
 *
 * @param davClientFactory the factory for creating _DavClient_ instances
 */
class TrackService(val davClientFactory: DavClientFactory) {
    /**
     * Stores the tracking state on the server. This field is initialized on
     * first access.
     */
    private var trackState: ServerTrackState? = null

    /** Stores the current _DavClient_ instance.*/
    private var davClientField: DavClient? = null

    /**
     * Returns the current DAV client used by this instance. The client is
     * created on demand.
     * @return the _DavClient_
     */
    fun davClient(): DavClient {
        val currentClient = davClientField
        if (currentClient == null) {
            val newClient = davClientFactory.createDavClient()
            davClientField = newClient
            return newClient
        }
        return currentClient
    }

    /**
     * Returns a list with the paths of the files with location data that are
     * currently stored on the server.
     * @return a list with paths of files on the server
     */
    suspend fun filesOnServer(): List<String> =
        filesFromTrackState(getTrackState())

    /**
     * Determines the files which are outdated according to the given reference
     * time. These files are removed from the server (and the track state kept
     * locally is updated).
     * @param refTime the reference time
     * @return a flag whether all updates have been successful
     */
    suspend fun removeOutdated(refTime: TimeData): Boolean = coroutineScope {
        val oldState = getTrackState()
        val (removeFiles, nextState) = oldState.removeOutdatedFiles(refTime)
        if (removeFiles.isNotEmpty()) {
            log.info("Removing {} outdated files from server.", removeFiles.size)
            val removePaths = calcRemovePaths(removeFiles, nextState)
            val removeOpsAsync = removePaths.map { path -> async { davClient().delete(path) } }
            val removeOps = removeOpsAsync.awaitAll()
            if (removeOps.all { it }) {
                trackState = nextState
                true
            } else {
                if (removeOps.any { it }) {
                    val successFiles = successfullyRemovedFiles(removePaths, removeOps, oldState)
                    trackState = oldState.removeFiles(successFiles)
                    log.warn("Removing outdated files failed partially!")
                } else log.warn("Removing outdated files failed completely!")
                false
            }
        } else true
    }

    /**
     * Creates a location file on the server with the given data. If necessary,
     * a directory is created first. Note that it is assumed that the time of
     * the location is after all other data objects on the server.
     * @param locationData the location data to be uploaded
     * @return a flag whether this operation was successful
     */
    suspend fun addLocation(locationData: LocationData): Boolean {
        val fileData = FileData(locationData.time.dateString, locationData.time.timeString)
        log.info("Uploading location data for {}.", fileData)

        val state = getTrackState()
        if (!state.hasFolder(locationData.time.dateString)) {
            if (!davClient().createFolder(fileData.folderPath())) {
                log.error("Could not create directory {} for upload!", fileData.folderPath())
                return false
            }
        }

        val uploadSuccess = davClient().upload(fileData.toPath(), locationData.stringRepresentation())
        if (uploadSuccess) {
            trackState = state.appendFile(fileData)
        } else log.error("Upload failed for {}!", fileData)
        return uploadSuccess
    }

    /**
     * Reads a file with location data from the server. The content of this
     * file is read and parsed to a _LocationData_ object. If this fails,
     * result is *null*.
     * @param file the path to the file to be read
     * @return the extracted _LocationData_ or *null*
     */
    suspend fun readLocation(file: String): LocationData? {
        val refTime = refTimeFromPath(file)
        if (refTime != null) {
            val fileData = fileDataFromPath(file)
            if (getTrackState().files.contains(fileData)) {
                val content = davClient().readFile(file)
                return LocationData.parse(content, refTime)
            }
        }
        return null
    }

    /**
     * Reads all files with location data specified in the given list from the
     * server. The resulting map contains only the files that could be loaded
     * successfully.
     * @param files the files to be loaded
     * @return a map that assigns file paths with corresponding location data
     */
    suspend fun readLocations(files: List<String>): Map<String, LocationData> = coroutineScope {
        val locations = files.map { async { readLocation(it) } }.awaitAll()
        val locationsMap = files.zip(locations).toMap()
        @Suppress("UNCHECKED_CAST")  // null values are filtered out
        locationsMap.filterValues { it != null } as Map<String, LocationData>
    }

    /**
     * Resets the _DavClient_ used by this instance. This causes the creation
     * of a new client for the next request.
     */
    fun resetClient() {
        val client = davClient()
        davClientField = null
    }

    /**
     * Returns the current track state. If the data is already available, it is
     * returned directly. Otherwise, it needs to be fetched from the server
     * first.
     * @return the current _ServerTrackState_
     */
    private suspend fun getTrackState(): ServerTrackState {
        val state = trackState
        if (state != null) return state

        val initState = initTrackState()
        trackState = initState
        return initState
    }

    /**
     * Loads data from the track server and creates a corresponding
     * _ServerTrackState_ object from it.
     * @return the track state
     */
    private suspend fun initTrackState(): ServerTrackState = coroutineScope {
        log.info("Initializing track state.")
        val rootFolder = davClient().loadFolder("")
        val trackFoldersAsync = rootFolder.elements
            .filter { davElement -> davElement.isFolder && davElement.name.startsWith(NamePrefix) }
            .map { async { davClient().loadFolder("/${it.name}") } }
        val trackFolders = trackFoldersAsync.awaitAll()
        val files = trackFolders.flatMap { folder ->
            folder.elements.filter { davElement -> !davElement.isFolder && davElement.name.startsWith(NamePrefix) }
                .map { FileData(folder.path.substring(NamePrefix.length + 1), it.name.substring(PrefixLength)) }
        }

        log.info("Found {} location files on server.", files.size)
        ServerTrackState(files)
    }

    companion object {
        /** A prefix for the file and folder names with location data.*/
        const val NamePrefix = "track_"

        /** The length of the prefix used for folders and files.*/
        private const val PrefixLength = NamePrefix.length

        /** The length of a folder reference (YYYY-MM-DD).*/
        private const val FolderRefLength = 10

        /** The length of a file reference (HH_MM_SS).*/
        private const val FileRefLength = 8

        /** Regular expression for parsing the time values from a file path.*/
        private val patTimestamp =
            Pattern.compile("""/$NamePrefix(\d{4})-(\d{2})-(\d{2})/$NamePrefix(\d{2})_(\d{2})_(\d{2})""")

        private val log = LoggerFactory.getLogger(TrackService::class.java)

        /**
         * Creates a new, fully initialized instance of _TrackService_. Default
         * helper objects are created.
         * @param config the server configuration
         */
        fun create(config: ServerConfig): TrackService {
            return TrackService(DavClientFactory(config))
        }

        /**
         * Extracts the path of the folder from a _FileData_ object.
         * @return the folder path
         */
        private fun FileData.folderPath(): String =
            "/$NamePrefix$folderRef/"

        /**
         * Converts a _FileData_ object into a corresponding path.
         * @return the path
         */
        private fun FileData.toPath(): String =
            "${folderPath()}$NamePrefix$fileRef"

        /**
         * Generates a list with the files on the server from the given state
         * object. The paths of the files are generated based on naming
         * conventions and the date/time information.
         * @param state the track state object
         * @return the corresponding list of files
         */
        private fun filesFromTrackState(state: ServerTrackState): List<String> =
            state.files.map { it.toPath() }

        /**
         * Extracts the references for the folder and the file from the given
         * path. This is possible because of naming conventions. Note that all
         * paths have the same length.
         * @param path the path pointing to a location file
         * @return the corresponding _FileData_ for this file
         */
        private fun fileDataFromPath(path: String): FileData {
            val folderRef = folderRefFromPath(path)
            val fileRef = path.substring(path.length - FileRefLength)
            return FileData(folderRef, fileRef)
        }

        /**
         * Extracts the folder reference from a path for a file or folder.
         * @param path the path
         * @return the folder reference from the path
         */
        private fun folderRefFromPath(path: String) =
            path.substring(PrefixLength + 1, PrefixLength + 1 + FolderRefLength)

        /**
         * Extracts the time from a path of a location data file. This function
         * tests whether the path references a valid location file. If so, the
         * timestamp of this location is extracted based on naming conventions.
         * Otherwise, result is *null*.
         * @param path the path to a location file
         * @return the time of this location file or *null*
         */
        private fun refTimeFromPath(path: String): TimeData? {
            val matcher = patTimestamp.matcher(path)
            if (matcher.matches()) {
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(
                        matcher.group(1).toInt(), matcher.group(2).toInt() - 1, matcher.group(3).toInt(),
                        matcher.group(4).toInt(), matcher.group(5).toInt(), matcher.group(6).toInt()
                    )
                }
                return TimeData(cal.timeInMillis)
            }
            return null
        }

        /**
         * Determines the paths of files and folders that need to be removed
         * from the server.
         * @param removeFiles list of files to be removed
         * @param nextState the updated tracking state
         */
        private fun calcRemovePaths(removeFiles: List<FileData>, nextState: ServerTrackState): MutableSet<String> {
            val removeFolders = removeFiles.map { it.folderRef }.toSet()
                .filter { folder -> !nextState.hasFolder(folder) }.toSet()
            val removePaths = removeFiles
                .filter { fileData -> !removeFolders.contains(fileData.folderRef) }
                .map { it.toPath() }.toMutableSet()
            removePaths.addAll(removeFolders.map { "/$NamePrefix$it/" })
            return removePaths
        }

        /**
         * Determines the files that could be removed successfully in a remove
         * operation that was only partially successful. In this situation, the
         * new track state is the old state minus the files that could be
         * removed successfully.
         * @param removePaths the set with the paths that should be removed
         * @param removeOps the boolean results of the single remove operations
         * @param state the track state object
         * @return a collection with _FileData_ objects for removed files
         */
        private fun successfullyRemovedFiles(
            removePaths: MutableSet<String>, removeOps: List<Boolean>,
            state: ServerTrackState
        ): Set<FileData> {
            val filePathLength = 2 * PrefixLength + FolderRefLength + FileRefLength
            val (removeFolders, removeFiles) = removePaths.withIndex()
                .filter { indexedValue -> removeOps[indexedValue.index] }
                .partition { it.value.length < filePathLength }
            val successFiles = removeFiles
                .map { indexedValue -> fileDataFromPath(indexedValue.value) }
            val successFolderFiles = removeFolders.flatMap { indexedValue ->
                val folderRef = folderRefFromPath(indexedValue.value)
                state.files.filter { it.folderRef == folderRef }
            }

            val result = mutableSetOf<FileData>()
            result.addAll(successFiles)
            result.addAll(successFolderFiles)
            return result
        }

        /**
         * A factory class for creating [DavClient] instances.
         *
         * A _TrackService_ has such a factory; so it can create new client
         * instances when necessary.
         *
         * @param config the server configuration
         */
        class DavClientFactory(val config: ServerConfig) {
            /**
             * Creates a new _DavClient_ instance.
             * @return the new client
             */
            fun createDavClient(): DavClient = DavClient.create(config)
        }
    }
}
