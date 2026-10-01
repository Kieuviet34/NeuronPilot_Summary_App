#include "simple_model.h"
#include "utils/utils.h"
#include <android/log.h>

SimpleModel::SimpleModel(const std::string& yamlConfigPath) {
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
        "SimpleModel: init from yaml %s", yamlConfigPath.c_str());

    // Force reset config to default values
    modelOptions_ = {};
    runtimeOptions_ = {};

    // Load yaml config
    utils::parseLlmConfigYaml(yamlConfigPath, modelOptions_, runtimeOptions_);

    bool status = mtk_llm_init(&llmRuntime_, modelOptions_, runtimeOptions_);
    if (!status) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "mtk_llm_init FAILED for %s", yamlConfigPath.c_str());
        llmRuntime_ = nullptr;
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "SimpleModel READY!");
    }
}

SimpleModel::~SimpleModel() {
    if (llmRuntime_) {
        mtk_llm_release(llmRuntime_);
        llmRuntime_ = nullptr;
    }
}

bool SimpleModel::InferenceOnce(const std::vector<mtk::Tokenizer::TokenType>& inputTokens,
                                void** outputLogits, size_t* outputSize) {
    if (!llmRuntime_) return false;

    // mtk_llm_inference_once sẽ thực hiện inference và trả về pointer đến logits của token cuối cùng
    void* logits = mtk_llm_inference_once(llmRuntime_, inputTokens, mtk::LogitsKind::LAST);
    if (!logits) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "mtk_llm_inference_once FAILED");
        return false;
    }

    *outputLogits = logits;
    *outputSize = mtk_llm_get_per_token_logits_size(llmRuntime_);
    return true;
}

void SimpleModel::Reset() {
    if (llmRuntime_) {
        mtk_llm_reset(llmRuntime_);
    }
}
