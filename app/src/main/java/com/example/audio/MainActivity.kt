package com.example.audio

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var database: VideoDatabase
    private lateinit var python: Python
    private lateinit var pythonModule: PyObject

    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = VideoDatabase(this)

        // QUAN TRỌNG:
        // Python phải start trước Python.getInstance()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        python = Python.getInstance()
        pythonModule = python.getModule("youtobe")

        connectMediaController()

        setContent {
            AudioApp(
                database = database,
                pythonModule = pythonModule,
                lifecycleScope = lifecycleScope,
                mediaControllerProvider = {
                    mediaController
                }
            )
        }
    }

    private fun connectMediaController() {

        Log.d(
            "MEDIA_CONTROLLER",
            "Đang kết nối MediaController..."
        )

        val sessionToken = SessionToken(
            this,
            ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        val future = MediaController.Builder(
            this,
            sessionToken
        ).buildAsync()

        future.addListener(
            {
                try {

                    mediaController = future.get()

                    Log.d(
                        "MEDIA_CONTROLLER",
                        "MediaController đã kết nối"
                    )

                } catch (e: Exception) {

                    Log.e(
                        "MEDIA_CONTROLLER",
                        "Không kết nối được MediaController",
                        e
                    )

                    mediaController = null

                    runOnUiThread {

                        Toast.makeText(
                            this,
                            "Không kết nối được trình phát: ${
                                e.message ?: "lỗi không xác định"
                            }",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            mainExecutor
        )
    }

    override fun onDestroy() {

        mediaController?.release()
        mediaController = null

        database.close()

        super.onDestroy()
    }
}

private enum class AppPage {
    HOME,
    VIDEOS
}

@Composable
fun AudioApp(
    database: VideoDatabase,
    pythonModule: PyObject,
    lifecycleScope: CoroutineScope,
    mediaControllerProvider: () -> MediaController?
) {

    var page by remember {
        mutableStateOf(AppPage.HOME)
    }

    var selectedChannel by remember {
        mutableStateOf<ChannelItem?>(null)
    }

    when (page) {

        AppPage.HOME -> {

            HomeScreen(
                database = database,
                pythonModule = pythonModule,
                lifecycleScope = lifecycleScope,
                onOpenChannel = { channel ->

                    selectedChannel = channel
                    page = AppPage.VIDEOS
                }
            )
        }

        AppPage.VIDEOS -> {

            val channel = selectedChannel

            if (channel == null) {

                page = AppPage.HOME

            } else {

                VideoScreen(
                    database = database,
                    pythonModule = pythonModule,
                    lifecycleScope = lifecycleScope,
                    channel = channel,

                    onBack = {
                        page = AppPage.HOME
                    },

                    mediaControllerProvider = mediaControllerProvider
                )
            }
        }
    }
}

fun normalizeChannelUrl(
    input: String
): String {

    var url = input
        .trim()
        .trimEnd('/')

    val suffixes = listOf(
        "/videos",
        "/streams",
        "/featured",
        "/shorts",
        "/live"
    )

    for (suffix in suffixes) {

        if (url.endsWith(suffix)) {

            url = url
                .removeSuffix(suffix)
                .trimEnd('/')

            break
        }
    }

    return url
}

@Composable
fun HomeScreen(
    database: VideoDatabase,
    pythonModule: PyObject,
    lifecycleScope: CoroutineScope,
    onOpenChannel: (ChannelItem) -> Unit
) {

    val context = LocalContext.current

    var url by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var statusText by remember {
        mutableStateOf("")
    }

    var channels by remember {
        mutableStateOf(
            database.getAllChannels()
        )
    }

    var channelToDelete by remember {
        mutableStateOf<ChannelItem?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Audio",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        TextField(
            value = url,
            onValueChange = {
                url = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("URL YouTube")
            },
            singleLine = true,
            enabled = !loading
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                modifier = Modifier.weight(1f),
                enabled = !loading && url.isNotBlank(),

                onClick = {

                    syncChannel(
                        scope = lifecycleScope,
                        rawUrl = url,
                        mode = "videos",
                        database = database,
                        pythonModule = pythonModule,

                        onLoading = {

                            loading = true
                            statusText =
                                "Đang đồng bộ video..."
                        },

                        onSuccess = { channel ->

                            loading = false
                            statusText = ""

                            channels =
                                database.getAllChannels()

                            onOpenChannel(channel)
                        },

                        onError = { message ->

                            loading = false
                            statusText = ""

                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            ) {
                Text("Video")
            }

            Button(
                modifier = Modifier.weight(1f),
                enabled = !loading && url.isNotBlank(),

                onClick = {

                    syncChannel(
                        scope = lifecycleScope,
                        rawUrl = url,
                        mode = "streams",
                        database = database,
                        pythonModule = pythonModule,

                        onLoading = {

                            loading = true
                            statusText =
                                "Đang đồng bộ livestream..."
                        },

                        onSuccess = { channel ->

                            loading = false
                            statusText = ""

                            channels =
                                database.getAllChannels()

                            onOpenChannel(channel)
                        },

                        onError = { message ->

                            loading = false
                            statusText = ""

                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            ) {
                Text("Livestream")
            }
        }

        if (loading) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                CircularProgressIndicator(
                    modifier = Modifier.padding(
                        end = 10.dp
                    )
                )

                Text(statusText)
            }
        }

        if (channels.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "Kênh đã lấy",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {

                items(
                    items = channels,
                    key = {
                        "${it.id}_${it.type}"
                    }
                ) { channel ->

                    ChannelRow(
                        channel = channel,

                        onClick = {
                            onOpenChannel(channel)
                        },

                        onDelete = {
                            channelToDelete = channel
                        }
                    )
                }
            }
        }
    }

    channelToDelete?.let { channel ->

        AlertDialog(

            onDismissRequest = {
                channelToDelete = null
            },

            title = {
                Text("Xóa kênh")
            },

            text = {
                Text(
                    "Bạn có muốn xóa kênh " +
                            "\"${channel.name}\" không?\n\n" +
                            "Toàn bộ video đã lấy của " +
                            "kênh này cũng sẽ bị xóa."
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        database.deleteChannel(
                            channel.id
                        )

                        channels =
                            database.getAllChannels()

                        channelToDelete = null
                    }
                ) {
                    Text("Xóa")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        channelToDelete = null
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun ChannelRow(
    channel: ChannelItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = channel.name,

            modifier = Modifier
                .weight(1f)
                .clickable {
                    onClick()
                }
                .padding(vertical = 12.dp),

            style = MaterialTheme.typography.bodyLarge
        )

        TextButton(
            onClick = onDelete
        ) {
            Text("Xóa")
        }
    }

    HorizontalDivider()
}

private fun syncChannel(
    scope: CoroutineScope,
    rawUrl: String,
    mode: String,
    database: VideoDatabase,
    pythonModule: PyObject,
    onLoading: () -> Unit,
    onSuccess: (ChannelItem) -> Unit,
    onError: (String) -> Unit
) {

    val cleanUrl =
        normalizeChannelUrl(rawUrl)

    if (cleanUrl.isBlank()) {

        onError("URL không hợp lệ")
        return
    }

    onLoading()

    scope.launch(Dispatchers.IO) {

        try {

            Log.d(
                "SYNC_CHANNEL",
                "Bắt đầu: url=$cleanUrl mode=$mode"
            )

            val result =
                pythonModule.callAttr(
                    "get_channel_batch",
                    cleanUrl,
                    mode,
                    0,
                    50
                ).toString()

            Log.d(
                "SYNC_CHANNEL",
                "Python result length=${result.length}"
            )

            val json =
                JSONObject(result)

            if (!json.optBoolean(
                    "success",
                    false
                )
            ) {

                throw Exception(
                    "Không lấy được danh sách video"
                )
            }

            val channelName =
                json.optString(
                    "channel",
                    "YouTube"
                )

            var channel =
                database.getChannel(
                    cleanUrl,
                    mode
                )

            if (channel == null) {

                val channelId =
                    database.insertChannel(
                        channelName,
                        cleanUrl,
                        mode
                    )

                if (channelId <= 0L) {

                    throw Exception(
                        "Không tạo được channel"
                    )
                }

                channel =
                    database.getChannel(
                        cleanUrl,
                        mode
                    )
            }

            if (channel == null) {

                throw Exception(
                    "Không đọc được channel"
                )
            }

            val videos =
                parseVideos(
                    json,
                    channel.id
                )

            database.insertVideos(
                channel.id,
                videos
            )

            withContext(Dispatchers.Main) {

                onSuccess(channel)
            }

        } catch (e: Exception) {

            Log.e(
                "SYNC_CHANNEL",
                "Lỗi đồng bộ channel",
                e
            )

            withContext(Dispatchers.Main) {

                onError(
                    e.message
                        ?: "Không lấy được video"
                )
            }
        }
    }
}

private data class RemoteBatch(
    val start: Int,
    val videos: List<VideoItem>,
    val hasMore: Boolean
)

private fun parseVideos(
    json: JSONObject,
    channelId: Long
): List<VideoItem> {

    val result =
        mutableListOf<VideoItem>()

    val videosJson =
        json.optJSONArray("videos")
            ?: return result

    for (i in 0 until videosJson.length()) {

        val item =
            videosJson.getJSONObject(i)

        val videoId =
            item.optString("id")

        val title =
            item.optString(
                "title",
                "Không có tiêu đề"
            )

        val videoUrl =
            item.optString("url")

        if (
            videoId.isNotBlank() &&
            videoUrl.isNotBlank()
        ) {

            result.add(
                VideoItem(
                    id = 0,
                    channelId = channelId,
                    videoId = videoId,
                    title = title,
                    url = videoUrl
                )
            )
        }
    }

    return result
}

private class ChannelQueue(
    private val scope: CoroutineScope,
    private val pythonModule: PyObject,
    private val channel: ChannelItem,
    private val batchSize: Int = 50
) {

    private val jobs =
        mutableMapOf<Int, Job>()

    private val results =
        mutableMapOf<Int, RemoteBatch>()

    fun prefetch(start: Int) {

        synchronized(this) {

            if (jobs.containsKey(start)) {
                return
            }

            if (results.containsKey(start)) {
                return
            }

            val job =
                scope.launch(Dispatchers.IO) {

                    try {

                        val batch =
                            fetchBatch(start)

                        synchronized(
                            this@ChannelQueue
                        ) {

                            results[start] =
                                batch
                        }

                    } catch (e: Exception) {

                        Log.e(
                            "CHANNEL_QUEUE",
                            "Prefetch lỗi start=$start",
                            e
                        )

                    } finally {

                        synchronized(
                            this@ChannelQueue
                        ) {

                            jobs.remove(start)
                        }
                    }
                }

            jobs[start] = job
        }
    }

    suspend fun get(
        start: Int
    ): RemoteBatch {

        val ready =
            synchronized(this) {
                results.remove(start)
            }

        if (ready != null) {
            return ready
        }

        val job =
            synchronized(this) {
                jobs[start]
            }

        if (job != null) {

            job.join()

            val afterWait =
                synchronized(this) {
                    results.remove(start)
                }

            if (afterWait != null) {
                return afterWait
            }
        }

        return fetchBatch(start)
    }

    private suspend fun fetchBatch(
        start: Int
    ): RemoteBatch {

        Log.d(
            "CHANNEL_QUEUE",
            "Fetch batch start=$start"
        )

        val result =
            pythonModule.callAttr(
                "get_channel_batch",
                channel.url,
                channel.type,
                start,
                batchSize
            ).toString()

        val json =
            JSONObject(result)

        if (!json.optBoolean(
                "success",
                false
            )
        ) {

            throw Exception(
                "Không lấy được video tiếp theo"
            )
        }

        val videos =
            parseVideos(
                json,
                channel.id
            )

        val hasMore =
            json.optBoolean(
                "has_more",
                videos.size >= batchSize
            )

        return RemoteBatch(
            start = start,
            videos = videos,
            hasMore = hasMore
        )
    }
}

@Composable
fun VideoScreen(
    database: VideoDatabase,
    pythonModule: PyObject,
    lifecycleScope: CoroutineScope,
    channel: ChannelItem,
    onBack: () -> Unit,
    mediaControllerProvider: () -> MediaController?
) {

    val context = LocalContext.current

    val videos =
        remember {
            mutableStateListOf<VideoItem>()
        }

    var loading by remember {
        mutableStateOf(false)
    }

    var hasMore by remember {
        mutableStateOf(true)
    }

    var displayOffset by remember {
        mutableStateOf(0)
    }

    var remoteOffset by remember {
        mutableStateOf(0)
    }

    var errorText by remember {
        mutableStateOf("")
    }

    val listState =
        rememberLazyListState()

    val queue =
        remember(
            channel.id,
            channel.type
        ) {

            ChannelQueue(
                scope = lifecycleScope,
                pythonModule = pythonModule,
                channel = channel,
                batchSize = 50
            )
        }

    LaunchedEffect(channel.id) {

        val first =
            withContext(Dispatchers.IO) {

                database.getVideos(
                    channelId = channel.id,
                    offset = 0,
                    limit = 50
                )
            }

        videos.clear()
        videos.addAll(first)

        displayOffset =
            first.size

        /*
         * Channel mới sync 50 remote item.
         *
         * Đây chỉ là offset khởi đầu.
         * Pagination tiếp tục từ đây.
         */
        remoteOffset =
            if (first.isNotEmpty()) {
                50
            } else {
                0
            }

        hasMore =
            first.size == 50

        if (hasMore) {

            queue.prefetch(
                remoteOffset
            )

            queue.prefetch(
                remoteOffset + 50
            )
        }
    }

    LaunchedEffect(channel.id) {

        snapshotFlow {

            val layoutInfo =
                listState.layoutInfo

            val totalItems =
                layoutInfo.totalItemsCount

            val lastVisible =
                layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: -1

            totalItems > 0 &&
                    lastVisible >=
                    totalItems - 5

        }.collect { nearEnd ->

            if (!nearEnd) {
                return@collect
            }

            if (!hasMore) {
                return@collect
            }

            if (loading) {
                return@collect
            }

            loading = true
            errorText = ""

            try {

                /*
                 * =====================================
                 * 1. SQLITE TRƯỚC
                 * =====================================
                 */

                val localVideos =
                    withContext(Dispatchers.IO) {

                        database.getVideos(
                            channelId = channel.id,
                            offset = displayOffset,
                            limit = 50
                        )
                    }

                if (localVideos.isNotEmpty()) {

                    videos.addAll(
                        localVideos
                    )

                    displayOffset +=
                        localVideos.size

                    if (
                        localVideos.size == 50
                    ) {

                        queue.prefetch(
                            remoteOffset
                        )

                        queue.prefetch(
                            remoteOffset + 50
                        )

                    } else {

                        hasMore = true
                    }

                } else {

                    /*
                     * =====================================
                     * 2. SQLITE HẾT
                     * =====================================
                     */

                    var addedSomething =
                        false

                    var attempts = 0

                    while (
                        attempts < 5 &&
                        hasMore
                    ) {

                        attempts++

                        val batch =
                            queue.get(
                                remoteOffset
                            )

                        if (
                            batch.videos.isEmpty()
                        ) {

                            hasMore = false
                            break
                        }

                        /*
                         * QUAN TRỌNG:
                         *
                         * remoteOffset tăng theo số
                         * remote item đã đọc.
                         *
                         * Không tăng theo số insert DB.
                         */
                        remoteOffset +=
                            batch.videos.size

                        withContext(
                            Dispatchers.IO
                        ) {

                            database.insertVideos(
                                channel.id,
                                batch.videos
                            )
                        }

                        val newLocal =
                            withContext(
                                Dispatchers.IO
                            ) {

                                database.getVideos(
                                    channelId =
                                        channel.id,
                                    offset =
                                        displayOffset,
                                    limit = 50
                                )
                            }

                        if (newLocal.isNotEmpty()) {

                            videos.addAll(
                                newLocal
                            )

                            displayOffset +=
                                newLocal.size

                            addedSomething = true

                            break
                        }

                        if (!batch.hasMore) {

                            hasMore = false
                            break
                        }

                        queue.prefetch(
                            remoteOffset
                        )

                        queue.prefetch(
                            remoteOffset + 50
                        )
                    }

                    if (!addedSomething) {

                        if (attempts >= 5) {

                            hasMore = false
                        }
                    }

                    if (hasMore) {

                        queue.prefetch(
                            remoteOffset
                        )

                        queue.prefetch(
                            remoteOffset + 50
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    "VIDEO_PAGINATION",
                    "Lỗi pagination",
                    e
                )

                errorText =
                    e.message
                        ?: "Không lấy thêm được video"

                hasMore = false

            } finally {

                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBack
            ) {
                Text("Quay lại")
            }

            Text(
                text = channel.name,

                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),

                style =
                    MaterialTheme.typography.titleLarge
            )
        }

        if (errorText.isNotBlank()) {

            Text(
                text = errorText,
                modifier = Modifier.padding(
                    vertical = 8.dp
                )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {

            items(
                items = videos,
                key = {
                    /*
                     * id của DB là UNIQUE.
                     */
                    it.id
                }
            ) { video ->

                VideoRow(
                    video = video,

                    onClick = {

                        playVideo(
                            context = context,
                            scope = lifecycleScope,
                            video = video,
                            pythonModule = pythonModule,
                            mediaController =
                                mediaControllerProvider()
                        )
                    }
                )
            }

            if (loading) {

                item {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),

                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit
) {

    Text(
        text = video.title,

        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(
                vertical = 14.dp,
                horizontal = 8.dp
            ),

        style =
            MaterialTheme.typography.bodyLarge
    )

    HorizontalDivider()
}

private fun playVideo(
    context: android.content.Context,
    scope: CoroutineScope,
    video: VideoItem,
    pythonModule: PyObject,
    mediaController: MediaController?
) {

    Log.d(
        "PLAY_VIDEO",
        "CLICK: ${video.title}"
    )

    /*
     * Nếu MediaController chưa kết nối,
     * báo rõ cho người dùng.
     */
    if (mediaController == null) {

        Log.e(
            "PLAY_VIDEO",
            "MediaController == null"
        )

        Toast.makeText(
            context,
            "Trình phát chưa sẵn sàng. Thử lại sau 1 giây.",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    Toast.makeText(
        context,
        "Đang lấy audio...",
        Toast.LENGTH_SHORT
    ).show()

    scope.launch(Dispatchers.IO) {

        try {

            Log.d(
                "PLAY_VIDEO",
                "Gọi get_media_info(): ${video.url}"
            )

            val result =
                pythonModule.callAttr(
                    "get_media_info",
                    video.url
                ).toString()

            Log.d(
                "PLAY_VIDEO",
                "get_media_info result: $result"
            )

            val json =
                JSONObject(result)

            val success =
                json.optBoolean(
                    "success",
                    true
                )

            if (!success) {

                throw Exception(
                    json.optString(
                        "error",
                        "yt-dlp không lấy được media"
                    )
                )
            }

            val mediaUrl =
                json.optString("url")

            val title =
                json.optString(
                    "title",
                    video.title
                )

            if (mediaUrl.isBlank()) {

                throw Exception(
                    "yt-dlp trả về URL audio rỗng"
                )
            }

            Log.d(
                "PLAY_VIDEO",
                "Đã lấy media URL"
            )

            withContext(Dispatchers.Main) {

                val controller =
                    mediaController

                if (controller == null) {

                    throw Exception(
                        "MediaController không còn kết nối"
                    )
                }

                val mediaItem =
                    MediaItem.Builder()
                        .setUri(mediaUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .build()
                        )
                        .build()

                Log.d(
                    "PLAY_VIDEO",
                    "setMediaItem()"
                )

                controller.setMediaItem(
                    mediaItem
                )

                Log.d(
                    "PLAY_VIDEO",
                    "prepare()"
                )

                controller.prepare()

                Log.d(
                    "PLAY_VIDEO",
                    "play()"
                )

                controller.play()

                Toast.makeText(
                    context,
                    "Đang phát: $title",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {

            Log.e(
                "PLAY_VIDEO",
                "PLAYBACK ERROR",
                e
            )

            withContext(Dispatchers.Main) {

                Toast.makeText(
                    context,
                    "Lỗi phát: ${
                        e.message
                            ?: "Không xác định"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}