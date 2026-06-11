#!/usr/bin/env python3
"""Generates res/raw/watchcal_alarm.wav — the bundled alarm tone.

Design (specs/01-architecture.md decision record): urgent dual-tone beep
pattern, near-full-scale with soft clipping for perceived loudness on tiny
watch speakers. Four 120 ms beeps (880 Hz + 1760 Hz), 80 ms gaps, 400 ms
pause, then the player loops the file.

Deterministic: rerunning produces a byte-identical file.
"""

import math
import struct
import wave
from pathlib import Path

RATE = 44100
BEEP_MS, GAP_MS, PAUSE_MS, BEEPS = 120, 80, 400, 4
LOW_HZ, HIGH_HZ = 880.0, 1760.0
DRIVE = 2.5  # soft-clip drive: squarer wave = louder perception

OUT = Path(__file__).resolve().parent.parent / "apps/watchcal/src/main/res/raw/watchcal_alarm.wav"


def samples_for(ms: int) -> int:
    return RATE * ms // 1000


def beep() -> list[int]:
    n = samples_for(BEEP_MS)
    fade = samples_for(5)  # 5 ms ramp kills click artifacts
    out = []
    for i in range(n):
        t = i / RATE
        s = 0.6 * math.sin(2 * math.pi * LOW_HZ * t) + 0.4 * math.sin(2 * math.pi * HIGH_HZ * t)
        s = math.tanh(DRIVE * s) / math.tanh(DRIVE)
        env = min(1.0, i / fade, (n - 1 - i) / fade)
        out.append(int(32000 * s * env))
    return out


def silence(ms: int) -> list[int]:
    return [0] * samples_for(ms)


def main() -> None:
    pcm: list[int] = []
    for i in range(BEEPS):
        pcm += beep()
        pcm += silence(GAP_MS if i < BEEPS - 1 else PAUSE_MS)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(struct.pack(f"<{len(pcm)}h", *pcm))
    print(f"wrote {OUT} ({len(pcm) / RATE:.2f}s)")


if __name__ == "__main__":
    main()
