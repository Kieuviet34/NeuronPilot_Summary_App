# Dịch ngôn ngữ real-time cho voice (bóc nội dung cuộc họp) — kế hoạch thực thi

> Quy ước từ ngữ: "**dịch**"/"**translate**" trong tài liệu này luôn nghĩa là **dịch ngôn ngữ** (machine translation) văn bản STT sang ngôn ngữ đích (ví dụ Việt → Anh) — thực hiện bằng cách đưa câu STT vào **prompt cho LLM** (Llama-3.2 hoặc Qwen2.5, cùng model đang dùng cho chat) và gọi qua `computeStreaming`/`libmtk_llm.so`. Đây **không phải** bước xử lý âm thanh hay giải mã (decode) nào khác — chỉ là dùng LLM sẵn có của app như một công cụ dịch văn bản.

## 1. Hiện trạng: `VoiceInputManager.java`

- `start()` (dòng 180): mở `AudioRecord` (16kHz mono PCM16), ghi liên tục ra file `voice_input.pcm` trong `recordingRunnable` (dòng 222).
- Dừng ghi chỉ khi người dùng bấm nút (`stopKeepResult()`, dòng 271) hoặc chạm mốc cứng `MAX_RECORD_MS = 28000` (dòng 60, 239). Không có VAD/endpoint tự động.
- Khi dừng: `pcmToWav()` (dòng 423) convert toàn bộ file PCM thành 1 WAV, `sendFullRecordingToServer()` (dòng 330) gửi đúng 1 request HTTP multipart tới `http://127.0.0.1:8080/inference`.
- `httpClient` (dòng 104-107) dùng `readTimeout = 30s`, `enqueue()` bất đồng bộ (dòng 349) — không chặn thread gọi.
- `initModel()` (dòng 130) chỉ ping 1 lần lúc khởi tạo tab Voice, không có kiểm tra/kết nối lại nếu `whisper-server` chết giữa phiên.
- `Callback` (dòng 72-83) có `onFinalText(String)` (dòng 76) trả 1 lần cho toàn phiên; `onPartialText` khai báo sẵn nhưng không được gọi ở đâu.

## 2. Hiện trạng: LLM inference (`MainActivity.java`)

- Một `modelHandle` duy nhất (dòng 82), init qua `initModel(yamlPath)` (native, dòng 153).
- `resetModel(modelHandle)` (dòng 971) gọi trước mỗi lượt sinh token → runtime native stateful, không reentrant: hai lệnh `computeStreaming` không được chạy đồng thời trên cùng `modelHandle`.
- `computeStreaming(modelHandle, prompt, TokenCallback)` (dòng 154, 988) chạy trong `StreamingComputeTask extends AsyncTask` (dòng 853).
- Token trả về qua `TokenCallback.onTokenReceived(String token)` (dòng 159), buffer và flush UI mỗi 45ms (`UI_FLUSH_INTERVAL_MS`, dòng 867) — tái dùng nguyên cho output dịch.
- `getJavaStopMarkers()` (dòng 948) cắt phần rác native trả thừa theo model — tái dùng cho output dịch.

## 3. Nguyên lý pipeline

Ghi âm + phát hiện ngắt câu (VAD/endpoint) chạy liên tục, không phụ thuộc tiến độ STT/dịch của câu trước — nếu không, mỗi câu sẽ phải chờ câu trước xử lý xong mới bắt đầu ghi/ngắt câu tiếp theo, gây trễ dồn tích khi họp kéo dài.

STT (giọng nói → văn bản) và dịch ngôn ngữ (văn bản → văn bản ngôn ngữ đích) là hai backend độc lập, chạy song song thật được:
- **STT**: HTTP loopback tới `whisper-server` — tiến trình PhoWhisper chạy ngay trên thiết bị (không phải server ngoài/cloud), encoder dùng NPU của chip, tiến trình tách biệt với app qua `127.0.0.1:8080` — dòng 330-390 `VoiceInputManager.java`.
- **Dịch ngôn ngữ**: JNI `computeStreaming` vào `libmtk_llm.so` trong tiến trình app, dùng prompt dịch thay vì prompt chat — dòng 154, 988 `MainActivity.java`.

Câu N đang chờ dịch ngôn ngữ qua LLM không chặn câu N+1 gửi STT tới `whisper-server`. Tranh chấp chỉ xảy ra trong nội bộ từng backend (2 request STT cùng lúc, hoặc 2 lệnh `computeStreaming` dịch cùng lúc trên 1 `modelHandle`) → mỗi backend cần single-flight riêng, xếp hàng FIFO theo `segmentId`, không cần khoá chung giữa 2 backend.

## 4. Thực thi theo file

### 4.1 `VoiceInputManager.java` — ghi âm liên tục, ngắt câu tự động

- Bỏ mô hình dừng hẳn `AudioRecord`/`recordingThread` mỗi lần gửi kết quả (`stopInternal()`, dòng 289). Recording thread chạy liên tục suốt phiên; chỉ đánh dấu mốc bắt đầu/kết thúc câu trong lúc thread vẫn đọc `AudioRecord`.
- Tách rời việc đọc audio khỏi việc ghi đĩa: hiện tại `recordingRunnable` (dòng 222-249) gọi `audioRecord.read()` rồi `pcmOut.write()` (I/O đĩa, dòng 231) **trong cùng một vòng lặp** — nếu đĩa chậm (I/O contention khi STT/dịch đang chạy song song), vòng đọc audio bị trễ theo, có nguy cơ tràn buffer nội bộ của `AudioRecord` và mất mẫu. Đổi sang: đọc vào ring buffer trong RAM, một thread khác tiêu thụ ring buffer để ghi đĩa/cắt segment — vòng đọc audio không bao giờ chờ I/O.
- Đặt `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)` cho `recordingThread` (hiện tạo bằng `new Thread(recordingRunnable, ...)`, dòng 211, không set priority) — nếu không, khi CPU bận xử lý STT/dịch cùng lúc, scheduler có thể trì hoãn thread ghi âm và gây giật/ngắt quãng audio.
- Tính dBFS trên `buffer` đang đọc (dòng 228) để tự phát hiện điểm bắt đầu/kết thúc câu; giữ lại một đoạn "pre-roll" ~200-300ms audio trước mốc phát hiện bắt đầu nói (dùng chính ring buffer ở trên) để không cắt mất âm tiết đầu câu.
- Ngưỡng dBFS đơn thuần dễ bắt nhầm tiếng ồn phòng họp (điều hòa, tiếng người khác) thành câu nói — cần thêm độ trễ xác nhận (chỉ tính là "bắt đầu nói" nếu vượt ngưỡng liên tục vài trăm ms, tương tự `RT_VAD_START_MS`) và "hangover" vài trăm ms sau khi năng lượng xuống dưới ngưỡng trước khi chốt là hết câu, tránh cắt câu giữa một khoảng ngừng ngắn tự nhiên khi nói.
- Giữ nguyên cách gọi bất đồng bộ `httpClient.newCall(request).enqueue(...)` (dòng 349) khi chuyển sang gửi theo từng câu.
- Thêm watchdog ping định kỳ tới `WHISPER_SERVER_URL` (thay vì chỉ ping 1 lần trong `initModel()`) để phát hiện server chết giữa phiên dài; xem lại `readTimeout = 30s` (dòng 106) cho phù hợp với câu ngắn gửi liên tục thay vì 1 request/lần bấm.

### 4.2 Single-flight riêng cho từng backend, có kiểm soát tải

- **STT**: nếu câu N chưa nhận response từ `whisper-server` mà câu N+1 đã ngắt xong, vẫn gửi request cho N+1 ngay, không chờ N.
- **Dịch ngôn ngữ (qua LLM/`mtk_llm`)**: thêm hàng đợi FIFO riêng cho bước dịch giữa các câu voice — câu nào STT xong trước vào hàng dịch trước; không gọi 2 lệnh `computeStreaming` dịch đồng thời trên cùng `modelHandle`.
- Cả 2 hàng đợi (STT chờ response, dịch chờ lượt) phải **có giới hạn kích thước** (ví dụ tối đa 5-8 câu đang chờ) và **chính sách khi đầy** (bỏ câu cũ nhất kèm đánh dấu "đã bỏ qua" trên UI, hoặc gộp 2 câu liên tiếp thành 1 lần dịch) — nếu không giới hạn, một đoạn NPU/CPU bị chậm tạm thời (nóng máy, throttling) sẽ khiến backlog tăng dần không giới hạn trong suốt phiên họp 60 phút, đúng rủi ro chính đã nêu ở mục 3.
- Vì `resetModel`/`computeStreaming` (dòng 971, 988) không reentrant trên `modelHandle`: bọc toàn bộ lệnh gọi 2 hàm này (kể cả từ hàng đợi dịch mới) bằng **1 khoá duy nhất** (ví dụ `synchronized`/`Semaphore(1)`) ở cấp `MainActivity`, thay vì chỉ dựa vào giả định "chat và dịch không bao giờ chạy cùng lúc" — nếu người dùng chuyển tab rất nhanh (vừa nhận câu dịch cuối, vừa bấm gửi tin nhắn chat), thiếu khoá thật sẽ có nguy cơ 2 luồng gọi native đồng thời, gây lỗi khó tái hiện thay vì bị chặn tường minh.
- Theo dõi thứ tự bằng `segmentId` tăng dần theo câu nói. Mỗi câu chỉ có đúng 1 kết quả STT + 1 kết quả dịch ngôn ngữ, không có bản nháp/partial nên không cần `version`.

### 4.3 Dịch ngôn ngữ qua `computeStreaming`

- Khi có text STT của 1 câu: build prompt yêu cầu LLM dịch câu đó sang ngôn ngữ đích, dùng template chat token đã có (`LLAMA_*`/`QWEN_*`, dòng 64-78) để bọc nội dung yêu cầu dịch, rồi gọi `computeStreaming(modelHandle, translatePrompt, callback)` theo đúng mẫu `StreamingComputeTask` (dòng 853). Route output (bản dịch) vào UI dòng phụ đề liên tục thay vì `ChatAdapter`.
- Gọi `resetModel(modelHandle)` (dòng 971) trước mỗi câu cần dịch, như hành vi bắt buộc hiện tại của runtime.
- Tái dùng nguyên `startUiFlushLoop()`/`uiPendingBuffer` (dòng 863-885) và `getJavaStopMarkers()` (dòng 948) cho output bản dịch.

### 4.4 `MainActivity.java` — mở rộng callback

- Thêm vào `VoiceInputManager.Callback` (dòng 72-83): `onSegmentTranscribed(segmentId, sourceText)` và `onSegmentTranslated(segmentId, translatedText)`, thay cho `onFinalText(String)` hiện chỉ trả 1 lần/phiên.
- Bỏ `onPartialText` (dòng 75, hiện dead) khỏi interface khi refactor — mô hình theo câu này không có bản nháp.

### 4.5 Chạy phiên họp trong Foreground Service, không gắn vào vòng đời `MainActivity`

- Toàn bộ pipeline hiện tại (ghi âm, gọi STT, gọi dịch, giữ `segmentId`/lịch sử câu) sống trong `Activity` (`MainActivity`/`VoiceInputManager` đều là đối tượng gắn với Activity). Với phiên họp dự kiến chạy 60 phút, cần chuyển state của phiên (audio pipeline + hàng đợi + lịch sử segment) vào một **Foreground Service** (loại `microphone`, bắt buộc khai báo từ Android 10+/12+) có thông báo (notification) hiển thị đang ghi/dịch.
- Lý do bắt buộc, không phải tuỳ chọn: nếu người dùng khoá màn hình hoặc chuyển sang app khác giữa cuộc họp (rất phổ biến trong thực tế), Android sẽ giới hạn/tạm dừng hoạt động của tiến trình chạy trong Activity ở background; ghi âm mic liên tục 60 phút không thể đảm bảo chỉ dựa vào `Activity` đang foreground.
- `MainActivity` khi đó chỉ còn vai trò hiển thị (bind tới Service, nhận `onSegmentTranscribed`/`onSegmentTranslated` qua Service để cập nhật UI phụ đề) — nếu Activity bị huỷ/tạo lại giữa chừng (xoay app, low-memory), phiên họp và dữ liệu đã dịch không bị mất vì state nằm trong Service.

## 5. Cần đo trước khi chốt ngưỡng thời gian

- Độ trễ 1 request STT (audio ngắn 1-3s) trên `whisper-server` thực tế của thiết bị — chưa có số đo nào trong code/log hiện tại.
- Độ trễ `computeStreaming` cho 1 prompt dịch ngắn (5-15 từ) — log `startTime`/token count như `StreamingComputeTask` đang tự báo tokens/s (dòng 982), áp dụng lại cho tác vụ dịch.
- Độ trễ **prefill cố định** của bước dịch (xem mục 6.3) — đo riêng, vì đây là chi phí sàn (floor) không phụ thuộc độ dài câu, quyết định cadence tối đa mà pipeline dịch có thể theo kịp.

## 6. Đánh giá chuyên sâu & tự phản biện

### 6.1 Mô hình callback JNI đã xác nhận là đồng bộ, chặn thread gọi

Đọc `app/src/main/cpp/nn_sample.cpp`: `computeStreaming` nhận thẳng `JNIEnv*` của thread gọi (dòng 80) và gọi `env->CallVoidMethod(_callback, onTokenReceivedMethod, ...)` trực tiếp trong native (dòng 135, 187) — nghĩa là toàn bộ vòng sinh token chạy **đồng bộ, chặn cứng thread Java gọi nó** cho tới khi xong hoặc gặp stop token. Điều này xác nhận chắc chắn (không còn là suy đoán) rằng:
- Không thể gọi `computeStreaming` từ 2 thread cùng lúc trên 1 `modelHandle` — sẽ đụng độ ở tầng JNI/runtime C++, không chỉ là vấn đề tổ chức code Java.
- Việc dùng `synchronized`/`Semaphore(1)` bọc quanh `computeStreaming` (mục 4.2) là bắt buộc về mặt kỹ thuật, không phải "cẩn thận cho chắc".

### 6.2 `AsyncTask` đã deprecated, và đang dùng `SERIAL_EXECUTOR` toàn app theo mặc định

`StreamingComputeTask extends AsyncTask` (dòng 853) được gọi bằng `.execute(prompt)` (dòng 269) — không chỉ định executor, nên mặc định dùng `AsyncTask.SERIAL_EXECUTOR`, **một hàng đợi tuần tự dùng chung cho MỌI `AsyncTask` trong tiến trình**, kể cả các `AsyncTask` khác (`InitModelTask`, v.v.). Đây từng là lý do vô tình giúp code hiện tại an toàn (chat luôn tuần tự), nhưng `AsyncTask` đã bị đánh dấu deprecated từ API 30 và app đang target SDK 34 — không nên xây thêm tính năng mới (tác vụ dịch) dựa trên hành vi ngầm định của một API đã lỗi thời. Khuyến nghị: thay bằng 1 `ExecutorService` tường minh (`Executors.newSingleThreadExecutor()`) làm "làn NPU" dùng chung cho cả sinh chat và dịch, kết hợp khoá ở mục 4.2 — vừa rõ ràng vừa không phụ thuộc hành vi mặc định có thể đổi khác giữa các phiên bản Android.

### 6.3 Chi phí prefill cố định khi dùng LLM để dịch từng câu ngắn

`config_llama3.2_1b_instruct.yaml` khai báo `promptTokenBatchSize: 128` và trỏ tới 2 DLA cố định hình dạng: `dlaPromptPaths` (`..._128t1024c_0.dla`, dùng cho prefill) và `dlaGenPaths` (`..._1t1024c_0.dla`, dùng cho decode từng token). Nghĩa là **mọi lệnh gọi `computeStreaming` sau `resetModel` đều chạy đúng 1 lần inference prefill kích thước cố định 128 token trên NPU**, bất kể prompt dịch thực tế dài hay ngắn (một câu 5 từ vẫn tốn chi phí prefill tương đương một câu gần lấp đầy 128 token). Hệ quả:
- Có lợi: độ trễ prefill gần như **hằng số**, dễ benchmark và dự đoán, không tăng theo độ dài câu (miễn còn nằm trong 128 token).
- Bất lợi cần benchmark trước khi cam kết cadence: nếu chi phí prefill cố định này (chưa đo) lớn hơn khoảng cách trung bình giữa các câu nói trong một cuộc họp nói nhanh, pipeline dịch sẽ liên tục tụt lại phía sau dù đã có hàng đợi FIFO — lúc đó vấn đề không nằm ở tổ chức code mà ở giới hạn phần cứng/model, và cần cân nhắc giảm tần suất dịch (gộp 2-3 câu ngắn thành 1 lần dịch) thay vì cố dịch từng câu một.

### 6.4 Rủi ro bộ nhớ/nhiệt khi tích luỹ theo phiên 60 phút

- `largeHeap="true"` đã được bật sẵn trong `AndroidManifest.xml` — dấu hiệu app từng gặp áp lực bộ nhớ; danh sách segment/phụ đề tích luỹ suốt 60 phút (audio buffer, text gốc, bản dịch) cần có giới hạn số lượng giữ trong RAM/UI (ví dụ chỉ giữ chi tiết N câu gần nhất trong `RecyclerView`, phần cũ hơn ghi ra file nếu cần lưu biên bản), không giữ vô hạn như lịch sử chat hiện tại (`fullHistory`, có cơ chế `trimHistoryToPromptBudget`/auto-reset theo `maxTurns` — cơ chế tương tự nên áp dụng cho phiên họp, nhưng dựa trên bộ nhớ chứ không phải token budget).
- Không có cơ chế đo/phản ứng với nhiệt độ thiết bị trong code hiện tại — trước khi thêm state machine NORMAL/SLOW/HOT, việc tối thiểu cần có là **giới hạn hàng đợi** (mục 4.2) để tự động "xuống cấp" một cách tự nhiên (bỏ câu cũ) thay vì phải cài đặt riêng một cảm biến nhiệt.
