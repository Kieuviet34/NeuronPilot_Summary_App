# Hướng Dẫn Nhanh: Đẩy File và Cập Nhật Đường Dẫn Model

Tài liệu này bao gồm 2 công đoạn thiết yếu nhất để model hoạt động: (1) Lệnh đẩy file vào bộ nhớ thiết bị, và (2) Sửa mã nguồn để App sử dụng file đó.

---

### Bước 1: Đẩy các 
file (.dla, .bin, .yaml) vào thiết bị qua ADB
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

**Mở tệp:** `app/src/main/java/com/mediatek/neuropilot/jnidemo/MainActivity.java`

1. **Thêm hằng số đường dẫn YAML của bạn (ở phần khai báo đầu file):**
```java
// Ghi đúng đường dẫn bạn vừa push tệp yaml ở Bước 1
private static final String NEW_MODEL_YAML_PATH = "/data/local/tmp/llm_sdk/<model_name>/config_new_model.yaml";
```

2. **Dùng biến đó ở trong thủ tục Load Model (khoảng dòng ~147):**
Tìm đến thân hàm lệnh `loadModel(ModelType type)` và ghi đè thẳng biến `yamlPath`! 

```java
private void loadModel(ModelType type) {
    // ...
    
    // Ép cứng app đọc cấu hình của bạn:
    String yamlPath = NEW_MODEL_YAML_PATH; 
    String modelName = "Tên Model Mới";
    
    // (System sẽ dùng đường dẫn yamlPath này cấp vào JNI C++ qua AsyncTask InitModelTask)
    // ...
}
```

Hoàn tất! Bấm **Run** ứng dụng là nó sẽ chỉ trích xuất cấu hình model của bạn.
