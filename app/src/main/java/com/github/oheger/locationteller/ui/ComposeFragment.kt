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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.compose.runtime.Composable

import com.github.oheger.locationteller.databinding.FragmentComposeBinding

/**
 * A base class for fragments that consist of a single Compose view. The class takes care of the correct initialization
 * of the UI. Derived classes need to provide the actual content of the UI.
 */
abstract class ComposeFragment : androidx.fragment.app.Fragment() {
    /** Holds the binding of this fragment. */
    private var _binding: FragmentComposeBinding? = null

    /**
     * A property for the convenient access to the binding, as long as this
     * fragment is active.
     */
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.composeView.setContent(getContent())
    }

    /**
     * Provide the [Composable] content to be displayed in this fragment. The UI controls emitted by this function
     * are added to the managed compose view.
     */
    protected abstract fun getContent(): @Composable () -> Unit
}
