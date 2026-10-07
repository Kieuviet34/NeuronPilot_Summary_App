# Báo cáo đối chiếu App2 với app chính

**Ngày:** 07/10/2026  
**Phạm vi:** `app/` của repository chính và `App2/app/`  
**Mục tiêu:** xác định khác biệt chức năng, kiến trúc, dữ liệu, AI/native, UI và phần có thể đưa vào app chính mà không phá vỡ các bất biến của G720.

## 1. Kết luận điều hành

`App2` không phải một bản UI thay thế đơn giản. Đây là một ứng dụng meeting-notes Java độc lập, có kiến trúc production-oriented hơn ở tầng orchestration, background processing, versioning và test; trong khi app chính hiện giữ lợi thế về tích hợp NeuroPilot/I2S và đã có UI BA v2/Kotlin tương thích với code native hiện tại.

**Khuyến nghị:** không merge App2 theo kiểu copy toàn bộ hoặc đổi app chính sang Java. Nên chọn app chính làm shell/entry point, giữ nguyên native NeuroPilot và I2S, sau đó port có chọn lọc các capability của App2 theo thứ tự:

1. `ProcessingService` + tiến độ/huỷ/khôi phục.
2. Mô hình `processing_runs` để reprocess/compare/chọn bản chính.
3. Các abstraction `ProcessMeetingUseCase`, chunking, parser và model lifecycle.
4. UI responsive/accessibility và version strip của App2, rồi đồng bộ lại với UI BA v2 hiện tại.
5. Chỉ sau khi có migration và test tích hợp mới cân nhắc hợp nhất schema.

## 2. Bảng so sánh nhanh

| Hạng mục | App chính | App2 | Đánh giá |
|---|---|---|---|
| Ngôn ngữ/UI | Kotlin + XML, package hiện tại có cả lớp `com.mediatek...` | Java + XML/ViewBinding + Hilt | Không merge trực tiếp; cần chọn một boundary rõ ràng |
| Build | AGP 8.3.2, Kotlin 1.9.22, Java/Kotlin target 1.8 | Gradle 8.7 wrapper, Java 17, Hilt, ViewBinding | App2 hiện đại hơn nhưng nâng target cần kiểm tra thiết bị/NDK |
| Entry point | `MeetingNotesActivity` | `HomeActivity` | App2 điều hướng sạch hơn, app chính bám BA v2 hiện có |
| Ghi âm | `AudioRecordingService` + `I2SAudioRecorder`, foreground microphone | `RecordingService` + `RecordingController`/`SegmentRecorder` | Giữ I2S của app chính; port lifecycle/controller của App2 |
| Xử lý AI | `MeetingProcessingActivity` gọi trực tiếp `MeetingAiPipeline` | `ProcessingService` chạy nền, `ProcessMeetingUseCase`, `ProcessingTracker` | App2 tốt hơn rõ rệt cho foreground/background, huỷ và retry |
| ASR/LLM | Whisper server loopback + NeuroPilot LLM; Plan B hiện có fallback mẫu | Demo/Online/OpenAI/Offline native whisper.cpp + llama.cpp | App2 linh hoạt hơn nhưng Online xung đột C1; không thay NeuroPilot native ngay |
| Quản lý RAM | Có quy tắc trong tài liệu và bridge, nhưng pipeline Activity sở hữu bridge | `ModelManager` ép một model nặng trong RAM và unload trong `finally` | Nên port abstraction lifecycle của App2 |
| Database | Room Kotlin, version 5, glossary/edit log/soft delete | Room Java, version 3, `processing_runs`, run-scoped actions, migration test | Hai schema không tương thích trực tiếp; phải viết migration hợp nhất |
| Reprocess/compare | Chưa thấy mô hình run độc lập trong flow hiện tại | Có retry, LLM-only, nhiều phiên bản, compare, chọn primary | Đây là capability có giá trị merge cao |
| UI tablet | Bố cục BA v2 giàu chi tiết, 1024x600 landscape | `layout-sw600dp`, dùng dimen/strings/ViewBinding, 2-pane có fallback phone | App2 có nền responsive/accessibility tốt hơn; app chính có visual spec cụ thể hơn |
| Offline | Gần với C1 nhưng manifest vẫn có INTERNET và loopback whisper | Có Online/OpenAI và INTERNET rõ ràng | Chỉ port phần Offline/Demo, loại Online khỏi build sản phẩm offline |
| Test | Có test glossary và test hiện có trong build | App2 có test cho domain/audio/Room/OpenAI/native; CLAUDE ghi 491 test | App2 có độ bao phủ logic tốt hơn, cần chuyển test cùng capability |

## 3. Khác biệt chức năng chính

### 3.1 Điều phối pipeline và chạy nền

**App chính**

- [`MeetingProcessingActivity.kt`](../app/src/main/java/com/bhs/meetingnotes/MeetingProcessingActivity.kt) khởi tạo [`MeetingAiPipeline`](../app/src/main/java/com/bhs/meetingnotes/ai/MeetingAiPipeline.kt) trực tiếp trong Activity.
- Pipeline cập nhật UI bằng callback và lưu trực tiếp vào `MeetingEntity`.
- Khi Activity bị huỷ, chuyển nền hoặc process bị hệ thống kill, flow tiếp tục/khôi phục không được tách thành một service chuyên dụng.
- Có Plan B, nhưng trong trường hợp không có NeuroPilot, phần summary/action có thể trở thành dữ liệu mẫu hoặc thông báo không có LLM. Đây là hành vi demo hữu ích nhưng không nên được coi là inference thật.

**App2**

- [`ProcessingService.java`](../App2/app/src/main/java/com/bhs/meetingnotes/service/ProcessingService.java) là foreground service loại `dataSync`.
- [`ProcessMeetingUseCase.java`](../App2/app/src/main/java/com/bhs/meetingnotes/domain/ProcessMeetingUseCase.java) tách orchestration khỏi UI.
- Có progress tracker, notification, cancel, retry, `ProcessingRecovery`, giữ trạng thái lỗi và không làm hỏng bản chính.
- Có xử lý từng chunk và lưu transcript từng phần để retry có thể bỏ qua chunk đã hoàn tất.

**Kết luận:** đây là phần App2 đáng port nhất. Không nên copy nguyên service vì App2 dùng entity/repository/native interface khác; nên giữ contract của app chính và đưa orchestration vào use case/service tương đương.

### 3.2 Nhiều phiên bản kết quả

App2 có bảng `processing_runs`, `primary_run_id`, `run_id` trên action items và migration 2→3 để:

- xử lý lại toàn bộ hoặc chỉ LLM;
- lưu cấu hình/model/thời gian của từng lần chạy;
- so sánh hai phiên bản bằng diff;
- chọn một phiên bản làm bản chính;
- xoá/retry phiên bản mà không xoá kết quả chính.

App chính hiện có soft delete meeting, glossary và edit log nhưng kết quả vẫn nằm trực tiếp trên meeting. Nếu merge capability này, cần chuyển dần các cột `summary`, transcript và action items sang run-scoped data, hoặc tạo lớp compatibility để cột trên meeting chỉ là projection của primary run.

### 3.3 Ghi âm và foreground service

App chính có lợi thế quan trọng: [`I2SAudioRecorder.kt`](../app/src/main/java/com/bhs/meetingnotes/audio/I2SAudioRecorder.kt) và [`AudioRecordingService.kt`](../app/src/main/java/com/bhs/meetingnotes/audio/AudioRecordingService.kt) đã bám yêu cầu microphone/I2S của G720.

App2 có thiết kế service/controller tốt hơn:

- service chỉ giữ lifecycle, notification và wake lock;
- controller sở hữu state ghi âm;
- notification có nút pause/resume/stop;
- có grace period khi mở microphone và trạng thái `STARTING/PAUSED/RECORDING`.

**Khuyến nghị:** không thay `I2SAudioRecorder` bằng `AudioRecord`/recorder của App2. Port state machine/controller và notification actions, rồi nối chúng vào I2S recorder hiện có.

### 3.4 ASR/LLM và model lifecycle

App chính gọi [`NeuroPilotLlmBridge.kt`](../app/src/main/java/com/mediatek/neuropilot/jnidemo/aibox/ai/NeuroPilotLlmBridge.kt) và whisper server loopback; đây là phần gắn với NPU/Genio và không nên bị thay thế bởi native tree của App2 trong một lần merge.

App2 dùng interface engine và [`ModelManager.java`](../App2/app/src/main/java/com/bhs/meetingnotes/data/nativeai/ModelManager.java):

- chuẩn bị/kiểm tra model files;
- unload ASR trước khi load LLM và ngược lại;
- abort native call;
- unload trong `finally`.

**Phần nên port:** interface engine, `ModelManager` contract, `AudioChunkPlanner`, `WavSlicer`, `TextChunker`, `ResponseParser`, validator và thermal/cancellation boundary.  
**Phần không nên copy:** `cpp/third_party`, JNI wrapper và loader của App2 vào native NeuroPilot hiện tại. Hai native stack có ABI, lifecycle và dependency khác nhau; merge mù có thể gây duplicate symbol, tăng APK và phá runtime G720.

### 3.5 Data layer và migration

App chính dùng [`MeetingDatabase.kt`](../app/src/main/java/com/bhs/meetingnotes/db/MeetingDatabase.kt), version 5, gồm meeting/segment/bookmark/glossary/alias/edit log; có `fallbackToDestructiveMigration()`.

App2 dùng [`AppDatabase.java`](../App2/app/src/main/java/com/bhs/meetingnotes/data/local/AppDatabase.java), version 3, gồm meeting/segment/action/bookmark/processing run; migration được khai báo trong [`Migrations.java`](../App2/app/src/main/java/com/bhs/meetingnotes/data/local/Migrations.java), có schema export và test đối chiếu.

**Rủi ro quan trọng của app chính:** `fallbackToDestructiveMigration()` có thể xoá dữ liệu meeting/WAV metadata khi gặp schema version không có migration. Với ứng dụng ghi âm cuộc họp, đây là rủi ro dữ liệu mức cao.

**Không thể merge schema bằng cách chép entity.** Cần một migration hợp nhất, ít nhất:

1. bảo toàn toàn bộ bảng glossary/edit log/soft delete hiện có;
2. thêm `processing_runs`, `primary_run_id`, `action_items.run_id`;
3. backfill mỗi meeting đã có kết quả thành run #1;
4. kiểm tra foreign key và index;
5. bỏ destructive fallback sau khi tất cả đường nâng cấp được test.

## 4. Đánh giá UI/UX

### 4.1 Điểm mạnh của app chính

- Bố cục meeting list và result bám mockup BA v2, phù hợp màn ngang 1024x600.
- Màn processing có timeline 4 bước và preview raw/corrected, thể hiện rõ pipeline ASR → sửa thuật ngữ → summary → actions.
- Màn result có hành động lớn, metadata model/language/duration và các tab theo nghiệp vụ.
- Có hỗ trợ soft delete/trash, glossary và edit log, hữu ích cho người dùng kỹ thuật.

### 4.2 Điểm yếu UI của app chính

- Nhiều layout có text hardcoded thay vì `strings.xml`, ví dụ [`activity_meeting_list.xml`](../app/src/main/res/layout/activity_meeting_list.xml), [`activity_meeting_processing.xml`](../app/src/main/res/layout/activity_meeting_processing.xml) và [`activity_meeting_result.xml`](../app/src/main/res/layout/activity_meeting_result.xml). Điều này làm giảm khả năng bản địa hoá và tạo khác biệt giữa phone/tablet.
- Một số kích thước/padding được ghi trực tiếp bằng dp/sp; khó bảo trì khi density/font scale thay đổi.
- Nhiều view được tìm bằng `findViewById` và nullable field; dễ tạo trạng thái UI không đồng nhất khi layout variant thay đổi.
- Processing gắn với Activity nên UX khi rời màn hình hoặc xoay/khôi phục kém chắc chắn hơn service-driven flow.
- Accessibility chưa đồng đều: icon click có nơi dùng `contentDescription`, có nơi chỉ là `ImageView` clickable; một số text dùng emoji trong label thay vì icon/button Material có semantics rõ.

### 4.3 Điểm mạnh UI của App2

- Dùng ViewBinding, resource strings/dimens và `layout-sw600dp`; giảm lỗi id/layout và hỗ trợ tablet/phone rõ ràng.
- Touch target chuẩn hoá qua `touch_min=48dp`, spacing/radius/elevation tập trung trong resources.
- Home/result/processing có 2-pane cho tablet và fallback một cột/tab cho phone.
- Result có version strip, actions panel cố định trên tablet, TTS, export và compare.
- Có content description cho back button và dùng MaterialButton/MaterialAlertDialog nhiều hơn.

### 4.4 Điểm yếu UI của App2

- Visual language đơn giản hơn BA v2 của app chính; một số màn có thể ít “dashboard” và ít nổi bật hơn trên thiết bị 10 inch.
- `activity_processing.xml` dùng cột timeline rộng cố định `400dp`; cần đo lại với font scale lớn và nội dung tiếng Việt dài.
- Có cả `layout/` và `layout-sw600dp/`; cần kiểm tra parity id/behavior để tránh lỗi chỉ xảy ra trên một kích thước.
- App2 có Online mode trong Settings; UI phải hiển thị cảnh báo rõ việc gửi audio/text ra ngoài nếu sản phẩm không còn thuần offline.

### 4.5 Chấm điểm định hướng

| Tiêu chí | App chính | App2 | Nhận xét |
|---|---:|---:|---|
| Hoàn thiện flow meeting | 7.5/10 | 9/10 | App2 có reprocess/compare/retry/background |
| Tính đúng với C1 offline | 8/10 | 5/10 | App2 có OpenAI/Online; chỉ nên port Demo/Offline |
| Tích hợp G720/NeuroPilot | 9/10 | 6.5/10 | App chính là nguồn tích hợp phần cứng hiện tại |
| Khả năng khôi phục dữ liệu | 6.5/10 | 8.5/10 | App2 có run recovery; app chính cần bỏ destructive fallback |
| UI 1024x600 | 8/10 | 8/10 | App chính đẹp/bám BA; App2 responsive/accessibility tốt hơn |
| Khả năng bảo trì | 6.5/10 | 8.5/10 | App2 có layers, ViewBinding, Hilt, test boundary |

Các điểm trên là đánh giá code/layout, **chưa phải usability test trên G720**. Cần đo thực tế với touch, font scale, xoay process, tắt màn hình và nhiệt.

## 5. Những phần nên merge

### Ưu tiên P0 — nên làm trước

1. `ProcessingService`/notification/cancel/retry/recovery.
2. Tách pipeline khỏi Activity thành use case/repository boundary.
3. Model lifecycle một-model-in-RAM và `finally` cleanup.
4. Explicit error state, không biến lỗi native/missing model thành success-shaped sample.
5. Migration test và loại bỏ `fallbackToDestructiveMigration()`.

### Ưu tiên P1 — giá trị sản phẩm cao

1. `processing_runs` + version strip + compare + chọn primary.
2. Lưu transcript theo chunk để retry/resume.
3. Chunk planner/slicer/text chunker/response parser của App2.
4. ViewBinding, strings/dimens, `sw600dp` và touch/accessibility patterns.
5. Processing/result UI 2-pane của App2, nhưng giữ màu/visual hierarchy theo BA v2.

### Không merge trực tiếp

- Toàn bộ `App2/app/src/main/cpp/third_party`.
- `OpenAi*`, `INTERNET` flow và permission `MANAGE_EXTERNAL_STORAGE` nếu sản phẩm giữ C1 offline.
- Đổi namespace/applicationId/package của app chính.
- Copy toàn bộ activity Java đè lên activity Kotlin.
- Copy database/entity/migration mà không có kế hoạch backfill và test upgrade.

## 6. Kế hoạch merge đề xuất

### Giai đoạn A — tạo boundary và test

- Định nghĩa interface chung cho `AsrEngine`, `LlmEngine`, `OnDeviceRuntime`, `MeetingRepository`, `ProcessingProgress`.
- Viết adapter từ `NeuroPilotLlmBridge` và whisper loopback của app chính vào interface đó.
- Thêm fake engine/repository để test huỷ, retry, missing model, no speech và crash recovery.
- Giữ UI hiện tại, chưa đổi database.

### Giai đoạn B — background processing

- Tạo `ProcessingService` Kotlin hoặc Java tùy module hiện tại, nhưng chỉ gọi use case.
- Di chuyển pipeline logic khỏi `MeetingProcessingActivity`.
- Gắn notification tiến độ/hủy và trạng thái processing vào Room.
- Kiểm tra kill/relaunch, screen off, lock screen, thiếu model và nhiệt.

### Giai đoạn C — versioning/migration

- Chốt schema mới dựa trên DB app chính, không dùng schema App2 làm nguồn duy nhất.
- Viết migration v5→v6 cho runs/primary/action run id.
- Backfill kết quả cũ thành run #1 trong transaction.
- Chạy test nâng cấp từ mọi schema được phát hành; xoá `fallbackToDestructiveMigration`.

### Giai đoạn D — UI

- Port version strip/compare/reprocess và tablet two-pane.
- Đưa toàn bộ text mới vào `strings.xml`, dimen vào resources.
- Kiểm tra 1024x600, font scale 1.3–2.0, TalkBack/content description và touch target.
- Chạy screenshot/golden review trên G720 thật trước khi thay UI BA v2.

### Giai đoạn E — native/performance

- Chỉ adapter hóa model lifecycle; không thay native backend.
- Benchmark Plan B trước, sau đó NeuroPilot Plan C/A theo Gate G-N1/G-N2/G-N3.
- Đo RAM peak, nhiệt, thời gian xử lý và khả năng huỷ native call.

## 7. Rủi ro và tiêu chí chấp nhận merge

| Rủi ro | Mức | Biện pháp |
|---|---|---|
| Trộn hai native stack gây lỗi ABI/symbol/APK | Blocker | Không merge third-party C++; dùng adapter |
| Migration làm mất meeting metadata | Blocker | Transaction + schema upgrade tests + backup/export |
| Online mode vi phạm C1 | High | Tách build/product mode hoặc loại OpenAI khỏi production offline |
| Processing chết khi Activity bị đóng | High | Foreground `dataSync` service + recovery state |
| Chỉ một model nặng trong RAM không được đảm bảo | High | Model manager + benchmark RAM/thermal |
| UI overflow 1024x600/font scale | Medium | screenshot test trên G720 và test font scale |
| Java/Kotlin dependency/DI tăng độ phức tạp | Medium | Port capability, không port cả app; chọn một owner cho domain contract |

**Merge chỉ được chấp nhận khi:**

- build arm64 và unit tests chạy;
- migration từ DB hiện tại giữ nguyên meeting/WAV/glossary/edit log;
- tắt màn hình/rời Activity không dừng ghi âm hoặc xử lý;
- cancel/retry không làm hỏng primary result;
- không có network call trong Offline/Demo;
- native load/unload không giữ đồng thời ASR và LLM;
- UI hiển thị đúng trên 1024x600 và không cắt nút chính;
- benchmark đạt các ngưỡng trong [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md).

## 8. Giới hạn kiểm chứng trong lần review này

- Đã đọc source/config/layout trực tiếp của hai app và các tài liệu dự án liên quan.
- Đã xác nhận App2 có Java 151 file, root `app` có Kotlin 61 file; App2 có native tree riêng lớn hơn.
- Đã thử chạy `App2\gradlew.bat testDebugUnitTest`; build không bắt đầu vì `App2/local.properties` trỏ tới SDK Linux `/home/duongnh/Android/Sdk`, không tồn tại trên Windows. Không tự sửa file cấu hình này.
- Chưa chạy instrumentation/UI test trên G720 và chưa có phép đo runtime/thermal/RAM. Các nhận xét UI là static review của XML/code, không thay thế usability test.

## 9. Kết luận cuối

App2 **có thể cải thiện app chính đáng kể**, nhưng giá trị nằm ở các capability cấp domain/service/data chứ không nằm ở việc thay toàn bộ UI hoặc native backend. Chiến lược an toàn nhất là:

> **Giữ app chính làm nền tảng G720/NeuroPilot/I2S và BA UI; port có kiểm soát orchestration, background processing, versioning, migration discipline và responsive/accessibility pattern của App2.**

