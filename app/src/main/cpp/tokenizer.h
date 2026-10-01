#ifndef NP_TOKENIZER_H
#define NP_TOKENIZER_H

#include <string>
#include <vector>
#include <map>
#include <sstream>
#include <cstring>
#include <android/log.h>

#define LOG_TAG_TOKENIZER "NP_TOKENIZER"

class Tokenizer {
public:
    Tokenizer() {}
    ~Tokenizer() {}

    // Nạp tệp vocab từ bộ nhớ đệm (Buffer)
    bool LoadFromBuffer(const char* buffer, size_t size) {
        if (!buffer || size == 0) return false;

        token_to_id.clear();
        id_to_token.clear();

        size_t offset = 0;
        while (offset + sizeof(int) * 2 <= size) {
            int id;
            std::memcpy(&id, buffer + offset, sizeof(int));
            offset += sizeof(int);

            int len;
            std::memcpy(&len, buffer + offset, sizeof(int));
            offset += sizeof(int);

            if (offset + len > size) break;

            std::string text(buffer + offset, len);
            offset += len;
            
            token_to_id[text] = id;
            id_to_token[id] = text;
        }
        
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG_TOKENIZER, "Loaded %zu tokens from buffer", token_to_id.size());
        return true;
    }

    // Mã hóa văn bản thành Tokens (Sử dụng giải thuật Greedy Longest Match)
    std::vector<int> Encode(const std::string& text) {
        std::vector<int> tokens;
        size_t start = 0;
        
        while (start < text.length()) {
            bool found = false;
            // Tìm token dài nhất khớp từ vị trí 'start'
            for (size_t len = text.length() - start; len > 0; --len) {
                std::string sub = text.substr(start, len);
                if (token_to_id.count(sub)) {
                    tokens.push_back(token_to_id[sub]);
                    start += len;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                // Nếu không thấy (byte lạ), bỏ qua 1 ký tự (fallback đơn giản)
                start++;
            }
        }
        return tokens;
    }

    // Giải mã 1 Token thành chuỗi văn bản
    std::string Decode(int token) {
        if (id_to_token.count(token)) {
            return id_to_token[token];
        }
        return "";
    }

    int GetEOS() { return 128001; } // Mã EOS mặc định của Llama 3

private:
    std::map<std::string, int> token_to_id;
    std::map<int, std::string> id_to_token;
};

#endif
