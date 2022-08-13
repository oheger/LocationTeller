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

import java.util.EnumMap

/**
 * A class that manages the different properties used by the Composable function to edit durations.
 *
 * The duration editor consists of multiple input fields for integer numbers corresponding to the single components of
 * the duration: the seconds, minutes, hours, and days. This class is able to split a duration in seconds into these
 * components and to combine the components again to a duration. The number of components is dynamic.
 */
class DurationEditorModel private constructor(
    /** Holds the values of the single components. */
    private val components: EnumMap<Component, Int>,

    /** The maximum component represented by this model. */
    private val maxComponent: Component
) {
    companion object {
        /**
         * Create a [DurationEditorModel] instance for the given [duration] (in seconds) that manages a duration up to
         * the given [maxComponent].
         */
        fun create(duration: Int, maxComponent: Component = Component.MINUTE): DurationEditorModel =
            DurationEditorModel(splitDuration(duration, maxComponent), maxComponent)
    }

    /**
     * Return the value for the given [component].
     */
    operator fun get(component: Component): Int = components[component] ?: 0

    /**
     * Set the [value] for the given [component].
     */
    operator fun set(component: Component, value: Int) {
        components[component] = value
    }

    /**
     * Return the duration (in seconds) that corresponds to the components stored in this instance.
     */
    fun duration(): Int {
        fun processComponent(currentDuration: Int, component: Component, factor: Int): Int {
            val nextDuration = currentDuration + get(component) * factor
            return if (component == maxComponent) nextDuration
            else processComponent(nextDuration, component.next(), factor * component.upperBound)
        }

        return processComponent(0, Component.SECOND, 1)
    }

    /**
     * An enum class defining the supported components of a duration.
     */
    enum class Component(
        /** Holds the upper bound for this component. */
        val upperBound: Int
    ) {
        SECOND(60),
        MINUTE(60),
        HOUR(24),
        DAY(Int.MAX_VALUE)
    }
}

/**
 * Split a [duration] in seconds into its single components up to [maxComponent].
 */
private fun splitDuration(
    duration: Int,
    maxComponent: DurationEditorModel.Component
): EnumMap<DurationEditorModel.Component, Int> {
    val components = EnumMap<DurationEditorModel.Component, Int>(DurationEditorModel.Component::class.java)

    fun processComponent(currentDuration: Int, component: DurationEditorModel.Component) {
        if (component == maxComponent) {
            components[component] = currentDuration
        } else {
            components[component] = currentDuration % component.upperBound
            processComponent(currentDuration / component.upperBound, component.next())
        }
    }

    processComponent(duration, DurationEditorModel.Component.SECOND)
    return components
}

/**
 * Return the next higher component (in ascending order).
 */
private fun DurationEditorModel.Component.next(): DurationEditorModel.Component =
    DurationEditorModel.Component.values()[ordinal + 1]
