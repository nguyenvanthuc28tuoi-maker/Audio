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