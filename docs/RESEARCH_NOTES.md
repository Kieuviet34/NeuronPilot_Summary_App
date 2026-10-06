# RESEARCH NOTES — Meeting Notes App (G720 AI Box)

> **Tra cứu ngày 02/10/2026.** Bằng chứng và nguồn cho mã `[N#]` trong `PROJECT_CONTEXT.md` và `TECHNICAL_REVIEW.md`. Chỉ ghi điều nguồn nói, kèm diễn giải ngắn. Mục cuối liệt kê các điểm nguồn **mâu thuẫn hoặc chưa kiểm chứng**.

## A. Nền tảng Genio 720 / MediaTek

| ID | Nội dung nguồn nói | Nguồn |
|---|---|---|
| N1 | Genio 720: 2× Cortex-A78 2.4–2.6 GHz + 6× Cortex-A55 2.0 GHz; GPU Mali-G57 MC2; NPU thế hệ 8, 9 TOPS (1× MDLA5.3, GenAI); bộ nhớ x32 LP4X-4266 (tới 8 GB) hoặc x32 LP5(X)-6400 (tới 16 GB) | [mediatek.com/products/iot/genio-iot/genio-720](https://www.mediatek.com/products/iot/genio-iot/genio-720) |
| N2 | Tổng hiệu năng AI 10 TOPS, trong đó NPU 9 TOPS; Android và Yocto được hỗ trợ chính thức | [genio.mediatek.com/genio-720](https://genio.mediatek.com/genio-720) |
| N3 | Module SoM-SD721 dùng Genio 720, 8 GB LPDDR5, hỗ trợ Android 15 | [amobile-solutions.com](https://www.amobile-solutions.com/product-detail4-18-54-0.htm) |
| N4 | Bảng hiệu năng GenAI trên G720 (prompt/generative tok/s): Qwen2.5-3B 163.23/10.64; Qwen2.5-1.5B 337.06/19.25; Qwen2.5-7B 69.47/4.73; Qwen3-0.6B 535.94/22.93; Qwen3-1.7B 262.73/13.84; Qwen3-4B 125.58/7.25; Qwen3-8B 80.17/4.76; gemma3-4B 176.79/5.93; llama3.2-3B 153.56/10.36; Phi-3-mini 127.56/7.28. ASR: Whisper-base (8w16a) 160.6 ms / 174.8 tok/s; Qwen3-ASR-0.6B 127.6/7.1 tok/s; Moonshine-Tiny "V" (đã xác minh). Trang cập nhật 02/10/2026 | [litert_gai_g720](https://genio.mediatek.com/doc/iot-aihub/ai_hub/model_zoo/litert_gai/litert_gai_g720.html) |
| N5 | `asr_cmdline_tool`: runner Whisper (encoder DLA chạy 1 lần rồi giải mã tham lam với tiền tố bắt đầu cố định, mặc định tiếng Anh; encoder 1500 frame = 30 s; cache giải mã 256; tối đa 448 token), Qwen3-ASR (encoder + LLM, nhận `-p` văn bản bổ sung, thư mục DLA `llm_256t2048c_fms` / `llm_1t2048c_fms`), Moonshine (chia đoạn 2 s). Đầu vào WAV 16 kHz mono PCM16. Ví dụ Qwen3-ASR: encoder 0.404 s, prompt 136 token trong 1.07 s, decode 7.1 tok/s | [asr_cmdline_tool](https://genio.mediatek.com/doc/iot-aihub/ai_hub/supported_os/yocto/litert_gai/asr_cmdline_tool.html) |
| N6 | Gói dựng sẵn cho G360/420/520/720: Qwen3-0.6B/1.7B/4B/8B, Qwen2.5-1.5B/3B/7B-Instruct, llama3.2-1B/3B, Qwen3VL-2B, Qwen3-ASR-0.6B (có thể có bản khác theo trang) | [supported_models](https://genio.mediatek.com/doc/iot-aihub/ai_hub/model_zoo/litert_gai/supported_models.html) |
| N7 | Mục GenAI chỉ cung cấp dữ liệu hiệu năng/năng lực; **GAI toolkit cần NDA** với MediaTek | [litert_gai](https://genio.mediatek.com/doc/iot-aihub/ai_hub/model_zoo/litert_gai.html) |
| N8 | Tài liệu/công cụ/ảnh OS Android giới hạn Direct Customer có tài khoản MOL; Android hỗ trợ TFLite(LiteRT) và ONNX Runtime; thư viện ONNX Runtime trên Android phải liên hệ đại diện MediaTek, chưa có demo | [supported_os/android](https://genio.mediatek.com/doc/iot-aihub/ai_hub/supported_os/android.html) |
| N9 | Trả lời của MediaTek (26/05/2026): Whisper "chưa được hỗ trợ" trên G420/520/720; gợi ý liên hệ đại diện MediaTek | [community #2361](https://genio-community.mediatek.com/t/about-openais-whisper-model/2361) |
| N10 | Trả lời của MediaTek (26/03/2026): Qwen3-ASR-0.6B chưa hỗ trợ/kiểm chứng trên G520; có toolkit cho Whisper nhưng không gồm tiền/hậu xử lý; đối tác thứ ba: IntelliGo | [community #1892](https://genio-community.mediatek.com/t/regarding-neuropilots-support-for-qwen3-asr-models/1892) |
| N11 | Gói LLM dựng sẵn công khai, chạy bằng `llm_cmdline_tool` trên Yocto v26.0 trở lên; GAI toolkit và NeuroPilot SDK nằm sau NDA; GAI toolkit là đường duy nhất chuyển LLM sang DLA. Có chủ đề liên quan về NeuronRuntime crash trên G520 | [community #2894](https://genio-community.mediatek.com/t/genio-520-llm-npu-inference-pre-built-models-vs-converting-our-own-model/2894) |
| N12 | Chủ đề diễn đàn: "garbage transcription" khi dùng encoder Whisper chuyển DLA ghép decoder PyTorch (Genio 510) | [community #1461](https://genio-community.mediatek.com/t/garbage-transcription-when-using-whisper-encoder-converted-via-neuropilot-sdk-dla-with-pytorch-decoder/1461) (chỉ thấy tiêu đề) |
| N13 | Bên thứ ba: Genio 720 có ONNX NeuronExecutionProvider mặc định; Whisper chạy được trên NPU qua ONNX Runtime hoặc TFLite, biên dịch offline thành nhị phân cố định | [proventusnova: TFLite/NPU](https://proventusnova.com/blog/mediatek-genio-on-device-ai-no-cloud), [Whisper on Genio NPU](https://proventusnova.com/blog/whisper-mediatek-genio-npu) |

## B. ASR tiếng Việt và code-switching

| ID | Nội dung nguồn nói | Nguồn |
|---|---|---|
| N14 | PhoWhisper: 5 cỡ (tiny 39M, base 74M, small 244M, medium 769M, large 1.55B), fine-tune Whisper trên 844 giờ tiếng Việt nhiều giọng vùng; bảng WER: base 16.19/8.46/19.70/43.01, small 11.08/6.33/15.93/32.96 (bốn bộ test) | [arXiv 2406.02555](https://arxiv.org/pdf/2406.02555) |
| N15 | ViMedCSS (34.6 giờ, 16.576 câu, mỗi câu ≥1 thuật ngữ Anh). Zero-shot (WER / CS-WER): PhoWhisper-Small 36.31/62.55; PhoWhisper-Large 31.24/55.05; Whisper-Large-v3 34.47/46.69; VietASR 27.56/58.38; Whisper-Small 50.03/61.25. Fine-tune PhoWhisper-small (24.31 giờ): LoRA 27.13/30.26; Attention Guide 23.67/19.50; RS 35.60/60.07; DV 36.29/62.46; AdaCS 39.40/32.91. Kết luận tác giả: mô hình tối ưu tiếng Việt tốt ở phần câu, mô hình đa ngôn ngữ tốt ở đoạn xen Anh; thích ứng tham số hiệu quả (nhất là AG) hơn biasing chỉ ở bộ giải mã | [arXiv 2602.12911](https://arxiv.org/html/2602.12911) |
| N16 | Adapter LoRA cho PhoWhisper-large trên bộ dữ liệu song ngữ Việt–Anh: WER chuẩn hóa 20.95% → 4.56% trên tập giữ lại; VIVOS (đơn ngữ Việt) 4.61% → 7.20%; r=32, α=64, 57.7M tham số huấn luyện, speed perturbation + SpecAugment; giấy phép CC-BY-4.0 | [HF: rinhoooo/phowhisper-large-vien-cs-asr](https://huggingface.co/rinhoooo/phowhisper-large-vien-cs-asr) |
| N17 | Nghiên cứu nhận dạng âm vị Việt–Anh: người nói thường "địa phương hóa" từ tiếng Anh theo quy tắc phát âm tiếng Việt, làm ASR khó hơn; dùng giọng tổng hợp để tạo dữ liệu xen ngôn ngữ | [arXiv 2508.19270](https://arxiv.org/html/2508.19270v1) |
| N18 | sherpa-onnx chạy offline trên Android và nhiều nền tảng: ASR, VAD, diarization, speaker ID, khử nhiễu, TTS; có model tiếng Việt: `zipformer-vi-30M-int8-2026-02-09`, `zipformer-vi-2025-04-20` (và int8), `moonshine-base-vi-quantized-2026-02-27` | [k2-fsa.github.io/sherpa/onnx](https://k2-fsa.github.io/sherpa/onnx/index.html), [pretrained models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html) |
| N19 | Hotwords chỉ hỗ trợ model **transducer**, bắt buộc `modified_beam_search`, đơn vị mô hình bpe/cjkchar; có điểm cộng chung và theo từng từ; không cần huấn luyện lại. Ví dụ RTF zipformer 0.065–0.13 (CPU) | [hotwords](https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html) |
| N20 | Diarization offline của sherpa-onnx: pyannote-segmentation-3.0 + embedding (3D-Speaker ERes2Net hoặc NeMo TitaNet) + gom cụm; cấu hình `numClusters`, `threshold`, `minDurationOn/Off` | [diarization C API](https://k2-fsa.github.io/sherpa/onnx/c-api/html/speaker_diarization.html), [models](https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/models.html) |
| N21 | Moonshine-base-vi có trang hướng dẫn streaming trên Android của sherpa-onnx | [sherpa Moonshine](https://k2-fsa.github.io/sherpa/onnx/moonshine/index.html) |
| N22 | Qwen3-ASR-0.6B/1.7B hỗ trợ 30 ngôn ngữ (có tiếng Việt) và 22 phương ngữ Trung | [HF: Qwen/Qwen3-ASR-0.6B](https://huggingface.co/Qwen/Qwen3-ASR-0.6B) |
| N23 | whisper.cpp trên RK3588 (Turing Pi): RTF tiny 0.10, base 0.16, small 0.47; RSS đỉnh 299 / 410 / 889 MB | [turingpi.com](https://turingpi.com/whisper-cpp-piper-tts-arm64-turing-pi-rk3588/) |
| N24 | Android: dùng `whisper_full` lặp trên buffer lớn dần cho streaming chậm ~5× thời gian thực; chế độ batch thì nhanh | [whisper.cpp #3567](https://github.com/ggml-org/whisper.cpp/discussions/3567) |
| N38 | Bên thứ ba (`phostt`): Zipformer-vi-30M INT8 ~75 MB, WER ~7.7% trên GigaSpeech2-vi (sạch), "huấn luyện ~70.000 giờ" (mâu thuẫn với N39) | [pypi.org/project/phostt](https://pypi.org/project/phostt/) |
| N39 | Tài liệu sherpa-onnx: `zipformer-vi-30M-int8-2026-02-09` lấy từ `hynt/Zipformer-30M-RNNT-6000h`, huấn luyện trên ~6.000 giờ tiếng Việt chất lượng cao | [sherpa: zipformer transducer models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html) |
| N25 | Chống ảo giác: Silero VAD giảm đáng kể WER và ảo giác; beam size 1 cho tỉ lệ ảo giác thấp nhất; `hallucination_silence_threshold` chỉ cải thiện nhẹ; mặc định `no_speech` 0.6, compression ratio 2.4; có báo cáo `compression_ratio_threshold` không lọc như mong đợi; VAD + khử nhiễu giúp trên audio ồn | [arXiv 2501.11378](https://arxiv.org/html/2501.11378v1), [whisper #2378](https://github.com/openai/whisper/discussions/2378), [whisper #679](https://github.com/openai/whisper/discussions/679) |

## C. LLM, tóm tắt, ràng buộc output

| ID | Nội dung nguồn nói | Nguồn |
|---|---|---|
| N26 | llama.cpp: GBNF ràng buộc output; chuyển được tập con JSON Schema sang GBNF; schema chỉ ràng buộc output, **không được đưa vào prompt** | [grammars/README](https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md) |
| N27 | Bài viết bên thứ ba: ràng buộc ngữ pháp giúp model nhỏ cho JSON hợp lệ; với tác vụ suy luận nhiều bước, ép schema có thể làm giảm độ chính xác đáng kể | [tianpan.co](https://tianpan.co/blog/2026/04/16/grammar-constrained-generation-output-reliability) |
| N28 | Llama 3.2 3B Q4_K_M chạy trong Termux trên Snapdragon 865 / Android 15: ~7.8–8.9 token/s sinh | [pypi termux-llamacpp](https://pypi.org/project/termux-llamacpp/) |
| N29 | llama.cpp: biến thể Q4_0 tối ưu cho ARM (Q4_0_4_4 / Q4_0_4_8) tăng tốc 2–3× trên Snapdragon X; cờ build `GGML_NATIVE` / `GGML_CPU_ARM_ARCH`, ví dụ `armv8-a+i8mm+dotprod`; Arm Learning Path nhắc tới "repack kernels" và yêu cầu CPU báo `asimddp`/`i8mm` | [llama.cpp #8273](https://github.com/ggml-org/llama.cpp/discussions/8273), [Arm Learning Path](https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/ai-portal-mobile-vision-language/4-understand-app/) |

## D. Dự án và nghiên cứu tương tự

| ID | Nội dung nguồn nói | Nguồn |
|---|---|---|
| N30 | **Local-Summerizer**: app Android offline, whisper.cpp + llama.cpp (Qwen2.5 Instruct GGUF); tuần tự nạp Whisper → giải phóng → nạp Llama; ForegroundService + WakeLock; tóm tắt phân cấp theo ngân sách token đo bằng tokenizer của model; audio 1 giờ ≈ 230 MB PCM nên ghi file tạm và đọc native; nạp model qua `/proc/self/fd` (mmap); biến thể ggml theo mức tính năng ARM (dotprod, i8mm); Vulkan/OpenCL trên Android chưa ổn định; họp 1 giờ: ASR 10–25 phút trên máy flagship; tắt backup tự động; báo cáo chẩn đoán không có nội dung | [GitHub](https://github.com/Stastyle/Local-Summerizer) |
| N31 | **Meetily**: trợ lý họp mã nguồn mở, riêng tư, Whisper.cpp/Parakeet + Ollama/llama.cpp; tự nhận rằng LLM nhỏ có thể bịa nên chất lượng tóm tắt kém, đang cải thiện | [GitHub fork](https://github.com/ZHNathanielLee/fork-meeting-minutes-cpp), [DEV](https://dev.to/zackriya/we-built-a-self-hosted-ai-meeting-note-taker-because-every-cloud-solution-failed-our-privacy-1eml) |
| N32 | **ownscribe**: CLI họp cục bộ; faster-whisper, diarization tùy chọn, llama.cpp, mẫu tóm tắt (meeting/lecture/brief), hỏi đáp trên nhiều ghi chú | [PyPI](https://pypi.org/project/ownscribe/) |
| N33 | Hệ thống recap họp bằng LLM (CSCW): bản "chương" phân cấp, đánh dấu ý chính/action, mỗi action có ô người phụ trách và ngày sửa được, transcript gốc luôn nằm dưới tóm tắt | [arXiv 2307.15793](https://arxiv.org/html/2307.15793) |
| N34 | BooookScore: so sánh hierarchical merging và incremental updating; incremental hưởng lợi khi chunk lớn; hierarchical có thể mất phụ thuộc xa nhưng chỉ thị đơn giản | [OpenReview](https://openreview.net/pdf?id=7Ttk3RzDeu) |
| N35 | Diarization pyannote 3.1 trong sản xuất: DER <8% trên audio phát thanh sạch; AliMeeting (họp xa, mic đơn) 24.4%; AVA-AVD ~50%; chồng lấn và câu ngắn là điểm yếu | [Fora Soft](https://www.forasoft.com/learn/ai-for-video-engineering/articles-ai/pyannote-speaker-diarization-production) |

## E. Android

| ID | Nội dung nguồn nói | Nguồn |
|---|---|---|
| N36 | Từ Android 14, loại FGS bắt buộc khai báo; FGS microphone cần `FOREGROUND_SERVICE_MICROPHONE`; **không thể tạo FGS microphone khi app ở nền** (RECORD_AUDIO là while-in-use) trừ vài ngoại lệ; thiếu loại ném `MissingForegroundServiceTypeException`; ví dụ thực tế: ghi nền cần FGS, nếu không mic bị câm khi tắt màn hình | [developer.android.com](https://developer.android.com/about/versions/14/changes/fgs-types-required), [sanenotes #87](https://github.com/swiftsaneai/sanenotes/issues/87) |
| N37 | Android 15 thay đổi loại FGS: có `Service.onTimeout(int, int)`; một số loại bị hạn chế khởi chạy từ `BOOT_COMPLETED` | [developer.android.com](https://developer.android.com/about/versions/15/changes/foreground-service-types) |

## F. Điểm mâu thuẫn hoặc chưa kiểm chứng

| # | Điểm | Ghi chú |
|---|---|---|
| F1 | Whisper trên G720 | MediaTek forum (05/2026) nói chưa hỗ trợ [N9]; bảng G720 (02/10/2026) liệt kê Whisper-base 8w16a [N4]. Hỏi trực tiếp MediaTek |
| F2 | Zipformer-vi-30M huấn luyện bao nhiêu giờ | Trang sherpa: ~6.000 giờ (nguồn gốc `hynt/Zipformer-30M-RNNT-6000h`) [N39]; gói bên thứ ba `phostt` ghi ~70.000 giờ và WER ~7.7% GigaSpeech2-vi [N38]. Chưa kiểm chứng, tự đo |
| F3 | Số "~75 MB INT8", WER 7.7% của Zipformer-vi | Chỉ từ bên thứ ba [N38] |
| F4 | RTF whisper.cpp trên G720 | Chỉ có số RK3588 [N23]; G720 có 2 nhân lớn thay vì 4 |
| F5 | Tốc độ llama.cpp CPU trên G720 | Chỉ có số điện thoại Snapdragon 865 [N28]; chưa có số đo G720 |
| F6 | Ngữ cảnh tối đa của gói NPU LLM | Chỉ suy từ tên thư mục gói Qwen3-ASR (`256t2048c`) [N5]; cần mở gói LLM kiểm tra |
| F7 | Độ chính xác code-switching cho thuật ngữ **kỹ thuật** | Số đo có sẵn là miền y khoa [N15]; cần G6 trên họp thật |
| F8 | Embedding diarization cho giọng Việt | Model mẫu của sherpa là zh-cn/en [N20]; chưa có số đo tiếng Việt |
| F9 | Khả năng nhận `initial_prompt` của runner Whisper NPU | Runner cho ép tiền tố token bắt đầu (`--tokens`) [N5]; chưa rõ có nhận prompt văn bản |
| F10 | Chỉ số "Vietnamese WER 9.21% vs 13.00%" trên một bản Qwen3-ASR int4 | Không rõ đối chiếu với cái gì; không dùng |
