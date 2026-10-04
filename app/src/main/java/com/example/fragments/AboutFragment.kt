package com.example.fragments

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.example.R

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_about, container, false)
        val videoView = view.findViewById<VideoView?>(R.id.video_view)
        if (videoView != null) {
            try {
                val pkg = requireActivity().packageName
                val uri = Uri.parse("android.resource://$pkg/${R.raw.video}")
                videoView.setVideoURI(uri)
                val mediaController = MediaController(requireContext())
                mediaController.setAnchorView(videoView)
                videoView.setMediaController(mediaController)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                }
                videoView.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return view
    }
}
