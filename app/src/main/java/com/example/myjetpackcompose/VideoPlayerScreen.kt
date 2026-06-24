package com.example.myjetpackcompose

import android.net.Uri
import android.view.SurfaceView
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

@AndroidOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoFiles: List<File>,
    startIndex: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    var currentIndex by remember { mutableIntStateOf(startIndex) }
    val thisVideoFile = videoFiles[currentIndex]
    val hasPrevious = player.hasPreviousMediaItem()
    val hasNext = player.hasNextMediaItem()





    // --- ExoPlayer setup ---
//    val player = remember {
//        ExoPlayer.Builder(context).build().also { exo ->
////            exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(thisVideoFile)))
////            exo.prepare()
//            exo.playWhenReady = true
//        }
//    }




    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // --- UI state ---
    var isPlaying by remember { mutableStateOf(true) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    var videoRatio by remember { mutableFloatStateOf(16f / 9f) }

//    var currentMS by remember { mutableLongStateOf(0L) }
//    var durationMS by remember { mutableLongStateOf(0L) }

    // this runs whenever currentIndex changes
    LaunchedEffect(videoFiles) {
        val mediaItems = videoFiles.map {
            MediaItem.fromUri(Uri.fromFile(it))
        }

        player.setMediaItems(mediaItems)
        player.prepare()
        player.seekTo(startIndex, 0)
        player.play()
    }

    // Listen to player state changes
//    DisposableEffect(player) {
//        val listener = object : Player.Listener {
//            override fun onIsPlayingChanged(playing: Boolean) {
//                isPlaying = playing
//            }
//        }
//        player.addListener(listener)
//        onDispose { player.removeListener(listener) }
//    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                currentIndex = player.currentMediaItemIndex
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) {
                    videoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Poll position every 500ms
    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) {
                currentMs = player.currentPosition
                durationMs = player.duration.coerceAtLeast(0L)
                if (durationMs > 0) {
                    sliderPosition = currentMs.toFloat() / durationMs.toFloat()
                }
            }
            delay(500)
        }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        // --- Video Surface ---
//        AndroidView(
//            factory = { ctx ->
//                SurfaceView(ctx).also { sv ->
//                    player.setVideoSurfaceView(sv)
//                }
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .aspectRatio(16f / 9f)
//                .align(Alignment.Center)
//        )

//        DisposableEffect(player) {
//            val listener = object : Player.Listener {
//                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
//                    if (videoSize.height > 0) {
//                        videoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
//                    }
//                }
//                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
//            }
//            player.addListener(listener)
//            onDispose { player.removeListener(listener) }
//        }

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    player.setVideoSurfaceView(sv)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(videoRatio)   // ← now uses real video dimensions
                .align(Alignment.Center)
        )

        // --- Controls overlay ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .align(Alignment.TopStart)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = thisVideoFile.nameWithoutExtension,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Center controls: skip back | play/pause | skip forward
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    IconButton(
                        onClick = {  player.seekToPreviousMediaItem() },
                        enabled = hasPrevious,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Skip Previous",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    IconButton(
                        onClick = { player.seekToNextMediaItem()},
                        enabled = hasNext,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Skip Next",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Bottom: seekbar + timestamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 200.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(currentMs),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = formatMs(durationMs),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            isSeeking = true
                            sliderPosition = newValue
                            currentMs = (newValue * durationMs).toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo((sliderPosition * durationMs).toLong())
                            isSeeking = false
                        },
                        thumb = {
                            Box(
                                Modifier.
                                    size(30.dp).
                                    background(Color.White, CircleShape)
                            )

                        },
                        track = {sliderState ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.35f), CircleShape)
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(sliderState.value)
                                    .height(3.dp)
                                    .background(Color.White, CircleShape)
                            )
                        },


                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val skipOptions = listOf(
                        "-10m" to -600_000L,
                        "-1m"  to -60_000L,
                        "-10s" to -10_000L,
                        "+10s" to  10_000L,
                        "+1m"  to  60_000L,
                        "+10m" to  600_000L,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
//                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        skipOptions.forEach { (label, ms) ->
                            OutlinedButton(
                                onClick = {
                                    player.seekTo(
                                        (player.currentPosition + ms)
                                            .coerceIn(0L, player.duration)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)    // ← each button takes equal share of the row
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White)
                            ) {
                                Text(label, fontSize = 8.sp)
                            }
                        }
                    }

                }

            }
        }
    }
}

// --- Helper ---
fun formatMs(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}