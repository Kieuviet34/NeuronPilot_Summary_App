# Phase 2 - Sửa lỗi bằng luật + Glossary/Alias (BA v2, bước 2 pipeline)

**Mục tiêu 1 câu:** thay bước "sửa lỗi" bằng luật xác định có log + hoàn tác (I7, I8), nối ASR thật nếu có, hiển thị trên UI.

## Ràng buộc/bất biến liên quan
- C1 offline (ASR chỉ loopback 127.0.0.1), C13 Plan B luôn chạy, C10 luôn giữ `raw_transcript`.
- I7 glossary là chuẩn, luật chạy TRƯỚC LLM. I8 mọi chỉnh sửa có log (vị trí, trước, sau) và hoàn tác được.
- KHÔNG sửa `src/main/cpp`, `jniLibs`, `com.mediatek.neuropilot.jnidemo.aibox.*` (chỉ gọi).

## Phạm vi
1. DB v4: `glossary`, `glossary_alias`, `edit_log` + Migration 3->4 (giữ dữ liệu họp).
2. `GlossaryCorrector` (thuần Kotlin, có unit test): khớp alias không dấu, cửa sổ trượt nhiều âm tiết, mode `always`/`suggest` (suggest chỉ thay khi chunk có >= 2 tín hiệu kỹ thuật), giữ nguyên dấu câu, `revert`.
3. `GlossaryRepository`: seed mặc định, thêm alias, chạy sửa + ghi `edit_log`.
4. Pipeline: Bước 1 thử `WhisperServerClient` theo từng segment (fallback Plan B nếu server tắt); Bước 2 chạy luật trước.
5. UI: tab Transcript chuyển "Đã sửa / Bản gốc" + danh sách chỉnh sửa; Cài đặt: thêm alias thuật ngữ.

## Ngoài phạm vi / follow-up
- D6: đường NPU vẫn gọi LLM viết lại toàn văn SAU luật (chưa bỏ, cần chốt).
- Slider CPU threads/temperature -> LLM cần sửa JNI (bị cấm bởi invariant), để sau.
- R2-lite, diarization, LoRA: Phase 2 của PROJECT_CONTEXT, chưa chốt.
