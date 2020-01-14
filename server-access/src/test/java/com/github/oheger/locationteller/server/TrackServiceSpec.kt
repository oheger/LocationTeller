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

import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.collections.shouldNotContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import java.util.*

/**
 * Test class for [TrackService].
 */
class TrackServiceSpec : StringSpec() {
    init {
        "The companion object should create a correct instance" {
            val config = ServerConfig("someUri", "somePath", "user", "pwd")

            val service = TrackService.create(config)
            service.davClient.config shouldBe config
        }

        "TrackService should determine the files on the server" {
            val davClient = createPreparedDavClient()
            val service = TrackService(davClient)

            service.filesOnServer() shouldBe expectedFiles
        }

        "TrackService should remove outdated elements from the server" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(20, 12, 0, 0)
            coEvery { davClient.delete(folderPath(folder1)) } returns true
            coEvery { davClient.delete(expectedFiles[2]) } returns true
            val service = TrackService(davClient)

            service.removeOutdated(refTime) shouldBe true
            service.filesOnServer() shouldBe expectedFiles.drop(3)
        }

        "TrackService should handle the case that no outdated files need to be removed" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(18, 21, 57, 5)
            val service = TrackService(davClient)
            val oldFiles = service.filesOnServer()

            service.removeOutdated(refTime) shouldBe true
            service.filesOnServer() shouldBe oldFiles
        }

        "TrackService should remove the last folder if it contains only outdated files" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(20, 19, 28, 46)
            coEvery { davClient.delete(folderPath(folder1)) } returns true
            coEvery { davClient.delete(folderPath(folder2)) } returns true
            val service = TrackService(davClient)

            service.removeOutdated(refTime) shouldBe true
        }

        "TrackService should keep the old state if a remove operation fails completely" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(21, 12, 0, 0)
            coEvery { davClient.delete(any()) } returns false
            val service = TrackService(davClient)

            service.removeOutdated(refTime) shouldBe false
            service.filesOnServer() shouldBe expectedFiles
        }

        "TrackService should keep files in the state for folders which could not be removed" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(20, 12, 0, 0)
            coEvery { davClient.delete(folderPath(folder1)) } returns false
            coEvery { davClient.delete(expectedFiles[2]) } returns true
            val service = TrackService(davClient)

            service.removeOutdated(refTime) shouldBe false
            val files = service.filesOnServer()
            files shouldContain expectedFiles[0]
            files shouldContain expectedFiles[1]
            files shouldNotContain expectedFiles[2]
        }

        "TrackService should keep single files in the state that could not be removed" {
            val davClient = createPreparedDavClient()
            val refTime = referenceTime(20, 12, 0, 0)
            coEvery { davClient.delete(folderPath(folder1)) } returns true
            coEvery { davClient.delete(expectedFiles[2]) } returns false
            val service = TrackService(davClient)

            service.removeOutdated(refTime) shouldBe false
            val files = service.filesOnServer()
            files shouldNotContain expectedFiles[0]
            files shouldNotContain expectedFiles[1]
            files shouldContain expectedFiles[2]
        }

        "TrackService should upload new location data to an existing directory" {
            val refTime = referenceTime(21, 22, 27, 16)
            val expPath = pathFromTime(refTime)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.upload(expPath, locData.stringRepresentation()) } returns true
            val service = TrackService(davClient)

            service.addLocation(locData) shouldBe true
            coVerify { davClient.upload(expPath, locData.stringRepresentation()) }
        }

        "TrackService should evaluate the result of a file upload operation" {
            val refTime = referenceTime(21, 21, 36, 43)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.upload(any(), any()) } returns false
            val service = TrackService(davClient)

            service.addLocation(locData) shouldBe false
        }

        "TrackService should create a new folder for an upload operation if necessary" {
            val refTime = referenceTime(25, 21, 39, 3)
            val folderPath = folderPathFromTime(refTime)
            val expPath = pathFromTime(refTime)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.createFolder(folderPath) } returns true
            coEvery { davClient.upload(expPath, locData.stringRepresentation()) } returns true
            val service = TrackService(davClient)

            service.addLocation(locData) shouldBe true
            coVerifyOrder {
                davClient.createFolder(folderPath)
                davClient.upload(expPath, locData.stringRepresentation())
            }
        }

        "TrackService should evaluate the result of creating a new folder during an upload" {
            val refTime = referenceTime(25, 21, 43, 39)
            val folderPath = folderPathFromTime(refTime)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.createFolder(folderPath) } returns false
            val service = TrackService(davClient)

            service.addLocation(locData) shouldBe false
        }

        "TrackService should add uploaded files to the tracking state" {
            val refTime = referenceTime(25, 21, 27, 17)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.createFolder(any()) } returns true
            coEvery { davClient.upload(any(), any()) } returns true
            coEvery { davClient.delete(any()) } returns true
            val service = TrackService(davClient)
            service.addLocation(locData)

            service.removeOutdated(referenceTime(25, 21, 50, 9))
            coVerify { davClient.delete(folderPathFromTime(refTime)) }
        }

        "TrackService should add an uploaded file to the tracking state only if successful" {
            val refTime = referenceTime(21, 21, 36, 43)
            val locData = LocationData(123.456, 789.321, refTime)
            val davClient = createPreparedDavClient()
            coEvery { davClient.upload(any(), any()) } returns false
            val service = TrackService(davClient)
            service.addLocation(locData)

            service.filesOnServer() shouldNotContain pathFromTime(refTime)
        }

        "TrackService should return null if an invalid location file path is requested" {
            val davClient = createPreparedDavClient()
            val service = TrackService(davClient)

            service.readLocation("/non/existing/file") shouldBe null
        }

        "TrackService should read a valid location file" {
            val latitude = 48.1234
            val longitude = 8.7564
            val time = referenceTime(19, 10, 5, 10)
            val expLocData = LocationData(latitude, longitude, time)
            val path = expectedFiles[0]
            val davClient = createPreparedDavClient()
            coEvery { davClient.readFile(path) } returns "$latitude;$longitude"
            val service = TrackService(davClient)

            service.readLocation(path) shouldBe expLocData
        }

        "TrackService should return null if a location file cannot be resolved" {
            val path = expectedFiles[2].replace('1', '2')
            val davClient = createPreparedDavClient()
            val service = TrackService(davClient)

            service.readLocation(path) shouldBe null
        }

        "TrackService should return null if a location file could not be loaded" {
            val path = expectedFiles[1]
            val davClient = createPreparedDavClient()
            coEvery { davClient.readFile(path) } returns ""
            val service = TrackService(davClient)

            service.readLocation(path) shouldBe null
        }

        "TrackService should load multiple location files from the server" {
            val fileCount = 3
            val files = expectedFiles.take(fileCount)
            val locations = (0 until fileCount).map { locationDataForFile(it) }
            val pairs = files.zip(locations)
            val davClient = createPreparedDavClient()
            pairs.forEach { pair ->
                coEvery { davClient.readFile(pair.first) } returns pair.second.stringRepresentation()
            }
            val service = TrackService(davClient)

            val locationMap = service.readLocations(files)
            locationMap.keys shouldContainExactly files
            pairs.forEach { pair ->
                locationMap[pair.first] shouldBe pair.second
            }
        }

        "TrackService should filter out failed location files when loading multiple elements" {
            val davClient = createPreparedDavClient()
            val locationData = locationDataForFile(0)
            coEvery { davClient.readFile(expectedFiles[0]) } returns locationData.stringRepresentation()
            coEvery { davClient.readFile(expectedFiles[1]) } returns ""
            val service = TrackService(davClient)

            val locationMap = service.readLocations(expectedFiles.take(2))
            locationMap.size shouldBe 1
            locationMap[expectedFiles[0]] shouldBe locationData
        }
    }

    companion object {
        /**
         * Constant for the root folder of the track server with some content.
         */
        private val rootFolder = DavFolder(
            "/", listOf(
                createElement("2019-06-19", true),
                createElement("2019-06-20", true),
                DavElement("otherDir", true),
                createElement("2019-06-21", true),
                DavElement("dataFile.xyz", false)
            )
        )

        /**
         * Constant for a test folder with location data of a specific date
         * with some content.
         */
        private val folder1 = DavFolder(
            "/${elementName("2019-06-19")}",
            listOf(
                DavElement("foo.txt", false),
                createElement("10_05_10", false),
                DavElement("subFolder", true),
                createElement("12_18_27", false)
            )
        )

        /**
         * Constant for a test folder with location data of a specific date
         * with some content.
         */
        private val folder2 = DavFolder(
            "/${elementName("2019-06-20")}",
            listOf(
                createElement("11_11_11", false),
                createElement("19_28_45", false)
            )
        )

        /**
         * Constant for a test folder with location data of a specific date
         * with some content.
         */
        private val folder3 = DavFolder(
            "/${elementName("2019-06-21")}",
            listOf(
                createElement("06_05_58", false),
                createElement("14_19_33"),
                createElement("22_02_29", false)
            )
        )

        /** A list with timestamps that correspond to the test files. */
        private val fileTimes = listOf(
            referenceTime(19, 10, 5, 10),
            referenceTime(19, 12, 18, 27),
            referenceTime(20, 11, 11, 11),
            referenceTime(20, 19, 28, 45),
            referenceTime(21, 6, 5, 58),
            referenceTime(21, 14, 19, 33),
            referenceTime(21, 22, 2, 29)
        )

        /**
         * A list with the test files contained on the track server.
         */
        val expectedFiles = fileTimes.map { pathFromTime(it) }

        /**
         * Generates the name of a file or folder with location information based
         * on the given name. Adds the prefix.
         * @param name the element name
         * @return the resulting element name
         */
        private fun elementName(name: String): String =
            TrackService.NamePrefix + name

        /**
         * Generates the path of a folder with a trailing slash.
         * @param folder the folder
         * @return the path of this folder
         */
        private fun folderPath(folder: DavFolder): String = "${folder.path}/"

        /**
         * Creates an element with tracking data based on the given information.
         * @param name the element name
         * @param isFolder flag whether the element is a folder
         * @return the newly created element
         */
        private fun createElement(name: String, isFolder: Boolean = false): DavElement =
            DavElement(elementName(name), isFolder)

        /**
         * Returns a _TimeData_ object that is initialized with the given time.
         * @param date the date (year and month are set to defaults)
         * @param hour the hour
         * @param min the minute
         * @param sec the seconds
         * @return the _TimeData_ object
         */
        private fun referenceTime(date: Int, hour: Int, min: Int, sec: Int): TimeData {
            val cal = Calendar.getInstance()
            cal.clear()
            cal.set(2019, Calendar.JUNE, date, hour, min, sec)
            return TimeData(cal.timeInMillis)
        }

        /**
         * Generates the path of a folder from a timestamp.
         * @param time the time
         * @return the folder path derived from this time
         */
        private fun folderPathFromTime(time: TimeData): String =
            "/${TrackService.NamePrefix}${time.dateString}/"

        /**
         * Generates the path of a file from a timestamp.
         * @param time the time
         * @return the file path derived from this time
         */
        private fun pathFromTime(time: TimeData) =
            "${folderPathFromTime(time)}${TrackService.NamePrefix}${time.timeString}"

        /**
         * Generates a test _LocationData_ object for a test file path.
         * @param index the index of the test file
         * @return a _LocationData_ object for this file
         */
        private fun locationDataForFile(index: Int): LocationData {
            val secs = expectedFiles[index].takeLast(2).toInt()
            return LocationData(
                48.0 + secs / 10.0, 8.0 + secs / 10.0,
                fileTimes[index]
            )
        }

        /**
         * Prepares expectations for the given client mock to return test data
         * when folders on the server are requested.
         * @param davClient the mock for the client
         * @return the same client mock
         */
        private fun prepareDavClientForFolders(davClient: DavClient): DavClient {
            coEvery { davClient.loadFolder("") } returns rootFolder
            coEvery { davClient.loadFolder(folder1.path) } returns folder1
            coEvery { davClient.loadFolder(folder2.path) } returns folder2
            coEvery { davClient.loadFolder(folder3.path) } returns folder3
            return davClient
        }

        /**
         * Creates a mock for a _DavClient_ that is prepared for folder
         * requests.
         * @return the prepared mock client
         */
        private fun createPreparedDavClient(): DavClient = prepareDavClientForFolders(mockk())
    }
}
