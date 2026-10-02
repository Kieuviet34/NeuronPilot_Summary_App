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

<!-- skillforge:begin (auto-generated, edit outside this block) -->
## Prime directive — bám cốt lõi, đừng lan man
- Xác định ĐÚNG MỘT vấn đề cốt lõi của yêu cầu và nhắc lại nó bằng 1 câu trước khi làm. Mọi thay đổi phải phục vụ câu đó; việc ngoài phạm vi thì ghi ra "follow-up", không tự làm.
- Đọc vừa đủ: chỉ mở file/đoạn liên quan trực tiếp (dùng search theo symbol, không đọc cả cây thư mục, không dán file lớn vào ngữ cảnh). Đủ thông tin để hành động thì hành động, đừng khảo sát tràn lan.
- Đổi ít nhất có thể: diff nhỏ, đúng chỗ; không refactor/đổi tên/đổi style ngoài yêu cầu; không xoá code/test chưa hiểu rõ.
- Không bịa: không chắc về API/hành vi thì kiểm chứng bằng code/tài liệu (hoặc skill tra cứu), không đoán. Nêu rõ chỗ chưa chắc.
- Không trôi phạm vi: sau 2 lần sửa không xong hoặc thấy mình đang mở rộng ra ngoài cốt lõi thì DỪNG, tóm tắt, và xác nhận hướng lại.
- Chỉ nạp skill khớp task; bỏ qua skill không liên quan để khỏi loãng ngữ cảnh và sai trọng tâm.

## Working rules (all agents)
- Before coding: restate the goal, list assumptions, ask only when a wrong guess is costly. For >1 file or unclear scope, write a short plan first (`docs/ai/plans/`).
- Simplest thing that works. No speculative abstractions, no unrequested features, no drive-by refactors. Match surrounding code style.
- Surgical diffs: touch only what the task needs; never reformat unrelated code; never delete code/tests you don't understand.
- Read before write: search for existing helpers/patterns before adding new ones. Prefer editing over creating files.
- Verify, don't assume: run the build/tests/lint in **Commands** after changes and report real output. Done = verification passes, or the gap is stated.
- Bugs: reproduce -> root cause -> failing test -> fix -> confirm. No guess-and-patch loops; after 2 failed attempts stop and re-analyze.
- Tests for logic you add/change (behaviour, not implementation). Never weaken or skip a test to make it pass.
- Security: never hardcode/log secrets, tokens or PII; validate external input; least privilege; parameterized queries.
- Dependencies: justify each new one; prefer platform/std lib; pin versions.
- Git: small logical commits, imperative messages; never force-push, rewrite history or commit secrets without explicit approval.
- Context economy: open files on demand, not whole trees; keep durable knowledge in `docs/ai/` (ARCHITECTURE.md, decisions/) instead of this file.
- Skills: when a task matches an installed skill, follow it instead of improvising; ignore skills unrelated to the task.
- Report: concise — what changed, why, how it was verified, risks/follow-ups.

## Android rules (Kotlin)
- Kotlin + Jetpack Compose + Material 3 for new UI. UI (stateless composables, state hoisting) -> ViewModel (`StateFlow` UI state, unidirectional data flow) -> domain (use cases, optional) -> data (repositories, single source of truth).
- Coroutines/Flow: structured concurrency (`viewModelScope`), injected dispatchers, `collectAsStateWithLifecycle`; never `GlobalScope`, never block the main thread.
- Hilt for DI; Room/DataStore for persistence; Retrofit/Ktor + kotlinx.serialization. Follow the existing module layout (`:app`, `:core:*`, `:feature:*`).
- Gradle: all deps in `gradle/libs.versions.toml`; don't bump AGP/Kotlin/compileSdk unless asked.
- Commands: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lint`, `./gradlew connectedDebugAndroidTest` (device needed).
- No hardcoded UI strings (`strings.xml`); support dark theme + font scaling; `contentDescription` on icons.
- Never edit `build/`, keystores, `local.properties` or generated code.
<!-- skillforge:end -->
