#!/usr/bin/env python3
"""Create original mono science-fiction effects using Python and ffmpeg (no recordings)."""

import math
from pathlib import Path
import random
import shutil
import struct
import subprocess
import tempfile
import wave

SAMPLE_RATE = 44100
SOUNDS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tropercraft-mod/sounds"


def synthesize(name, duration, kind, seed):
    rng = random.Random(seed)
    frames = bytearray()
    phase = 0.0
    previous = 0.0
    for index in range(int(duration * SAMPLE_RATE)):
        t = index / SAMPLE_RATE
        progress = t / duration
        noise = rng.uniform(-1, 1)
        crackle = noise - previous * 0.85
        previous = noise
        if kind == "laser":
            frequency = 150 + 1900 * math.exp(-t * 16)
            phase += math.tau * frequency / SAMPLE_RATE
            value = (math.sin(phase + 2.2 * math.sin(phase * 0.5)) * 0.62 + crackle * 0.12)
            value *= math.exp(-t * 12)
        elif kind == "impact":
            phase += math.tau * (1000 - progress * 700) / SAMPLE_RATE
            value = (crackle * 0.5 + math.sin(phase) * 0.3) * math.exp(-t * 23)
        else:
            frequency = {"radio": 500, "hurt": 300, "death": 220}[kind]
            frequency *= 1 + 0.3 * math.sin(t * 22)
            if kind == "death":
                frequency *= 1 - progress * 0.7
            phase += math.tau * frequency / SAMPLE_RATE
            syllable = max(0, math.sin(t * (25 if kind == "radio" else 17)))
            carrier = math.sin(phase + 1.5 * math.sin(phase * 2))
            value = (carrier * syllable * 0.33 + crackle * 0.12)
            if t < 0.06 or duration - t < 0.06:
                value += math.sin(math.tau * 1800 * t) * 0.14
        fade = min(1, t / 0.005, (duration - t) / 0.025)
        sample = max(-1, min(1, value * fade * 0.85))
        frames.extend(struct.pack("<h", round(sample * 32767)))
    with tempfile.TemporaryDirectory(prefix="tropercraft-audio-") as temporary:
        wav = Path(temporary) / f"{name}.wav"
        with wave.open(str(wav), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(SAMPLE_RATE)
            output.writeframes(frames)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                        "-c:a", "libvorbis", "-q:a", "5", str(SOUNDS / f"{name}.ogg")], check=True)


if __name__ == "__main__":
    if not shutil.which("ffmpeg"):
        raise SystemExit("Instala ffmpeg para regenerar los sonidos; no es necesario para jugar ni compilar.")
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for index, (name, duration, kind) in enumerate([
        ("blaster_shoot_1", 0.32, "laser"), ("blaster_shoot_2", 0.29, "laser"),
        ("laser_impact", 0.18, "impact"), ("trooper_radio_1", 0.8, "radio"),
        ("trooper_radio_2", 1.1, "radio"), ("trooper_hurt", 0.3, "hurt"),
        ("trooper_death", 0.7, "death"),
    ]):
        synthesize(name, duration, kind, index + 1701)
        print(f"Generado: {name}.ogg")
