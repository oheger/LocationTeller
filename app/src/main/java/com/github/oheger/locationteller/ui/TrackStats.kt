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
package com.github.oheger.locationteller.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.github.oheger.locationteller.R

@Composable
fun StatsLine(labelRes: Int, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(all = 2.dp)) {
        Text(text = stringResource(id = labelRes))
        Spacer(modifier = modifier.width(width = 4.dp))
        Text(text = value)
    }
}

@Preview
@Composable
fun LinePreview() {
    StatsLine(labelRes = R.string.stats_tracking_started, value = "test")
}