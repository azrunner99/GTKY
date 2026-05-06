"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type Mood = "calm" | "playful";
type PlayerState = "stopped" | "playing" | "paused";

const SCALE_CALM = ["C4", "D4", "E4", "G4", "A4", "C5", "D5", "E5", "G5"];
const SCALE_PLAYFUL = ["C5", "D5", "E5", "G5", "A5", "C6", "D6", "E6"];

function generateMelody(mood: Mood, length = 16): (string | null)[] {
  const scale = mood === "calm" ? SCALE_CALM : SCALE_PLAYFUL;
  const restChance = mood === "calm" ? 0.25 : 0.1;
  const melody: (string | null)[] = [];
  let idx = 1;

  for (let i = 0; i < length; i++) {
    if (Math.random() < restChance) {
      melody.push(null);
      continue;
    }
    const leap = Math.random() < 0.7 ? 1 : Math.floor(Math.random() * 3) + 1;
    const dir = Math.random() < 0.55 ? 1 : -1;
    idx = Math.max(0, Math.min(scale.length - 1, idx + dir * leap));
    melody.push(scale[idx]);
  }

  return melody;
}

export default function BabyTunesPlayer() {
  const [mood, setMood] = useState<Mood>("calm");
  const [playerState, setPlayerState] = useState<PlayerState>("stopped");
  const [melody, setMelody] = useState<(string | null)[]>([]);
  const [activeStep, setActiveStep] = useState<number>(-1);

  // All Tone.js objects live here — never in state
  const tone = useRef<{
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    mod: any;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    synth: any;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    reverb: any;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    sequence: any;
  } | null>(null);

  // Dynamically import Tone.js (avoids SSR issues with Web Audio API)
  useEffect(() => {
    import("tone").then((mod) => {
      tone.current = { mod, synth: null, reverb: null, sequence: null };
    });
    return () => teardown();
  }, []);

  // Seed a melody on mount
  useEffect(() => {
    setMelody(generateMelody("calm"));
  }, []);

  function teardown() {
    if (!tone.current) return;
    const { mod } = tone.current;
    tone.current.sequence?.dispose();
    tone.current.synth?.dispose();
    tone.current.reverb?.dispose();
    mod.getTransport().stop();
    tone.current.sequence = null;
    tone.current.synth = null;
    tone.current.reverb = null;
  }

  const handleStop = useCallback(() => {
    teardown();
    setPlayerState("stopped");
    setActiveStep(-1);
  }, []);

  const handleGenerate = useCallback(() => {
    handleStop();
    setMelody(generateMelody(mood));
  }, [mood, handleStop]);

  const handlePlay = useCallback(async () => {
    if (!tone.current || melody.length === 0) return;
    const { mod } = tone.current;

    await mod.start(); // unlock AudioContext on user gesture

    if (playerState === "paused") {
      mod.getTransport().start();
      setPlayerState("playing");
      return;
    }

    // Tear down any previous session
    teardown();

    const bpm = mood === "calm" ? 65 : 92;
    const transport = mod.getTransport();
    transport.bpm.value = bpm;

    const reverb = new mod.Reverb({
      decay: mood === "calm" ? 4 : 2,
      wet: 0.35,
    }).toDestination();
    await reverb.ready; // Reverb needs to generate its IR

    const synth = new mod.Synth({
      oscillator: { type: mood === "calm" ? "sine" : "triangle" },
      envelope: {
        attack: mood === "calm" ? 0.15 : 0.04,
        decay: 0.2,
        sustain: 0.5,
        release: mood === "calm" ? 2 : 1,
      },
      volume: -6,
    }).connect(reverb);

    const interval = mood === "calm" ? "4n" : "8n";
    const duration = mood === "calm" ? "4n" : "8n";

    let step = 0;
    const draw = mod.getDraw();

    const sequence = new mod.Sequence(
      (time: number, note: string | null) => {
        if (note) synth.triggerAttackRelease(note, duration, time);
        const captured = step % melody.length;
        draw.schedule(() => setActiveStep(captured), time);
        step++;
      },
      melody,
      interval
    );

    sequence.loop = true;
    tone.current.synth = synth;
    tone.current.reverb = reverb;
    tone.current.sequence = sequence;

    transport.start();
    sequence.start(0);

    setPlayerState("playing");
  }, [melody, mood, playerState]);

  const handlePause = useCallback(() => {
    if (!tone.current) return;
    tone.current.mod.getTransport().pause();
    setPlayerState("paused");
  }, []);

  const moodChanged = useCallback(
    (next: Mood) => {
      if (playerState !== "stopped") handleStop();
      setMood(next);
      setMelody(generateMelody(next));
    },
    [playerState, handleStop]
  );

  const noteLabel = (note: string | null) =>
    note ? note.replace(/\d/, "") : "·";

  const barHeights = [40, 65, 50, 80, 55, 70, 45, 60];

  return (
    <div className="flex flex-col items-center gap-8 w-full max-w-md">
      {/* Title */}
      <div className="text-center">
        <h1 className="text-5xl font-bold text-purple-700 tracking-tight">
          Baby Tunes
        </h1>
        <p className="mt-2 text-purple-400 text-sm tracking-wide uppercase">
          unique music for little ones
        </p>
      </div>

      {/* Visualiser card */}
      <div className="relative w-full bg-white rounded-3xl shadow-xl p-8 flex flex-col items-center gap-6 border border-purple-100">
        {/* Animated bars */}
        <div className="flex items-end gap-2 h-24">
          {barHeights.map((h, i) => (
            <div
              key={i}
              className="w-5 rounded-full transition-all"
              style={{
                height: playerState === "playing" ? `${h}%` : "20%",
                backgroundColor:
                  playerState === "playing"
                    ? `hsl(${270 + i * 12}, 70%, 65%)`
                    : "#e9d5ff",
                animation:
                  playerState === "playing"
                    ? `bounce-bar ${0.4 + i * 0.07}s ease-in-out infinite alternate`
                    : "none",
              }}
            />
          ))}
        </div>

        {/* Mood selector */}
        <div className="flex gap-3">
          {(["calm", "playful"] as Mood[]).map((m) => (
            <button
              key={m}
              onClick={() => moodChanged(m)}
              className={`px-5 py-2 rounded-full text-sm font-semibold transition-all ${
                mood === m
                  ? "bg-purple-600 text-white shadow-md"
                  : "bg-purple-100 text-purple-600 hover:bg-purple-200"
              }`}
            >
              {m === "calm" ? "🌙 Calm" : "☀️ Playful"}
            </button>
          ))}
        </div>

        {/* Controls */}
        <div className="flex gap-3">
          <button
            onClick={handleGenerate}
            className="px-4 py-2 rounded-full text-sm font-semibold bg-pink-100 text-pink-600 hover:bg-pink-200 transition-all"
          >
            ✨ Generate
          </button>

          {playerState === "playing" ? (
            <button
              onClick={handlePause}
              className="px-6 py-2 rounded-full text-sm font-semibold bg-purple-600 text-white hover:bg-purple-700 transition-all shadow"
            >
              ⏸ Pause
            </button>
          ) : (
            <button
              onClick={handlePlay}
              className="px-6 py-2 rounded-full text-sm font-semibold bg-purple-600 text-white hover:bg-purple-700 transition-all shadow"
            >
              ▶ Play
            </button>
          )}

          {playerState !== "stopped" && (
            <button
              onClick={handleStop}
              className="px-4 py-2 rounded-full text-sm font-semibold bg-gray-100 text-gray-500 hover:bg-gray-200 transition-all"
            >
              ■ Stop
            </button>
          )}
        </div>

        {/* Note pills */}
        {melody.length > 0 && (
          <div className="flex flex-wrap gap-1.5 justify-center">
            {melody.map((note, i) => (
              <span
                key={i}
                className={`w-8 h-8 flex items-center justify-center rounded-full text-xs font-bold transition-all ${
                  activeStep === i
                    ? "bg-purple-600 text-white scale-110"
                    : "bg-purple-100 text-purple-500"
                }`}
              >
                {noteLabel(note)}
              </span>
            ))}
          </div>
        )}
      </div>

      <p className="text-purple-300 text-xs text-center">
        Every melody is uniquely generated — no two tunes are the same
      </p>
    </div>
  );
}
