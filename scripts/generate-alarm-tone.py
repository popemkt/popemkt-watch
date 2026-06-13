#!/usr/bin/env python3
"""Generates res/raw/watchcal_alarm.wav — the bundled alert tone.

Design (specs/01-architecture.md decision record): a *gentle, sustained*
chime rather than a piercing beep. A warm rising arpeggio (A4–C#5–E5–A5)
is struck as soft bells — quick attack, long exponential decay — and the
phrase repeats calmly to fill ~10 s. Pure additive sines (no soft-clip
drive) keep it easy on the ears; the length carries attention instead of
loudness. Played once per fire as the notification channel sound.

Deterministic: rerunning produces a byte-identical file.
"""

import math
import struct
import wave
from pathlib import Path

RATE = 44100
TARGET_MS = 10_000
PHRASE_MS = 2600          # one rising arpeggio + breathing room, then repeat
NOTE_SPACING_MS = 550     # gap between strikes within a phrase
ARPEGGIO_HZ = [440.0, 554.365, 659.255, 880.0]  # A major: A4 C#5 E5 A5
DECAY_TAU_S = 0.55        # bell-like exponential tail
ATTACK_MS = 12            # soft, click-free onset
AMP = 0.55                # per-note level before normalisation
SHIMMER = 0.12            # 2nd-partial weight for warmth
PEAK = 0.7                # final normalisation ceiling — gentle, never clipped

OUT = Path(__file__).resolve().parent.parent / "apps/watchcal/src/main/res/raw/watchcal_alarm.wav"


def add_bell(buf: list[float], start: int, freq: float) -> None:
    """Sum one soft bell strike into the float buffer at sample offset `start`."""
    attack = RATE * ATTACK_MS // 1000
    length = int(RATE * DECAY_TAU_S * 6)  # tail out to ~e^-6 (inaudible)
    for i in range(length):
        pos = start + i
        if pos >= len(buf):
            break
        t = i / RATE
        tone = math.sin(2 * math.pi * freq * t) + SHIMMER * math.sin(4 * math.pi * freq * t)
        env = math.exp(-t / DECAY_TAU_S) * min(1.0, i / attack)
        buf[pos] += AMP * tone * env


def main() -> None:
    total = RATE * TARGET_MS // 1000
    buf = [0.0] * total

    phrase = 0
    while True:
        base = phrase * RATE * PHRASE_MS // 1000
        if base >= total:
            break
        for n, freq in enumerate(ARPEGGIO_HZ):
            add_bell(buf, base + n * RATE * NOTE_SPACING_MS // 1000, freq)
        phrase += 1

    peak = max(abs(s) for s in buf) or 1.0
    scale = PEAK / peak
    pcm = [int(32767 * s * scale) for s in buf]

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(struct.pack(f"<{len(pcm)}h", *pcm))
    print(f"wrote {OUT} ({len(pcm) / RATE:.2f}s)")


if __name__ == "__main__":
    main()
