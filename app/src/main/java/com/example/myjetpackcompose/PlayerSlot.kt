package com.example.myjetpackcompose

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

data class PlayerSlot(
    val index: Int,
    val player: ExoPlayer
)

fun createPreparedPlayer(
    context: Context,
    file: File,
    playWhenReady: Boolean = false
): ExoPlayer {
    val tag = "VaultPlayer"
    val start = System.currentTimeMillis()

    Log.d(tag, "========== createPreparedPlayer ==========")
    Log.d(tag, "file=${file.name}")
    Log.d(tag, "exists=${file.exists()}")
    Log.d(tag, "sizeMB=${file.length() / 1024 / 1024}")
    Log.d(tag, "playWhenReady=$playWhenReady")

    return ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val text = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }

                Log.d(tag, "[${file.name}] state=$text after ${System.currentTimeMillis() - start}ms")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "[${file.name}] ERROR", error)
            }
        })

        Log.d(tag, "[${file.name}] setMediaItem")
        setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))

        this.playWhenReady = playWhenReady

        Log.d(tag, "[${file.name}] prepare")
        prepare()

        Log.d(tag, "[${file.name}] prepare called")
    }
}
