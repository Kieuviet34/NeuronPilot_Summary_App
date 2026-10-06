# Giai đoạn 2a — Kế hoạch thực thi

**Spec:** `docs/superpowers/specs/2026-10-03-phase2a-recording-responsive-design.md`
**Cách thực thi:** tự làm trong phiên (native), TDD cho logic thuần, ghi tiến độ vào `ledger.md`. Không dùng git.

## Ràng buộc chung
Giữ nguyên các ràng buộc của plan giai đoạn 1 và `.claude/rules/*.md`. Thêm: mọi truy vấn Room chạy ngoài main thread; không log nội dung cuộc họp; thời lượng đoạn lấy theo số mẫu PCM.

## Review Focus
- Người dùng từ chối quyền micro → không crash, báo rõ, không tạo cuộc họp rỗng.
- Thiết bị không có micro / `AudioRecord` khởi tạo thất bại → báo lỗi, không treo.
- Hết dung lượng hoặc ghi file lỗi giữa chừng → đoạn đang ghi vẫn được đóng với header hợp lệ.
- App bị kill khi đang ghi → mở lại, đoạn dở được khôi phục.
- Xoay màn hình / rời app khi đang ghi → ghi vẫn tiếp tục, không mất trạng thái.
- Layout phone ở màn hình hẹp (360dp) và chế độ landscape không bị cắt nút chính.

## Các task
1. **Layout phone/tablet**: qualifier `sw600dp`, chuyển layout hiện tại sang `layout-sw600dp/`, viết layout phone cho 6 màn hình, `ScreenActivity` đặt hướng màn hình, màn Kết quả 3 tab trên phone. Kiểm chứng: build + lint.
2. **Lõi WAV/PCM** (TDD): `WavFormat`, `WavWriter`, `WavRecovery`, `PcmLevel`.
3. **SegmentRecorder** (TDD với `PcmSource` giả) + `AudioRecordSource`.
4. **Đường dẫn lưu trữ** (TDD): `AppPaths` nhận thư mục gốc, `StorageLocator` chọn gốc theo quyền.
5. **Room**: entity có chú thích, DAO, `RoomMeetingRepository`, bảng bookmarks/glossary, khôi phục đoạn dở (TDD với Fake).
6. **RecordingSession + RecordingController** (TDD): tiến độ tuyệt đối từ recorder, lưu đoạn/dấu mốc, trạng thái UI.
7. **RecordingService + quyền + RecordingActivity/ViewModel**: foreground service, notification, xin quyền.
8. **ViewModel nạp bất đồng bộ**, nút nạp dữ liệu mẫu, nhãn "mô phỏng" cho phần AI.
9. **Kiểm chứng cuối**: build, test, lint, reviewer độc lập, cập nhật tài liệu/rules.
