# Meeting Notes App — Giai đoạn 1: UI (dữ liệu mẫu)

## Mục tiêu
App Android (Java) `com.bhs.meetingnotes` cho G720 AI Box, landscape 1024x600, giao diện theo `docs/Meeting_Notes_App_Tablet_v2.pdf`. Giai đoạn 1 chỉ làm UI đầy đủ 6 màn hình, chạy được với dữ liệu mẫu. Nền tảng để giai đoạn 2 (ghi âm, Room, Service) và 3 (native AI) gắn vào mà không sửa UI.

## Quyết định đã chốt
- Result: theo mockup — 2 tab (Tóm tắt, Transcript) + panel Hành động cố định bên phải.
- Export: PDF (android.graphics.pdf.PdfDocument) + TXT. Không DOCX.
- Storage: `/sdcard/MeetingNotes/` (cần All files access, xử lý ở giai đoạn 2).
- Ngôn ngữ VI/EN: dialog khi bấm "Ghi âm cuộc họp" (kèm đặt tên), mặc định lấy từ Cài đặt.
- Tiếng Việt có dấu đầy đủ trong UI.

## Kiến trúc
- Single-module Gradle, AGP 8.x, Java 17, minSdk 30, targetSdk/compileSdk 35 (fallback 34), abi arm64-v8a.
- Material 3, ViewBinding, AndroidX ViewModel/LiveData. Hilt theo tài liệu kỹ thuật.
- Không có native ở giai đoạn 1 (cpp/ để giai đoạn 3).
- `MeetingRepository` (interface) + `FakeMeetingRepository` trả dữ liệu mẫu. Giai đoạn 2 thay bằng bản Room.
- Cấu trúc package theo mục 7 tài liệu kỹ thuật: `ui/{home,recording,segments,processing,result,settings}`, `data`, `domain`, `di`, `util`.

## Màn hình
1. **Home**: sidebar (Tất cả/Tiếng Việt/English + số đếm, nút Ghi âm), lưới 2 cột card, tìm kiếm, nút cài đặt. Card đang ghi: viền đỏ, badge REC nhấp nháy.
2. **Recording**: trái = tên đoạn, vòng timer, 4 nút (Tạm dừng, Dừng đoạn, Đoạn mới, Đánh dấu), waveform. Phải = tab "Các đoạn đã ghi" / "Thông tin". Timer và trạng thái RECORDING/PAUSED mô phỏng bằng handler.
3. **Segment Select**: danh sách có checkbox + play, đoạn bỏ chọn mờ; panel phải thống kê + ước tính + tip; nút "Xử lý AI (n đoạn)".
4. **Processing**: timeline dọc 4 bước, preview text gốc/đã sửa, thanh ước tính. Tiến độ mô phỏng bằng timer.
5. **Result**: tab Tóm tắt/Transcript, panel Hành động, nút Đọc tóm tắt (Android TTS), Xuất báo cáo (PDF/TXT qua share).
6. **Settings**: menu trái 5 mục, panel phải; slider threads (1-8) và temperature (0.1-0.3, mặc định 0.3), toggle cắt im lặng, biểu đồ dung lượng.

## Kiểm chứng
`gradlew assembleDebug` thành công; cài lên emulator/thiết bị 1024x600; chụp ảnh từng màn hình đối chiếu mockup.

## Ngoài phạm vi
AudioRecord/WAV thật, Room, ProcessingService, JNI llama/whisper/RNNoise/VAD, DOCX.
