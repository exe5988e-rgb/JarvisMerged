#!/usr/bin/env python3
"""
elevenlabs_tts.py  —  ElevenLabs TTS with multi-key rotation
=============================================================
Runs on Phone A (Termux).
Called by Android app via HTTP (port 8892) OR directly by agent_server
when Jarvis wants to speak a result.

Multiple API keys rotate on quota exhaustion (429 / 401).
Audio played via termux-media-player or mpv.

Endpoint:
  POST /speak  { "text": "...", "voice_id": "..." }
  GET  /voices List available voices
  GET  /health
"""

import os
import json
import queue
import shutil
import tempfile
import threading
import subprocess
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 8892

# ---------------------------------------------------------------------------
# Key pool — add as many as you have
# ---------------------------------------------------------------------------
# Keys loaded from env or config file at ~/workflows/.elevenlabs_keys
# Format in file: one key per line

_KEY_FILE = os.path.expanduser("~/workflows/.elevenlabs_keys")

def _load_keys() -> list:
    keys = []
    # From env
    env_key = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if env_key:
        keys.append(env_key)
    # From file
    if os.path.isfile(_KEY_FILE):
        with open(_KEY_FILE) as f:
            for line in f:
                k = line.strip()
                if k and not k.startswith("#") and k not in keys:
                    keys.append(k)
    return keys

_keys: list = []
_key_index = 0
_key_lock  = threading.Lock()

def _get_key() -> str:
    global _keys
    if not _keys:
        _keys = _load_keys()
    if not _keys:
        raise RuntimeError("No ElevenLabs API keys configured.\n"
                           f"Add keys to {_KEY_FILE} (one per line)")
    with _key_lock:
        return _keys[_key_index % len(_keys)]

def _rotate_key():
    global _key_index
    with _key_lock:
        _key_index += 1
        new = _keys[_key_index % len(_keys)] if _keys else None
    print(f"[tts] Rotated to key index {_key_index % len(_keys)}")
    return new

# ---------------------------------------------------------------------------
# Default voice — Jarvis custom voice
# ---------------------------------------------------------------------------

DEFAULT_VOICE    = "lNiTyQyEeDoFcsYb4RUT"   # ← Jarvis voice (was Adam pNInz6obpgDQGcFmaJgB)
DEFAULT_MODEL    = "eleven_turbo_v2"
ELEVENLABS_BASE  = "https://api.elevenlabs.io/v1"

# ---------------------------------------------------------------------------
# TTS core
# ---------------------------------------------------------------------------

def _tts_request(text: str, voice_id: str) -> bytes:
    """Returns raw MP3 bytes. Rotates key on 429/401."""
    max_attempts = max(len(_keys) if _keys else 1, 1) + 1
    for attempt in range(max_attempts):
        key = _get_key()
        url = f"{ELEVENLABS_BASE}/text-to-speech/{voice_id}"
        payload = json.dumps({
            "text": text,
            "model_id": DEFAULT_MODEL,
            "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75,
                "style": 0.2,
                "use_speaker_boost": True
            }
        }).encode()
        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "xi-api-key":   key,
                "Content-Type": "application/json",
                "Accept":       "audio/mpeg",
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            if e.code in (429, 401) and len(_keys) > 1:
                print(f"[tts] Key quota/auth error ({e.code}), rotating...")
                _rotate_key()
                continue
            raise
    raise RuntimeError("All ElevenLabs API keys exhausted")


def _play_audio(mp3_bytes: bytes):
    """Play MP3 bytes using available player."""
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as f:
        f.write(mp3_bytes)
        path = f.name
    try:
        # Try termux-media-player first (works on Termux without root)
        if shutil.which("termux-media-player"):
            subprocess.run(["termux-media-player", "play", path],
                           check=True, timeout=60)
        elif shutil.which("mpv"):
            subprocess.run(["mpv", "--no-video", path],
                           check=True, timeout=60)
        elif shutil.which("ffplay"):
            subprocess.run(["ffplay", "-nodisp", "-autoexit", path],
                           check=True, timeout=60)
        else:
            print(f"[tts] No audio player found. Audio saved: {path}")
            return
    finally:
        try:
            os.unlink(path)
        except Exception:
            pass


# Speech queue — prevents concurrent TTS calls
_speech_queue: "queue.Queue[tuple]" = queue.Queue()

def _speech_worker():
    while True:
        text, voice_id = _speech_queue.get()
        try:
            print(f"[tts] Speaking ({voice_id}): {text[:60]!r}")
            mp3 = _tts_request(text, voice_id)
            _play_audio(mp3)
        except Exception as e:
            print(f"[tts] Error: {e}")
        finally:
            _speech_queue.task_done()

_worker = threading.Thread(target=_speech_worker, daemon=True)
_worker.start()


def speak(text: str, voice_id: str = DEFAULT_VOICE, block: bool = False):
    """Queue text for speaking. Non-blocking by default."""
    _speech_queue.put((text, voice_id))
    if block:
        _speech_queue.join()


# ---------------------------------------------------------------------------
# HTTP handler
# ---------------------------------------------------------------------------

class TTSHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        pass

    def _json(self, code: int, data: dict):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        n = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(n)) if n else {}

    def do_GET(self):
        if self.path == "/health":
            loaded = _load_keys()
            self._json(200, {"ok": True, "keys": len(loaded)})
        elif self.path == "/voices":
            self._json(200, {"voices": [
                {"id": "lNiTyQyEeDoFcsYb4RUT", "name": "Jarvis"},   # ← default first
                {"id": "pNInz6obpgDQGcFmaJgB", "name": "Adam"},
                {"id": "EXAVITQu4vr4xnSDxMaL", "name": "Sarah"},
                {"id": "onwK4e9ZLuTAKqWW03F9", "name": "Daniel"},
                {"id": "XB0fDUnXU5powFXDhCwa", "name": "Charlotte"},
                {"id": "N2lVS1w4EtoT3dr4eOWO", "name": "Callum"},
            ]})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path == "/speak":
            b = self._body()
            text     = b.get("text", "").strip()
            voice_id = b.get("voice_id", DEFAULT_VOICE)
            if not text:
                self._json(400, {"error": "text required"})
                return
            speak(text, voice_id)
            self._json(200, {"ok": True, "queued": text[:60]})
        else:
            self._json(404, {"error": "not found"})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


if __name__ == "__main__":
    _keys = _load_keys()
    print(f"[tts] Loaded {len(_keys)} API key(s)")
    print(f"[tts] Default voice: {DEFAULT_VOICE} (Jarvis)")
    if not _keys:
        print(f"[tts] WARNING: No keys found. Add to {_KEY_FILE}")
    server = HTTPServer(("0.0.0.0", PORT), TTSHandler)
    print(f"[tts] Listening on 0.0.0.0:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("[tts] Stopped")
