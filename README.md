# 🎙️ Meeting Notes App — G720 AI Box

> **Ứng dụng ghi chú và tóm tắt cuộc họp thông minh chạy On-Device (hoàn toàn ngoại tuyến) trên thiết bị MediaTek Genio 720 (Android 15, 10-inch Landscape 1024x600).**

---

## 🚀 Tính Năng Chính (Theo BA v2)

1. **Ghi Âm Đa Đoạn (Multi-Segment Audio Recording)**:
   - Thu âm chất lượng cao qua **I2S MIC** (16kHz / 16-bit / Mono / WAV PCM).
   - Hỗ trợ chia đoạn tự động hoặc thủ công (`Hop_BSP_01.wav`, `Hop_BSP_02.wav`...), cho phép tạm dừng/tiếp tục giữa các phần cuộc họp.
2. **Chọn Đoạn Ghi Âm Tối Ưu Hóa (Smart Segment Selection)**:
   - Người dùng xem trước, nghe thử và chọn lọc các đoạn âm thanh cần phân tích.
   - Loại bỏ các đoạn nghỉ giải lao, nói chuyện phiếm giúp tiết kiệm 40-60% thời gian xử lý NPU.
3. **Pipeline Xử Lý AI 4 Bước Khép Kín**:
   - **Bước 1 (ASR)**: PhoWhisper (Tiếng Việt) / Whisper (English).
   - **Bước 2 (Text Clean)**: Qwen2.5 3B sửa chính tả, ngữ pháp, tên riêng, thuật ngữ kỹ thuật.
   - **Bước 3 (Summary)**: Qwen2.5 3B tóm tắt các điểm chính, quyết định và vấn đề kỹ thuật.
   - **Bước 4 (Action Items)**: Qwen2.5 3B trích xuất việc cần làm, người phụ trách, deadline.
4. **Báo Cáo & TTS**:
   - Giao diện kết quả 3 tab (Tóm tắt | Hành động | Transcript).
   - Đọc bản tóm tắt bằng giọng nói qua loa (TTS Engine).
   - Xuất báo cáo đa định dạng (PDF, DOCX, TXT).

---

## 🏛️ Cấu Trúc Thư Mục Chuẩn Hệ Thống

```
NeuronPilot_Summary_App/
├── .agents/                               # Bộ quy chuẩn AI Agent, Rules và Skills chuẩn Android
│   ├── rules/                             (GEMINI.md, security.md, global-standard.md...)
│   └── skills/                            (clean-code, mobile-design, edge-to-edge, profiler...)
├── AGENTS.md                              # Zero-Trust Agent Router & Invariants Gateway
│
├── docs/                                  # Tài liệu kỹ thuật, BA và kiến trúc
│   ├── Meeting_Notes_App_Tablet_1.pdf     (Tài liệu BA v1)
│   ├── Meeting_Notes_App_Tablet_v2.pdf    (Tài liệu BA v2)
│   ├── UI_SPEC_BA_V2.md                   (Đặc tả kỹ thuật & giao diện BA v2)
│   ├── architecture/                      (Hướng dẫn kiến trúc & nạp model)
│   ├── logs/                              (Logcat và dữ liệu trace)
│   └── references/                        (Mã nguồn tham khảo bên ngoài)
│
└── app/                                   # Module ứng dụng Android chính
    ├── src/main/cpp/                      # [BẤT BIẾN] Native C++ JNI & NeuroPilot SDK Drivers
    ├── src/main/jniLibs/                  # [BẤT BIẾN] Prebuilt shared libraries (.so)
    └── src/main/java/
        ├── com.mediatek.neuropilot.jnidemo/ # [BẤT BIẾN] Hardware JNI & NPU Bridge
        └── com.bhs.meetingnotes/          # [PHẦN CHÍNH] Meeting Notes Application Layer
            ├── data/                      (Room Database, Entities, Audio Recorder)
            ├── domain/                    (Meeting AI Pipeline, Prompts)
            ├── ui/                        (Màn hình List, Record, Select, Processing, Result, Settings)
            └── util/                      (Waveform, TTS, ReportExporter, Formatters)
```

---

## 🛡️ Nguyên Tắc An Toàn (Zero Regression)
- Không chỉnh sửa các thư viện C++ JNI và cấu hình NPU (`src/main/cpp/*`, `CMakeLists.txt`, `jniLibs/*`).
- Giữ vững tỷ lệ màn hình 10-inch Landscape 1024x600 trên Android 15.
- Tất cả xử lý nặng (ASR, LLM inference, AudioRecord) chạy trong Background Thread / Foreground Service.

---

## 📚 Tài Liệu Tham Khảo Thêm
- [Đặc tả UI/UX theo BA v2](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/docs/UI_SPEC_BA_V2.md)
- [Kiến trúc NeuroPilot Runtime](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/docs/architecture/architecture_guide_v2.md)
- [Hướng dẫn nạp Model qua ADB](file:///d:/DMHoang/Project_GitHub/NeuronPilot_Summary_App/docs/architecture/MODEL_PUSH_GUIDE.md)
