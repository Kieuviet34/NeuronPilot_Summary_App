# Meeting Notes App — Giai đoạn 1 (UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng đầy đủ 6 màn hình UI landscape của Meeting Notes App (Java, Android) chạy với dữ liệu mẫu, build được APK debug.

**Architecture:** Single-module Android app, MVVM (ViewModel + LiveData + ViewBinding), Hilt DI. `MeetingRepository` là interface, giai đoạn 1 dùng `FakeMeetingRepository`; giai đoạn 2 thay bằng Room. Logic thuần (đặt tên file, format, ước tính thời gian, parse transcript diff) tách thành class không phụ thuộc Android để unit-test được.

**Tech Stack:** Java 17, AGP 8.5.2, Gradle 8.7, Material 3, AndroidX Lifecycle 2.8.4, Hilt 2.51.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-meeting-notes-ui-design.md`

## Global Constraints

- Package `com.bhs.meetingnotes`; minSdk 30; targetSdk 35; compileSdk 35 (nếu không cài được platform 35 thì 34); `abiFilters 'arm64-v8a'`.
- Màn hình landscape cố định 1024x600 (`screenOrientation="landscape"` ở mọi Activity).
- Tiếng Việt có dấu đầy đủ; mọi chuỗi UI nằm trong `res/values/strings.xml`.
- Cấu trúc package theo mục 7 tài liệu kỹ thuật: `ui/{home,recording,segments,processing,result,settings}`, `data/{local,export}`, `domain`, `di`, `util`.
- Result: 2 tab (Tóm tắt, Transcript) + panel Hành động cố định bên phải. Export: PDF + TXT. Không DOCX.
- Ngôn ngữ chọn qua dialog khi bấm "Ghi âm cuộc họp"; mặc định lấy từ Cài đặt.
- Không native code, không Room, không AudioRecord thật ở giai đoạn này.
- Temperature mặc định 0.3 (slider 0.1–0.3); CPU threads mặc định 4 (slider 1–8).
- Segment naming: `{title}_{nn:02d}.wav`, title bỏ dấu, space → `_`, bỏ ký tự đặc biệt; mặc định `Cuochop_yyyyMMdd_HHmm`.
- Tên dự án trong git: thư mục chưa phải git repo → không có bước commit; bỏ qua.

## Review Focus

- Title cuộc họp rỗng / toàn ký tự đặc biệt / có dấu tiếng Việt (đ, Đ) → tên file hợp lệ, không rỗng.
- Bỏ chọn toàn bộ segment → nút "Xử lý AI" bị vô hiệu, không crash khi tính ước tính.
- Duration 0 hoặc >= 1 giờ → format đúng ("00:00", "1h 04 phút").
- Xoay/recreate Activity giữa chừng (timer ghi âm, pipeline mô phỏng) → state nằm trong ViewModel, không reset.
- Thiết bị không có TTS engine/không có ứng dụng xử lý share → toast lỗi, không crash.

---

### Task 1: Scaffold Gradle + theme + Hilt + HomeActivity rỗng build được

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `local.properties` (sdk.dir), `app/build.gradle`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/bhs/meetingnotes/App.java`, `ui/home/HomeActivity.java`
- Create: `app/src/main/res/values/{strings,colors,themes}.xml`, `res/layout/activity_home.xml`
- Create: Gradle wrapper (qua `gradle wrapper`)

**Interfaces:**
- Produces: theme `Theme.MeetingNotes` (Material3 Light, NoActionBar); colors `primary #1A73E8`, `rec_red #E53935`, `warn_orange #F57C00`, `ok_green #2E7D32`, `surface`, `on_surface_variant`; `@HiltAndroidApp App`.

- [ ] **Step 1:** Cài platform 35: `D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-35"` (accept licenses). Nếu thất bại → dùng compileSdk 34.
- [ ] **Step 2:** Viết các file Gradle, manifest, App, HomeActivity rỗng (TextView).
- [ ] **Step 3:** Sinh wrapper: `gradle wrapper --gradle-version 8.7` bằng Gradle trong `~/.gradle/wrapper/dists`.
- [ ] **Step 4:** Run `gradlew assembleDebug` (JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`). Expected: BUILD SUCCESSFUL.

### Task 2: Model, Repository giả, util thuần + unit test

**Files:**
- Create: `data/local/{MeetingEntity,SegmentEntity,ActionItemEntity}.java` (POJO, chưa phải Room)
- Create: `domain/MeetingRepository.java`, `data/local/FakeMeetingRepository.java`, `di/AppModule.java`
- Create: `util/SegmentNaming.java`, `util/TimeFormat.java`, `util/ProcessingEstimator.java`
- Test: `app/src/test/java/com/bhs/meetingnotes/util/{SegmentNamingTest,TimeFormatTest,ProcessingEstimatorTest}.java`

**Interfaces:**
- `SegmentNaming.sanitize(String): String` ; `segmentFileName(String title, int n): String` ; `defaultTitle(long millis): String`
- `TimeFormat.clock(long ms): String` ("08:24"); `TimeFormat.minutes(long ms): String` ("1h 04 phút" / "30 phút 12 giây"); `TimeFormat.size(long bytes): String` ("28.9 MB")
- `ProcessingEstimator.estimate(long totalMs): Estimate{asrMin, fixMin, summaryMin, totalMin}` theo bảng mục 3.1 (ASR ≈ 0.17×, sửa ≈ 0.13×, tóm tắt tối thiểu 2 phút).
- `MeetingRepository`: `LiveData<List<MeetingEntity>> meetings()`, `MeetingEntity getMeeting(long)`, `List<SegmentEntity> getSegments(long)`, `void setSegmentSelected(long segId, boolean)`, `List<ActionItemEntity> getActions(long)`.
- Entities: `MeetingEntity{id,title,createdAt,language("vi"/"en"),status,progress,summary,rawTranscript,cleanTranscript,wordCount,totalDurationMs,actionCount}`; `SegmentEntity{id,meetingId,number,audioPath,sizeBytes,durationMs,status,isSelected,pauseCount,note}`; `ActionItemEntity{id,meetingId,description,assignee,deadline,isDone}`.

- [ ] **Step 1:** Viết test thất bại: `sanitize("Họp Đặc biệt #1!")` → `Hop_Dac_biet_1`; `sanitize("###")` → `Cuochop`; `segmentFileName("Hop BSP", 3)` → `Hop_BSP_03.wav`; `clock(504000)` → `08:24`; `minutes(3840000)` → `1h 04 phút`; `estimate(0)` không chia 0.
- [ ] **Step 2:** `gradlew testDebugUnitTest` → FAIL (class chưa tồn tại).
- [ ] **Step 3:** Cài đặt tối thiểu; `FakeMeetingRepository` có 4 cuộc họp mẫu khớp mockup (Hop Review Sprint Q2, Product Roadmap Discussion, Hop ky thuat BSP đang ghi, Hop trien khai IoT Gateway), 4 segment BSP (03 bỏ chọn), 5 action items.
- [ ] **Step 4:** `gradlew testDebugUnitTest` → PASS.

### Task 3: Home

**Files:** `ui/home/{HomeActivity,HomeViewModel,MeetingListAdapter}.java`, `layout/{activity_home,item_meeting_card}.xml`, `drawable/*` (badge, card đỏ).
**Interfaces:** Consumes `MeetingRepository.meetings()`. `HomeViewModel.setFilter(String lang|null)`, `setQuery(String)`, `LiveData<List<MeetingEntity>> visible()`, `LiveData<Counts>`.

- [ ] **Step 1:** Layout: sidebar 200dp (title, 3 mục lọc kèm count, nút "Ghi âm cuộc họp"), vùng phải gồm thanh trên (tiêu đề, ô tìm kiếm, icon cài đặt) + RecyclerView `GridLayoutManager(2)`.
- [ ] **Step 2:** Adapter: card tên/ngày/thời lượng/số từ/số action/badge; card đang ghi viền đỏ + badge REC nhấp nháy (`ObjectAnimator` alpha).
- [ ] **Step 3:** Nút ghi âm mở `NewMeetingDialog` (tên tuỳ chọn + chọn VI/EN) rồi `startActivity(RecordingActivity)`; bấm card hoàn tất → `ResultActivity`; bấm card đang ghi → `RecordingActivity`; icon cài đặt → `SettingsActivity`.
- [ ] **Step 4:** Build + chạy test.

### Task 4: Recording

**Files:** `ui/recording/{RecordingActivity,RecordingViewModel,SegmentListAdapter,NewMeetingDialog}.java`, `layout/{activity_recording,item_segment_recording,dialog_new_meeting}.xml`, `view/WaveformView.java`, `view/TimerRingView.java`.
**Interfaces:** `RecordingViewModel`: `enum State{RECORDING,PAUSED}`, `LiveData<State> state()`, `LiveData<Long> elapsedMs()`, `LiveData<List<SegmentEntity>> segments()`, `togglePause()`, `stopSegment()` (→ đoạn "saved", dẫn tới màn chọn đoạn), `newSegment()`, `bookmark()`. Timer bằng `Handler` 1s, không tăng khi PAUSED; waveform mô phỏng.

- [ ] **Step 1:** Layout hai panel; panel phải có `TabLayout` (Các đoạn đã ghi / Thông tin) + 4 ô thống kê (tổng thời lượng, dung lượng, audio format, model ASR).
- [ ] **Step 2:** ViewModel + logic state; `newSegment()` lưu đoạn hiện tại, thêm đoạn `number+1`, reset timer, tên file qua `SegmentNaming`.
- [ ] **Step 3:** Nút "Dừng đoạn" mở `SegmentSelectActivity` khi người dùng chọn "Xử lý" (menu 2 lựa chọn: Ghi đoạn mới / Xử lý AI).
- [ ] **Step 4:** Build + test.

### Task 5: Segment Select

**Files:** `ui/segments/{SegmentSelectActivity,SegmentSelectViewModel,SegmentSelectAdapter}.java`, `layout/{activity_segment_select,item_segment}.xml`.
**Interfaces:** `SegmentSelectViewModel.toggle(long segId)`, `selectAll(boolean)`, `LiveData<Summary{selected,total,totalMs,totalBytes,skippedMs,Estimate}>`.

- [ ] **Step 1:** Danh sách checkbox + nút play (toast "Phát thử" ở giai đoạn 1), đoạn bỏ chọn alpha 0.45 + ghi chú cam.
- [ ] **Step 2:** Panel phải: thống kê, ước tính từng bước (`ProcessingEstimator`), tip box vàng. Nút "Xử lý AI (n đoạn)" disabled khi n=0.
- [ ] **Step 3:** Bấm → `ProcessingActivity`. Build + test.

### Task 6: Processing

**Files:** `ui/processing/{ProcessingActivity,ProcessingViewModel}.java`, `layout/activity_processing.xml`, `view/StepTimelineView` (hoặc layout 4 hàng), `util/DiffSpanBuilder.java` + test.
**Interfaces:** `ProcessingViewModel`: `LiveData<StepState[4]>` (`PENDING/RUNNING/DONE` + progress%), `LiveData<String> rawText`, `LiveData<String> cleanText`, `LiveData<String> etaText`. Mô phỏng bằng Handler: bước 1 → 2 → 3+4, xong → `ResultActivity`. `DiffSpanBuilder.build(raw, clean): SpannableString` đánh dấu từ khác nhau (gạch đỏ ở raw, nền xanh ở clean) theo diff từ-theo-từ đơn giản (LCS).

- [ ] **Step 1:** Test `DiffSpanBuilder`: raw "em vinh nap nhat" vs clean "Em Vĩnh nâng cấp" → số span lỗi/sửa đúng; chuỗi rỗng không crash. Chạy FAIL.
- [ ] **Step 2:** Cài đặt; chạy PASS.
- [ ] **Step 3:** Layout timeline dọc 4 bước + 2 thẻ preview + thanh ước tính dưới.
- [ ] **Step 4:** ViewModel mô phỏng; state giữ qua recreate. Build.

### Task 7: Result + Export + TTS

**Files:** `ui/result/{ResultActivity,ResultViewModel,SummaryFragment,TranscriptFragment,ActionAdapter}.java`, `layout/{activity_result,fragment_summary,fragment_transcript,item_action}.xml`, `data/export/{PdfExporter,TxtExporter}.java`, `data/export/TtsReader.java`.
**Interfaces:** `PdfExporter.export(Context, MeetingEntity, List<ActionItemEntity>, File outDir): File`; `TxtExporter.export(MeetingEntity, File outDir): File`; `TtsReader.speak(Context, String)` / `stop()`.

- [ ] **Step 1:** Test `TxtExporter` ghi file chứa title + clean transcript (Robolectric không cần — dùng thư mục tạm). FAIL → cài đặt → PASS.
- [ ] **Step 2:** Layout 2 tab (`ViewPager2`) + panel Hành động bên phải + metadata bar; nút "Đọc tóm tắt" và "Xuất báo cáo" (menu PDF/TXT, chia sẻ qua `FileProvider`).
- [ ] **Step 3:** Xử lý lỗi: không có TTS → toast; không có app share → toast. Build.

### Task 8: Settings

**Files:** `ui/settings/{SettingsActivity,SettingsViewModel,GlossaryAdapter}.java`, `layout/activity_settings.xml`, `data/local/SettingsStore.java` (SharedPreferences), `view/StorageBarView.java`.
**Interfaces:** `SettingsStore{getLanguage/setLanguage, getThreads/setThreads, getTemperature/setTemperature, isSilenceCut/set, isAutoTts/set}`; `NewMeetingDialog` đọc `getLanguage()` làm mặc định.

- [ ] **Step 1:** Menu trái 5 mục (Ngôn ngữ & ASR, Model LLM, Âm thanh, Lưu trữ, Xuất báo cáo) chuyển panel phải.
- [ ] **Step 2:** Card VI/EN, slider threads/temperature (clamp 0.1–0.3), toggle cắt im lặng, thanh dung lượng, glossary (thêm/xoá thuật ngữ).
- [ ] **Step 3:** Lưu/khôi phục qua `SettingsStore`. Build + test.

### Task 9: Kiểm chứng cuối

- [ ] **Step 1:** `gradlew clean assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, tất cả test PASS.
- [ ] **Step 2:** Chạy lint `gradlew lintDebug`, sửa lỗi mức Error.
- [ ] **Step 3:** Báo cáo APK tại `app/build/outputs/apk/debug/`. Nếu có thiết bị/emulator: `adb install`, chụp ảnh 6 màn hình. Nếu không: nêu rõ chưa kiểm chứng trên màn hình thật.

## Self-Review

- Spec coverage: 6 màn hình (Task 3–8), kiến trúc (Task 1–2), kiểm chứng (Task 9). Dialog ngôn ngữ ở Task 3 và 8.
- Review Focus được gắn: sanitize/format (Task 2), n=0 (Task 5), recreate (Task 4, 6), TTS/share lỗi (Task 7).
- Giai đoạn 2 & 3 nằm ngoài kế hoạch này, sẽ có plan riêng.
