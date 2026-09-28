package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ChannelItem(
    val id: Long,
    val name: String,
    val url: String,
    val type: String
)

data class VideoItem(
    val id: Long,
    val channelId: Long,
    val videoId: String,
    val title: String,
    val url: String
)

class VideoDatabase(context: Context) :
    SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {

    companion object {
        private const val DATABASE_NAME = "audio.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_CHANNELS = "channels"
        private const val TABLE_VIDEOS = "videos"
    }

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE $TABLE_CHANNELS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                type TEXT NOT NULL,
                UNIQUE(url, type)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_VIDEOS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id INTEGER NOT NULL,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                UNIQUE(channel_id, video_id),
                FOREIGN KEY(channel_id)
                    REFERENCES $TABLE_CHANNELS(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX index_videos_channel
            ON $TABLE_VIDEOS(channel_id)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 2) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CHANNELS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    type TEXT NOT NULL,
                    UNIQUE(url, type)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS videos_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    channel_id INTEGER NOT NULL,
                    video_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    UNIQUE(channel_id, video_id),
                    FOREIGN KEY(channel_id)
                        REFERENCES $TABLE_CHANNELS(id)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL("DROP TABLE IF EXISTS videos")

            db.execSQL(
                "ALTER TABLE videos_new RENAME TO videos"
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_videos_channel
                ON videos(channel_id)
                """.trimIndent()
            )
        }

        if (oldVersion < 3) {

            db.execSQL(
                "DELETE FROM $TABLE_VIDEOS"
            )

            db.execSQL(
                """
                DELETE FROM $TABLE_CHANNELS
                WHERE url = 'local://old'
                """.trimIndent()
            )
        }
    }

    fun insertChannel(
        name: String,
        url: String,
        type: String
    ): Long {

        val db = writableDatabase

        val values = ContentValues().apply {
            put("name", name)
            put("url", url)
            put("type", type)
        }

        val result = db.insertWithOnConflict(
            TABLE_CHANNELS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        if (result != -1L) {
            return result
        }

        return getChannel(url, type)?.id ?: -1L
    }

    fun getChannel(
        url: String,
        type: String
    ): ChannelItem? {

        val db = readableDatabase

        val cursor = db.query(
            TABLE_CHANNELS,
            arrayOf(
                "id",
                "name",
                "url",
                "type"
            ),
            "url = ? AND type = ?",
            arrayOf(url, type),
            null,
            null,
            "id DESC",
            "1"
        )

        cursor.use {

            if (!it.moveToFirst()) {
                return null
            }

            return ChannelItem(
                id = it.getLong(0),
                name = it.getString(1),
                url = it.getString(2),
                type = it.getString(3)
            )
        }
    }

    fun getAllChannels(): List<ChannelItem> {

        val result = mutableListOf<ChannelItem>()

        val db = readableDatabase

        val cursor = db.query(
            TABLE_CHANNELS,
            arrayOf(
                "id",
                "name",
                "url",
                "type"
            ),
            null,
            null,
            null,
            null,
            "id DESC"
        )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    ChannelItem(
                        id = it.getLong(0),
                        name = it.getString(1),
                        url = it.getString(2),
                        type = it.getString(3)
                    )
                )
            }
        }

        return result
    }

    fun insertVideo(
        channelId: Long,
        videoId: String,
        title: String,
        url: String
    ): Boolean {

        val db = writableDatabase

        val values = ContentValues().apply {
            put("channel_id", channelId)
            put("video_id", videoId)
            put("title", title)
            put("url", url)
        }

        val result = db.insertWithOnConflict(
            TABLE_VIDEOS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return result != -1L
    }

    fun insertVideos(
        channelId: Long,
        videos: List<VideoItem>
    ): Int {

        if (videos.isEmpty()) {
            return 0
        }

        val db = writableDatabase

        var inserted = 0

        db.beginTransaction()

        try {

            for (video in videos) {

                val values = ContentValues().apply {
                    put("channel_id", channelId)
                    put("video_id", video.videoId)
                    put("title", video.title)
                    put("url", video.url)
                }

                val result = db.insertWithOnConflict(
                    TABLE_VIDEOS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                if (result != -1L) {
                    inserted++
                }
            }

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }

        return inserted
    }

    fun getVideos(
        channelId: Long,
        offset: Int = 0,
        limit: Int = 50
    ): List<VideoItem> {

        val result = mutableListOf<VideoItem>()

        val db = readableDatabase

        val cursor = db.query(
            TABLE_VIDEOS,
            arrayOf(
                "id",
                "channel_id",
                "video_id",
                "title",
                "url"
            ),
            "channel_id = ?",
            arrayOf(channelId.toString()),
            null,
            null,
            "id ASC",
            "$offset,$limit"
        )

        cursor.use {

            while (it.moveToNext()) {

                result.add(
                    VideoItem(
                        id = it.getLong(0),
                        channelId = it.getLong(1),
                        videoId = it.getString(2),
                        title = it.getString(3),
                        url = it.getString(4)
                    )
                )
            }
        }

        return result
    }

    fun getVideoCount(
        channelId: Long
    ): Int {

        val db = readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_VIDEOS
            WHERE channel_id = ?
            """.trimIndent(),
            arrayOf(channelId.toString())
        )

        cursor.use {

            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }

        return 0
    }

    fun deleteVideos(
        channelId: Long
    ) {

        writableDatabase.delete(
            TABLE_VIDEOS,
            "channel_id = ?",
            arrayOf(channelId.toString())
        )
    }

    /*
     * Xóa toàn bộ dữ liệu của một channel.
     *
     * Không duyệt từng video bằng Kotlin.
     * SQLite xóa trực tiếp theo channel_id.
     *
     * index_videos_channel giúp truy vấn/xóa nhanh.
     */
    fun deleteChannel(
        channelId: Long
    ) {

        val db = writableDatabase

        db.beginTransaction()

        try {

            db.delete(
                TABLE_VIDEOS,
                "channel_id = ?",
                arrayOf(channelId.toString())
            )

            db.delete(
                TABLE_CHANNELS,
                "id = ?",
                arrayOf(channelId.toString())
            )

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }
}