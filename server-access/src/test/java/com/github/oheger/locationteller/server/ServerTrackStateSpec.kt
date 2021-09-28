/*
 * Copyright 2019-2021 The Developers.
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.Calendar

/**
 * Test class for [ServerTrackState].
 */
class ServerTrackStateSpec : StringSpec() {
    /**
     * Creates a _TimeData_ object representing a specific point in time.
     * @param day the day (year and month are fix)
     * @param hour the hour
     * @param min the minute
     * @param sec the seconds
     */
    private fun time(day: Int, hour: Int, min: Int, sec: Int): TimeData {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(2019, Calendar.JUNE, day, hour, min, sec)
        return TimeData(cal.timeInMillis)
    }

    /**
     * Returns a _FileData_ object based on the naming conventions to represent
     * the given time.
     * @param time the time of the file
     * @return the corresponding _FileData_
     */
    private fun fileDataForTime(time: TimeData): FileData =
        FileData(time.dateString, time.timeString)

    init {
        "ServerTrackState should handle an empty state when finding outdated files" {
            val state = ServerTrackState(emptyList())

            val result = state.removeOutdatedFiles(time(20, 18, 13, 20))
            result.first shouldHaveSize 0
            result.second shouldBeSameInstanceAs state
        }

        "ServerTrackState should find outdated files" {
            val times = listOf(
                time(18, 8, 20, 17),
                time(18, 10, 30, 27),
                time(19, 8, 11, 12),
                time(19, 10, 20, 30),
                time(19, 13, 13, 13),
                time(19, 17, 28, 55),
                time(20, 1, 42, 19)
            )
            val files = times.map(::fileDataForTime)
            val refTime = time(19, 12, 0, 0)
            val outdated = files.subList(0, 4)
            val remaining = files.subList(4, files.size)
            val state = ServerTrackState(files)

            val result = state.removeOutdatedFiles(refTime)
            result.first shouldContainExactlyInAnyOrder outdated
            result.second.files shouldContainExactlyInAnyOrder remaining
        }

        "ServerTrackState should handle the case that all files are outdated" {
            val times = listOf(
                time(20, 6, 59, 50),
                time(20, 7, 11, 0),
                time(20, 11, 59, 59)
            )
            val files = times.map(::fileDataForTime)
            val refTime = time(20, 12, 0, 0)
            val state = ServerTrackState(files)

            val result = state.removeOutdatedFiles(refTime)
            result.first shouldContainExactlyInAnyOrder files
            result.second.files shouldHaveSize 0
        }

        "ServerTrackState should handle the case that all files are up-to-date" {
            val times = listOf(
                time(20, 0, 0, 0),
                time(20, 12, 0, 0),
                time(21, 1, 1, 1)
            )
            val files = times.map(::fileDataForTime)
            val refTime = time(19, 23, 59, 59)
            val state = ServerTrackState(files)

            val result = state.removeOutdatedFiles(refTime)
            result.first shouldHaveSize 0
            result.second shouldBeSameInstanceAs state
        }

        "ServerTrackState should report the presence of a folder if empty" {
            val state = ServerTrackState(emptyList())

            state.hasFolder("2019-06-22") shouldBe false
        }

        "ServerTrackState should report the presence of a folder if it is found" {
            val times = listOf(
                time(21, 10, 11, 12),
                time(21, 15, 16, 7),
                time(22, 8, 9, 10)
            )
            val state = ServerTrackState(times.map(::fileDataForTime))

            state.hasFolder("2019-06-22") shouldBe true
        }

        "ServerTrackState should report the presence of a folder if it is too small" {
            val times = listOf(
                time(21, 10, 11, 12),
                time(21, 15, 16, 7),
                time(22, 8, 9, 10)
            )
            val state = ServerTrackState(times.map(::fileDataForTime))

            state.hasFolder("2019-06-20") shouldBe false
        }

        "ServerTrackState should report the presence of a folder if it is too big" {
            val times = listOf(
                time(21, 10, 11, 12),
                time(21, 15, 16, 7),
                time(22, 8, 9, 10)
            )
            val state = ServerTrackState(times.map(::fileDataForTime))

            state.hasFolder("2019-06-23") shouldBe false
        }

        "ServerTrackState should support adding a FileData" {
            val file1 = fileDataForTime(time(22, 20, 58, 10))
            val file2 = fileDataForTime(time(22, 21, 0, 30))
            val fileNew = fileDataForTime(time(22, 20, 59, 20))
            val state = ServerTrackState(listOf(file1, file2))

            val nextState = state.appendFile(fileNew)
            nextState.files shouldContainExactly listOf(file1, file2, fileNew)
        }

        "ServerTrackState should support adding a FileData out of order" {
            val file1 = fileDataForTime(time(22, 20, 58, 10))
            val file2 = fileDataForTime(time(22, 21, 0, 30))
            val fileNew = fileDataForTime(time(22, 20, 59, 20))
            val state = ServerTrackState(listOf(file1, file2))

            val nextState = state.appendFile(fileNew, inOrder = false)
            nextState.files shouldContainExactly listOf(file1, fileNew, file2)
        }

        "ServerTrackState should support removing files" {
            val file1 = fileDataForTime(time(22, 20, 58, 10))
            val file2 = fileDataForTime(time(22, 20, 59, 20))
            val file3 = fileDataForTime(time(22, 21, 0, 30))
            val file4 = fileDataForTime(time(22, 21, 9, 24))
            val state = ServerTrackState(listOf(file1, file2, file3, file4))
            val removeFiles = setOf(file1, file4, fileDataForTime(time(1, 1, 2, 3)))

            val nextState = state.removeFiles(removeFiles)
            nextState.files shouldContainExactlyInAnyOrder listOf(file2, file3)
        }
    }
}