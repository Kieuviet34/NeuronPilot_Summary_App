---
trigger: always_on
---

# GLOBAL STANDARD RULE 
> **Mục tiêu**: Bắt buộc AI (Antigravity) đưa ra đánh giá, giải phẫu logic bằng số lượng, tỷ lệ %, và giữ tương tác cực kỳ ngắn gọn, không lan man.

## 1. QUY TẮC MINH BẠCH SỐ LIỆU (100% BẮT BUỘC)
- Mọi tối ưu hóa/thay đổi code phải kèm theo **[Số lượng thay đổi]**, **[Hiệu suất dự kiến tăng %]**, hoặc **[Token/Thời gian tiết kiệm]**. 
- Dừng ngay định dạng trả lời "văn vở", sử dụng Bullet Point (gạch đầu dòng) và **in đậm điểm quan trọng**.

## 2. QUY TẮC CHỐNG LẤP LIẾM BUGS
- Khai báo rõ ràng: **Số lỗi tiềm ẩn (Bugs) = X**.
- Nếu X > 0, liệt kê lập tức: `[Vị trí] - [Nguyên Nhân] - [Cách fix]`.
- Mập mờ không khai báo hoặc che giấu lỗi sẽ bị xem là vi phạm thiết kế lõi hệ thống.

## 3. TIẾN TRÌNH LUÂN CHUYỂN SKILLS CHUYÊN BIỆT
- AI tự động đánh giá: `Task => Gọi Skill tương ứng` để không trùng lặp tác vụ.
- Tên Skill gọi tắt phải ngắn, ví dụ: AI thay vì nói "app-builder", hãy dùng tag `[App-Builder]` để đại diện xử lý.

## 4. TỪ CHỐI ĐOÁN MÒ (ZERO-GUESSING)
- Nếu Input đầu vào thiếu `< 80%>` dữ kiện kỹ thuật, lập tức **[HỎI LẠI]** người dùng với tối đa 3 câu hỏi ngắn (mỗi câu < 15 chữ).

## 5. QUY TẮC TIẾT KIỆM TOKEN
- Khi chạy lệnh terminal, **ưu tiên `rtk`** cho mọi lệnh đọc/log/test/build/git nếu công cụ có sẵn.
- Khi tìm kiếm file hoặc text, **ưu tiên `rg` / `Get-ChildItem`** thay vì lệnh chậm và nhiều nhiễu.
- Khi báo cáo kết quả, chỉ nêu **lỗi, số lượng thay đổi, và dòng cần sửa**; bỏ toàn bộ output thừa.
- Khi kiểm tra build/test, dùng các lệnh gọn như `rtk npm run build`, `rtk npm test`, `rtk git diff`.
