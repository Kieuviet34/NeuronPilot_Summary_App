# Hướng Dẫn Nhanh: Đẩy File và Cập Nhật Đường Dẫn Model (MediaTek NeuroPilot)

Tài liệu này bao gồm 2 công đoạn thiết yếu nhất để model hoạt động: (1) Lệnh đẩy file vào bộ nhớ thiết bị, và (2) Sửa mã nguồn để App sử dụng file đó.

---

### Bước 1: Đẩy các file (.dla, .bin, .yaml) vào thiết bị qua ADB
Dưới đây là các lệnh `adb push` chuẩn tuỳ thuộc vào việc bạn có trích xuất common weights từ file DLA hay không:

**Nếu bạn ĐÃ trích xuất shared weights từ file DLA:**
```bash
adb shell mkdir -p /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_Embedding_BIN> /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_Shared_BIN> /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_Extracted_DLA1> /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_Extracted_DLA2> /data/local/tmp/llm_sdk/<model_name>
adb push config_new_model.yaml /data/local/tmp/llm_sdk/<model_name>
```

**Nếu bạn KHÔNG trích xuất shared weights từ file DLA:**
```bash
adb shell mkdir -p /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_Embedding_BIN> /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_DLA1> /data/local/tmp/llm_sdk/<model_name>
adb push <path_to_DLA2> /data/local/tmp/llm_sdk/<model_name>
adb push config_new_model.yaml /data/local/tmp/llm_sdk/<model_name>
```

> [!TIP]
> Việc liên kết tới các file `.bin` hay `.dla` mà bạn vừa push phải được cấu hình đường dẫn chính xác nằm bên trong ruột của chính tệp `config_new_model.yaml`.

---

### Bước 2: Khai báo đường dẫn YAML trong Code

Hệ thống JNI (C++) sẽ tự động đọc tệp `.yaml` của bạn để trích xuất đường dẫn `.dla` và tự xử lý Model.
