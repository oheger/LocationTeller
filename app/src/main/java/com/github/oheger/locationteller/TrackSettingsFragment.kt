package com.github.oheger.locationteller

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * A simple [Fragment] subclass.
 *
 */
class TrackSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i("TrackSettingsFragment", "Creating TrackSettingsFragment")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_track_settings, container, false)
    }

}
