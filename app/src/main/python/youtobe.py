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
            raise Exception("Không lấy được audio URL")

        return json.dumps(
            {
                "title": title,
                "url": media_url,
                "duration": duration
            },
            ensure_ascii=False
        )


def build_channel_url(url, mode):
    url = url.strip().rstrip("/")

    suffixes = [
        "/videos",
        "/streams",
        "/featured",
        "/shorts",
        "/live"
    ]

    for suffix in suffixes:
        if url.endswith(suffix):
            url = url[:-len(suffix)]
            url = url.rstrip("/")

    if mode == "streams":
        return url + "/streams"

    return url + "/videos"


def get_channel_batch(
    url,
    mode,
    start,
    batch_size
):
    channel_url = build_channel_url(url, mode)

    first_item = start + 1
    last_item = start + batch_size

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,

        # Chỉ lấy metadata danh sách.
        "extract_flat": True,

        "noplaylist": False,
        "cachedir": False,

        # Chỉ lấy đúng batch cần thiết.
        "playlist_items": f"{first_item}-{last_item}",

        # Tối ưu tốc độ.
        "ignoreerrors": True,
        "lazy_playlist": True,
    }

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(
            channel_url,
            download=False
        )

        channel_name = (
            info.get("channel")
            or info.get("uploader")
            or info.get("title")
            or "YouTube"
        )

        entries = info.get("entries") or []

        videos = []

        for entry in entries:
            if not entry:
                continue

            video_id = entry.get("id")
            title = entry.get("title") or "Không có tiêu đề"

            webpage_url = entry.get("webpage_url")

            if not webpage_url and video_id:
                webpage_url = (
                    "https://www.youtube.com/watch?v="
                    + video_id
                )

            if not video_id:
                continue

            if not webpage_url:
                continue

            videos.append(
                {
                    "id": video_id,
                    "title": title,
                    "url": webpage_url
                }
            )

        playlist_count = info.get("playlist_count")

        if playlist_count is not None:
            try:
                playlist_count = int(playlist_count)
            except Exception:
                playlist_count = None

        if playlist_count is not None:
            has_more = (
                start + len(videos)
                < playlist_count
            )
        else:
            has_more = (
                len(videos) >= batch_size
            )

        return json.dumps(
            {
                "success": True,
                "channel": channel_name,
                "mode": mode,
                "start": start,
                "count": len(videos),
                "total": playlist_count,
                "has_more": has_more,
                "videos": videos
            },
            ensure_ascii=False
        )