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

import android.app.Application

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.duration.TimeDeltaFormatter
import com.github.oheger.locationteller.map.AlphaRange
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.RangeTimeDeltaAlphaCalculator

/**
 * Definition of an interface that defines the contract of the view model used for the receiver part of the application
 * (the map view showing the recorded locations).
 */
interface ReceiverViewModel {
    /** The configuration with settings related to the receiver part. */
    val receiverConfig: ReceiverConfig

    /** The factory for the markers to be added to the map. */
    val markerFactory: MarkerFactory

    /**
     * Set the current [ReceiverConfig] to [newConfig]. This causes updates on some objects managed by this instance.
     */
    fun updateReceiverConfig(newConfig: ReceiverConfig)
}

/**
 * The productive implementation of [ReceiverViewModel].
 */
class ReceiverViewModelImpl(application: Application) : AndroidViewModel(application), ReceiverViewModel {
    companion object {
        /** The alpha calculator for fast and strong fading. */
        val CALCULATOR_FAST_STRONG = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, DurationModel.Component.HOUR.toMillis()),
                AlphaRange(0.5f, 0.2f, DurationModel.Component.DAY.toMillis())
            ), 0.1f
        )

        /** The alpha calculator for slow and strong fading. */
        val CALCULATOR_SLOW_STRONG = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, DurationModel.Component.DAY.toMillis()),
                AlphaRange(0.5f, 0.2f, 7 * DurationModel.Component.DAY.toMillis())
            ), 0.1f
        )

        /** The alpha calculator for fast normal fading. */
        val CALCULATOR_FAST = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, DurationModel.Component.HOUR.toMillis()),
                AlphaRange(0.7f, 0.5f, DurationModel.Component.DAY.toMillis())
            ), 0.4f
        )

        /** The alpha calculator for slow normal fading. */
        val CALCULATOR_SLOW = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, DurationModel.Component.DAY.toMillis()),
                AlphaRange(0.7f, 0.5f, 7 * DurationModel.Component.DAY.toMillis())
            ), 0.4f
        )
    }

    /** Stores the shared [PreferencesHandler] instance. */
    private val preferencesHandler = PreferencesHandler.getInstance(application)

    /** Stores the formatter for time deltas. */
    private val timeDeltaFormatter = createTimeDeltaFormatter(application)

    /**
     * Holds the current [ReceiverConfig]. The configuration gets updated when the user changes settings.
     */
    private val currentReceiverConfig = mutableStateOf(ReceiverConfig.fromPreferences(preferencesHandler))

    /**
     * Stores the current [MarkerFactory]. Everytime the configuration changes, a new factory is created that is
     * configured accordingly.
     */
    private val currentMarkerFactory = mutableStateOf(createMarkerFactory())

    override val receiverConfig: ReceiverConfig
        get() = currentReceiverConfig.value

    override val markerFactory: MarkerFactory
        get() = currentMarkerFactory.value

    override fun updateReceiverConfig(newConfig: ReceiverConfig) {
        currentReceiverConfig.value = newConfig
        newConfig.save(preferencesHandler)

        currentMarkerFactory.value = createMarkerFactory()
    }

    /**
     * Create a new [MarkerFactory] based on the current configuration.
     */
    private fun createMarkerFactory(): MarkerFactory {
        val alphaCalculator = with(currentReceiverConfig.value) {
            if (!fadeOutEnabled) DisabledFadeOutAlphaCalculator
            else if (strongFadeOut) {
                if (fastFadeOut) CALCULATOR_FAST_STRONG
                else CALCULATOR_SLOW_STRONG
            } else {
                if (fastFadeOut) CALCULATOR_FAST
                else CALCULATOR_SLOW
            }
        }

        return MarkerFactory(timeDeltaFormatter, alphaCalculator)
    }

    /**
     * Create the [TimeDeltaFormatter] that is used by the [MarkerFactory] managed by this model.
     */
    private fun createTimeDeltaFormatter(application: Application): TimeDeltaFormatter =
        TimeDeltaFormatter(
            unitSec = application.getString(R.string.time_secs),
            unitMin = application.getString(R.string.time_minutes),
            unitHour = application.getString(R.string.time_hours),
            unitDay = application.getString(R.string.time_days)
        )
}
