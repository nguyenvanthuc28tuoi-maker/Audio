package com.example.audio

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            var url by remember { mutableStateOf("") }
            var result by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") }
                )

                Button(
                    enabled = !loading,
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
                        result = "Đang lấy URL..."

                        Thread {
                            try {
                                val python = Python.getInstance()
                                val module = python.getModule("youtobe")

                                val mediaUrl = module
                                    .callAttr(
                                        "get_media_url",
                                        url.trim()
                                    )
                                    .toString()

                                runOnUiThread {
                                    loading = false
                                    result = mediaUrl
                                }

                            } catch (e: Exception) {

                                runOnUiThread {
                                    loading = false
                                    result = "LỖI: ${e.message}"
                                }
                            }
                        }.start()
                    }
                ) {
                    Text(
                        if (loading) "Đang lấy..." else "Lấy URL"
                    )
                }

                Text(
                    text = result
                )
            }
        }
    }
}