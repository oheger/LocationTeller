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
import io.kotest.matchers.shouldBe

/**
 * Test class for [DavFolder].
 */
class DavFolderSpec : StringSpec({
    "DavFolderSpec should resolve a child file" {
        val folder = DavFolder("/base/path", listOf(DavElement("foo", isFolder = true)))
        val elem = DavElement("file.txt", isFolder = false)

        folder.resolve(elem) shouldBe folder.path + "/" + elem.name
    }

    "DavFolder should resolve a child folder" {
        val child = DavElement("foo", isFolder = true)
        val folder = DavFolder("/main/folder", listOf(child))

        folder.resolve(child) shouldBe folder.path + "/" + child.name + "/"
    }

    "LocationData should support serialization and deserialization" {
        val time1 = TimeData(20190624174901L)
        val time2 = TimeData(20190624174922L)
        val locData1 = LocationData(123.456, 321.654, time1)

        val serialForm = locData1.stringRepresentation()
        val locData2 = LocationData.parse(serialForm, time2)
        locData2?.latitude shouldBe locData1.latitude
        locData2?.longitude shouldBe locData1.longitude
        locData2?.time shouldBe time2
    }

    "LocationData.parse() should handle an invalid format" {
        LocationData.parse("invalid", TimeData(20190624175658L)) shouldBe null
    }

    "LocationData.parse() should handle a string without a latitude" {
        LocationData.parse(";1234", TimeData(2019024180012L)) shouldBe null
    }

    "LocationData.parse() should handle a string without a longitude" {
        LocationData.parse("12.34;", TimeData(2019024180109L)) shouldBe null
    }

    "LocationData.parse() should handle non numeric components" {
        LocationData.parse("To be; or not to be", TimeData(2019024180252L)) shouldBe null
    }
})
