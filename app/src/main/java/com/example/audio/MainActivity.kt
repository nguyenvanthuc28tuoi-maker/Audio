package com.example.audio

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var controllerFuture:
            ListenableFuture<MediaController>

    private var mediaController:
            MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(
                AndroidPlatform(this)
            )
        }

        val sessionToken =
            SessionToken(
                this,
                android.content.ComponentName(
                    this,
                    PlaybackService::class.java
                )
            )

        controllerFuture =
            MediaController.Builder(
                this,
                sessionToken
            ).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    mediaController =
                        controllerFuture.get()
                } catch (_: Exception) {
                }
            },
            ContextCompat.getMainExecutor(this)
        )

        setContent {

            var url by remember {
                mutableStateOf("")
            }

            var loading by remember {
                mutableStateOf(false)
            }

            var isPlaying by remember {
                mutableStateOf(false)
            }

            var position by remember {
                mutableFloatStateOf(0f)
            }

            var duration by remember {
                mutableFloatStateOf(0f)
            }

            LaunchedEffect(Unit) {

                while (true) {

                    val controller =
                        mediaController

                    if (controller != null) {

                        val currentDuration =
                            controller.duration

                        if (
                            currentDuration > 0 &&
                            currentDuration != Long.MIN_VALUE
                        ) {
                            duration =
                                currentDuration.toFloat()

                            position =
                                controller.currentPosition
                                    .coerceAtLeast(0L)
                                    .toFloat()
                        }

                        isPlaying =
                            controller.isPlaying
                    }

                    delay(500)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("YouTube URL")
                    }
                )

                Button(
                    enabled = !loading,
                    modifier =
                        Modifier.fillMaxWidth(),
                    onClick = {

                        if (url.isBlank()) {

                            Toast.makeText(
                                this@MainActivity,
                                "Nhập URL trước",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@Button
                        }

                        loading = true

                        Thread {

                            try {

                                val python =
                                    Python.getInstance()

                                val module =
                                    python.getModule(
                                        "youtobe"
                                    )

                                val jsonText =
                                    module
                                        .callAttr(
                                            "get_media_info",
                                            url.trim()
                                        )
                                        .toString()

                                val json =
                                    JSONObject(jsonText)

                                val mediaUrl =
                                    json.getString("url")

                                val title =
                                    json.getString("title")

                                runOnUiThread {

                                    val controller =
                                        mediaController

                                    if (controller == null) {

                                        loading = false

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Media player chưa sẵn sàng",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        return@runOnUiThread
                                    }

                                    try {

                                        val mediaItem =
                                            MediaItem.Builder()
                                                .setMediaId(
                                                    url.trim()
                                                )
                                                .setUri(
                                                    mediaUrl
                                                )
                                                .setMediaMetadata(
                                                    MediaMetadata.Builder()
                                                        .setTitle(
                                                            title
                                                        )
                                                        .build()
                                                )
                                                .build()

                                        controller.setMediaItem(
                                            mediaItem
                                        )

                                        controller.prepare()

                                        controller.play()

                                        loading = false
                                        isPlaying = true
                                        position = 0f
                                        duration = 0f

                                    } catch (e: Exception) {

                                        loading = false

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Lỗi phát: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                            } catch (e: Exception) {

                                runOnUiThread {

                                    loading = false

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Lỗi: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                        }.start()
                    }
                ) {

                    Text(
                        if (loading) {
                            "Đang lấy..."
                        } else {
                            "Lấy URL"
                        }
                    )
                }

                if (duration > 0f) {

                    Slider(
                        value =
                            position.coerceIn(
                                0f,
                                duration
                            ),
                        onValueChange = {
                            position = it
                        },
                        onValueChangeFinished = {

                            mediaController?.seekTo(
                                position.toLong()
                            )
                        },
                        valueRange =
                            0f..duration,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        Button(
                            onClick = {

                                val controller =
                                    mediaController
                                        ?: return@Button

                                if (controller.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.play()
                                }

                                isPlaying =
                                    controller.isPlaying
                            }
                        ) {

                            Text(
                                if (isPlaying) {
                                    "Pause"
                                } else {
                                    "Play"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {

        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(
                controllerFuture
            )
        }

        mediaController = null

        super.onDestroy()
    }
}