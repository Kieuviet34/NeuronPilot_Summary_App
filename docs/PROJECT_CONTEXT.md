# PROJECT CONTEXT — Meeting Notes App (G720 AI Box)

> **v2.0 · 02/10/2026.** Nguồn sự thật cho AI/lập trình viên. Đọc toàn bộ trước khi viết hoặc sửa code.
> - Yêu cầu mới mâu thuẫn với mục **3 (Ràng buộc cứng)** → DỪNG và hỏi người dùng.
> - **[CHỜ CHỐT]** = đề xuất, chưa phải yêu cầu. **[ƯỚC TÍNH]** = suy ra từ số liệu khác, chưa đo trên G720.
> - Mã `[N#]` trỏ tới nguồn trong `RESEARCH_NOTES.md`. Phân tích và phương án: `TECHNICAL_REVIEW.md`. Kết quả đo: `BENCHMARKS.md`.

---

## 1. Mục tiêu

**Một câu:** Ứng dụng Android chạy **offline hoàn toàn** trên G720 AI Box, ghi âm cuộc họp (tiếng Việt xen thuật ngữ tiếng Anh) qua mic I2S, rồi tạo **transcript sạch, tóm tắt, danh sách hành động** bằng AI chạy trên thiết bị.

**Người dùng:** nhóm kỹ thuật BHS Electronics, họp kỹ thuật (BSP, driver, sprint, boot, kernel…). Họp thường 15–60 phút; thiết kế chịu tải **tới 2 giờ** **[CHỜ CHỐT]**.
**Vì sao on-device:** nội dung họp nhạy cảm; không phụ thuộc internet.

### Tiêu chí thành công (đo được) — mọi ngưỡng **[CHỜ CHỐT]** sau khi có baseline

| # | Tiêu chí | Mục tiêu đề xuất |
|---|---|---|
| S1 | Không mất dữ liệu ghi âm | Crash/mất điện: khôi phục WAV, mất tối đa ~30 giây cuối |
| S2 | Ghi âm ổn định | 2 giờ liên tục, tắt màn hình, không underrun/xrun |
| S3 | Thuật ngữ đúng | Recall thuật ngữ glossary (TER) ≥ 90% trên bộ test phòng họp thật |
| S4 | ASR | Đo WER và CS-WER (WER riêng cho đoạn xen tiếng Anh); ngưỡng chốt sau baseline |
| S5 | Thời gian xử lý | ≤ 0.5× thời lượng họp (Plan B), ≤ 0.2× nếu đi đường NPU (Plan A) |
| S6 | Không bịa | Tên/số/deadline trong tóm tắt và action đều có trong transcript (validator kiểm) |
| S7 | Tài nguyên | RAM peak ≤ 5.5 GB; nhiệt SoC < 75°C kéo dài |

---

## 2. Phạm vi

**MVP:** ghi nhiều đoạn (pause/resume, bookmark), chọn đoạn, pipeline AI, kết quả 3 tab, glossary + alias, xuất PDF/TXT, TTS (nếu có engine tiếng Việt), cài đặt, **R1** (VAD realtime + mức âm).

**Phase 2 [CHỜ CHỐT]:** phụ đề nháp trực tiếp (R2-lite), ASR nền trong lúc ghi (R3), phân tách người nói offline (R4), tổng hợp nhiều cuộc họp (họp giao ban), fine-tune PhoWhisper bằng dữ liệu công ty.

**Ngoài phạm vi:** cloud khi chạy, nhận diện người nói realtime theo tên (R5), dịch, đồng bộ nhiều thiết bị, DOCX, iOS.

---

## 3. Ràng buộc cứng

| ID | Ràng buộc |
|---|---|
| C1 | **Offline khi chạy.** Không network call cho audio/text, không telemetry nội dung. (Dữ liệu huấn luyện: theo chính sách công ty, cần đồng ý người tham gia.) |
| C2 | Genio 720, 8 GB RAM, Android 15, màn 1024×600 landscape, ABI `arm64-v8a`, Java + C++ (NDK/JNI). |
| C3 | Audio 16 kHz / 16-bit / mono PCM, lưu WAV; mỗi segment 1 file độc lập. |
| C4 | Mặc định **1 model nặng trong RAM tại một thời điểm**. Chạy chồng (ví dụ ASR CPU + LLM NPU) chỉ sau khi G3 chứng minh RAM đủ. Model phụ nhỏ (VAD, diarization) giải phóng trước khi nạp model nặng. |
| C5 | **Ghi âm là ưu tiên số 1.** Thread ghi dùng `THREAD_PRIORITY_URGENT_AUDIO`, không bị chặn bởi DB/UI/inference. Ghi trong **Foreground Service loại `microphone`**, khởi động khi app đang ở foreground [N36]. |
| C6 | Không inference/IO nặng trên main thread. |
| C7 | RAM peak ≤ 5.5 GB. Không nạp nguyên file audio dài vào Java heap; đọc theo block/native [N30]. |
| C8 | Giám sát nhiệt SoC; throttle ở 65°C / 75°C. |
| C9 | Output tiếng Việt; **giữ nguyên thuật ngữ Anh và tên riêng**. |
| C10 | LLM không đáng tin: temperature ≤ 0.3, output qua validator, luôn lưu `raw_transcript` gốc. |
| C11 | Không ghi đè/xóa WAV gốc; pipeline **resumable** (checkpoint theo chunk). |
| C12 | Engine/model chỉ chọn trong danh sách mục 5, theo kết quả cổng benchmark. Ngoài danh sách → hỏi. |
| C13 | **Plan B (CPU thuần, không NDA) luôn phải chạy được.** Mọi tối ưu NPU là lớp thêm vào, có fallback CPU. |

---

## 4. Nền tảng phần cứng (đã xác minh từ tài liệu MediaTek)

| Hạng mục | Thông số | Hệ quả thiết kế |
|---|---|---|
| CPU | 2× Cortex-A78 ≤2.6 GHz + 6× Cortex-A55 2.0 GHz [N1] | big.LITTLE: thử `n_threads` = 2 (nhân lớn) / 4 / 6, ghim affinity; không mặc định 4 |
| NPU | MediaTek 8th gen, 1× MDLA 5.3, 9 TOPS (10 TOPS hệ thống) [N1][N2] | Có đường GenAI chính thức |
| RAM | LPDDR4X-4266 x32 (≤8 GB) **hoặc** LPDDR5(X)-6400 x32 (≤16 GB) [N1] | Băng thông lý thuyết ≈ 17 GB/s (LP4X) / 25.6 GB/s (LP5) **[ƯỚC TÍNH]**. Decode LLM bị chặn bởi băng thông: trần ≈ băng thông ÷ kích thước trọng số. **Phải xác nhận loại RAM của box (G0)** |
| LLM trên NPU (MediaTek đo, prompt / decode tok/s) | Qwen2.5-3B **163 / 10.6** · Qwen3-4B 126 / 7.3 · Qwen3-1.7B 263 / 13.8 · Qwen2.5-1.5B 337 / 19.3 [N4] | Decode ~10 tok/s là **trần thực tế** cho 3B: không sinh lại toàn văn |
| ASR trên NPU | Whisper-base 8w16a: encoder 160.6 ms (cửa sổ 30 s) + decode 174.8 tok/s; Qwen3-ASR-0.6B: 127.6 / 7.1 tok/s [N4][N5] | Whisper-base trên NPU nhanh hơn CPU nhiều lần **[ƯỚC TÍNH]**; Qwen3-ASR quá chậm cho batch |
| Truy cập | Gói DLA dựng sẵn: công khai. GAI toolkit (chuyển model riêng): **NDA**. Tài liệu/công cụ AI cho Android: giới hạn Direct Customer có tài khoản MOL [N7][N8][N11] | Quyết định D12 |
| OS | Android 15 được hỗ trợ [N3] | Foreground service types, scoped storage |

---

## 5. Kiến trúc mục tiêu và ba phương án

```
Ghi âm (FGS microphone) → WAV/segment ─┬─ R1: VAD realtime + mức âm
                                       └─ [chọn segments] → tiền xử lý → (diarization*) → gom chunk ≤30s
→ ASR (+ glossary) → chống ảo giác → sửa bằng luật/alias → LLM map (JSON) → reduce → validator → Room DB
                                                                               (* Phase 2)
```

| Plan | ASR | LLM | Điều kiện | Thời gian xử lý họp 60′ / 120′ **[ƯỚC TÍNH]** |
|---|---|---|---|---|
| **B** (nền tảng) | whisper.cpp + PhoWhisper (CPU) | llama.cpp + Qwen2.5-3B/Qwen3-4B (CPU) | Không cần NDA | 25–36′ / 50–70′ (hoặc ~15′ / ~30′ nếu ASR chạy nền lúc ghi) |
| **C** (lai) | như B | NPU, gói DLA dựng sẵn (Qwen2.5-3B) | Gói chạy được trong app Android (G-N1) | 16–27′ / 32–54′ (có thể chồng ASR và LLM) |
| **A** (NPU) | NPU, PhoWhisper chuyển bằng toolkit MediaTek | NPU như C | Toolkit + PhoWhisper chuyển được, WER chấp nhận được (G-N3) | ~8′ / ~16′ |

**Quy tắc chọn:** làm Plan B trước (C13). Lên C khi G-N1 và G-N2 đạt. Lên A khi G-N3 đạt. Chi tiết tính toán và giả định: `TECHNICAL_REVIEW.md` §3.

---

## 6. Bất biến (invariants)

- I1. PAUSE: `AudioRecord` vẫn chạy, buffer bị bỏ, **không** ghi WAV → không có khoảng im lặng khi resume.
- I2. `bookmarks.timestamp_ms` theo **thời gian trong file WAV** (đã trừ pause).
- I3. Header WAV cập nhật mỗi 30 giây và khi đóng; khi mở app, tìm segment `recording` để sửa header từ kích thước file thực.
- I4. `UNIQUE(meeting_id, segment_number)`, số tăng theo meeting.
- I5. Transcript gộp theo `segment_number`, ngăn cách `---`.
- I6. Mốc thời gian chunk lấy từ VAD/audio, không phụ thuộc timestamp do ASR sinh.
- I7. Thuật ngữ glossary là **chuẩn**; biến thể sai sửa bằng luật xác định **trước** khi nhờ LLM.
- I8. Mọi chỉnh sửa tự động ghi log (vị trí, trước, sau) và hoàn tác được.
- I9. Mỗi hành động/ý chính giữ `chunk_index` nguồn để truy vết.
- I10. Chunk bị đánh dấu ảo giác không bao giờ được dùng làm prompt cho chunk sau.

---

## 7. Quyết định cần chốt

| ID | Vấn đề | Đề xuất |
|---|---|---|
| D1 | PDF: 4 bước (tóm tắt và action là 2 lần inference riêng); MD: gộp 3+4 | Gộp 1 lần; UI hiển thị 3 bước; cập nhật PDF |
| D2 | Temperature 0.3 (PDF) vs 0.2 (MD) | 0.2 sửa lỗi, 0.3 tóm tắt; slider tối đa 0.5 |
| D3 | Màn danh sách: văn bản PDF nói FAB + bottom nav, ảnh mockup là sidebar landscape | Theo mockup sidebar |
| D4 | Xuất: PDF/DOCX vs PDF/TXT vs chỉ PdfExporter | MVP: PDF + TXT |
| D5 | Nút "Đoạn mới" chưa có trong state machine | Thêm transition nguyên tử |
| D6 | Bước sửa lỗi LLM sinh lại toàn văn | **Bỏ.** Thay bằng luật + LLM chỉ xuất chỉnh sửa (xem mục 8, 10) |
| D7 | `/sdcard/MeetingNotes` có thể bị chặn bởi scoped storage | Thư mục app-specific, trừ khi BSP cấp quyền rộng |
| D8 | TTS tiếng Việt phụ thuộc engine trên box | Kiểm tra; không có thì ẩn nút |
| D9 | Mức realtime | Mặc định R1; R2-lite ở Phase 2 |
| D10 | Họp tối đa | 2 giờ |
| D11 | Model Whisper English của AI Hub không phải ggml (đường NPU: DLA) | Viết `NpuWhisperEngine` riêng sau interface `AsrEngine` |
| D12 | BHS có quyền truy cập NeuroPilot Android/GAI toolkit (Direct Customer/NDA) không? App NeuroPilot JNI Demo trên thiết bị đang dùng runtime nào, chạy model gì? | Hỏi MediaTek/ODM; kiểm tra demo trước |
| D13 | "245 MB" trong tài liệu gốc: PhoWhisper-base có 74M tham số, small có 244M [N14] | Xác nhận bản đang tích hợp là base hay small, độ lượng tử hóa |
| D14 | Qwen2.5-3B (nhanh hơn) hay Qwen3-4B (chậm hơn ~30% trên NPU) | Chọn theo bộ test tóm tắt tiếng Việt (G9) |
| D15 | Bản gói NPU có `cache` cố định (gói Qwen3-ASR dùng 2048 [N5]); gói LLM có thể tương tự | Xác nhận; chunk LLM ≤ ~1.200 token vào |

---

## 8. Chiến lược ASR và thuật ngữ Anh (có bằng chứng)

**Thực tế:** trên bộ test tiếng Việt xen thuật ngữ Anh (y khoa), PhoWhisper-small zero-shot có WER 36.3% và **CS-WER (riêng đoạn xen tiếng Anh) 62.6%**; PhoWhisper-large 55.1%; Whisper-large-v3 46.7% [N15]. Tức là ~một nửa thuật ngữ Anh bị sai nếu không xử lý. Kỳ vọng tương tự cho thuật ngữ kỹ thuật cho đến khi G6 đo thật.

**Thang cải thiện (đã đo trong [N15], PhoWhisper-small, 24 giờ huấn luyện):**

| Cách | WER | CS-WER | Ghi chú |
|---|---|---|---|
| Zero-shot | 36.3 | 62.6 | Mốc |
| Biasing lúc giải mã (RS/DV) | 35.6 / 36.3 | 60.1 / 62.5 | Cải thiện nhỏ |
| Chuẩn hóa sau giải mã (AdaCS) | 39.4 | 32.9 | CS-WER giảm mạnh nhưng WER tăng |
| **LoRA fine-tune** | 27.1 | 30.3 | Hiệu quả nhất trong nhóm đơn giản |
| LoRA + dẫn hướng ngôn ngữ (AG) | **23.7** | **19.5** | Tốt nhất |

**Kế hoạch:**
1. **Ngay (MVP):** glossary + alias + sửa bằng luật xác định có ngưỡng (giống tinh thần AdaCS: ưu tiên precision, ghi log, hoàn tác được). `initial_prompt` có thể thử nhưng **chưa có số đo tin cậy**, chỉ giữ nếu G6 cho thấy lợi.
2. **Phase 2:** fine-tune LoRA PhoWhisper (base/small) bằng dữ liệu họp công ty: giữ nguyên lớp Whisper nên chuyển sang ggml hoặc toolkit NPU được. Dữ liệu nhãn có thể tạo bằng mô hình lớn chạy trên PC (teacher) rồi người sửa riêng các thuật ngữ.
3. Vòng dữ liệu: bản sửa tay của người dùng trong tab Transcript là nguồn dữ liệu và bộ test (có đồng ý).

**Chống ảo giác Whisper:** Silero VAD trước ASR; beam size 1; `no_speech` 0.6, `logprob` −1.0, `compression_ratio` 2.4 làm điểm xuất phát; tự kiểm tra lặp n-gram (ngưỡng compression của Whisper không luôn hiệu quả [N25]); danh sách chặn câu ảo giác; không dùng chunk nghi ảo giác làm prompt (I10).

---

## 9. Realtime và người nói (phân tầng)

| Mức | Nội dung | Trạng thái |
|---|---|---|
| **R1** | VAD (Silero) + thanh mức âm + "đang có giọng nói", thread riêng có hàng đợi | **MVP** |
| **R2-lite** | Phụ đề nháp: cắt theo điểm kết thúc VAD, giải mã từng câu bằng model nhỏ (Zipformer-vi-30M ~75 MB INT8 hoặc Moonshine-base-vi qua sherpa-onnx) [N18][N21]; sau họp, bản chính thay bản nháp. **Không** gọi lại `whisper_full` trên buffer đang lớn dần (chậm hơn thời gian thực nhiều lần [N24]) | Phase 2 |
| **R3** | ASR chính chạy nền theo từng chunk lúc ghi, không hiện chữ | Phase 2, cổng G4/G5 |
| **R4** | Diarization offline: "Người nói 1/2/3", đổi tên được; người dùng nhập **số người tham dự** (đếm biết trước ổn định hơn tự động) | Phase 2 |
| R5 | Nhận diện người nói realtime theo tên (đăng ký giọng mẫu) | Chỉ xét sau R4 nếu đo EER ngoài thực tế đạt |

Kỳ vọng thực tế cho R4: mic đơn, xa 1–3 m. Pyannote 3.1 báo DER 24.4% trên AliMeeting (họp xa mic đơn) so với <8% trên audio phát thanh sạch [N35]; nghĩa là cỡ 1/4 thời lượng có thể gán nhầm. Pipeline đổi thành `VAD → diarization → chunk theo lượt nói → ASR`.

---

## 10. LLM: quy tắc thiết kế

1. **Không sinh lại toàn văn.** Với NPU decode ~10.6 tok/s, sinh lại 15.000 token (họp 60′) mất ~24 phút; CPU còn lâu hơn **[ƯỚC TÍNH]** [N4][N28].
2. Chuỗi: luật xác định → (tùy chọn) LLM chỉnh sửa có chọn lọc cho chunk tin cậy thấp → **map** (JSON mỗi chunk) → **reduce** → validator.
3. Đếm token bằng **tokenizer thật của model**, không dùng `từ × 1.8` [N30].
4. Trên CPU dùng **GBNF/JSON schema** để JSON hợp lệ theo cấu trúc; schema không nằm trong prompt nên vẫn phải mô tả cấu trúc trong prompt [N26][N27]. Trên NPU (không có grammar): parse + validate + thử lại một lần.
5. Validator: tên người phải thuộc danh sách tham dự/glossary, nếu không ghi "Chưa xác định"; deadline phải có trong văn bản; `replace` không được tạo thuật ngữ ngoài glossary/transcript.
6. Mỗi hành động giữ `chunk_index` (I9). UI cho sửa người phụ trách/hạn, giữ transcript gốc luôn truy cập được [N33].
7. Model nhỏ hay bịa khi tóm tắt [N31]: luôn cho phép tắt/bật hiển thị chỉnh sửa và xem bản gốc.

---

## 11. Cổng benchmark (chi tiết và mẫu kết quả: `BENCHMARKS.md`)

| # | Đo gì | Quyết định |
|---|---|---|
| G0 | Loại RAM (LP4X/LP5), `/proc/cpuinfo` (dotprod/i8mm), số nhân, đọc nhiệt | Trần băng thông, build llama.cpp |
| G1 | llama.cpp CPU: prefill/decode tok/s (Qwen2.5-3B, Qwen3-4B; Q4_0 và Q4_K_M; 2/4/6 thread) | Plan B, D6 |
| G2 | whisper.cpp: RTF PhoWhisper base và small; chunk đóng gói ≤30 s vs ngắn | Chọn base/small |
| G3 | RAM từng giai đoạn; KV cache theo `n_ctx`; chạy chồng ASR+LLM | C4, C7 |
| G4 | Nhiệt 10′ và 30′ liên tục | C8, R3 |
| G5 | Ghi 2 giờ tắt màn hình + VAD realtime | S2, R1 |
| G6 | WER, CS-WER, TER trên 20–30 phút họp thật: raw vs HPF+normalize vs RNNoise; có/không prompt; có/không alias | Tiền xử lý, S3 |
| G7 | I2S mic qua `AudioRecord` (`SOURCE_*`, AGC/NS) | Nền tảng |
| G8 | Engine TTS tiếng Việt | D8 |
| G9 | Tóm tắt tiếng Việt: Qwen2.5-3B vs Qwen3-4B vs Qwen3-1.7B trên 5 cuộc họp thật, chấm độ trung thực | D14 |
| G-N1 | Chạy gói DLA Qwen2.5-3B dựng sẵn trong app Android (prompt/decode thực, context tối đa) | Lên Plan C |
| G-N2 | Chất lượng tóm tắt NPU ≥ CPU; JSON parse thành công ≥ 95% | Lên Plan C |
| G-N3 | Chuyển PhoWhisper-base/small → DLA; WER chênh so với CPU ≤ ngưỡng chốt; không "garbage transcript" [N12] | Lên Plan A |
| G-D | Diarization: DER trên 2 cuộc họp thật, CPU time | R4 |

---

## 12. Quy tắc làm việc cho AI

1. Đọc file này trước mỗi phiên; khi lập kế hoạch nêu rõ ràng buộc C1–C13 và bất biến I1–I10 bị ảnh hưởng.
2. Không đổi ràng buộc, engine, schema DB hoặc thứ tự pipeline nếu chưa hỏi; khi đề xuất đổi, kèm số đo hoặc nguồn.
3. Mâu thuẫn giữa PDF, MD và file này → ưu tiên file này, ghi vào mục 7.
4. Số **[ƯỚC TÍNH]** không dùng để cắt tính năng; chờ `BENCHMARKS.md`.
5. Mọi output LLM qua validator; mọi bước pipeline có checkpoint.
6. Mỗi tính năng xong phải có test cho các bất biến liên quan, xử lý hủy/lỗi giữa chừng, và không làm vượt C7.
7. Mọi engine nằm sau interface (`AsrEngine`, `LlmEngine`, `Diarizer`, `Vad`) để thay CPU/NPU không đụng orchestrator.
8. Giao diện tiếng Việt, tối ưu 1024×600 landscape, thao tác chính một chạm.
9. Xong thay đổi lớn: cập nhật mục 7 (quyết định) và `BENCHMARKS.md`.
10. Không thêm thư viện/mạng/telemetry mới nếu chưa được duyệt (C1).

---

## 13. Thuật ngữ

**ASR** nhận dạng giọng nói · **WER/CER** tỉ lệ lỗi từ/ký tự · **CS-WER** WER riêng cho đoạn xen ngôn ngữ · **TER** tỉ lệ lỗi thuật ngữ glossary · **RTF** thời gian xử lý ÷ thời lượng audio · **VAD** phát hiện giọng nói · **DER** tỉ lệ lỗi phân tách người nói · **Diarization** ai nói lúc nào · **Code-switching** xen ngôn ngữ trong câu · **Prefill/decode** đọc prompt / sinh token · **KV cache** bộ nhớ ngữ cảnh LLM · **DLA** nhị phân model đã biên dịch cho NPU MediaTek · **GAI toolkit** bộ công cụ chuyển LLM sang DLA (NDA) · **8w16a** lượng tử hóa trọng số 8-bit, kích hoạt 16-bit · **FGS** Foreground Service · **GBNF** ngữ pháp ràng buộc output của llama.cpp.
