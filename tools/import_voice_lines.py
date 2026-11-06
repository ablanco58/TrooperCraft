#!/usr/bin/env python3
"""Split a local recording at pauses and prepare a Minecraft voice bank (requires ffmpeg)."""

import argparse
import json
import math
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tropercraft-mod"


def run(*arguments):
    return subprocess.run(arguments, check=True, capture_output=True, text=True)


def indices(value):
    return {int(number) for number in value.split(",") if number.strip()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", type=Path)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--author", default="")
    parser.add_argument("--combat-clips", default="")
    parser.add_argument("--shared-clips", default="")
    parser.add_argument("--silence-seconds", type=float, default=0.6)
    parser.add_argument("--noise-db", type=float, default=-35)
    args = parser.parse_args()
    if not args.audio.is_file():
        parser.error("El audio de entrada no existe.")
    duration = float(run("ffprobe", "-v", "error", "-show_entries", "format=duration",
                         "-of", "default=noprint_wrappers=1:nokey=1", str(args.audio)).stdout)
    detection = run("ffmpeg", "-hide_banner", "-i", str(args.audio), "-af",
                    f"silencedetect=noise={args.noise_db}dB:d={args.silence_seconds}", "-f", "null", "-")
    active_start = 0.0
    segments = []
    for event, value in re.findall(r"silence_(start|end): ([\d.]+)", detection.stderr):
        time = min(float(value), duration)
        if event == "start":
            if time - active_start >= 0.2:
                segments.append((max(0, active_start - 0.08), min(duration, time + 0.12)))
        else:
            active_start = time
    if duration - active_start >= 0.2:
        # A terminal silence_start was already added above; only append genuine trailing audio.
        if not re.search(r"silence_end: [\d.]+ \| silence_duration: [\d.]+\s*$", detection.stderr):
            segments.append((max(0, active_start - 0.08), duration))
    if not segments:
        raise SystemExit("No se detectaron frases. Ajusta el umbral de silencio.")
    combat, shared = indices(args.combat_clips), indices(args.shared_clips)
    if (combat | shared) - set(range(1, len(segments) + 1)):
        raise SystemExit("Un índice de frase no existe en el audio detectado.")
    sounds_path = ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text())
    sounds = {key: value for key, value in sounds.items() if not key.startswith("trooper_voice_")}
    voices_dir = ASSETS / "sounds/voices"
    voices_dir.mkdir(parents=True, exist_ok=True)
    clips = []
    for index, (start, end) in enumerate(segments, 1):
        name = f"trooper_voice_{index:03d}"
        output = voices_dir / f"{name}.ogg"
        length = end - start
        # Downmix first, level dialogue consistently, and apply short fades to prevent clicks.
        filters = ("loudnorm=I=-18:TP=-2:LRA=7,"
                   f"afade=t=in:d=0.008,afade=t=out:st={max(0, length - 0.025):.6f}:d=0.025")
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{start:.6f}",
            "-i", str(args.audio), "-t", f"{length:.6f}", "-vn", "-ac", "1", "-ar", "44100",
            "-af", filters, "-c:a", "libvorbis", "-q:a", "5", str(output))
        mood = "both" if index in shared or not combat else "combat" if index in combat else "patrol"
        clips.append({"id": name, "startSeconds": round(start, 4), "endSeconds": round(end, 4),
                      "durationTicks": math.ceil(length * 20), "mood": mood})
        sounds[name] = {"subtitle": "subtitles.tropercraft-mod.trooper_voice",
                        "sounds": [{"name": f"tropercraft-mod:voices/{name}", "volume": 1.0,
                                    "attenuation_distance": 48}]}
        print(f"{name}: {start:6.2f}–{end:6.2f} s ({length:.2f} s), {mood}")
    manifest = {"source": {"url": args.source_url, "title": args.title, "author": args.author,
                            "durationSeconds": duration},
                "processing": {"silenceSeconds": args.silence_seconds, "noiseDb": args.noise_db,
                               "format": "Ogg Vorbis, mono, 44100 Hz", "loudnessLufs": -18},
                "clips": clips}
    (ASSETS / "voice_lines.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n")
    print(f"Preparados {len(clips)} clips de voz; no se incluye la grabación completa en el mod.")


if __name__ == "__main__":
    main()
