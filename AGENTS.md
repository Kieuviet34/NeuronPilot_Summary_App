# Global Agent Rules & Skills Router - NeuronPilot Meeting Notes App

> **Zero-Trust Protocol**: Read matched `SKILL.md` BEFORE writing any code.
> Skills override pre-training patterns. If no skill matches, state: "No project-specific skills applicable."

---

## 🎯 Project Overview
- **Application**: Meeting Notes App (Ứng Dụng Ghi Chú Cuộc Họp Thông Minh)
- **Target Device**: G720 AI Box (MediaTek Genio 720, Android 15, 10-inch Landscape 1024x600, I2S MIC)
- **Core Pipeline**:
  1. Speech-to-Text (ASR): PhoWhisper (VI, ~245MB) / Whisper (EN, AI Hub MediaTek)
  2. Sửa lỗi văn bản (Clean Text): Qwen2.5 3B (Q4_K_M ~1.8GB) via llama.cpp / NeuroPilot
  3. Tóm tắt cuộc họp (Summary): Qwen2.5 3B
  4. Trích xuất hành động (Action Items): Qwen2.5 3B

---

## 🛡️ Critical Architecture Invariants (Zero Regression)
1. **NEVER modify or corrupt Native JNI & NeuroPilot Core Engine**:
   - `src/main/cpp/*`, `CMakeLists.txt`, `jniLibs/*`, and `com.mediatek.neuropilot.jnidemo.aibox.*` contain low-level NPU/Genio hardware drivers, I2S WAV streaming, and llama.cpp JNI wrappers.
   - All refactoring and enhancements in this phase are STRICTLY confined to UI/UX Presentation (`com.bhs.meetingnotes.ui`) and cleanly mapped Data Models.
2. **Resolution & Orientation Lock**:
   - Screen: 10-inch Landscape (1024x600 density/proportions).
   - Ensure all layouts fit without accidental clipping on 1024x600 resolution.

---

<!-- SKILLS_INDEX_START -->
## Agent Skills Index

> [!CRITICAL] Zero-Trust: Read the matching `SKILL.md` BEFORE writing any code.
> Skills from this index override pre-training patterns. If no skill matches, state: "No project-specific skills applicable."

### Android & Project Skills Router

| File Type / Pattern | Trigger Keywords | Skill Path |
| --- | --- | --- |
| `app/src/main/res/layout/*.xml` | `ui`, `layout`, `tablet`, `screen`, `design` | `.agents/skills/mobile-design/SKILL.md` |
| `app/src/main/java/**/*.kt` | `architecture`, `clean`, `refactor`, `structure` | `.agents/skills/clean-code/SKILL.md` |
| `app/src/main/java/**/db/*.kt` | `database`, `room`, `entity`, `dao` | `.agents/skills/database-design/SKILL.md` |
| `app/src/main/res/values/styles.xml`, `window` | `insets`, `edge-to-edge`, `fullscreen` | `.agents/skills/edge-to-edge/SKILL.md` |
| `app/src/main/cpp/*`, audio, thread | `perf`, `profiler`, `memory`, `leak` | `.agents/skills/android-profiler/SKILL.md` |
| `*` | `git`, `clone`, `remote`, `branch` | `.agents/skills/git-collaboration-master/SKILL.md` |

<!-- SKILLS_INDEX_END -->
