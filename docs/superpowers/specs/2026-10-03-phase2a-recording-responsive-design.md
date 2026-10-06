# Giai đoạn 2a — Ghi âm thật + giao diện phone/tablet

## Mục tiêu
Người dùng test được trên **điện thoại Android**: ghi âm thật nhiều đoạn (pause/resume), lưu WAV, lưu dữ liệu bền vững; giao diện tự đổi bố cục theo loại thiết bị. Phần AI (ASR, sửa lỗi, tóm tắt) **chưa làm thật**, tiếp tục mô phỏng và hiển thị rõ là "mô phỏng".

## Quyết định (người dùng chốt 03/10/2026)
- Ghi âm bằng API chuẩn Android: `AudioRecord` 16kHz / mono / PCM16 → file WAV, mỗi đoạn một file.
- Test trên điện thoại trước; layout tự chọn theo loại thiết bị (phone / tablet).
- AI xử lý để sau; được tư vấn API online nhưng không tích hợp ở bước này.

## Thiết kế
### Giao diện thích ứng
- Phân loại bằng resource qualifier `sw600dp` (smallest width ≥ 600dp): tablet dùng `layout-sw600dp/` (bố cục 2 panel hiện có), phone dùng `layout/` (một cột, cuộn, nút chính ở đáy).
- `bool is_tablet`, `integer home_span_count` theo qualifier. Tablet khoá landscape; phone xoay tự do (layout cuộn được).
- Mọi id mà code Java dùng đều có ở cả hai biến thể; id chỉ có ở một biến thể thì code kiểm tra null.
- Màn Kết quả: tablet 2 tab + panel Hành động; phone 3 tab (thêm tab Hành động).
- Phần nội dung Cài đặt dùng chung một layout con cho cả hai biến thể.

### Ghi âm
- `PcmSource` (trừu tượng hoá AudioRecord) → `SegmentRecorder` chạy thread riêng ưu tiên URGENT_AUDIO: đọc PCM, ghi WAV khi RECORDING, bỏ PCM khi PAUSED (AudioRecord vẫn chạy), tính mức âm cho waveform, cập nhật header mỗi 30 giây, kết thúc đoạn an toàn khi lỗi.
- Thời lượng đoạn tính theo **số mẫu đã ghi** (không theo đồng hồ treo tường).
- `WavWriter`/`WavRecovery`: header 44 byte; sửa header cho file bị dừng đột ngột.
- `RecordingController` (Hilt singleton, không phụ thuộc Activity) giữ `RecordingSession` + recorder, lưu đoạn/dấu mốc qua `MeetingRepository`, phát `LiveData<RecordingUiState>`.
- `RecordingService` (Foreground Service, type microphone) giữ tiến trình sống khi app ở nền và hiển thị notification; Android 11+ chặn mic ở nền nếu không có service.
- Quyền: `RECORD_AUDIO` xin trước khi bắt đầu; `POST_NOTIFICATIONS` (Android 13+) xin kèm, từ chối vẫn ghi được.

### Lưu trữ
- Room theo schema tài liệu kỹ thuật (meetings, segments, action_items, glossary, bookmarks) thay `FakeMeetingRepository` ở bản chạy thật; Fake giữ cho test.
- Thư mục gốc: `/sdcard/MeetingNotes` nếu được cấp All files access, ngược lại dùng thư mục riêng của app (`getExternalFilesDir`) để chạy được ngay không cần cấp quyền đặc biệt.
- Khôi phục khi mở app: đoạn còn trạng thái `recording/paused` → sửa header WAV, tính thời lượng từ kích thước file, đánh dấu `saved`.
- Cài đặt có nút nạp dữ liệu mẫu để xem giao diện khi danh sách trống.

### Truy cập dữ liệu không chặn main thread
Room không cho query trên main thread nên các ViewModel nạp dữ liệu qua executor `@Named("io")` và `postValue`.

## Ngoài phạm vi
ASR/LLM thật (kể cả online), RNNoise/VAD, pipeline JNI, `ProcessingService`, DOCX.

## Kiểm chứng
Unit test (TDD) cho WAV, SegmentRecorder (nguồn PCM giả), RecordingSession/Controller, đường dẫn, khôi phục; `assembleDebug`, `lintDebug`. Ghi âm thật và giao diện phone cần người dùng thử trên điện thoại.
