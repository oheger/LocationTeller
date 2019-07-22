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
package com.github.oheger.locationteller.map

import com.github.oheger.locationteller.map.LocationTestHelper.createFile
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.map.LocationTestHelper.createState
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

/**
 * Test class for _MarkerFactory_.
 */
class MarkerFactorySpec : StringSpec() {
    init {
        "MarkerFactory should set the correct position" {
            val state = createState(1..8)
            val index = 5
            val key = createFile(index)
            val expLocation = createMarkerData(index)
            val factory = MarkerFactory()

            val options = factory.createMarker(state, key, 0)
            options.position shouldBe expLocation.position
        }

        "MarkerFactory should throw if an invalid key is passed" {
            val state = createState(1..2)
            val factory = MarkerFactory()

            shouldThrow<IllegalArgumentException> {
                factory.createMarker(state, createFile(4), 0)
            }
        }
    }
}