# BENCHMARKS — Meeting Notes App (G720 AI Box)

> Mẫu ghi kết quả đo. Điền khi chạy từng cổng; mọi con số trong `PROJECT_CONTEXT.md` đánh dấu **[ƯỚC TÍNH]** phải được thay bằng số ở đây.
> **Cách ghi:** ngày, bản build, nhiệt độ môi trường, thread, ngữ cảnh. Chạy lại khi đổi model, build llama.cpp/whisper.cpp, tham số VAD hoặc prompt.

## Bộ test chuẩn (làm một lần)

- 20–30 phút họp thật bằng chính mic của box, có thuật ngữ trong glossary; chép tay chuẩn; đánh dấu thuật ngữ Anh để tính CS-WER và TER.
- 5 cuộc họp (15–60 phút) cho chấm tóm tắt (G9); 2 cuộc họp ≥3 người cho diarization (G-D).
- Ghi người chấm, tiêu chí: đủ ý, đúng tên/số/hạn, không bịa (0 = bịa nặng, 1 = bịa nhẹ, 2 = trung thực).

## Cổng

| # | Đo gì | Cách đo | Ngưỡng đề xuất | Kết quả | Ngày |
|---|---|---|---|---|---|
| G0 | Loại RAM, số nhân, đặc tính CPU, nhiệt | `adb shell cat /proc/cpuinfo`, `/proc/meminfo`, thermal zones, hỏi BSP | Ghi lại | | |
| G1 | llama.cpp CPU: prefill/decode (Qwen2.5-3B, Qwen3-4B; Q4_0, Q4_K_M; 2/4/6 thread) | `llama-bench` bản NDK | Ghi lại; quyết định D6 | | |
| G2 | whisper.cpp: RTF PhoWhisper base, small; chunk ≤30 s vs ngắn; 2/4/6 thread | Cùng file 10 phút | RTF ≤ 0.35 (base) | | |
| G3 | RAM từng giai đoạn; KV cache theo `n_ctx` (4096/8192/16384); chạy chồng ASR+LLM | `dumpsys meminfo`, log nội bộ | Peak ≤ 5.5 GB | | |
| G4 | Nhiệt khi chạy liên tục 10′ và 30′ | Đọc thermal zones mỗi 5 s | < 75°C | | |
| G5 | Ghi 2 giờ tắt màn hình + VAD realtime | Đếm xrun, kiểm tra WAV liền mạch | 0 xrun, WAV đủ độ dài | | |
| G6 | WER / CS-WER / TER: raw vs HPF+norm vs +khử nhiễu; có/không prompt; có/không alias | Bộ test chuẩn | TER ≥ 90% (mục tiêu) | | |
| G7 | I2S mic qua `AudioRecord` (`SOURCE_*`, AGC/NS) | `tinycap`, app thử | Có tín hiệu, mức ổn | | |
| G8 | TTS tiếng Việt khả dụng | Cài đặt hệ thống | Có/không | | |
| G9 | Tóm tắt: Qwen2.5-3B vs Qwen3-4B vs Qwen3-1.7B (và 7B cho reduce) | 5 cuộc họp, chấm 0–2 | Chọn mô hình có độ trung thực cao nhất đạt S5 | | |
| G-N1 | Chạy gói DLA Qwen2.5-3B trong app Android: prompt/decode thực, ngữ cảnh tối đa, RAM | App thử / JNI Demo | ≥ số MediaTek −10% | | |
| G-N2 | Chất lượng tóm tắt NPU ≥ CPU; tỉ lệ JSON parse thành công | Như G9 | ≥ 95% parse | | |
| G-N3 | Chuyển PhoWhisper-base/small → DLA; WER so với CPU; kiểm tra "garbage transcript" | Toolkit MediaTek; bộ test chuẩn | Chênh WER ≤ ngưỡng chốt | | |
| G-D | Diarization: DER và thời gian CPU trên 2 cuộc họp thật | sherpa-onnx offline | Ghi lại; so với 24.4% tham chiếu | | |

## Nhật ký (dán kết quả chi tiết bên dưới)

```
[YYYY-MM-DD] Gx — mô tả — kết quả — kết luận/việc tiếp theo
```
