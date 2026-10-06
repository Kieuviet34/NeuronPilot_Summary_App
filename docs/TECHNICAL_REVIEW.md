# TECHNICAL REVIEW — Meeting Notes App (G720 AI Box)

> **v2.0 · 02/10/2026.** Đi kèm `PROJECT_CONTEXT.md`. File này trả lời **vì sao** và **làm thế nào**: phân tích nền tảng, mô hình chi phí, các phương án cho từng thành phần, bài học từ dự án tương tự, lộ trình và rủi ro.
> Số liệu có mã `[N#]` đã được tra nguồn (xem `RESEARCH_NOTES.md`). Số ghi **[ƯỚC TÍNH]** là suy ra, **chưa đo trên G720**. Cổng benchmark: `BENCHMARKS.md`.

---

## 1. Những phát hiện thay đổi thiết kế (so với v1)

| # | Phát hiện | Hệ quả |
|---|---|---|
| 1 | G720 có **NPU 9 TOPS (MDLA 5.3)**; MediaTek công bố Qwen2.5-3B chạy NPU **163 token/s prefill, 10.6 token/s decode** [N4] | Có đường NPU chính thức cho LLM; prefill nhanh gấp nhiều lần CPU **[ƯỚC TÍNH]** |
| 2 | Decode ~10 token/s là trần ngay cả trên NPU → sinh lại toàn văn 15.000 token ≈ **24 phút** (họp 60′) | Bỏ bước "sửa lỗi bằng cách viết lại toàn văn" (D6) |
| 3 | MediaTek có gói **Whisper-base 8w16a** trên G720: encoder 160.6 ms/cửa sổ 30 s, decode 174.8 token/s [N4][N5] | ASR có thể nhanh gấp hàng chục lần CPU **[ƯỚC TÍNH]** nếu chuyển được PhoWhisper (cùng kiến trúc Whisper, [N14]) |
| 4 | Gói DLA dựng sẵn là công khai; **chuyển model riêng cần GAI toolkit (NDA)**; tài liệu Android giới hạn Direct Customer [N7][N8][N11] | Quyết định D12; Plan B luôn là nền tảng (C13) |
| 5 | Trên bộ test tiếng Việt xen thuật ngữ Anh, PhoWhisper-small zero-shot sai ~**63%** đoạn xen tiếng Anh; LoRA giảm còn ~30%, LoRA + AG còn ~19.5% [N15] | Glossary biasing/alias chưa đủ; cần fine-tune ở Phase 2 |
| 6 | Biasing lúc giải mã (RS/DV) chỉ cải thiện nhẹ; chuẩn hóa sau giải mã (AdaCS) giảm CS-WER mạnh nhưng làm WER tăng [N15] | Sửa bằng luật phải có ngưỡng, ghi log, hoàn tác được |
| 7 | whisper.cpp trên RK3588 (cùng lớp A76/A55): RTF tiny 0.10, base 0.16, small 0.47 [N23]; G720 chỉ có 2 nhân lớn → **[ƯỚC TÍNH]** base 0.16–0.35, small 0.47–1.0 | PhoWhisper-small trên CPU không kịp cho họp dài; cần NPU hoặc ASR nền |
| 8 | Gọi lặp `whisper_full` trên buffer tăng dần chậm hơn thời gian thực nhiều lần; chế độ batch thì nhanh [N24] | R2 không làm bằng whisper.cpp streaming; dùng model nhỏ theo từng câu |
| 9 | Silero VAD giảm rõ WER và ảo giác của Whisper; beam size 1 cho tỉ lệ ảo giác thấp nhất [N25] | VAD bắt buộc, beam 1 |
| 10 | Dự án tương tự đã chứng minh mô hình tuần tự nạp/giải phóng model, tóm tắt phân cấp theo tokenizer thật, FGS + WakeLock, audio đọc native [N30] | Giữ kiến trúc, bổ sung bài học (mục 5) |

---

## 2. Phân tích nền tảng

### 2.1 CPU, RAM, băng thông

- 2× A78 (≤2.6 GHz) + 6× A55 (2.0 GHz) [N1]. Chạy LLM/ASR trên 6 nhân nhỏ kéo chậm cả nhóm; thử: 2 thread (nhân lớn), 4, 6 và ghim affinity.
- RAM: LPDDR4X-4266 x32 hoặc LPDDR5(X)-6400 x32 [N1]. Băng thông lý thuyết = tốc độ × 4 byte ≈ **17.0 GB/s** hoặc **25.6 GB/s** **[ƯỚC TÍNH]**. Decode 3B Q4 (~2 GB trọng số) bị chặn: trần ≈ 8.5 hoặc 12.8 token/s; thực tế thấp hơn. Tham chiếu: Llama 3.2 3B Q4_K_M trên Snapdragon 865 (Termux) đạt 7.8–8.9 token/s [N28]. **Kỳ vọng CPU 4–8 token/s [ƯỚC TÍNH]**, cần đo G1.
- Vì decode là băng thông, **thêm thread không cứu được**; chỉ giảm số token sinh ra mới cứu được.
- Cờ build llama.cpp: tên cờ đã đổi (`GGML_NATIVE`, `GGML_CPU_ARM_ARCH`) [N29]; kiểm tra `/proc/cpuinfo` (`asimddp`, `i8mm`) rồi chọn biến thể. Dự án Local-Summerizer build nhiều biến thể ggml theo mức tính năng ARM và nạp biến thể nhanh nhất lúc chạy [N30]; nên làm tương tự nếu phân phối cho nhiều phần cứng.

### 2.2 NPU và quyền truy cập

| Đường | Có gì | Hạn chế |
|---|---|---|
| Gói DLA dựng sẵn (LLM, ASR) | Qwen2.5-1.5B/3B/7B, Qwen3-0.6B/1.7B/4B/8B, Llama 3.2 1B/3B, Whisper-base 8w16a, Qwen3-ASR-0.6B, Moonshine-tiny [N4][N6] | Công khai, nhưng ví dụ chạy chính thức là `llm_cmdline_tool` / `asr_cmdline_tool` trên **Yocto**; app Android cần runtime NeuroPilot (Direct Customer) [N5][N8] |
| GAI toolkit | Chuyển model riêng (kể cả PhoWhisper đã fine-tune) sang DLA | **NDA** [N7][N11] |
| ONNX Runtime + NeuronEP | Mặc định trên Genio 720 theo bên thứ ba [N13] | Trên Android phải liên hệ MediaTek lấy thư viện, không có demo [N8] |
| TFLite (LiteRT) + Neuron delegate | Có [N13] | Cần chuyển model, giới hạn toán tử |

**Cảnh báo:** forum MediaTek (05/2026) có câu trả lời "Whisper chưa hỗ trợ trên G420/520/720" [N9], trong khi bảng hiệu năng G720 cập nhật 02/10/2026 đã liệt kê Whisper-base 8w16a [N4]; một câu trả lời khác (03/2026) nói có toolkit Whisper nhưng không kèm tiền/hậu xử lý [N10]. Trạng thái đang thay đổi, cần hỏi trực tiếp MediaTek. Có báo cáo "garbage transcription" khi ghép encoder DLA với decoder PyTorch trên Genio 510 [N12]: rủi ro lượng tử hóa/ghép nối cần G-N3.

**Việc nên làm ngay:** kiểm tra app NeuroPilot JNI Demo trên thiết bị (runtime nào, model nào, có đọc được `.dla` từ Android không). Đây là bằng chứng sớm nhất cho D12.

### 2.3 Hệ quả cho bộ nhớ

- ASR: whisper.cpp base ~410 MB, small ~889 MB peak RSS trên RK3588 [N23].
- LLM 3B Q4: ~2 GB trọng số. KV cache Qwen2.5-3B (36 layer, 2 KV head, head dim 128) ≈ **36 KB/token fp16** **[ƯỚC TÍNH từ cấu hình]**: 4.096 token ≈ 150 MB, 16.384 token ≈ 0.6 GB. `n_ctx` lớn tốn ít RAM; cái đắt là **prefill** dài.
- Chạy chồng ASR (CPU) + LLM (NPU) cộng ~3 GB: nằm trong ngân sách nhưng NPU có thể dùng vùng bộ nhớ riêng (carveout); đo G3 trước khi bỏ C4.

---

## 3. Mô hình chi phí

```
T_LLM ≈ token_vào ÷ tốc_độ_prefill + token_ra ÷ tốc_độ_decode
T_ASR ≈ RTF × thời_lượng_audio
```

**Giả định:** nói liên tục (không trừ VAD, thực tế trừ được 20–40%), 140 từ/phút, 1.8 token/từ (hệ số của tài liệu gốc; phải đo bằng tokenizer thật) → **~250 token/phút**: họp 60′ ≈ 15.000 token, 120′ ≈ 30.000 token.

### 3.1 ASR (thời lượng xử lý, họp 60′ / 120′)

| Phương án | RTF | 60′ | 120′ | Nguồn |
|---|---|---|---|---|
| whisper.cpp PhoWhisper-base (CPU) | 0.16–0.35 | 10–21′ | 19–42′ | [N23] + ngoại suy |
| whisper.cpp PhoWhisper-small (CPU) | 0.47–1.0 | 28–60′ | 56–120′ | [N23] + ngoại suy |
| Whisper-base 8w16a (NPU) | ~0.03 | ~2′ | ~4′ | suy từ [N4]: 0.16 s + ~0.7 s/cửa sổ 30 s |
| Qwen3-ASR-0.6B (NPU) | ~0.7 | ~42′ | ~84′ | suy từ [N5]: ví dụ ~9 s audio mất ~6.4 s |
| Zipformer-vi-30M INT8 (CPU) | ~0.05–0.15 | 3–9′ | 6–18′ | suy từ RTF zipformer nhỏ [N19]; cần đo |

### 3.2 LLM (họp 60′ / 120′)

| Bước | Chạy ở | 60′ | 120′ | Ghi chú |
|---|---|---|---|---|
| Sinh lại toàn văn (bỏ) | NPU, 10.6 tok/s | ~24′ | ~47′ | [N4] |
| Sinh lại toàn văn (bỏ) | CPU, 4–8 tok/s | 31–63′ | 63–126′ | [ƯỚC TÍNH] |
| Map JSON (prefill toàn văn + ~150 token ra mỗi 1.200 token vào) | NPU | ~4–5′ | ~9′ | 15k/163 + 2k/10.6 |
| Map JSON | CPU | ~10–15′ | ~20–30′ | prefill CPU chưa biết (giả định 20–50 tok/s) |
| Reduce (1–2 cấp) | NPU / CPU | ~1′ / ~2′ | ~1.5′ / ~3′ | |

### 3.3 Tổng theo Plan (đã dùng trong `PROJECT_CONTEXT.md` §5)

| Plan | 60′ | 120′ |
|---|---|---|
| B: CPU base + CPU LLM | 25–36′ | 50–70′ |
| B + R3 (ASR nền lúc ghi) | ~15′ sau khi dừng | ~30′ |
| C: CPU base + NPU LLM | 16–27′ | 32–54′ (có thể chồng ASR/LLM) |
| A: NPU ASR + NPU LLM | ~8′ | ~16′ |

Các con số trên chỉ để **so sánh phương án**; chốt sau G1, G2, G-N1, G-N3.

---

## 4. Phương án theo thành phần

### 4.1 Engine ASR

| Mã | Phương án | Ưu | Nhược | Khi nào chọn |
|---|---|---|---|---|
| E1 | **whisper.cpp + PhoWhisper (CPU)** | Chạy được ngay, có `initial_prompt`, ggml quen thuộc, không NDA | Chậm khi họp dài/small; trên G720 chỉ 2 nhân lớn | **Nền tảng (Plan B)** |
| E2 | **Whisper trên NPU (toolkit MediaTek) + PhoWhisper chuyển sang** | Nhanh gấp nhiều lần, giải phóng CPU, nhiệt thấp | Cần toolkit/NDA; chỉ thấy gói base; lượng tử hóa 8w16a có thể tăng WER; runner giải mã tham lam, không beam/prompt sẵn | Khi G-N3 đạt |
| E3 | sherpa-onnx **Zipformer-vi-30M INT8** (~75 MB, RNN-T) [N18][N38] | Rất nhẹ; có **hotwords** (chỉ transducer, `modified_beam_search`) [N19]; hợp phụ đề nháp | Không dấu câu/viết hoa; thuật ngữ Anh phụ thuộc vocab BPE tiếng Việt; số giờ huấn luyện nguồn mâu thuẫn (6.000 h theo [N39], 70.000 h theo [N38]) | R2-lite; so sánh CS-WER |
| E4 | sherpa-onnx **Moonshine-base-vi** quantized [N21] | Thiết kế cho streaming ngắn; có APK mẫu | Chưa có số đo tiếng Việt/xen Anh | R2-lite (thử song song E3) |
| E5 | **Qwen3-ASR-0.6B** (NPU gói dựng sẵn) [N5][N22] | Hỗ trợ tiếng Việt; nhận `-p` văn bản bổ sung (có thể thử đưa glossary) | Decode 7.1 token/s → RTF ~0.7; quá chậm cho batch | Chỉ để chấm lại đoạn tin cậy thấp |
| E6 | **Whisper đa ngôn ngữ** (small/large-v3) lượt hai cho đoạn xen Anh | Nhận tiếng Anh tốt hơn (CS-WER Whisper-large-v3 46.7% < PhoWhisper-large 55.1% [N15]) | Tốn thêm thời gian, tiếng Việt kém hơn | Phase 2, chỉ chunk có nhiều từ Anh/tin cậy thấp |
| E7 | **PhoWhisper + LoRA** huấn luyện bằng dữ liệu công ty | Giảm CS-WER ~½ (62.6 → 30.3), WER 36.3 → 27.1 [N15] | Cần dữ liệu và huấn luyện; chuyển lại sang ggml/DLA | **Phase 2, đòn bẩy lớn nhất về chất lượng** |

**Khuyến nghị:** MVP = E1 (base) + luật alias. Song song thử E2 (G-N3). Phase 2 = E7, thêm E3/E4 cho phụ đề nháp.

Về kích thước PhoWhisper: tiny 39M, base 74M, small 244M, medium 769M, large 1.55B tham số; WER giảm rõ từ base (16.19 / 8.46 / 19.70 / 43.01 trên bốn bộ test của bài báo) sang small (11.08 / 6.33 / 15.93 / 32.96) [N14]. Nếu 245 MB trong tài liệu gốc là bản **small**, đừng gọi là "base" (D13).

### 4.2 Thuật ngữ Anh trong câu Việt (code-switching)

Hiểu nguyên nhân: người Việt đọc từ Anh theo âm Việt hóa nên ASR vừa phải đoán từ Anh vừa phải khớp phát âm địa phương [N17]. Thang giải pháp (đã đo ở [N15]) nằm ở `PROJECT_CONTEXT.md` §8. Thiết kế chi tiết các lớp:

**Lớp 1: Glossary biasing.**
- `initial_prompt` (câu tự nhiên có chứa thuật ngữ đúng) + đoạn cuối chunk trước đã kiểm tra. Giới hạn vài chục thuật ngữ quan trọng nhất. Chưa có bằng chứng định lượng → chỉ giữ nếu G6 có lợi; **không** dùng chunk nghi ảo giác làm prompt (I10).
- Nếu dùng E3: truyền `hotwords-file` + `hotwords-score` (mỗi từ có thể kèm điểm riêng), bắt buộc `modified_beam_search` [N19].

**Lớp 2: Sửa bằng luật xác định (giống AdaCS nhưng không cần huấn luyện).**

```sql
CREATE TABLE glossary (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  term TEXT NOT NULL UNIQUE,                 -- dạng chuẩn: "BSP", "sprint", "kernel"
  kind TEXT DEFAULT 'term'                   -- 'term' | 'person' | 'project'
);
CREATE TABLE glossary_alias (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  glossary_id INTEGER NOT NULL,
  alias TEXT NOT NULL,                       -- "bê ét pê", "sờ prin", "đờ rai vơ"
  mode TEXT DEFAULT 'suggest',               -- 'always' | 'suggest' (chỉ thay khi có tín hiệu phụ)
  FOREIGN KEY (glossary_id) REFERENCES glossary(id) ON DELETE CASCADE
);
CREATE TABLE edit_log (                      -- I8
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  meeting_id INTEGER NOT NULL, chunk_index INTEGER NOT NULL,
  pos INTEGER NOT NULL, before TEXT NOT NULL, after TEXT NOT NULL, rule TEXT NOT NULL
);
```

- So khớp: chữ thường, bỏ dấu để so, cửa sổ trượt 1–3 âm tiết; alias chính xác trước, khớp gần (khoảng cách chỉnh sửa nhỏ) sau.
- Cụm đồng âm với từ thông thường ("bạn sẽ phải" ↔ BSP): chỉ thay khi `mode='always'`, hoặc khi cụm có `avg_logprob` thấp, hoặc ngữ cảnh kỹ thuật (chunk chứa ≥ N thuật ngữ khác).
- Bộ alias khởi tạo bằng **log lỗi thật** từ G6, không tự bịa; UI cho phép người dùng thêm alias từ chính bản sửa của họ.
- Ưu tiên precision: AdaCS cho thấy chuẩn hóa mạnh làm WER chung tăng [N15].

**Lớp 3: Độ tin cậy.** Lưu `avg_logprob`/xác suất token theo chunk; đoạn thấp → gạch chân trên UI, ứng viên cho E6/E5 và cho LLM chỉnh sửa có chọn lọc.

**Lớp 4: Fine-tune (E7).** Công thức đã chứng minh [N15][N16]: LoRA (r=32, α=64, target q/k/v/out/fc1/fc2) + speed perturbation {0.9, 1.0} + SpecAugment; trên PhoWhisper-small với 24 giờ dữ liệu. Dữ liệu công ty: ghi âm họp có đồng ý, **teacher–student**: mô hình lớn chạy trên PC tạo nhãn tạm, người chỉ sửa thuật ngữ; bổ sung giọng tổng hợp (TTS) cho câu chứa glossary là hướng thử có tiền lệ [N17], nhưng phải kiểm tra chất lượng. Có thể giữ lại một phần dữ liệu tiếng Việt thường để tránh quên (adapter large của [N16] làm VIVOS tăng từ 4.61 lên 7.20 WER).

**Lớp 5: Chỉ chỉnh sửa bằng LLM với validator** (mục 4.8).

### 4.3 VAD, chia chunk, chống ảo giác

| Việc | Cách làm |
|---|---|
| VAD | Silero (ONNX ~2 MB). Điểm xuất phát: ngưỡng 0.40–0.50, hysteresis (bật cao hơn tắt ~0.15), giọng tối thiểu ~250 ms, im lặng để cắt 300–500 ms, đệm 200 ms. Mic xa → hạ ngưỡng |
| Chia chunk | Gom đoạn nói liền kề thành cửa sổ ≤25–30 s (Whisper tốn encoder cố định mỗi cửa sổ), cắt ở chỗ im lặng dài nhất, không cắt giữa từ |
| Giải mã | Beam size 1; `no_speech` 0.6; `logprob` −1.0; `compression_ratio` 2.4 làm điểm xuất phát [N25] |
| Kiểm tra bổ sung | Tự phát hiện lặp n-gram (ngưỡng compression của Whisper có lúc không bắt được [N25]); danh sách chặn câu ảo giác (xây từ log thực tế, ví dụ các câu kiểu "hãy đăng ký kênh…"); chunk bị loại không làm prompt cho chunk sau |
| Ghi nhận | Mỗi chunk lưu: bắt đầu/kết thúc (ms trong WAV), `avg_logprob`, cờ nghi ảo giác |

### 4.4 Tiền xử lý audio

- Thử 3 cấu hình trong G6: (a) raw, (b) lọc thông cao ~80 Hz + chuẩn hóa mức âm có giới hạn khuếch đại, (c) (b) + khử nhiễu. Chọn theo WER/TER; khử nhiễu không chắc có lợi cho Whisper.
- **RNNoise** chạy ở 48 kHz (frame 480 mẫu = 10 ms); ở 16 kHz phải upsample–downsample. Nếu cần khử nhiễu mạnh hơn: sherpa-onnx có mô-đun speech enhancement chạy offline [N18] (phải đo chi phí CPU).
- Mic xa, vọng: vị trí đặt box và gain ảnh hưởng nhiều hơn mọi mô hình; thử `SOURCE_UNPROCESSED`, kiểm AGC/NS ở HAL (G7).

### 4.5 Phân tách người nói

| Mã | Phương án | Ghi chú |
|---|---|---|
| S1 | **sherpa-onnx offline diarization**: pyannote-segmentation-3.0 + embedding (3D-Speaker ERes2Net hoặc NeMo TitaNet-small) + gom cụm [N20] | Có API cho Android; nhập sẵn số cụm (`numClusters`) ổn định hơn tự động |
| S2 | Đăng ký giọng mẫu (speaker identification của sherpa-onnx) để gán tên | Cần đo EER ngoài thực tế |
| S3 | Phần cứng: mic array ≥2 mic, tính hướng nguồn âm | Hỏi BSP; thay đổi lớn nhất về chất lượng nếu có |

Kỳ vọng thực tế: pyannote 3.1 DER 24.4% trên AliMeeting (họp xa, mic đơn), >50% trên video "in-the-wild" nhiều chồng lấn; <8% trên audio sạch [N35]. Nói chồng và câu ngắn là nguồn lỗi chính. Embedding mẫu công khai huấn luyện trên tiếng Trung/Anh; phải đo trên giọng Việt trước khi tin (G-D).

Tích hợp: `VAD → diarization → cắt chunk theo lượt nói → ASR`; transcript gửi LLM dạng `[Người nói 2]: …`; tên người phụ trách phải khớp danh sách tham dự.

### 4.6 Realtime: phương án và chi phí

| Mức | Cách làm | Chi phí CPU | Rủi ro |
|---|---|---|---|
| R1 | Silero VAD trên luồng ghi qua hàng đợi | <1% | Thấp |
| R2-lite | VAD kết thúc câu → E3/E4 giải mã câu đó (nháp), sau họp thay bằng bản chính | Thấp–trung bình | Chất lượng nháp thấp; phải gắn nhãn "bản nháp" |
| R2-whisper | Gọi `whisper_full` liên tục trên buffer lớn dần | Rất cao | **Không làm** [N24] |
| R3 | ASR chính chạy nền từng chunk trên nhân nhỏ, ưu tiên thấp | Trung bình | Nhiệt/xrun; kiểm G4, G5. Rất giá trị với họp 2 giờ |
| R5 | Nhận diện người nói realtime | Cao | Độ chính xác thấp ở mic xa; chỉ làm sau R4 |

Nguyên tắc: thread ghi không bao giờ chờ bất kỳ tác vụ nào (C5); nếu hàng đợi đầy, bỏ việc nháp chứ không bỏ audio.

### 4.7 Engine và model LLM

| Mã | Phương án | Hiệu năng (prompt/decode tok/s) | Ghi chú |
|---|---|---|---|
| L1 | **llama.cpp CPU** + Qwen2.5-3B Q4_K_M/Q4_0 | decode kỳ vọng 4–8 [ƯỚC TÍNH] | Có GBNF; nền tảng Plan B; Q4_0 có repack ARM, cần đo [N29] |
| L2 | **NPU gói dựng sẵn Qwen2.5-3B-Instruct** | **163 / 10.6** [N4] | Không cần GAI toolkit để *dùng* gói; không có grammar; ngữ cảnh có thể cố định (gói Qwen3-ASR dùng cache 2048 [N5]) |
| L3 | NPU Qwen3-4B | 126 / 7.3 [N4] | Chất lượng đa ngôn ngữ có thể tốt hơn, chậm ~30%; Qwen3 có chế độ suy luận cần tắt |
| L4 | NPU Qwen3-1.7B hoặc Qwen2.5-1.5B | 263 / 13.8 và 337 / 19.3 [N4] | Nhanh nhất; thử cho bước chỉnh sửa/gom, chất lượng tiếng Việt phải đo |
| L5 | NPU Qwen2.5-7B | 69 / 4.7 [N4] | Chất lượng tốt hơn, chậm; chỉ cho bước **reduce** cuối nếu bộ nhớ cho phép |
| L6 | NPU Gemma3-4B / Llama3.2-3B / Phi-3 | 177/5.9; 154/10.4; 128/7.3 [N4] | Llama 3.2 không liệt kê tiếng Việt trong ngôn ngữ chính thức; so sánh nếu cần |

**Khuyến nghị:** L1 cho Plan B. Thử L2 (G-N1, G-N2) và so L2/L3/L4 bằng G9 (5 cuộc họp thật, chấm độ trung thực và đủ ý). Dùng tokenizer thật để cắt chunk (dự án Local-Summerizer đo ngân sách token bằng tokenizer của chính model [N30]).

### 4.8 Pipeline LLM đề xuất

```
transcript đã sạch (luật)
  → [Map] mỗi chunk ≤ ~1.200 token vào → JSON có cấu trúc (+ trích dẫn nguyên văn)
  → [Validate] schema, trích dẫn có trong chunk, tên thuộc danh sách, hạn có trong văn bản
  → [Reduce] gộp JSON theo nhóm (≤ N mục mỗi lần), khử trùng lặp → JSON tổng
  → [Compose] viết tóm tắt từ JSON tổng (đầu vào nhỏ, ít bịa)
```

Schema map (ví dụ):

```json
{
  "y_chinh":   ["..."],
  "quyet_dinh":["..."],
  "hanh_dong": [{"viec":"...","nguoi":"... | null","han":"... | null","trich_dan":"<=15 từ nguyên văn"}]
}
```

- `trich_dan` phải là chuỗi con của chunk (sau chuẩn hóa); không khớp thì bỏ mục. Đây là phép kiểm rẻ nhất chống bịa.
- **CPU (L1):** chuyển schema sang GBNF để JSON hợp lệ theo cấu trúc; model **không thấy** schema nên prompt vẫn phải mô tả cấu trúc [N26]. Bên thứ ba báo nhiều mô hình nhỏ hỏng JSON ở lần đầu khi chỉ nhắc trong prompt, và ràng buộc có thể làm giảm độ chính xác của bài toán nhiều bước [N27], nên giữ nhiệm vụ mỗi lần gọi đơn giản.
- **NPU (L2–L6):** không có grammar; parse, validate, thử lại một lần với prompt ngắn hơn, rồi mới quay về dạng văn bản.
- Nghiên cứu hệ thống ghi chú họp của Microsoft: hiển thị theo "chương" phân cấp, mỗi hành động có ô người phụ trách/hạn chỉnh sửa được, luôn giữ transcript gốc phía dưới [N33]. Áp dụng cho tab Kết quả.
- **Họp giao ban / tổng hợp nhiều cuộc họp (Phase 2):** lưu JSON map của từng cuộc họp; tổng hợp chỉ là *reduce* trên các JSON đã lưu, không đọc lại transcript.
- Hierarchical merging đơn giản hơn nhưng có thể mất phụ thuộc xa; incremental updating hưởng lợi khi chunk lớn [N34]. Với model 3B ngữ cảnh ngắn, chọn hierarchical.

### 4.9 Ghi âm và Android 15

- **FGS `microphone`:** khai báo `foregroundServiceType`, quyền `FOREGROUND_SERVICE_MICROPHONE` + `RECORD_AUDIO`; **không thể tạo FGS microphone từ nền** (ràng buộc while-in-use), phải bắt đầu khi UI ghi âm đang foreground [N36]. Thiếu loại sẽ ném `MissingForegroundServiceTypeException`.
- Android 15: một số loại FGS có `onTimeout` (ví dụ `dataSync`); với pipeline xử lý dài, xử lý `onTimeout` bằng cách lưu checkpoint rồi dừng/tiếp tục [N37]. Đánh giá thêm loại phù hợp hơn cho xử lý media.
- App chạy trên box vendor có thể nới chính sách nền; **vẫn phải test** tắt màn hình 2 giờ (G5).
- Lưu trữ app-specific (D7). Audio và transcript không vào backup tự động (Local-Summerizer tắt backup để transcript không lên Drive [N30]).
- WakeLock một phần khi xử lý; ghi âm thì FGS đủ, nhưng đo thực tế.
- `AudioRecord`: buffer 4096 byte = 128 ms; đọc theo block, ghi qua hàng đợi; DB không bao giờ chạm thread ghi.

### 4.10 Checkpoint, nhiệt, bộ nhớ

- Checkpoint theo chunk (transcript chunk, JSON map, chỉ số hiện tại) trong Room; `START_NOT_STICKY` nhưng mở app/service lại thì tiếp tục từ chunk chưa xong.
- Nhiệt: đọc nhiều thermal zone (không chỉ `thermal_zone0`), thêm hysteresis. NPU thường mát hơn CPU cho cùng khối lượng **[ƯỚC TÍNH]**.
- Nạp model qua `mmap` từ file descriptor, tránh copy 2 GB (Local-Summerizer) [N30].
- Giải phóng model nặng trước khi nạp model nặng khác (C4); cùng ggml cho whisper.cpp và llama.cpp khi build chung để giảm kích thước [N30].

---

## 5. Bài học từ dự án tương tự

| Dự án | Họ làm gì | Áp dụng / tránh |
|---|---|---|
| **Local-Summerizer** (Android, whisper.cpp + llama.cpp Qwen2.5, tiếng Do Thái) [N30] | Tuần tự nạp/giải phóng Whisper rồi Llama; FGS + WakeLock; tóm tắt phân cấp theo ngân sách token đo bằng tokenizer thật; audio giải mã ra file tạm đọc native; nạp model qua fd/mmap; biến thể ggml theo tính năng ARM; báo cáo chẩn đoán không chứa nội dung; backup tắt; họp 1 giờ: ASR ~10–25 phút trên máy flagship | **Áp dụng gần như nguyên mẫu** cho Plan B. Tránh: bỏ qua diarization và alias thuật ngữ |
| **Meetily** (Whisper.cpp/Parakeet + Ollama, Tauri) [N31] | Privacy-first, ghi chú ghi rõ mô hình LLM nhỏ hay bịa khi tóm tắt | Xác nhận rủi ro bịa → validator + trích dẫn |
| **ownscribe** (faster-whisper + diarization + llama.cpp) [N32] | Mẫu tóm tắt theo loại (meeting/lecture/brief), hỏi đáp trên nhiều cuộc họp | Gợi ý "mẫu tóm tắt" (họp giao ban, họp kỹ thuật) và truy vấn nhiều cuộc họp (Phase 2) |
| **sherpa-onnx** [N18][N20][N21] | VAD + ASR + diarization + speaker ID + khử nhiễu offline trên Android, có model tiếng Việt | Nguồn chính cho R2-lite và R4; một thư viện, nhiều chức năng |
| **MediaTek IoT AI Hub** [N4][N5][N6] | Gói DLA dựng sẵn + số đo trên G720 | Nguồn chính cho Plan A/C |
| **ViMedCSS và LoRA code-switching** [N15][N16] | Đo CS-WER; LoRA/AG hiệu quả nhất | Mẫu cho bộ test và Phase 2 |
| **whisper.cpp #3567** [N24] | Streaming bằng gọi lặp chậm | Tránh R2-whisper |
| **Whisper-WebUI / thảo luận ảo giác** [N25] | VAD + khử nhiễu giảm ảo giác; ngưỡng compression không đáng tin tuyệt đối | Tự kiểm tra lặp |
| **Hệ thống recap họp của Microsoft** [N33] | Chương phân cấp, action chỉnh sửa được, giữ transcript | UX tab Kết quả |

---

## 6. Lộ trình và cây quyết định

| Giai đoạn | Việc | Cổng |
|---|---|---|
| **0 (1 tuần)** | G0–G9, G-N1. Thu 20–30 phút họp thật + chép chuẩn (có thuật ngữ). Hỏi MediaTek/ODM (D12, D13). Kiểm tra app NeuroPilot JNI Demo | Chốt D6, D7, D8, D11–D15; chọn Plan khởi đầu |
| 1 (tuần 1–2) | FGS ghi âm, WAV streaming, pause/resume, recovery, R1 | G5 |
| 2 (tuần 3–4) | ASR (E1) + VAD + chống ảo giác + glossary/alias | TER, WER theo G6 |
| 3 (tuần 5–6) | Pipeline LLM mới (map/reduce/validator), checkpoint, thermal | S5, S6 |
| 4 (tuần 7–8) | UI, export, TTS, Settings, polish | Demo |
| **Phase 2** | G-N2/G-N3 → Plan C/A; R3; R2-lite; R4; E7 fine-tune; tổng hợp nhiều cuộc họp | Từng mục qua benchmark |

**Cây quyết định:**
- G-N1 đạt (gói Qwen2.5-3B chạy được trong app Android) và G-N2 đạt → LLM sang NPU (Plan C).
- G-N3 đạt (PhoWhisper chuyển được, WER chấp nhận được) → ASR sang NPU (Plan A).
- G-N1 không đạt (không có quyền/runtime) → ở lại Plan B, tập trung R3 và model nhỏ (Qwen3-1.7B/Qwen2.5-1.5B CPU cho bước map) để giữ S5.
- G6 cho thấy alias + prompt đạt TER ≥ 90% → hoãn E7; không đạt → ưu tiên E7.

---

## 7. Sổ rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Không có quyền NPU Android/GAI toolkit | Cao | C13: Plan B độc lập |
| PhoWhisper chuyển sang DLA bị "garbage transcript" hoặc WER tăng | Trung bình | G-N3; giữ E1 |
| RAM thực là LPDDR4X → decode CPU chậm hơn dự tính | Trung bình | G0/G1; map-reduce chỉ prefill nhiều |
| LLM 3B tóm tắt tiếng Việt kém | Trung bình–cao | G9; trích dẫn nguyên văn; mô hình lớn hơn chỉ cho reduce |
| Ghi âm bị ngắt khi tắt màn hình | Cao | FGS microphone; G5 |
| Diarization kém ở mic xa | Trung bình | Nhập số người; gán nhãn "gần đúng"; phần cứng mic array |
| Nhiệt khi chạy liền 2 giờ | Trung bình | Throttle, NPU, R3 chỉ khi G4 đạt |
| Dữ liệu huấn luyện nhạy cảm | Trung bình | Chính sách đồng ý; xử lý trong mạng nội bộ |
| NeuronRuntime lỗi/crash khi dùng GenAI | Trung bình | Báo cáo của cộng đồng trên G520 [N11]; sandbox bằng process riêng, fallback CPU |

---

## 8. Câu hỏi mở

1. Họp dài nhất: 60 phút hay 2 giờ? Có cần họp giao ban nhiều cuộc họp gộp lại không?
2. "Realtime" cần mức nào: chỉ biết có giọng nói (R1), hiển thị chữ nháp (R2-lite), hay biết **ai** nói (R4/R5)?
3. Loại RAM trên box (LP4X hay LP5)? Mic I2S là mic đơn hay mảng mic? HAL có AGC/NS không?
4. BHS có quyền Direct Customer/NDA (NeuroPilot Android, GAI toolkit) chưa? App NeuroPilot JNI Demo đang chạy gì?
5. PhoWhisper tích hợp là base hay small, lượng tử hóa nào? Dữ liệu huấn luyện có thuật ngữ kỹ thuật Anh không?
6. Có được ghi lại cặp (audio, bản sửa của người dùng) để huấn luyện không?
7. Box có engine TTS tiếng Việt không?
