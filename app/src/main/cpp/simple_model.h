#ifndef NP_SIMPLE_MODEL_H
#define NP_SIMPLE_MODEL_H

#include <android/log.h>
#include <sys/mman.h>
#include <string>
#include <vector>
#include "mtk_llm.h"

#define LOG_TAG "NP_LLM_DEMO"

class SimpleModel {
public:
    // yamlConfigPath: đường dẫn đến file cấu hình .yaml trên thiết bị
    explicit SimpleModel(const std::string& yamlConfigPath);
    ~SimpleModel();

    bool IsReady() const { return llmRuntime_ != nullptr; }

    // Dùng cho việc chạy một bước inference
    bool InferenceOnce(const std::vector<mtk::Tokenizer::TokenType>& inputTokens,
                       void** outputLogits, size_t* outputSize);

    void Reset();

    // Getters for model/runtime info
    mtk::Tokenizer::TokenType GetBosId() const { return runtimeOptions_.specialTokens.bosId; }
    mtk::Tokenizer::TokenType GetEosId() const { return runtimeOptions_.specialTokens.eosId; }
    bool ShouldAddBos() const { return runtimeOptions_.specialTokens.addBos; }

    bool IsStopToken(mtk::Tokenizer::TokenType token) const {
        const auto& stops = runtimeOptions_.specialTokens.stopToken;
        return (token == runtimeOptions_.specialTokens.eosId) || (stops.find(token) != stops.end());
    }

    mtk::LLMType GetOutputType() const { return modelOptions_.modelOutputType; }
    size_t GetPromptTokenBatchSize() const { return modelOptions_.promptTokenBatchSize; }

private:
    void* llmRuntime_ = nullptr;
    LlmModelOptions modelOptions_;
    LlmRuntimeOptions runtimeOptions_;
};

#endif // NP_SIMPLE_MODEL_H
