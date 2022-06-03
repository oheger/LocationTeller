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
package com.github.oheger.locationteller.track

import android.location.Location

/**
 * Definition of a service interface for querying the current GPS location.
 *
 * The interface defines a function that returns the current GPS location.
 * Depending on the concrete use case, there will be multiple implementations;
 * especially, the actual implementation may have to be decorated to satisfy
 * certain constraints.
 */
interface LocationRetriever {
    /**
     * Fetches the current GPS location. The operation may fail if no result
     * (or no result satisfying constraints regarding precession) can be
     * obtained; in this case, result is *null*.
     * @return the current location or *null*
     */
    suspend fun fetchLocation(): Location?
}
