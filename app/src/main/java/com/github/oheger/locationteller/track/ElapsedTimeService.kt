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
package com.github.oheger.locationteller.track

import android.os.SystemClock
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService

/**
 * A specialized implementation of [TimeService] which returns the elapsed
 * time from the _SystemClock_.
 */
object ElapsedTimeService : TimeService {
    override fun currentTime(): TimeData =
        TimeData(SystemClock.elapsedRealtime())
}
