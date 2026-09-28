=== NOTE TIẾP TỤC DỰ ÁN AUDIO ===

Tôi đang làm app Android Studio tên "Audio".

MỤC TIÊU APP:
- Kotlin + Jetpack Compose.
- Chaquopy 17.0.0.
- Python 3.11.
- Nhúng yt-dlp trực tiếp vào app Android, KHÔNG dùng server trung gian.
- File Python bắt buộc tên: youtobe.py
- Kotlin gọi:
  python.getModule("youtobe")
- Người dùng nhập link YouTube.
- Bấm "Lấy URL".
- yt-dlp lấy direct audio URL.
- App phát trực tiếp bằng Media3 ExoPlayer.
- Không hiển thị raw audio URL trên giao diện.
- Hiển thị title/video trong Media notification.
- Muốn lấy URL nhanh và ổn định nhất có thể.

CẤU HÌNH GRADLE HIỆN TẠI:

app/build.gradle.kts:

plugins {
alias(libs.plugins.android.application)
alias(libs.plugins.kotlin.compose)
id("com.chaquo.python")
}

android {
namespace = "com.example.audio"
compileSdk {
version = release(37)
}

    defaultConfig {
        applicationId = "com.example.audio"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a"
            )
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

chaquopy {
defaultConfig {
version = "3.11"

        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}

Top-level build.gradle.kts:

plugins {
alias(libs.plugins.android.application) apply false
alias(libs.plugins.kotlin.compose) apply false
id("com.chaquo.python") version "17.0.0" apply false
}

gradle.properties:
- Có:
  org.gradle.configuration-cache=false
  vì trước đó Chaquopy gặp vấn đề với configuration cache.


PYTHON:

File phải giữ nguyên tên:
youtobe.py

Code hiện tại:

import json
import yt_dlp


def get_media_info(url):
opts = {
"quiet": True,
"no_warnings": True,
"skip_download": True,
"noplaylist": True,
"format": "bestaudio/best",
"cachedir": False,
}

    with yt_dlp.YoutubeDL(opts) as ydl:

        info = ydl.extract_info(
            url,
            download=False
        )

        media_url = info.get("url")
        title = info.get("title") or "YouTube"

        duration = info.get("duration") or 0

        if not media_url:
            raise Exception(
                "Không lấy được audio URL"
            )

        return json.dumps(
            {
                "title": title,
                "url": media_url,
                "duration": duration
            },
            ensure_ascii=False
        )


KIẾN TRÚC MEDIA:

MainActivity
↓
MediaController
↓
PlaybackService : MediaSessionService
↓
ExoPlayer
↓
Direct audio URL từ yt-dlp

PlaybackService chạy foreground để phát nhạc nền/lock screen.


MAIN ACTIVITY:

MainActivity hiện có:
- Ô nhập YouTube URL.
- Nút "Lấy URL".
- Gọi Python trong Thread để không block UI.
- Python trả JSON:
  {
  "title": "...",
  "url": "...",
  "duration": ...
  }
- JSONObject lấy title/url.
- Tạo MediaItem:
  MediaItem.Builder()
  .setMediaId(url.trim())
  .setUri(mediaUrl)
  .setMediaMetadata(
  MediaMetadata.Builder()
  .setTitle(title)
  .build()
  )
  .build()
- controller.setMediaItem()
- controller.prepare()
- controller.play()

UI có:
- Slider tua trong app.
- Play/Pause.
- Không hiển thị direct audio URL.
- Poll position/duration mỗi 500ms.


PLAYBACKSERVICE:

Hiện dùng:

class PlaybackService : MediaSessionService

ExoPlayer:
- setSeekBackIncrementMs(10_000)
- setSeekForwardIncrementMs(10_000)

MediaSession có callback onConnect và cấp:
- COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
- COMMAND_SEEK_BACK
- COMMAND_SEEK_FORWARD
- COMMAND_PLAY_PAUSE
- COMMAND_GET_TIMELINE
- COMMAND_GET_METADATA

MỤC TIÊU NOTIFICATION:
- Hiển thị title video.
- Chỉ cần Play/Pause.
- Không cần Next/Previous.
- Muốn có thanh tiến trình ngay trong notification để kéo tua.
- Muốn kéo thanh notification là tua được.
- Audio chạy background và lock screen.

LƯU Ý:
Notification progress/seekbar là phần đang cần hoàn thiện.
Media3/Android notification phải nhận được timeline + duration + seek commands.
Nếu Samsung One UI không hiển thị seekbar mặc định thì cần xử lý tiếp bằng cách phù hợp, không được nói là đã xong nếu thực tế chưa có thanh kéo.


YÊU CẦU QUAN TRỌNG KHI SỬA CODE:
- Nếu tôi nói "viết toàn bộ code", hãy đưa TOÀN BỘ FILE để tôi copy đè.
- Không bắt tôi tự tìm rồi thay từng đoạn.
- Không đổi tên youtobe.py.
- Không đổi namespace:
  com.example.audio
- Không đổi applicationId:
  com.example.audio
- Không tự ý đổi kiến trúc sang server.
- Không dùng server trung gian.
- Giữ Chaquopy + yt-dlp.
- Giữ Media3 + ExoPlayer + MediaSession.
- Tôi thích code có thể copy trực tiếp.
- Khi đưa code, dùng code fence bình thường.
- Không dùng writing block cho code.


KHI VUỐT APP KHỎI RECENT APPS:

YÊU CẦU:
Khi vuốt app "Audio" khỏi màn hình Recent Apps:
- Dừng phát nhạc.
- Dừng PlaybackService.
- Xóa media đang phát.
- Notification biến mất.
- Không tiếp tục chạy nền.

Đã thêm ý tưởng trong PlaybackService:

override fun onTaskRemoved(rootIntent: Intent?) {
player.stop()
player.clearMediaItems()

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
}

onDestroy():
mediaSession.release()
player.release()
super.onDestroy()

Mục tiêu là app bị vuốt khỏi Recent Apps thì toàn bộ playback/service cũng đóng.


ANDROID MANIFEST:

Hiện có:

<?xml version="1.0" encoding="utf-8"?>

<manifest
xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission
        android:name="android.permission.INTERNET" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE" />

    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:allowBackup="true"
        android:label="Audio"
        android:supportsRtl="true"
        android:theme="@style/Theme.Audio">

        <activity
            android:name=".MainActivity"
            android:exported="true">

            <intent-filter>

                <action
                    android:name="android.intent.action.MAIN" />

                <category
                    android:name="android.intent.category.LAUNCHER" />

            </intent-filter>

        </activity>

        <service
            android:name=".PlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">

            <intent-filter>

                <action
                    android:name="androidx.media3.session.MediaSessionService" />

                <action
                    android:name="android.media.browse.MediaBrowserService" />

            </intent-filter>

        </service>

    </application>

</manifest>


THIẾT BỊ TEST:
- Samsung Galaxy M20.
- Android Studio trên Windows.
- Có lúc Android Studio báo:
  Couldn't terminate the existing process for com.example.audio.
  Device is offline.

PowerShell trước đó báo:
adb : The term 'adb' is not recognized...

ADB thực tế nằm ở:
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

Có thể chạy:

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices

Hoặc:

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" kill-server
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" start-server
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices

Hiện tại ADB/device connection chưa được xác nhận hoàn toàn.


MỤC TIÊU CUỐI CÙNG:

1. Nhập link YouTube.
2. Bấm "Lấy URL".
3. Chaquopy chạy yt-dlp trực tiếp trên điện thoại.
4. Lấy direct audio URL nhanh nhất có thể.
5. Không cần server.
6. ExoPlayer phát trực tiếp URL.
7. Hiển thị title trên notification.
8. Notification chỉ có Play/Pause.
9. Notification có thanh tiến trình để kéo tua.
10. Trong app cũng có slider tua.
11. Phát background/lock screen.
12. Vuốt app khỏi Recent Apps → dừng toàn bộ app/service/playback và notification biến mất.
13. Link thường phải tua bình thường.
14. Không hiển thị raw media URL cho người dùng.

KHI TIẾP TỤC:
- Đọc toàn bộ note này trước.
- Nếu tôi yêu cầu sửa code, đưa full file hoàn chỉnh để copy.
- Không hỏi lại những thông tin đã có trong note.
- Nếu cần kiểm tra API Media3 1.11.1 hoặc hành vi notification Android/Samsung thì kiểm tra tài liệu hiện tại trước khi khẳng định.