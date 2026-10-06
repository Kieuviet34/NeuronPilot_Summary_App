# 📱 UI/UX & Kỹ Thuật Chi Tiết - Meeting Notes App (BA v2)

> **Mục tiêu**: Hướng dẫn kỹ thuật và đặc tả UI/UX toàn diện theo tài liệu BA v2 (10/2026) dành riêng cho thiết bị G720 AI Box.

---

## 1. ⚙️ Thông Số Phần Cứng & Môi Trường
- **Thiết bị**: G720 AI Box
- **Chipset**: MediaTek Genio 720 (NPU tích hợp)
- **Hệ điều hành**: Android 15
- **Màn hình**: 10-inch Tablet Landscape, Độ phân giải **1024 x 600**
- **Đầu vào âm thanh**: I2S MIC — **16kHz / 16-bit / Mono / WAV (PCM)**
- **Mô hình AI (On-Device 100%, không cần Internet)**:
  - **ASR (STT)**: 
    - Tiếng Việt: PhoWhisper (~245 MB)
    - Tiếng Anh: Whisper (MediaTek AI Hub)
  - **LLM Runtime**: llama.cpp (Android NDK / JNI bridge)
  - **LLM Model**: Qwen2.5 3B (`Q4_K_M` ~1.8 GB)

---

## 2. 🔄 AI Processing Pipeline (4 Bước Tuần Tự)
```
[I2S Audio] ──> [WAV Segments] ──> [Bước 1: ASR] ──> [Bước 2: Sửa lỗi] ──> [Bước 3: Tóm tắt] ──> [Bước 4: Action Items]
```

### Prompt Engineering cho Qwen2.5 (llama.cpp JNI):
1. **Bước 2 - Sửa lỗi văn bản**:
   > *"Bạn là trợ lý sửa lỗi văn bản tiếng Việt. Hãy sửa lỗi chính tả, ngữ pháp, thêm dấu câu đúng, sửa tên riêng và thuật ngữ kỹ thuật. Giữ nguyên nội dung và ý nghĩa, không thêm bớt thông tin. Trả về văn bản đã sửa."*
2. **Bước 3 - Tóm tắt cuộc họp**:
   > *"Bạn là trợ lý tóm tắt cuộc họp. Từ nội dung cuộc họp bên dưới, hãy tóm tắt các điểm chính bao gồm: (1) Các nội dung đã thảo luận, (2) Các quyết định đã đưa ra, (3) Các vấn đề kỹ thuật được đề cập. Viết ngắn gọn, rõ ràng, có đánh số."*
3. **Bước 4 - Trích xuất hành động (Action Items)**:
   > *"Bạn là trợ lý trích xuất hành động từ cuộc họp. Từ nội dung cuộc họp bên dưới, hãy liệt kê tất cả các hành động cần thực hiện sau cuộc họp. Mỗi hành động gồm: (1) Mô tả việc cần làm, (2) Người phụ trách (nếu được đề cập), (3) Deadline (nếu có). Trả về dạng danh sách đánh số."*

---

## 3. 🎨 Đặc Tả 6 Màn Hình Giao Diện (BA v2)

### Màn hình 1: Danh Sách Cuộc Họp (`MeetingNotesActivity`)
- **Header**: Tiêu đề *"Meeting Notes"*, phụ đề *"G720 AI Box · On-device AI"*, nút Search, nút Settings.
- **Language Tabs**: 3 Tab: *Tất cả*, *Tiếng Việt*, *English*.
- **Meeting Cards**:
  - Tên cuộc họp, ngày giờ, thời lượng, số từ transcript, số action items.
  - Badge trạng thái: *Hoàn tất* (xanh lá) hoặc *Đang ghi* (viền đỏ nhấp nháy, timer realtime).
  - Badge ngôn ngữ: `VI` hoặc `EN`.
  - Preview tóm tắt ngắn (max 2 dòng).
- **Floating Action Button (FAB)**: Nút tròn màu xanh primary (icon Mic) ở góc dưới phải để tạo cuộc họp mới.
- **Bottom Navigation**: 2 mục: *Meetings* và *Cài đặt*.

### Màn hình 2: Ghi Âm Đa Phân Đoạn - Multi-Segment (`MeetingRecordActivity`)
*Bố cục ngang chia 2 Panel (tối ưu cho màn hình 1024x600):*
- **Panel Trái - Điều khiển ghi âm**:
  - Tên đoạn hiện tại (vd: `Hop_BSP_03`).
  - Vòng tròn ring animation với đồng hồ đếm thời gian ở giữa (vd: `08:24`), chấm đỏ REC nhấp nháy.
  - Audio waveform thanh level RMS trực quan.
  - **4 Nút điều khiển chính**:
    1. `Tạm dừng`: Pause/Resume trong cùng segment (không ghi âm lúc pause).
    2. `Dừng đoạn`: Kết thúc segment hiện tại, ghi flush file WAV.
    3. `Đoạn mới`: Kết thúc đoạn cũ và tạo segment mới ngay lập tức.
    4. `Đánh dấu`: Bookmark timestamp quan trọng.
- **Panel Phải - Danh sách đoạn đã ghi**:
  - Danh sách thẻ từng segment: STT (`01`, `02`), tên file (`Hop_BSP_01.wav`), thời lượng, dung lượng, số lần pause.
  - Badge trạng thái: `REC` (đỏ - đang ghi) hoặc `Đã lưu` (xanh - đã xong).
  - Khung thông tin tổng quan: Tổng thời lượng, tổng dung lượng, format audio (16kHz/16bit/Mono), Model ASR.
- **Quy tắc đặt tên file**: `{Tên_cuộc_họp}_{số_thứ_tự:02d}.wav`.

### Màn hình 3: Chọn Đoạn Ghi Âm Để Xử Lý (`MeetingSelectSegmentsActivity` - [MỚI])
*Xuất hiện sau khi hoàn tất ghi âm để tối ưu hóa thời gian chạy AI:*
- **Panel Trái - Danh sách segments**:
  - Checkbox chọn từng đoạn (mặc định chọn tất cả).
  - Nút *"Chọn tất cả / Bỏ chọn"* thao tác nhanh.
  - Thông tin segment: Tên file, thời lượng, dung lượng, ghi chú (vd: "Pause 2 lần").
  - Nút **Play** để nghe lại preview âm thanh trước khi quyết định.
  - Đoạn bỏ tick: Giảm opacity mờ nhạt kèm nhãn cam (vd: *"Giải lao - Bỏ chọn"*).
- **Panel Phải - Thông tin & Ước tính AI**:
  - Thống kê: Số đoạn đã chọn / Tổng số đoạn (vd: `3 / 4 đoạn`).
  - Tổng thời lượng và dung lượng các đoạn được chọn.
  - **Thời gian xử lý ước tính**:
    - Bước 1 ASR (PhoWhisper): ~5 phút
    - Bước 2 Sửa lỗi (Qwen2.5): ~3 phút
    - Bước 3+4 Tóm tắt & Actions: ~2 phút
    - **Tổng ước tính**: ~10 phút
  - Tip box hỗ trợ người dùng.
- **Nút Hành động**: Nút góc trên phải `Xử lý AI (N đoạn)`.

### Màn hình 4: Tiến Trình Xử Lý AI (`MeetingProcessingActivity`)
- **Vertical Pipeline Timeline (4 bước)**:
  - Icon trạng thái: `✓` xanh lá (hoàn tất), icon xoay xanh dương (đang xử lý), xám (đang chờ).
  - Đường kẻ timeline đổi màu theo tiến trình.
- **Khu vực Preview song song**:
  - Hộp trên: **TEXT GỐC (BƯỚC 1)** (PhoWhisper raw text có lỗi chính tả).
  - Hộp dưới: **TEXT ĐÃ SỬA (BƯỚC 2)** (Qwen2.5 cleaned text).
- **Thanh tiến độ & thời gian**:
  - Ước tính thời gian còn lại (vd: `~5 phút`).
  - Bước hiện tại (vd: `2/4`).
- **Chạy nền**: Sử dụng Android Foreground Service với thông báo hệ thống để user có thể chuyển màn hình mà không bị ngắt.

### Màn hình 5: Kết Quả Cuộc Họp (`MeetingResultActivity`)
- **Metadata Bar**: Thời lượng (vd: 45 phút), Số từ (2,847 từ), Ngôn ngữ (Tiếng Việt), Models (`PhoWhisper + Qwen2.5`).
- **3 Tab xem kết quả**:
  1. **Tab Tóm tắt**: Khung nền xanh nhẹ hiển thị các điểm chính, quyết định, vấn đề kỹ thuật được đánh số rõ ràng.
  2. **Tab Hành động (Action Items)**: Danh sách việc cần làm, tên người phụ trách (**in đậm**), deadline cụ thể.
  3. **Tab Transcript**: Toàn bộ nội dung cuộc họp đã sửa lỗi, có thanh cuộn đọc.
- **Nút hành động dưới cùng**:
  - `Đọc tóm tắt`: Kích hoạt Android TTS Engine phát ra loa.
  - `Xuất báo cáo`: Xuất file PDF / TXT / DOCX ra bộ nhớ thiết bị.

### Màn hình 6: Cài Đặt Hệ Thống (`MeetingSettingsActivity`)
- **Lựa chọn Ngôn ngữ & Model ASR**:
  - 2 Card lớn: `Tiếng Việt (PhoWhisper - 245MB)` và `English (Whisper AI Hub)`.
  - Hiển thị thông số: kích thước, độ chính xác, nguồn.
- **Cấu hình Model LLM (Qwen2.5 3B)**:
  - Slider CPU Threads (mặc định 4 threads tối ưu chip Genio 720).
  - Slider Temperature (mặc định `0.3` để chống ảo giác / hallucinations).
- **Cấu hình Âm thanh I2S MIC**:
  - Sample rate (16kHz), Bit depth (16-bit), Channels (Mono), Format WAV PCM.
  - Toggle: *Tự động phát hiện im lặng* (Voice Activity Detection).
- **Phân bổ Bộ nhớ (Storage Chart)**:
  - Biểu đồ phân khúc màu: Models (2.1 GB) | Audio files (520 MB) | Text data (85 MB) | Trống (7.3 GB).
- **Tùy chọn Báo cáo**:
  - Định dạng mặc định (PDF), toggle tự động đọc tóm tắt khi hoàn tất.

---

## 4. 🗄️ Kiến Trúc Dữ Liệu (Room Database v2)

### Table `meetings`
- `id`: Long (Primary Key, AutoGenerate)
- `title`: String
- `created_at`: Long (Timestamp)
- `duration_ms`: Long
- `word_count`: Int
- `language`: String (`"vi"` / `"en"`)
- `asr_model`: String
- `llm_model`: String
- `status`: String (`"RECORDING"`, `"PROCESSING"`, `"COMPLETED"`)
- `raw_transcript`: String
- `clean_transcript`: String
- `summary`: String
- `action_items_json`: String (JSON mảng ActionItem)

### Table `meeting_segments` [MỚI CHO BA v2]
- `id`: Long (Primary Key, AutoGenerate)
- `meeting_id`: Long (Foreign Key -> `meetings.id`)
- `segment_index`: Int (1, 2, 3...)
- `file_path`: String (vd: `/sdcard/MeetingNotes/Hop_BSP_01.wav`)
- `file_name`: String
- `duration_ms`: Long
- `file_size_bytes`: Long
- `pause_count`: Int
- `is_selected`: Boolean (Mặc định `true`)
- `note`: String (vd: "Pause 2 lần", "Giải lao")
- `created_at`: Long
