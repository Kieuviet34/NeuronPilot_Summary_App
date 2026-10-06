# Meeting Notes App — Phân Tích Kỹ Thuật Chuyên Sâu

> **Thiết bị:** G720 AI Box · MediaTek Genio 720 · 8GB RAM · Android 15  
> **Màn hình:** 10 inch landscape 1024×600  
> **Ngôn ngữ:** Java · C++ (NDK/JNI)  
> **Mục đích file này:** Phân tích kỹ thuật chuyên sâu để làm cơ sở phát triển code

---

## 1. Flow Tổng Quan

Ứng dụng chạy **tuần tự** — ghi âm xong rồi mới xử lý AI.  
Ghi âm hỗ trợ **nhiều đoạn (segments)** trong 1 cuộc họp và **pause/resume** trong từng đoạn.

### 1.1 Mô hình dữ liệu

```
1 Meeting (cuộc họp)
  └── N Segments (đoạn ghi âm)
        Segment 01: Hop_BSP_01.wav  (30 phút - phần mở đầu)
        Segment 02: Hop_BSP_02.wav  (25 phút - phần thảo luận)
        Segment 03: Hop_BSP_03.wav  (20 phút - phần kết luận)
```

Mỗi segment là 1 file WAV độc lập. Khi xử lý AI, user **chọn segments nào muốn include**.

### 1.2 Trạng thái ghi âm (3 trạng thái)

```
[IDLE] ──bấm "Ghi"──→ [RECORDING] ──bấm "Pause"──→ [PAUSED]
                            │                            │
                            │                       bấm "Resume"
                            │                            │
                            │ ←──────────────────────────┘
                            │
                       bấm "Kết thúc đoạn"
                            │
                            ▼
                     [SEGMENT SAVED]
                            │
              ┌─────────────┼─────────────┐
              │                           │
         bấm "Ghi đoạn mới"        bấm "Xử lý AI"
              │                           │
              ▼                           ▼
         [RECORDING]              [Chọn segments]
         (segment N+1)             → Pipeline AI
```

- **RECORDING → PAUSED → RECORDING:** Pause/resume trong cùng 1 file WAV. Phần pause không ghi vào file (im lặng bị cắt). Dùng cho tình huống ngắt quãng ngắn: gõ cửa, điện thoại reo, nói chuyện riêng.
- **RECORDING → SEGMENT SAVED:** Kết thúc 1 đoạn, lưu file WAV. Dùng cho giải lao, chuyển chủ đề, đoạn không liên quan.
- **SEGMENT SAVED → RECORDING:** Bắt đầu ghi segment mới (file WAV mới, số thứ tự tăng).
- **SEGMENT SAVED → Xử lý AI:** Chuyển sang chọn segments và chạy pipeline.

### 1.3 Đặt tên file

```
Quy tắc: {meeting_title}_{segment_number}.wav

Mặc định: Cuochop_20260410_1430_01.wav, ..._02.wav, ..._03.wav
Custom:   Hop_BSP_01.wav, Hop_BSP_02.wav, Hop_BSP_03.wav

User có thể đặt tên cuộc họp trước hoặc sau khi ghi.
Segment number tự tăng: 01, 02, 03, ...
```

### 1.4 Flow xử lý AI

```
[Giai đoạn 1: Ghi âm] (có thể ghi nhiều đoạn, pause/resume trong mỗi đoạn)
  User ghi segment 1 → pause/resume → kết thúc đoạn → lưu WAV
  User ghi segment 2 → kết thúc đoạn → lưu WAV
  User ghi segment 3 → kết thúc đoạn → lưu WAV
  CPU gần như rảnh, chỉ ghi file.

[Giai đoạn 2: Chọn segments]
  User thấy danh sách tất cả segments của cuộc họp
  Mỗi segment hiện: tên, thời lượng, kích thước, checkbox chọn/bỏ
  User tick chọn segments muốn xử lý (mặc định: chọn tất cả)
  Bấm "Xử lý"

[Giai đoạn 3: Xử lý AI] (chạy tuần tự trên các segments đã chọn)
  Selected segments (WAV files)
    → Bước 1: ASR từng segment → raw text per segment → ghép thành 1 raw transcript
    → unload ASR model, load LLM
    → Bước 2: Qwen2.5 sửa lỗi text → clean text
    → Bước 3+4: Qwen2.5 tóm tắt + trích xuất actions
  Lưu kết quả vào Room DB.
```

**KHÔNG chạy inference trong khi ghi âm.** Toàn bộ AI processing diễn ra sau khi kết thúc ghi.

---

## 2. Phân Tích Bộ Nhớ (8GB RAM)

### 2.1 Phân bổ RAM thực tế

| Thành phần | RAM ước tính |
|---|---|
| Android 15 system + services | ~1.5–2.0 GB |
| App process (UI, Room DB, buffers) | ~200–400 MB |
| PhoWhisper model loaded | ~250–500 MB |
| Qwen2.5 3B Q4 model loaded | ~2.0–2.5 GB |
| llama.cpp inference context (n_ctx=4096) | ~300–500 MB |
| Audio buffer (recording) | ~5–10 MB |
| **Tổng peak (khi chạy LLM)** | **~4.5–5.5 GB** |
| **Còn trống** | **~2.5–3.5 GB** |

### 2.2 Kết luận

- 8GB RAM **đủ thoải mái** cho flow tuần tự
- Chỉ cần 1 model trong RAM tại 1 thời điểm
- `n_ctx = 4096` là lựa chọn an toàn, `n_ctx = 8192` cũng khả thi (thêm ~500MB)
- Không cần lo swap hay OOM nếu quản lý model lifecycle đúng

---

## 3. Thách Thức Kỹ Thuật & Giải Pháp

### 3.1 Thời gian chờ sau khi bấm "Dừng"

**Vấn đề:** Đây là pain point UX lớn nhất. Ước tính thời gian xử lý:

| Cuộc họp | Bước 1 (ASR) | Load LLM | Bước 2 (sửa) | Bước 3+4 (tóm tắt + actions) | Tổng |
|---|---|---|---|---|---|
| 15 phút | ~1.5–2.5 phút | ~10s | ~1–2 phút | ~1–2 phút | ~4–7 phút |
| 30 phút | ~3–5 phút | ~10s | ~2–4 phút | ~2–3 phút | ~7–13 phút |
| 60 phút | ~6–10 phút | ~10s | ~4–7 phút | ~3–4 phút | ~14–22 phút |

**Giải pháp:**

1. **Background processing bắt buộc:** Chạy pipeline trong `Foreground Service` với persistent notification hiển thị progress. User có thể rời màn hình xử lý, dùng app khác, quay lại khi xong.

2. **Gộp bước 3 + 4:** Thay vì 2 lần inference riêng, gộp thành 1 prompt duy nhất yêu cầu Qwen2.5 vừa tóm tắt vừa trích actions → cắt 1 lần inference, tiết kiệm 1-2 phút.

3. **Skip VAD segments:** Dùng VAD trước khi ASR, bỏ qua các segment im lặng → giảm số segment cần inference, có thể cắt 20-40% thời gian ASR.

### 3.2 Long Text vượt Context Window

**Vấn đề:** Qwen2.5 3B Q4 context window ~4096 tokens. Cuộc họp 30 phút sinh ~4000-6000 từ tiếng Việt ≈ 8000-12000 tokens (tiếng Việt tokenize nhiều hơn tiếng Anh ~1.5-2x). Toàn bộ transcript **KHÔNG fit** trong 1 lần inference.

**Giải pháp — Chunking Strategy:**

```
BƯỚC 2 (Sửa lỗi) — Chunk & Fix:
  Chia transcript thành chunks ~800 từ
  Overlap 80 từ ở biên để không cắt giữa câu
  Inference từng chunk độc lập
  Ghép kết quả, xử lý vùng overlap (giữ bản mới hơn)
  
  → Bước này chunk dễ vì sửa lỗi mang tính local, không cần ngữ cảnh toàn bộ.

BƯỚC 3+4 (Tóm tắt + Actions) — Hierarchical:
  Nếu clean text ≤ 3000 tokens:
    → Gửi toàn bộ vào 1 prompt, tóm tắt + trích actions
  Nếu clean text > 3000 tokens:
    → Chia thành chunks ~1000 từ
    → Tóm tắt từng chunk ra ~100-150 từ (mini-summary)
    → Trích actions từng chunk (mini-actions)
    → Ghép tất cả mini-summaries + mini-actions (~500-800 từ tổng)
    → 1 lần inference cuối: tóm tắt tổng hợp + merge/deduplicate actions
```

**Code logic chunking:**

```java
public class TextChunker {
    private static final int CHUNK_SIZE_WORDS = 800;    // cho bước 2
    private static final int OVERLAP_WORDS = 80;
    private static final int SUMMARY_CHUNK_WORDS = 1000; // cho bước 3+4
    private static final int TOKEN_THRESHOLD = 3000;     // ngưỡng cần chunk
    
    public static List<String> chunkForCorrection(String text) {
        // Chia theo câu (dấu chấm, chấm hỏi, chấm than)
        // Ghép câu vào chunk cho đến khi đạt CHUNK_SIZE_WORDS
        // Overlap OVERLAP_WORDS từ cuối chunk trước sang đầu chunk sau
    }
    
    public static List<String> chunkForSummary(String text) {
        // Chỉ chunk nếu vượt TOKEN_THRESHOLD
        // Chia theo paragraph hoặc topic boundary nếu có
        // Mỗi chunk ~SUMMARY_CHUNK_WORDS từ
    }
}
```

### 3.3 Chất lượng ASR từ I2S MIC phòng họp

**Vấn đề:** Điều kiện thực tế khác lab:
- MIC cố định trên box, người nói cách 1-3m
- Tiếng vọng phòng họp (reverb)
- Tiếng nền (quạt, máy lạnh, gõ bàn phím)
- Nhiều người nói xen lẫn (crosstalk)
- Giọng vùng miền khác nhau

**Giải pháp — Audio Preprocessing Pipeline:**

```
WAV file (raw)
  → Bước A: RNNoise (noise reduction) — lọc tiếng nền
  → Bước B: Audio normalization — chuẩn hóa volume
  → Bước C: Silero VAD — đánh dấu segment có giọng nói
  → Chỉ segment có giọng nói → PhoWhisper inference
```

- **RNNoise:** Thư viện C nhẹ (~100KB), chạy rất nhanh, lọc tốt stationary noise (quạt, máy lạnh). Build bằng NDK, gọi qua JNI.
- **Silero VAD:** Model ONNX ~2MB, inference <1ms/frame. Dùng để cắt bỏ im lặng trước khi ASR → giảm segment inference, tăng accuracy.
- **Normalization:** Chuẩn hóa RMS amplitude về target level, tránh đoạn quá nhỏ hoặc quá lớn.

### 3.4 Chất lượng Output Qwen2.5 3B Q4 Tiếng Việt

**Vấn đề cụ thể:**
- Sửa đúng thành sai: "Vinh" → "Vĩnh", "BSP" → "bạn sẽ phải"
- Thuật ngữ Anh-Việt xen lẫn bị Việt hóa sai
- Tóm tắt hallucinate nội dung không có
- Action items mơ hồ, gán sai người

**Giải pháp — Prompt Engineering + Từ điển:**

Cho phép user tạo **glossary** trước cuộc họp (tên người, thuật ngữ dự án). Inject vào prompt:

```
Bước 2 - Prompt sửa lỗi:
"Bạn là trợ lý sửa lỗi transcript tiếng Việt từ cuộc họp kỹ thuật.

QUY TẮC:
1. Sửa lỗi chính tả, ngữ pháp, thêm dấu câu đúng
2. GIỮ NGUYÊN tất cả từ tiếng Anh kỹ thuật (BSP, driver, sprint, boot, kernel...)
3. GIỮ NGUYÊN tên riêng theo danh sách: {glossary}
4. KHÔNG thêm, bớt, hoặc diễn giải lại nội dung
5. KHÔNG dịch thuật ngữ tiếng Anh sang tiếng Việt
6. Trả về ĐÚNG văn bản đã sửa, không giải thích

DANH SÁCH TÊN RIÊNG VÀ THUẬT NGỮ:
{user_glossary}

VĂN BẢN CẦN SỬA:
{chunk_text}"
```

```
Bước 3+4 - Prompt tóm tắt + actions (gộp):
"Bạn là trợ lý tóm tắt cuộc họp kỹ thuật tiếng Việt.

Từ nội dung cuộc họp bên dưới, hãy trả về ĐÚNG 2 phần:

## TÓM TẮT
Tóm tắt các nội dung chính đã thảo luận, quyết định đã đưa ra, vấn đề được đề cập. 
Viết ngắn gọn, có đánh số.

## HÀNH ĐỘNG
Liệt kê tất cả việc cần làm sau cuộc họp. 
Mỗi hành động 1 dòng, format:
- [Việc cần làm] | Phụ trách: [Tên] | Deadline: [Thời hạn hoặc 'Chưa xác định']

QUY TẮC:
1. CHỈ tóm tắt và trích xuất từ nội dung có sẵn
2. KHÔNG suy đoán hoặc thêm thông tin không có trong transcript  
3. Nếu không rõ người phụ trách, ghi 'Chưa xác định'
4. Giữ nguyên tên riêng và thuật ngữ tiếng Anh

NỘI DUNG CUỘC HỌP:
{text}"
```

**Temperature:** Đặt `0.1–0.3` cho cả 3 bước. Thấp = ít sáng tạo = ít hallucination.

### 3.5 Model Lifecycle & Memory Management

**Vấn đề:** Dù 8GB đủ, vẫn cần quản lý chặt vì load/unload model mất thời gian.

**Giải pháp — ModelManager singleton:**

```
Trạng thái model trong app:

  [App khởi động] → không model nào loaded
  
  [User bấm "Dừng ghi" → bắt đầu pipeline]
    → Load PhoWhisper/Whisper (~245MB, ~3-5s)
    → Chạy ASR toàn bộ segments
    → Unload ASR model (free RAM)
    → Load Qwen2.5 3B Q4 (~2GB, ~8-15s)
    → Chạy Bước 2 (sửa lỗi) - tất cả chunks
    → Chạy Bước 3+4 (tóm tắt + actions) - tất cả chunks
    → Unload Qwen2.5 (free RAM)
    → Lưu kết quả vào DB
```

Nguyên tắc: **1 model loaded tại 1 thời điểm.** Gọi `System.gc()` + `native free()` trước khi load model tiếp theo.

### 3.6 Recording Reliability — Multi-Segment + Pause/Resume

**Vấn đề:** Cần đảm bảo: mỗi segment là 1 file WAV hoàn chỉnh, pause/resume không tạo khoảng im lặng trong WAV, file không corrupt nếu crash giữa chừng, đánh số segment chính xác.

**Giải pháp — Streaming WAV write với pause support:**

```
Mỗi segment = 1 file WAV riêng

1. Bắt đầu segment mới:
   - Tạo file: {meeting_title}_{segment_number:02d}.wav
   - Ghi WAV header placeholder (file size = 0)
   - AudioRecord.startRecording()
   
2. Recording loop (chạy liên tục):
   - AudioRecord.read() → PCM buffer (4096 bytes)
   - NẾU trạng thái == RECORDING: write buffer vào file
   - NẾU trạng thái == PAUSED: KHÔNG write (discard buffer, AudioRecord vẫn chạy)
   - Mỗi 30 giây: seek về header, update file size + data size, flush

3. Khi PAUSE:
   - Set flag isPaused = true
   - Recording loop vẫn chạy nhưng skip write
   - UI hiển thị "Tạm dừng" + đồng hồ pause riêng
   - KHÔNG stop AudioRecord (tránh glitch khi resume)

4. Khi RESUME:
   - Set flag isPaused = false  
   - Recording loop tiếp tục write → audio liền mạch, không gap

5. Khi KẾT THÚC ĐOẠN:
   - AudioRecord.stop()
   - Update WAV header lần cuối
   - Close file
   - Cập nhật DB: segment status = 'saved', duration, file size
   - Sẵn sàng cho segment tiếp theo hoặc chuyển sang xử lý

6. Recovery nếu crash:
   - App kiểm tra khi mở: tìm segment có status = 'recording'
   - Nếu WAV header size < actual file size → sửa header
   - Chuyển status thành 'saved'
```

**Đặt tên file — SegmentNaming logic:**

```java
public class SegmentNaming {
    // Tên mặc định nếu user không đặt
    public static String defaultTitle(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault());
        return "Cuochop_" + sdf.format(new Date(timestamp));
    }
    
    // Tạo tên file cho segment
    public static String segmentFileName(String meetingTitle, int segmentNumber) {
        // Sanitize title: bỏ dấu, thay space bằng _, bỏ ký tự đặc biệt
        String safe = sanitize(meetingTitle);
        return String.format("%s_%02d.wav", safe, segmentNumber);
        // Ví dụ: "Hop_BSP_01.wav", "Hop_BSP_02.wav"
    }
    
    // Tính segment number tiếp theo cho meeting
    public static int nextSegmentNumber(MeetingDao dao, long meetingId) {
        Integer max = dao.getMaxSegmentNumber(meetingId); // SELECT MAX(segment_number)
        return (max == null) ? 1 : max + 1;
    }
}
```

Recording thread: `android.os.Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)`

### 3.7 Thermal Management

**Vấn đề:** Giai đoạn ghi âm không nóng, nhưng giai đoạn xử lý AI 7-13 phút liên tục sẽ đẩy nhiệt lên.

**Giải pháp — Monitor & Throttle:**

```java
// Đọc nhiệt độ SoC
int temp = readThermalZone(); // /sys/class/thermal/thermal_zone0/temp

if (temp > 75000) {      // > 75°C — critical
    // Giảm thread count xuống 2
    // Chèn 500ms delay giữa các chunk inference  
} else if (temp > 65000) { // > 65°C — warm
    // Giảm thread count xuống 3
    // Chèn 200ms delay
} else {                   // normal
    // Full speed, 4 threads
}
```

Với flow tuần tự, thermal ít nghiêm trọng hơn so với streaming. Chỉ cần monitor, không cần state machine phức tạp.

---

## 4. Audio Pipeline Chi Tiết

### 4.1 Recording

```
I2S MIC Hardware
  ↓
ALSA/TinyALSA driver (kernel)
  ↓
Android AudioFlinger / AudioRecord API
  ↓
Java AudioRecord (SOURCE_DEFAULT hoặc SOURCE_UNPROCESSED)
  - Sample rate: 16000 Hz
  - Channel: MONO (AudioFormat.CHANNEL_IN_MONO)
  - Encoding: PCM_16BIT
  - Buffer size: AudioRecord.getMinBufferSize() * 2 (safety margin)
  ↓
PCM buffer (4096 bytes = 128ms @ 16kHz/16bit/mono)
  ↓
FileOutputStream → .wav file
```

**Lưu ý I2S MIC trên Genio 720:**
- Kiểm tra xem AudioRecord có cần SOURCE cụ thể không (SOURCE_DEFAULT hay SOURCE_MIC)
- Nếu I2S MIC không qua standard audio path, có thể cần custom HAL hoặc đọc trực tiếp từ ALSA device
- Test: `adb shell cat /proc/asound/cards` để xác nhận sound card
- Test: `adb shell tinycap /sdcard/test.wav -D 0 -d 0 -c 1 -r 16000 -b 16` để test ghi âm raw

### 4.2 Preprocessing (sau khi ghi xong)

```
Raw WAV file
  ↓
[1] Load toàn bộ PCM data vào memory (30 phút @ 16kHz/16bit/mono = ~57MB)
  ↓
[2] RNNoise: xử lý frame-by-frame (480 samples = 10ms/frame)
    Input: noisy PCM → Output: clean PCM
    Thời gian: ~2-3 giây cho 30 phút audio (rất nhanh)
  ↓
[3] Normalization: 
    Tính RMS toàn bộ → scale về target RMS (-20 dBFS)
    Clip protection: limit peak ở ±0.95
  ↓
[4] Silero VAD: 
    Chạy trên từng window 512 samples (32ms)
    Output: danh sách speech segments [(start_ms, end_ms), ...]
    Merge segments gần nhau (gap < 300ms)
    Pad mỗi segment thêm 200ms hai đầu
  ↓
[5] Chia thành ASR segments:
    Với mỗi speech segment từ VAD:
      Nếu < 30s → 1 ASR segment
      Nếu > 30s → chia tại điểm im lặng gần nhất (dùng energy threshold)
    Skip segment < 500ms (noise spikes)
  ↓
List<AudioSegment> segments (chỉ chứa speech, đã clean)
```

### 4.3 ASR Inference

```
Với mỗi AudioSegment:
  ↓
  Resample nếu cần (PhoWhisper expect 16kHz)
  ↓
  Convert PCM int16 → float32 normalized [-1.0, 1.0]
  ↓
  PhoWhisper/Whisper inference (qua JNI)
    - n_threads: 4
    - language: "vi" hoặc "en" 
    - translate: false
    - no_timestamps: true (không cần timestamp cho use case này)
  ↓
  Output: text string cho segment
  ↓
Ghép tất cả segment texts → raw_transcript
Lưu raw_transcript vào DB
Unload ASR model
```

---

## 5. LLM Pipeline Chi Tiết

### 5.1 llama.cpp Configuration

```
Khởi tạo model:
  - model_path: /sdcard/MeetingNotes/models/qwen2.5-3b-q4_k_m.gguf
  - n_ctx: 4096 (context window)
  - n_threads: 4 (physical cores)
  - n_gpu_layers: 0 (CPU only, trừ khi GPU offload được test ổn định)
  - use_mmap: true (memory-mapped, OS quản lý page)
  - use_mlock: false (không lock RAM, để OS tự swap nếu cần)

Inference parameters:
  - temperature: 0.2
  - top_p: 0.9
  - top_k: 40
  - repeat_penalty: 1.1
  - max_tokens: tuỳ bước (xem bên dưới)
```

### 5.2 Bước 2 — Sửa lỗi text

```
Input: raw_transcript (toàn bộ)
Chunking: 800 từ/chunk, overlap 80 từ

Với mỗi chunk:
  prompt = CORRECTION_SYSTEM_PROMPT + glossary + chunk_text
  max_tokens = len(chunk_text_tokens) * 1.2  (cho phép dài hơn 20% do thêm dấu câu)
  
  inference → corrected_chunk

Merge chunks:
  Với vùng overlap giữa chunk N và chunk N+1:
    Giữ phiên bản từ chunk N+1 (nó có ngữ cảnh tốt hơn ở đầu)
  
Output: clean_transcript
Lưu vào DB
```

### 5.3 Bước 3+4 — Tóm tắt + Actions (gộp)

```
Input: clean_transcript

Đếm tokens ước tính: word_count * 1.8 (hệ số tiếng Việt)

NẾU estimated_tokens ≤ 3000:
  → Single-pass:
    prompt = SUMMARY_ACTION_PROMPT + clean_transcript
    max_tokens = 1024
    inference → response chứa cả ## TÓM TẮT và ## HÀNH ĐỘNG

NẾU estimated_tokens > 3000:
  → Hierarchical:
    Chia clean_transcript thành chunks ~1000 từ
    
    Pass 1 — Với mỗi chunk:
      prompt = "Tóm tắt ngắn gọn đoạn sau và liệt kê hành động nếu có:\n" + chunk
      max_tokens = 300
      inference → mini_summary_and_actions
    
    Pass 2 — Ghép tất cả mini results:
      combined = join(all mini_summary_and_actions)
      prompt = SUMMARY_ACTION_PROMPT + combined
      max_tokens = 1024
      inference → final summary + actions

Parse response:
  Split tại "## TÓM TẮT" và "## HÀNH ĐỘNG"
  Parse actions thành List<ActionItem>(description, assignee, deadline)
  
Lưu summary + actions vào DB
Unload LLM
```

---

## 6. Database Schema (Room)

```sql
-- Bảng chính: cuộc họp (1 meeting = N segments)
CREATE TABLE meetings (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT NOT NULL,              -- "Hop BSP" (user đặt hoặc mặc định)
    created_at    INTEGER NOT NULL,           -- Unix timestamp
    language      TEXT NOT NULL DEFAULT 'vi', -- 'vi' hoặc 'en'
    status        TEXT NOT NULL DEFAULT 'recording',
        -- 'recording' | 'selecting_segments' | 'processing_asr' 
        -- | 'processing_correction' | 'processing_summary' 
        -- | 'completed' | 'error'
    progress      INTEGER DEFAULT 0,         -- 0-100% (progress tổng pipeline)
    
    -- Kết quả AI (từ các selected segments gộp lại)
    raw_transcript    TEXT,     -- Output bước 1 (ASR) — ghép từ tất cả selected segments
    clean_transcript  TEXT,     -- Output bước 2 (sửa lỗi)
    summary           TEXT,     -- Output bước 3
    word_count        INTEGER DEFAULT 0,
    total_duration_ms INTEGER DEFAULT 0,     -- Tổng thời lượng các selected segments
    
    asr_model         TEXT,     -- 'phowhisper' hoặc 'whisper'
    llm_model         TEXT,     -- 'qwen2.5-3b-q4'
    processing_time_ms INTEGER DEFAULT 0,
    error_message     TEXT
);

-- Bảng segments: mỗi đoạn ghi âm trong 1 cuộc họp
CREATE TABLE segments (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    meeting_id    INTEGER NOT NULL,
    segment_number INTEGER NOT NULL,          -- 1, 2, 3, ... (tự tăng theo meeting)
    audio_path    TEXT NOT NULL,              -- "/sdcard/MeetingNotes/audio/Hop_BSP_01.wav"
    audio_size    INTEGER DEFAULT 0,          -- Bytes
    duration_ms   INTEGER DEFAULT 0,          -- Thời lượng đoạn này
    created_at    INTEGER NOT NULL,           -- Timestamp bắt đầu ghi đoạn này
    status        TEXT NOT NULL DEFAULT 'recording',
        -- 'recording' | 'paused' | 'saved' | 'processing' | 'transcribed'
    is_selected   INTEGER DEFAULT 1,          -- 1 = sẽ đưa vào xử lý AI, 0 = bỏ qua
    
    -- Transcript riêng của segment này (output ASR)
    raw_transcript TEXT,                      -- ASR output cho riêng segment này
    
    -- Thống kê pause/resume trong segment
    pause_count   INTEGER DEFAULT 0,          -- Số lần pause
    total_pause_ms INTEGER DEFAULT 0,         -- Tổng thời gian pause (không ghi vào WAV)
    
    FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
    UNIQUE(meeting_id, segment_number)        -- Đảm bảo không trùng số thứ tự
);

-- Bảng action items
CREATE TABLE action_items (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    meeting_id  INTEGER NOT NULL,
    description TEXT NOT NULL,
    assignee    TEXT,
    deadline    TEXT,
    is_done     INTEGER DEFAULT 0,
    sort_order  INTEGER DEFAULT 0,
    FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
);

-- Bảng glossary (từ điển thuật ngữ)
CREATE TABLE glossary (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    term  TEXT NOT NULL UNIQUE
);

-- Bảng bookmarks (đánh dấu thời điểm quan trọng)
CREATE TABLE bookmarks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    segment_id   INTEGER NOT NULL,            -- Thuộc segment nào (không phải meeting)
    timestamp_ms INTEGER NOT NULL,            -- Thời điểm trong segment audio
    note         TEXT,
    FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE CASCADE
);
```

### 6.1 Quan hệ dữ liệu

```
Meeting "Hop BSP" (id=1)
  ├── Segment 01 (id=1, is_selected=1) → "Hop_BSP_01.wav" (30 phut)
  ├── Segment 02 (id=2, is_selected=1) → "Hop_BSP_02.wav" (25 phut)  
  ├── Segment 03 (id=3, is_selected=0) → "Hop_BSP_03.wav" (15 phut, user bo chon)
  ├── Segment 04 (id=4, is_selected=1) → "Hop_BSP_04.wav" (20 phut)
  │
  ├── raw_transcript = ASR(seg01) + ASR(seg02) + ASR(seg04)  ← chi selected
  ├── clean_transcript = Qwen(raw_transcript)
  ├── summary = Qwen(clean_transcript)
  └── ActionItems [...]
```

---

## 7. Cấu Trúc Project

```
com.bhs.meetingnotes/
│
├── App.java                              // Application class, Hilt setup
│
├── ui/
│   ├── home/
│   │   ├── HomeActivity.java             // Màn hình danh sách cuộc họp (landscape sidebar layout)
│   │   ├── HomeViewModel.java
│   │   └── MeetingListAdapter.java       // RecyclerView adapter cho danh sách
│   │
│   ├── recording/
│   │   ├── RecordingActivity.java        // Màn hình ghi âm multi-segment (landscape 2 panel)
│   │   ├── RecordingViewModel.java       // LiveData: timer, audio level, state, segment list
│   │   └── SegmentListAdapter.java       // Hiển thị danh sách segments đã ghi bên panel phải
│   │
│   ├── segments/
│   │   ├── SegmentSelectActivity.java    // Màn hình chọn segments trước khi xử lý AI
│   │   ├── SegmentSelectViewModel.java
│   │   └── SegmentSelectAdapter.java     // RecyclerView + checkbox chọn/bỏ segments
│   │
│   ├── processing/
│   │   ├── ProcessingActivity.java       // Màn hình pipeline 4 bước + preview
│   │   └── ProcessingViewModel.java      // LiveData: step, progress, preview texts
│   │
│   ├── result/
│   │   ├── ResultActivity.java           // Màn hình kết quả (tabs: tóm tắt, actions, transcript)
│   │   ├── ResultViewModel.java
│   │   ├── SummaryFragment.java
│   │   ├── ActionsFragment.java
│   │   └── TranscriptFragment.java
│   │
│   └── settings/
│       ├── SettingsActivity.java         // Màn hình cài đặt (landscape sidebar layout)
│       ├── SettingsViewModel.java
│       └── GlossaryAdapter.java          // Quản lý từ điển thuật ngữ
│
├── data/
│   ├── audio/
│   │   ├── AudioRecorder.java            // I2S MIC recording — pause/resume support
│   │   ├── AudioPreprocessor.java        // RNNoise + normalize + VAD
│   │   ├── WavFileWriter.java            // Streaming WAV write với header recovery
│   │   ├── AudioSegmenter.java           // Chia audio thành ASR chunks (30s)
│   │   └── SegmentNaming.java            // Logic đặt tên file: {title}_{nn}.wav
│   │
│   ├── asr/
│   │   ├── AsrEngine.java                // Interface chung cho ASR
│   │   ├── PhoWhisperEngine.java         // PhoWhisper JNI wrapper
│   │   └── WhisperEngine.java            // Whisper (AI Hub MediaTek) JNI wrapper
│   │
│   ├── llm/
│   │   ├── LlamaEngine.java             // llama.cpp JNI bridge (load/unload/inference)
│   │   ├── TextChunker.java             // Chunking logic (800 từ overlap 80)
│   │   ├── PromptBuilder.java           // Build prompts với glossary injection
│   │   ├── ResponseParser.java          // Parse "## TÓM TẮT" và "## HÀNH ĐỘNG"
│   │   └── ModelManager.java            // Singleton quản lý load/unload lifecycle
│   │
│   ├── local/
│   │   ├── AppDatabase.java             // Room database
│   │   ├── MeetingDao.java
│   │   ├── MeetingEntity.java
│   │   ├── SegmentEntity.java           // Đoạn ghi âm (1 meeting → N segments)
│   │   ├── SegmentDao.java
│   │   ├── ActionItemEntity.java
│   │   ├── GlossaryEntity.java
│   │   └── BookmarkEntity.java
│   │
│   └── export/
│       ├── PdfExporter.java             // Xuất báo cáo PDF
│       └── TtsReader.java               // Đọc tóm tắt qua loa (Android TTS)
│
├── domain/
│   ├── ProcessMeetingUseCase.java       // Orchestrate toàn bộ pipeline
│   └── MeetingRepository.java           // Repository pattern
│
├── service/
│   └── ProcessingService.java           // Foreground Service chạy pipeline background
│
├── util/
│   ├── ThermalMonitor.java              // Đọc nhiệt độ SoC, điều chỉnh thread count
│   └── FileUtils.java
│
├── di/
│   ├── AppModule.java                   // Hilt module
│   └── DatabaseModule.java
│
└── cpp/                                 // Native code (NDK)
    ├── CMakeLists.txt
    ├── llama-jni.cpp                    // llama.cpp JNI bridge
    ├── whisper-jni.cpp                  // whisper.cpp JNI bridge  
    └── rnnoise-jni.cpp                  // RNNoise JNI bridge

res/
├── layout/
│   ├── activity_home.xml               // Landscape: sidebar + grid cards
│   ├── activity_recording.xml          // Landscape: timer+controls left + segments list + info right
│   ├── activity_segment_select.xml     // Landscape: segment list with checkboxes + info panel
│   ├── activity_processing.xml         // Landscape: steps left + preview right
│   ├── activity_result.xml             // Landscape: summary left + actions right
│   ├── activity_settings.xml           // Landscape: menu left + panels right
│   ├── item_meeting_card.xml
│   ├── item_segment.xml               // Segment row: number, name, duration, checkbox
│   ├── item_segment_recording.xml     // Segment row trong recording screen (nhỏ hơn)
│   ├── item_action.xml
│   └── fragment_*.xml
│
├── values/
│   ├── strings.xml
│   ├── colors.xml
│   └── themes.xml                      // Material Design 3, landscape optimized
│
└── xml/
    └── backup_rules.xml
```

---

## 8. Key Files & Responsibilities

### 8.1 ProcessMeetingUseCase.java (Orchestrator — Multi-Segment)

Đây là file quan trọng nhất — điều phối toàn bộ pipeline cho nhiều segments:

```java
public class ProcessMeetingUseCase {
    
    /**
     * Xử lý cuộc họp: chỉ các segments có is_selected = true.
     * Gọi từ ProcessingService (Foreground Service).
     */
    public void execute(long meetingId) {
        Meeting meeting = repository.getMeetingById(meetingId);
        
        // Lấy danh sách segments đã chọn, sắp theo segment_number
        List<Segment> selectedSegments = repository.getSelectedSegments(meetingId);
        //   SELECT * FROM segments 
        //   WHERE meeting_id = ? AND is_selected = 1 
        //   ORDER BY segment_number ASC
        
        if (selectedSegments.isEmpty()) {
            updateError(meetingId, "Chưa chọn đoạn ghi âm nào");
            return;
        }
        
        // Tính tổng duration
        long totalDuration = 0;
        for (Segment seg : selectedSegments) {
            totalDuration += seg.durationMs;
        }
        meeting.totalDurationMs = totalDuration;
        
        // Tổng số ASR segments ước tính (để tính progress)
        int totalAsrSegments = 0;
        for (Segment seg : selectedSegments) {
            totalAsrSegments += Math.max(1, (int)(seg.durationMs / 30000)); // ~1 per 30s
        }
        
        // ============================================================
        // BƯỚC 0: Preprocessing audio (từng segment file)
        // ============================================================
        updateStatus(meetingId, "preprocessing", 0);
        AudioPreprocessor preprocessor = new AudioPreprocessor();
        
        // Preprocess từng segment WAV riêng
        // Output: List<List<AudioChunk>> — mỗi segment → list of speech chunks
        List<List<AudioChunk>> allChunks = new ArrayList<>();
        for (Segment seg : selectedSegments) {
            List<AudioChunk> chunks = preprocessor.process(seg.audioPath);
            // RNNoise → normalize → VAD → chia thành 30s chunks
            allChunks.add(chunks);
        }
        
        // ============================================================
        // BƯỚC 1: ASR — từng segment riêng, ghép transcript cuối
        // ============================================================
        updateStatus(meetingId, "processing_asr", 0);
        modelManager.loadAsr(meeting.language);
        
        StringBuilder fullRawTranscript = new StringBuilder();
        int processedChunks = 0;
        
        for (int segIdx = 0; segIdx < selectedSegments.size(); segIdx++) {
            Segment seg = selectedSegments.get(segIdx);
            List<AudioChunk> chunks = allChunks.get(segIdx);
            StringBuilder segTranscript = new StringBuilder();
            
            // Thêm separator giữa các segments trong transcript
            if (segIdx > 0) {
                fullRawTranscript.append("\n\n---\n\n"); // Ranh giới segment
            }
            
            for (AudioChunk chunk : chunks) {
                String text = asrEngine.transcribe(chunk);
                segTranscript.append(text).append(" ");
                processedChunks++;
                updateProgress(meetingId, processedChunks * 100 / totalAsrSegments);
            }
            
            // Lưu transcript riêng cho segment này
            String segText = segTranscript.toString().trim();
            seg.rawTranscript = segText;
            repository.updateSegment(seg);
            
            fullRawTranscript.append(segText);
        }
        
        // Lưu transcript gộp
        meeting.rawTranscript = fullRawTranscript.toString().trim();
        repository.updateMeeting(meeting);
        modelManager.unloadAsr();
        
        // ============================================================
        // Load LLM (1 lần, dùng cho bước 2 + 3 + 4)
        // ============================================================
        modelManager.loadLlm("qwen2.5-3b-q4");
        
        // ============================================================
        // BƯỚC 2: Sửa lỗi text
        // ============================================================
        updateStatus(meetingId, "processing_correction", 0);
        List<String> glossary = repository.getAllGlossaryTerms();
        List<String> textChunks = TextChunker.chunkForCorrection(meeting.rawTranscript);
        
        StringBuilder cleanText = new StringBuilder();
        for (int i = 0; i < textChunks.size(); i++) {
            String prompt = PromptBuilder.buildCorrectionPrompt(textChunks.get(i), glossary);
            String corrected = llamaEngine.generate(prompt, textChunks.get(i).length() * 2);
            cleanText.append(corrected).append("\n");
            updateProgress(meetingId, (i + 1) * 100 / textChunks.size());
        }
        
        meeting.cleanTranscript = TextChunker.mergeOverlap(cleanText.toString());
        repository.updateMeeting(meeting);
        
        // ============================================================
        // BƯỚC 3+4: Tóm tắt + Actions (gộp 1 lần inference)
        // ============================================================
        updateStatus(meetingId, "processing_summary", 0);
        int estimatedTokens = (int)(meeting.cleanTranscript.split("\\s+").length * 1.8);
        
        String summaryResponse;
        if (estimatedTokens <= 3000) {
            String prompt = PromptBuilder.buildSummaryActionPrompt(meeting.cleanTranscript);
            summaryResponse = llamaEngine.generate(prompt, 1024);
        } else {
            summaryResponse = hierarchicalSummarize(meeting.cleanTranscript);
        }
        
        // Parse response
        ParsedResult result = ResponseParser.parse(summaryResponse);
        meeting.summary = result.summary;
        meeting.wordCount = meeting.cleanTranscript.split("\\s+").length;
        meeting.status = "completed";
        repository.updateMeeting(meeting);
        repository.saveActionItems(meetingId, result.actionItems);
        
        modelManager.unloadLlm();
    }
}
```

### 8.2 ModelManager.java (Singleton)

```java
public class ModelManager {
    private static ModelManager instance;
    private boolean asrLoaded = false;
    private boolean llmLoaded = false;
    
    // Nguyên tắc: CHỈ 1 model loaded tại 1 thời điểm
    
    public void loadAsr(String language) {
        if (llmLoaded) unloadLlm();   // Đảm bảo LLM đã unload
        if (language.equals("vi")) {
            PhoWhisperEngine.loadModel(ASR_VI_MODEL_PATH);
        } else {
            WhisperEngine.loadModel(ASR_EN_MODEL_PATH);
        }
        asrLoaded = true;
    }
    
    public void unloadAsr() {
        PhoWhisperEngine.unloadModel();
        WhisperEngine.unloadModel();
        System.gc();  // Gợi ý GC
        asrLoaded = false;
    }
    
    public void loadLlm(String modelName) {
        if (asrLoaded) unloadAsr();   // Đảm bảo ASR đã unload
        LlamaEngine.loadModel(getLlmPath(modelName), 4096, 4);
        llmLoaded = true;
    }
    
    public void unloadLlm() {
        LlamaEngine.unloadModel();
        System.gc();
        llmLoaded = false;
    }
}
```

### 8.3 ProcessingService.java (Foreground Service)

```java
public class ProcessingService extends Service {
    // Foreground Service để pipeline chạy background
    // User có thể rời app, quay lại khi xong
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long meetingId = intent.getLongExtra("meeting_id", -1);
        
        // Tạo persistent notification
        Notification notification = buildNotification("Đang xử lý cuộc họp...", 0);
        startForeground(NOTIFICATION_ID, notification);
        
        // Chạy pipeline trên background thread
        executorService.execute(() -> {
            try {
                processMeetingUseCase.execute(meetingId);
                // Xong → notification "Hoàn tất"
                updateNotification("Đã hoàn tất xử lý!", 100);
            } catch (Exception e) {
                updateNotification("Lỗi: " + e.getMessage(), -1);
                repository.updateStatus(meetingId, "error", e.getMessage());
            } finally {
                stopForeground(false);
                stopSelf();
            }
        });
        
        return START_NOT_STICKY;
    }
}
```

---

## 9. Cấu Hình Build

### 9.1 build.gradle (app)

```groovy
android {
    compileSdk 35
    defaultConfig {
        applicationId "com.bhs.meetingnotes"
        minSdk 30
        targetSdk 35
        
        ndk {
            abiFilters 'arm64-v8a'  // Genio 720 là ARM64
        }
        
        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17 -O3"
                arguments "-DANDROID_STL=c++_shared",
                          "-DLLAMA_NATIVE=OFF"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }
}

dependencies {
    // UI
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    
    // Architecture
    implementation 'androidx.lifecycle:lifecycle-viewmodel:2.8.4'
    implementation 'androidx.lifecycle:lifecycle-livedata:2.8.4'
    implementation 'androidx.lifecycle:lifecycle-service:2.8.4'
    
    // Database
    implementation 'androidx.room:room-runtime:2.6.1'
    annotationProcessor 'androidx.room:room-compiler:2.6.1'
    
    // DI
    implementation 'com.google.dagger:hilt-android:2.51.1'
    annotationProcessor 'com.google.dagger:hilt-android-compiler:2.51.1'
}
```

### 9.2 CMakeLists.txt (Native)

```cmake
cmake_minimum_required(VERSION 3.22)
project(meetingnotes-native)

# llama.cpp (git submodule tại src/main/cpp/llama.cpp/)
add_subdirectory(llama.cpp)

# whisper.cpp (git submodule tại src/main/cpp/whisper.cpp/)
add_subdirectory(whisper.cpp)

# RNNoise
add_library(rnnoise STATIC
    rnnoise/src/denoise.c
    rnnoise/src/rnn.c
    rnnoise/src/rnn_data.c
    rnnoise/src/pitch.c
    rnnoise/src/celt_lpc.c
)
target_include_directories(rnnoise PUBLIC rnnoise/include)

# JNI bridges
add_library(meetingnotes-jni SHARED
    llama-jni.cpp
    whisper-jni.cpp
    rnnoise-jni.cpp
)

target_link_libraries(meetingnotes-jni
    llama
    whisper
    rnnoise
    android
    log
)
```

---

## 10. File & Storage Paths

```
/sdcard/MeetingNotes/
├── audio/                           # Thư mục theo meeting
│   ├── Hop_BSP_20260410/            # 1 thư mục per meeting
│   │   ├── Hop_BSP_01.wav           # Segment 1
│   │   ├── Hop_BSP_02.wav           # Segment 2
│   │   ├── Hop_BSP_03.wav           # Segment 3
│   │   └── Hop_BSP_04.wav           # Segment 4
│   └── Product_Roadmap_20260409/
│       ├── Product_Roadmap_01.wav
│       └── Product_Roadmap_02.wav
├── models/
│   ├── phowhisper-base.bin          # PhoWhisper model (~245MB)
│   ├── whisper-base-en.bin          # Whisper English model (AI Hub)
│   └── qwen2.5-3b-q4_k_m.gguf     # Qwen2.5 LLM (~1.8GB)
└── exports/
    └── Hop_BSP_20260410.pdf
```

Mỗi meeting có 1 thư mục riêng chứa tất cả segment WAV files. Tên thư mục = `{sanitized_title}_{date}`. Tên file = `{sanitized_title}_{segment_number:02d}.wav`.

---

## 11. Checklist Trước Khi Code

- [ ] Xác nhận I2S MIC hoạt động qua `tinycap` trên thiết bị G720
- [ ] Xác nhận AudioRecord API nhận được audio từ I2S (source nào?)
- [ ] Test PhoWhisper model inference trên Genio 720 — đo tốc độ (tokens/s)
- [ ] Test Qwen2.5 3B Q4 inference trên Genio 720 — đo tốc độ (tokens/s)
- [ ] Đo RAM usage thực tế khi load từng model
- [ ] Đo nhiệt độ SoC khi chạy inference liên tục 10 phút
- [ ] Build llama.cpp và whisper.cpp cho arm64-v8a trên Android NDK
- [ ] Chọn Whisper English model version từ AI Hub MediaTek

---

*Document version 1.0 — Tạo 10/2026 — Dùng làm input cho Claude Code development*
