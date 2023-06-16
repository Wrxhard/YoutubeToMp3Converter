import yt_dlp
import json


# Function to get video information
def get_video_info(video_url):
    with yt_dlp.YoutubeDL() as ydl:
        info = ydl.extract_info(video_url, download=False)

    return json.dumps(ydl.sanitize_info(info))

