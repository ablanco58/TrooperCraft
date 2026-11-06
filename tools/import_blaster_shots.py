#!/usr/bin/env python3
"""Prepare isolated blaster shots from a local recording, without changing the voice bank."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/tropercraft-mod"


def run(*arguments):
    return subprocess.run(arguments, capture_output=True, text=True, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", type=Path)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--author", required=True)
    parser.add_argument("--start", type=float, default=0)
    parser.add_argument("--end", type=float)
    parser.add_argument("--silence-seconds", type=float, default=0.08)
    parser.add_argument("--noise-db", type=float, default=-35)
    args = parser.parse_args()
    duration = float(run("ffprobe", "-v", "error", "-show_entries", "format=duration",
                         "-of", "default=noprint_wrappers=1:nokey=1", str(args.audio)).stdout)
    end = duration if args.end is None else min(args.end, duration)
    if not 0 <= args.start < end:
        parser.error("El intervalo de entrada es inválido.")
    detection = run("ffmpeg", "-hide_banner", "-ss", str(args.start), "-i", str(args.audio),
                    "-t", str(end - args.start), "-af",
                    f"silencedetect=noise={args.noise_db}dB:d={args.silence_seconds}", "-f", "null", "-")
    segments = []
    active = 0.0
    silent = False
    for event, value in re.findall(r"silence_(start|end): ([\d.]+)", detection.stderr):
        point = min(float(value), end - args.start)
        if event == "start":
            if point - active >= 0.12:
                segments.append((max(args.start, args.start + active - 0.015),
                                 min(end, args.start + point + 0.03)))
            silent = True
        else:
            active = point
            silent = False
    if not silent and end - args.start - active >= 0.12:
        segments.append((max(args.start, args.start + active - 0.015), end))
    if not segments:
        raise SystemExit("No se detectaron disparos separados; ajusta el umbral o el intervalo.")
    folder = ASSETS / "sounds/blaster"
    folder.mkdir(parents=True, exist_ok=True)
    clips = []
    for index, (start, stop) in enumerate(segments, 1):
        name = f"blaster_shot_{index:03d}"
        length = stop - start
        level = run("ffmpeg", "-hide_banner", "-ss", str(start), "-i", str(args.audio), "-t", str(length),
                    "-af", "aformat=channel_layouts=mono,volumedetect", "-f", "null", "-")
        match = re.search(r"max_volume: ([-\d.]+) dB", level.stderr)
        if match is None:
            raise SystemExit(f"No se pudo medir el disparo {index}.")
        gain = min(12, -3 - float(match.group(1)))
        filters = (f"aformat=channel_layouts=mono,volume={gain:.2f}dB,afade=t=in:d=0.002,"
                   f"afade=t=out:st={max(0, length - 0.02):.6f}:d=0.02")
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", str(start),
            "-i", str(args.audio), "-t", str(length), "-vn", "-af", filters, "-ac", "1", "-ar", "44100",
            "-c:a", "libvorbis", "-q:a", "5", str(folder / f"{name}.ogg"))
        clips.append({"id": name, "startSeconds": round(start, 5), "endSeconds": round(stop, 5),
                      "durationSeconds": round(length, 5), "gainDb": round(gain, 2)})
        print(f"{name}: {start:.3f}–{stop:.3f} s; {length:.3f} s")
    manifest = {"source": {"url": args.source_url, "title": args.title, "author": args.author,
                            "durationSeconds": duration,
                            "decodedAudioSha256": hashlib.sha256(args.audio.read_bytes()).hexdigest()},
                "processing": {"inputStartSeconds": args.start, "inputEndSeconds": end,
                               "silenceSeconds": args.silence_seconds, "noiseDb": args.noise_db,
                               "targetPeakDb": -3, "format": "Ogg Vorbis, mono, 44100 Hz"},
                "clips": clips}
    (ASSETS / "blaster_shots.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    path = ASSETS / "sounds.json"
    sounds = json.loads(path.read_text())
    sounds["blaster_shoot"] = {"subtitle": "subtitles.tropercraft-mod.blaster_shoot",
                                "sounds": [f"tropercraft-mod:blaster/{clip['id']}" for clip in clips]}
    path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n")
    print(f"Integradas {len(clips)} variantes aisladas en blaster_shoot.")


if __name__ == "__main__":
    main()
