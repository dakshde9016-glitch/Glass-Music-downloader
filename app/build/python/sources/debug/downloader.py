import os
import yt_dlp

def download_audio(query, output_dir=None, bitrate="128", callback=None):
    if not output_dir:
        output_dir = "/storage/emulated/0/Download/Music"
        
    os.makedirs(output_dir, exist_ok=True)
    out_tmpl = os.path.join(output_dir, "%(title)s.%(ext)s")

    def progress_hook(d):
        if callback is None:
            return

        status = d.get('status', '')
        if status == 'downloading':
            total_bytes = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            percent = (downloaded / total_bytes * 100.0) if total_bytes > 0 else 0.0
            speed = d.get('speed', 0.0) or 0.0
            filename = d.get('filename', '')
            
            speed_str = f"{speed / 1024 / 1024:.2f} MB/s" if speed > 1024 * 1024 else f"{speed / 1024:.1f} KB/s"
            
            try:
                callback.onProgress("downloading", float(percent), speed_str, os.path.basename(filename))
            except Exception:
                pass

        elif status == 'finished':
            filename = d.get('filename', '')
            try:
                callback.onProgress("completed", 100.0, "0 KB/s", os.path.basename(filename))
            except Exception:
                pass

    # Normalize plain search terms to standard ytsearch1
    search_target = query.strip()
    if not (search_target.startswith("http://") or search_target.startswith("https://")):
        search_target = f"ytsearch1:{search_target} audio"

    ydl_opts = {
        'format': 'bestaudio[ext=m4a]/bestaudio/best',
        'outtmpl': out_tmpl,
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'writethumbnail': False,
        'noplaylist': True,
        'progress_hooks': [progress_hook]
    }

    try:
        if callback:
            callback.onProgress("queued", 0.0, "0 KB/s", "")

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(search_target, download=True)
            if info and 'entries' in info and len(info['entries']) > 0:
                info = info['entries'][0]
            
            title = info.get('title', query) if info else query
            filename = ydl.prepare_filename(info) if info else f"{title}.m4a"

            if callback:
                callback.onProgress("completed", 100.0, "0 KB/s", os.path.basename(filename))
                
            return {"success": True, "title": title, "filename": filename}
    except Exception as e:
        error_msg = str(e)
        if callback:
            callback.onError(error_msg)
        return {"success": False, "error": error_msg}
