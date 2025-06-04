package com.example.app

import android.content.Context
import android.media.MediaPlayer

object SoundManager {

    private var mediaPlayer: MediaPlayer? = null

    fun playSound(context: Context, direction: Direction, incomingObject: Objects, intensity: Int) {

        val intensityString : String

        when (intensity) {
            0 -> intensityString = "low"
            1 -> intensityString = "medium"
            2 -> intensityString = "high"
            else -> intensityString = "low"
        }

        val resName = "${direction.name.lowercase()}_" + "${incomingObject.name.lowercase()}_" + intensityString

        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)

        if (resId == 0) {
            return
        }

        mediaPlayer?.let {
            it.stop()
            it.release()
        }

        mediaPlayer = MediaPlayer.create(context, resId).apply {
            isLooping = false
            setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
            start()
        }
    }
    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
            mediaPlayer = null
        }
    }
}