# Architecture — NeuronPilot Meeting Notes App (G720 AI Box)

<!-- Read on demand by agents (not auto-loaded). Keep facts, not tutorials. -->

## 1. Modules / Layers

### A. Presentation & Application Layer (`com.bhs.meetingnotes.*`)
- **Activities (6 screens, 1024x600 Landscape)**:
  - [`MeetingNotesActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingNotesActivity.kt): Meeting list, status badges, language filter, FAB recording trigger.
  - [`MeetingRecordActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingRecordActivity.kt): Split-panel multi-segment recording, RMS waveform animation, pause/resume/new segment/bookmark controls.
  - [`MeetingSelectSegmentsActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingSelectSegmentsActivity.kt): Audio segment filtering & AI processing time estimator.
  - [`MeetingProcessingActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingProcessingActivity.kt): 4-step pipeline timeline, dual preview (raw vs cleaned), execution monitor.
  - [`MeetingResultActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingResultActivity.kt): 3-tab review (Transcript, Summary, Action Items), report export, TTS.
  - [`MeetingSettingsActivity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/MeetingSettingsActivity.kt): Audio params, AI models, prompts, temperature (max 0.5), storage paths.
- **Adapters & UI Components**:
  - [`MeetingAdapter`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/adapter/MeetingAdapter.kt), [`RecordSegmentAdapter`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/adapter/RecordSegmentAdapter.kt), [`SelectSegmentAdapter`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/adapter/SelectSegmentAdapter.kt), [`WaveformView`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/util/WaveformView.kt).

### B. Persistence Layer (`com.bhs.meetingnotes.db.*`)
- **Database**: [`MeetingDatabase`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/MeetingDatabase.kt) (Room, SQLite).
- **Entities**: [`MeetingEntity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/MeetingEntity.kt) (1 meeting), [`SegmentEntity`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/SegmentEntity.kt) (N audio chunks).
- **Relation**: [`MeetingWithSegments`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/MeetingWithSegments.kt) (`@Relation(parentColumn = "id", entityColumn = "meeting_id")`).
- **DAOs**: [`MeetingDao`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/MeetingDao.kt), [`SegmentDao`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/db/SegmentDao.kt).

### C. Audio Capture & Recording (`com.bhs.meetingnotes.audio.*`, `com.mediatek.neuropilot.jnidemo.aibox.ai.*`)
- [`I2SAudioRecorder`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/java/com/bhs/meetingnotes/audio/I2SAudioRecorder.kt): 16kHz / 16-bit / Mono PCM, writes WAV with RIFF header.
- Threading: Background recording thread set to `Process.THREAD_PRIORITY_URGENT_AUDIO`.
- Service: Foreground Service (`type=microphone`) ensures background recording without OS kill.

### D. AI Orchestration Pipeline (`com.bhs.meetingnotes.ai.MeetingAiPipeline`)
- Coordinates the pipeline:
  1. Reads selected WAV segments from DB.
  2. Runs ASR (PhoWhisper loopback server or whisper.cpp).
  3. Applies deterministic dictionary/alias correction (preserving English terms & names).
  4. Runs LLM Map-Reduce for Summary & Action items (Qwen2.5 3B).
  5. Validates JSON output and updates `MeetingEntity` in DB.

### E. Native C++ & NeuroPilot NPU Layer (`app/src/main/cpp/*`, `com.mediatek.neuropilot.jnidemo.*`)
- Native JNI: [`nn_sample.cpp`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/cpp/nn_sample.cpp), [`simple_model.cpp`](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/app/src/main/cpp/simple_model.cpp).
- Pre-built Libraries: `libmtk_llm.so`, `libtokenizer.so`, `libyaml-cpp.so`.
- Hardware: MediaTek Genio 720 MDLA 5.3 NPU (9 TOPS) + big.LITTLE CPU (2x A78 + 6x A55).

---

## 2. Data Flow

```
[I2S Mic Hardware]
       │
       ▼
[I2SAudioRecorder / AudioCapture] (THREAD_PRIORITY_URGENT_AUDIO)
       │ (Ring buffer -> WAV Segment files on disk)
       ▼
[MeetingRecordActivity] ──> [SegmentEntity in MeetingDatabase]
       │
       ▼
[MeetingSelectSegmentsActivity] (User chooses valid segments, filters noise/breaks)
       │
       ▼
[MeetingAiPipeline]
  ├─ Step 1: ASR Engine (PhoWhisper @ 127.0.0.1:8080 or whisper.cpp CPU)
  │    └─ Output: raw_transcript (stored immutable for audit)
  ├─ Step 2: Text Sanitation & Glossary Biasing (Deterministic regex + alias table)
  │    └─ Output: cleaned_transcript (avoids costly 24-minute full LLM text regeneration)
  ├─ Step 3: LLM Map (Qwen2.5 3B via llama.cpp CPU or NeuroPilot NPU DLA)
  │    └─ Extracts structured JSON chunks (key points, decisions, actions)
  ├─ Step 4: LLM Reduce & Validator
  │    └─ Validates attendees, dates, non-hallucinated action items
  ▼
[MeetingDatabase] ──> [MeetingResultActivity] (Tabs: Transcript | Summary | Actions)
```

---

## 3. Architecture Plans (Three Execution Pathways)

| Pathway | ASR Engine | LLM Engine | Requirement | Processing Time (60m meeting) |
|---|---|---|---|---|
| **Plan B (Baseline)** | PhoWhisper CPU (whisper.cpp) | Qwen2.5 3B CPU (llama.cpp) | Zero NDA / Fully open-source | 25–36 min (or ~15 min with background ASR) |
| **Plan C (Hybrid)** | PhoWhisper CPU | Qwen2.5 3B NPU (Prebuilt DLA) | Prebuilt MediaTek DLA package | 16–27 min |
| **Plan A (Full NPU)** | PhoWhisper NPU (converted DLA) | Qwen2.5 3B NPU (Prebuilt DLA) | GAI Toolkit under MediaTek NDA | ~8 min |

> **Invariable Rule (C13)**: Plan B (CPU pure fallback) MUST ALWAYS remain operational. Plan C and Plan A are acceleration layers built upon Plan B.

---

## 4. Hard Constraints (C1 - C13) & Invariants (I1 - I10)

- **C1 (100% Offline)**: Strictly zero internet calls, zero cloud telemetry. Air-gapped on-device.
- **C2 (Platform)**: Genio 720 (8GB RAM, Android 15, 1024x600 Landscape, `arm64-v8a`).
- **C3 (Audio)**: 16 kHz, 16-bit, Mono PCM, WAV chunks.
- **C4 (Single Heavy Model)**: At most 1 heavy model loaded in RAM at a time (peak RAM ≤ 5.5 GB).
- **C5 (Recording Priority)**: Audio thread priority = `THREAD_PRIORITY_URGENT_AUDIO`, Foreground Service.
- **C10 (No Full-Text Regeneration)**: LLM decode is bounded at ~10.6 tok/s on NPU (~4-8 on CPU). Never regenerate 15k tokens of text with LLM; use deterministic rules + LLM JSON map/reduce.
- **I1 - I3 (Audio Continuity)**: PAUSE does not write WAV (avoids silent gaps). Header healed on app restart.
- **I7 - I10 (Truthfulness)**: Glossary is ground truth. Raw transcript is always retained and never overwritten.

---

## 5. External Services & Secrets

- **Zero Cloud Services**: No remote APIs, no secret keys, no telemetry tokens.
- **Local Services**: Loopback HTTP `http://127.0.0.1:8080/inference` (local on-device PhoWhisper server process).

---

## 6. Known Pitfalls & Defenses

1. **JNI Concurrency Crash**: Native `SimpleModel` / `libmtk_llm.so` is stateful and non-reentrant. Concurrent calls to `computeStreaming` on the same handle crash the process. *Defense*: Guard all LLM invocations with a single-flight mutex / FIFO queue.
2. **Code-Switching WER (Vietnamese + English terms)**: PhoWhisper zero-shot has ~62.6% CS-WER on English technical jargon. *Defense*: Pre-seed glossary dictionary, apply regex alias replacement, and provide LoRA fine-tuning roadmap.
3. **Android 15 Scoped Storage**: `/sdcard/MeetingNotes` will throw `SecurityException` on Android 15 unless using `context.getExternalFilesDir(null)` or platform-signed BSP permissions.
4. **Thermal Throttling**: Sustained NPU/CPU load triggers throttling at 65°C / 75°C. *Defense*: Thermal zone polling via `BENCHMARKS.md` Gate G4, batch processing in chunks with brief cool-downs.
