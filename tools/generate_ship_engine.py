#!/usr/bin/env python3
"""Create an original, seamless heavy spaceship engine loop (Python + ffmpeg)."""

import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/tropercraft-mod/sounds/ship/star_destroyer_engine.ogg"
RATE = 22050
DURATION = 12


def main():
    rng = random.Random(103)
    # Every oscillator completes an integer number of cycles in twelve seconds.
    vents = [(rng.randrange(1400, 8000)/DURATION, rng.random()*math.tau) for _ in range(14)]
    samples = []
    for index in range(RATE*DURATION):
        t = index/RATE
        rotor = (.29*math.sin(math.tau*22.5*t) + .25*math.sin(math.tau*35*t)
                 + .20*math.sin(math.tau*47.5*t) + .14*math.sin(math.tau*70.5*t)
                 + .09*math.sin(math.tau*94.5*t) + .05*math.sin(math.tau*141*t))
        pulse = .80 + .12*math.sin(math.tau*.25*t) + .08*math.sin(math.tau*.5*t)
        airflow = sum(math.sin(math.tau*f*t+phase) for f,phase in vents)*.012
        machinery = .045*math.sin(math.tau*207*t)*(.5+.5*math.sin(math.tau*t/DURATION))
        samples.append(rotor*pulse + airflow + machinery)
    gain = .82/max(abs(sample) for sample in samples)
    with tempfile.TemporaryDirectory(prefix="tropercraft-ship-engine-") as temporary:
        wav = Path(temporary)/"engine.wav"
        with wave.open(str(wav), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(b"".join(struct.pack("<h", round(sample*gain*32767)) for sample in samples))
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                        "-c:a", "libvorbis", "-q:a", "5", str(OUTPUT)], check=True)
    print(f"Generated {OUTPUT.relative_to(ROOT)}: {DURATION}s mono engine loop")


if __name__ == "__main__":
    main()
