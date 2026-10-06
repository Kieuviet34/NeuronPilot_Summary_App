---
name: codegraph
description: Code intelligence, AST symbol navigation, call graph analysis, and impact tracking for Android codebases using CodeGraph CLI and MCP. Use when exploring architecture, finding symbol usages/callers/callees, tracing Android Activity/Service lifecycles, and checking refactoring impact.
---

# CodeGraph Android Intelligence Skill

Use CodeGraph as your primary code intelligence tool for analyzing Java, Kotlin, XML layouts, and C++ native code in Android projects. CodeGraph builds an AST-based graph database (`.codegraph/codegraph.db`) enabling instant symbol navigation, call hierarchies, and blast-radius impact analysis without loading entire files into memory.

---

## 1. Quick CLI & MCP Reference

CodeGraph runs both as a CLI tool (`codegraph`) and as an MCP server registered in `.agents/mcp_config.json`.

| Action | CLI Command | Purpose |
| :--- | :--- | :--- |
| **Search Symbol** | `codegraph query <symbol>` | Tìm class, method, field, interface, import trong Java/Kotlin/XML |
| **Trace Callers** | `codegraph callers <symbol>` | Tìm tất cả method/class gọi đến symbol này |
| **Trace Callees** | `codegraph callees <symbol>` | Tìm tất cả hàm và dependencies mà symbol này gọi tới |
| **Blast Radius** | `codegraph impact <symbol>` | Đánh giá phạm vi ảnh hưởng trước khi sửa/xoá code |
| **Area Explore** | `codegraph explore <term>` | Tổng hợp cấu trúc, file, hàm liên quan đến một module |
| **Node Details** | `codegraph node <symbol>` | Đọc source code và mối liên kết của 1 node cụ thể |
| **Sync Changes** | `codegraph sync` | Cập nhật đồ thị sau khi chỉnh sửa file |
| **Status Check** | `codegraph status` | Kiểm tra số lượng nodes, edges, file đã index |

---

## 2. Android-Specific Navigation Workflows

### 🔍 A. Điều Tra Luồng Vòng Đời Android (Lifecycle Tracing)
Khi làm việc với `Activity`, `Fragment`, `Service`:
1. Tìm tất cả override vòng đời:
   ```bash
   codegraph query "onStart"
   codegraph query "onDestroy"
   ```
2. Tìm nơi khởi tạo hoặc Start Service/Activity:
   ```bash
   codegraph callers "DataloggerWifiService"
   codegraph callers "HandleDataActivity"
   ```

### ⚡ B. Truy Vết Luồng Dữ Liệu & Đa Luồng (Threading & IO)
Trong app Android phần cứng/datalogger (UART, WiFi, Sockets):
1. Tìm các hàm xử lý dữ liệu nhận từ UART/WiFi:
   ```bash
   codegraph callers "threadPlotDataOnGraph_func_v2"
   codegraph callees "UartPortManager"
   ```
2. Xác định các Worker Threads, Handlers, hoặc Runnables:
   ```bash
   codegraph callers "run"
   ```

### 🛡️ C. Đánh Giá Tác Động Trước Khi Refactor (Zero-Regression)
Trước khi thay đổi signature của một method hoặc xóa method/field:
1. Chạy phân tích Impact:
   ```bash
   codegraph impact "<MethodName>"
   ```
2. Nếu số lượng `Callers > 0`, kiểm tra chi tiết từng caller để cập nhật đồng bộ.
3. Sau khi sửa code, luôn chạy:
   ```bash
   codegraph sync
   ```

---

## 3. Hướng Dẫn Copy Sang Dự Án Android Khác

Để bất kỳ dự án Android mới nào cũng dùng được CodeGraph chuẩn, chỉ cần 3 bước:

### Bước 1: Sao chép thư mục Skill & Cấu hình MCP
Copy 2 thành phần này từ dự án hiện tại sang dự án Android mới:
1. Thư mục skill: `.agents/skills/codegraph/` -> `<project_root>/.agents/skills/codegraph/`
2. Cấu hình MCP trong `<project_root>/.agents/mcp_config.json`:
   ```json
   {
     "mcpServers": {
       "codegraph": {
         "command": "codegraph.cmd",
         "args": [
           "serve",
           "--mcp"
         ]
       }
     }
   }
   ```

### Bước 2: Khởi tạo và Index mã nguồn
Mở terminal tại thư mục gốc của dự án Android mới và chạy:
```bash
codegraph init
codegraph index
```
CodeGraph sẽ tự động quét tất cả file `.java`, `.kt`, `.xml`, `.cpp`, `.h` và tạo đồ thị trong `.codegraph/`.

### Bước 3: Thêm `.codegraph` vào `.gitignore`
Đảm bảo thêm dòng sau vào `.gitignore` của dự án mới để không commit database nhị phân:
```gitignore
/.codegraph
```
