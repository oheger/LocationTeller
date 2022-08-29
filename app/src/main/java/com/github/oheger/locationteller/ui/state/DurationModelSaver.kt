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
package com.github.oheger.locationteller.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

import com.github.oheger.locationteller.duration.DurationModel

/**
 * A [Saver] implementation to store instances of [DurationModel].
 */
val DURATION_SAVER = Saver<DurationModel, Array<Int>>(
    restore = { data ->
        data.takeIf { it.size == 2 }?.let { serial ->
            DurationModel.Component.values().find { it.ordinal == serial[1] }?.let { maxComponent ->
                DurationModel.create(data[0], maxComponent)
            }
        }
    },
    save = { model ->
        arrayOf(model.duration(), model.maxComponent.ordinal)
    }
)

@Composable
fun rememberDuration(duration: Int, maxComponent: DurationModel.Component): MutableState<DurationModel> =
    rememberSaveable(stateSaver = DURATION_SAVER) {
        mutableStateOf(DurationModel.create(duration, maxComponent))
    }
