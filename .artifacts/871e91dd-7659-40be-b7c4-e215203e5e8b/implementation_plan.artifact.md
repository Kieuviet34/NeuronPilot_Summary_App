# Tính năng Lưu trữ và Xem lại Lịch sử Chat (Room Database)

Tính năng này cho phép người dùng lưu lại toàn bộ các cuộc trò chuyện với AI vào cơ sở dữ liệu Room cục bộ, xem danh sách lịch sử và tải lại nội dung cũ khi cần.

## User Review Required

> [!IMPORTANT]
> Việc tự động tạo Session dựa trên câu hỏi đầu tiên có thể làm thay đổi luồng hiện tại. Khi App khởi động hoặc khi xóa chat, một Session ID mới sẽ được tạo ngầm định.

## Proposed Changes

### [Database Layer]

#### [NEW] [ChatEntities.kt](file:///app/src/main/java/com/mediatek/neuropilot/jnidemo/chat/db/ChatEntities.kt)
Định nghĩa 2 bảng:
- `ChatSession`: `id` (PK), `title` (câu hỏi đầu), `createdAt`.
- `ChatMessage`: `id` (PK), `sessionId` (FK), `role` (User/AI), `content`, `createdAt`.

#### [NEW] [ChatDao.kt](file:///app/src/main/java/com/mediatek/neuropilot/jnidemo/chat/db/ChatDao.kt)
Các truy vấn:
- `insertSession`, `updateSessionTitle`.
- `insertMessage`.
- `getAllSessions` (để hiện danh sách history).
- `getMessagesBySessionId` (để load lại chat).

#### [NEW] [ChatDatabase.kt](file:///app/src/main/java/com/mediatek/neuropilot/jnidemo/chat/db/ChatDatabase.kt)
Khởi tạo Room Database.

---

### [UI Layer]

#### [MODIFY] [activity_main.xml](file:///app/src/main/res/layout/activity_main.xml)
- Thêm `btn_history` (ImageView) vào `layout_header`, kích thước 55dp, nằm cạnh nút tóm tắt.
- Thêm `history_recording_card` (mượn style của voice card) để chứa danh sách lịch sử.

#### [NEW] [layout_chat_history.xml](file:///app/src/main/res/layout/layout_chat_history.xml)
Layout bên trong card lịch sử, chứa một `RecyclerView` hiển thị danh sách các Session.

#### [NEW] [item_chat_session.xml](file:///app/src/main/res/layout/item_chat_session.xml)
Layout cho từng dòng trong danh sách lịch sử (Title + Time).

---

### [Logic Layer]

#### [NEW] [ChatRepository.kt](file:///app/src/main/java/com/mediatek/neuropilot/jnidemo/chat/ChatRepository.kt)
Quản lý việc lưu trữ tin nhắn vào DB một cách không đồng bộ.

#### [MODIFY] [MainActivity.java](file:///app/src/main/java/com/mediatek/neuropilot/jnidemo/MainActivity.java)
- Khởi tạo Repository.
- Xử lý sự kiện bấm nút History để hiện/ẩn danh sách.
- Khi gửi tin nhắn hoặc nhận phản hồi AI: Gọi Repository để lưu vào DB.
- Khi bấm vào một Session trong history: Xóa chat hiện tại, load messages từ DB và hiển thị lại.

## Verification Plan

### Automated Tests
- Kiểm tra tính đúng đắn của DB bằng unit test đơn giản (nếu có thể).

### Manual Verification
1. Mở app, gửi vài câu hỏi -> Kiểm tra tiêu đề Session được cập nhật theo câu đầu.
2. Bấm nút History -> Danh sách hiện ra đúng Session vừa tạo.
3. Chat tiếp một Session mới (sau khi clear) -> Kiểm tra history có 2 Session.
4. Bấm vào Session cũ -> Nội dung chat cũ phải hiện lại chính xác.
