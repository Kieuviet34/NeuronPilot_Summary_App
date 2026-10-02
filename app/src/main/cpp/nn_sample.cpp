#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <algorithm>
#include <android/log.h>
#include "simple_model.h"
#include "utils/utils.h"
#include "tokenizer/tokenizer_factory.h"  // Auto-detects Tiktoken / HuggingFace / SentencePiece
#include "deque"
#include "unordered_map"

#define LOG_TAG "NP_LLM_DEMO"

std::unique_ptr<mtk::Tokenizer> global_tokenizer = nullptr;

jbyteArray makeErrResult(JNIEnv *env, const char* msg) {
    jbyteArray r = env->NewByteArray(strlen(msg));
    env->SetByteArrayRegion(r, 0, strlen(msg), reinterpret_cast<const jbyte*>(msg));
    return r;
}

extern "C"
JNIEXPORT jlong
JNICALL
Java_com_mediatek_neuropilot_jnidemo_MainActivity_initModel(
        JNIEnv *env,
        jobject /* this */,
        jstring _yamlConfigPath) {

    const char* yamlConfigPath = env->GetStringUTFChars(_yamlConfigPath, NULL);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "initModel: Loading from YAML: %s", yamlConfigPath);

    SimpleModel* model = new SimpleModel(std::string(yamlConfigPath));

    if (!model->IsReady()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "SimpleModel init FAILED for: %s", yamlConfigPath);
        delete model;
        env->ReleaseStringUTFChars(_yamlConfigPath, yamlConfigPath);
        return 0;
    }

    // Always reinitialize tokenizer for each model to ensure correct vocab per model.
    // TokenizerFactory auto-detects: HuggingFace (vocab.txt+merges.txt) or Tiktoken (.tiktoken)
    {
        LlmModelOptions mOpt;
        LlmRuntimeOptions rOpt;
        utils::parseLlmConfigYaml(std::string(yamlConfigPath), mOpt, rOpt);

        global_tokenizer = mtk::TokenizerFactory().create(
            rOpt.tokenizerPath,   // vector<string> of tokenizer files from YAML
            rOpt.tokenizerRegex   // regex pattern (used by Tiktoken / HuggingFace)
        );

        if (!global_tokenizer) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                "TokenizerFactory: failed to create tokenizer for: %s", yamlConfigPath);
            delete model;
            env->ReleaseStringUTFChars(_yamlConfigPath, yamlConfigPath);
            return 0;
        }

        if (rOpt.specialTokens.addBos) {
            global_tokenizer->enableBosToken(rOpt.specialTokens.bosId);
        }

        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
            "Tokenizer ready for: %s", yamlConfigPath);
    }

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "SimpleModel ready.");
    env->ReleaseStringUTFChars(_yamlConfigPath, yamlConfigPath);
    return (jlong)(uintptr_t)model;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_mediatek_neuropilot_jnidemo_MainActivity_computeStreaming(
        JNIEnv *env,
        jobject /* this */,
        jlong _modelHandle,
        jstring _inputText,
        jobject _callback) {

    SimpleModel* model = (SimpleModel*)(uintptr_t)_modelHandle;

    if (!model || !model->IsReady() || !global_tokenizer) {
        return;
    }

    // Get the onTokenReceived method ID (Return boolean Z)
    jclass callbackClass = env->GetObjectClass(_callback);
    jmethodID onTokenReceivedMethod = env->GetMethodID(callbackClass, "onTokenReceived", "(Ljava/lang/String;)Z");
    if (onTokenReceivedMethod == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to find onTokenReceived method");
        return;
    }

    const char* inputText = env->GetStringUTFChars(_inputText, NULL);
    std::string user_text(inputText);
    env->ReleaseStringUTFChars(_inputText, inputText);

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "computeStreaming: tokenizing input (%zu chars)", user_text.size());

    // 0. Use raw input text (formatting is handled in Java)
    std::string formatted_text = user_text;

    // 1. Tokenize input
    std::vector<mtk::Tokenizer::TokenType> tokens = global_tokenizer->tokenize(formatted_text);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Tokenized: %zu tokens", tokens.size());

    // 2. PREFILL
    auto start_prefill = std::chrono::high_resolution_clock::now();
    void* logits = nullptr;
    size_t logitsSize = 0;
    const size_t promptBatchSize = std::max<size_t>(1, model->GetPromptTokenBatchSize());
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
        "Prefill in chunks: total=%zu, batch=%zu", tokens.size(), promptBatchSize);

    for (size_t offset = 0; offset < tokens.size(); offset += promptBatchSize) {
        const size_t end = std::min(offset + promptBatchSize, tokens.size());
        std::vector<mtk::Tokenizer::TokenType> chunk(tokens.begin() + offset, tokens.begin() + end);

        if (!model->InferenceOnce(chunk, &logits, &logitsSize)) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                "Prefill failed at token range [%zu, %zu)", offset, end);
            return;
        }
    }
    auto end_prefill = std::chrono::high_resolution_clock::now();
    auto prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_prefill - start_prefill).count();
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Prefill took %lld ms", (long long)prefill_ms);

    mtk::LLMType outputType = model->GetOutputType();
    // Use model's actual output vocab size (logitsSize / element_size) rather than
    // tokenizer->vocabSize() so all tokens including special tokens (<|im_end|>=151645)
    // are reachable by argmax.
    size_t elementSize = mtk::getLLMTypeSize(outputType);
    size_t modelVocabSize = (elementSize > 0) ? (logitsSize / elementSize) : logitsSize / 2;
    mtk::Tokenizer::TokenType nextToken = utils::argmaxFrom16bitLogits(outputType, logits, modelVocabSize);

    std::string firstTokenStr = global_tokenizer->detokenize(nextToken);
    jstring jFirstToken = env->NewStringUTF(firstTokenStr.c_str());
    jboolean continueFirst = env->CallBooleanMethod(_callback, onTokenReceivedMethod, jFirstToken);
    env->DeleteLocalRef(jFirstToken);
    if (continueFirst == JNI_FALSE) return; // Dừng ngay nếu Java yêu cầu

    // 3. DECODE LOOP
    int maxResponse = 512;

    std::deque<mtk::Tokenizer::TokenType> recentTokens;
    const size_t REPEAT_WINDOW = 160;
    const int REPEAT_MAX_COUNT = 2;

    auto pushRecent = [&](mtk::Tokenizer::TokenType t){
        recentTokens.push_back(t);
        if (recentTokens.size() > REPEAT_WINDOW) {
            recentTokens.pop_front();
        }
    };

    auto applyRepeatSuppression = [&](void* logitsPtr){
        if (outputType != mtk::LLMType::INT16) return ;
        std::unordered_map<mtk::Tokenizer::TokenType , int> counts;
        for(auto t : recentTokens) counts[t]++;

        std::vector<mtk::Tokenizer::TokenType > toSuppress;
        for(auto& kv : counts){
            if (kv.second >= REPEAT_MAX_COUNT) toSuppress.push_back(kv.first);
        }
        if (!toSuppress.empty()){
            utils::suppressLogits(reinterpret_cast<int16_t*>(logitsPtr), toSuppress);
        }
    };

    pushRecent(nextToken);


    for (int step = 0; step < maxResponse; step++) {
        if (model->IsStopToken(nextToken)) break;

        std::vector<mtk::Tokenizer::TokenType> stepInput = {nextToken};
        if (!model->InferenceOnce(stepInput, &logits, &logitsSize)) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Inference FAILED at step %d", step);
            break;
        }
        modelVocabSize = (elementSize > 0) ? (logitsSize / elementSize) : logitsSize / 2;

        nextToken = utils::argmaxFrom16bitLogits(outputType, logits, modelVocabSize);

        if (model->IsStopToken(nextToken)) break;

        std::string tokenText = global_tokenizer->detokenize({nextToken});

        // Callback to Java and check if we should continue
        jstring jToken = env->NewStringUTF(tokenText.c_str());
        jboolean continueGen = env->CallBooleanMethod(_callback, onTokenReceivedMethod, jToken);
        env->DeleteLocalRef(jToken);

        if (continueGen == JNI_FALSE) {
            __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Generation ABORTED by Java request.");
            break;
        }

        pushRecent(nextToken);
    }

    // model->Reset(); // REMOVED: Keep KV cache for next turn
}

extern "C"
JNIEXPORT jint
JNICALL
Java_com_mediatek_neuropilot_jnidemo_MainActivity_countTokens(
        JNIEnv *env,
        jobject /* this */,
        jstring _text) {
    if (!global_tokenizer) {
        return -1;
    }

    const char* text = env->GetStringUTFChars(_text, NULL);
    std::string input(text);
    env->ReleaseStringUTFChars(_text, text);

    return static_cast<jint>(global_tokenizer->tokenize(input).size());
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_mediatek_neuropilot_jnidemo_MainActivity_resetModel(
        JNIEnv*, jobject, jlong _modelHandle) {
    if (_modelHandle) {
        ((SimpleModel*)(uintptr_t)_modelHandle)->Reset();
    }
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_mediatek_neuropilot_jnidemo_MainActivity_destroyModel(
        JNIEnv*, jobject, jlong _model) {
    if (_model) {
        delete (SimpleModel*)(uintptr_t)_model;
    }
}

// --- AiBox Bridge JNI ---

extern "C" JNIEXPORT jlong JNICALL
Java_com_mediatek_neuropilot_jnidemo_aibox_ai_NeuroPilotLlmBridge_nativeInitModel(
    JNIEnv *env, jobject thiz, jstring yaml_config_path) {
    return Java_com_mediatek_neuropilot_jnidemo_MainActivity_initModel(env, thiz, yaml_config_path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mediatek_neuropilot_jnidemo_aibox_ai_NeuroPilotLlmBridge_nativeComputeStreaming(
    JNIEnv *env, jobject thiz, jlong model_handle, jstring input_text, jobject callback) {
    Java_com_mediatek_neuropilot_jnidemo_MainActivity_computeStreaming(env, thiz, model_handle, input_text, callback);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mediatek_neuropilot_jnidemo_aibox_ai_NeuroPilotLlmBridge_nativeResetModel(
    JNIEnv *env, jobject thiz, jlong model_handle) {
    Java_com_mediatek_neuropilot_jnidemo_MainActivity_resetModel(env, thiz, model_handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mediatek_neuropilot_jnidemo_aibox_ai_NeuroPilotLlmBridge_nativeDestroyModel(
    JNIEnv *env, jobject thiz, jlong model_handle) {
    Java_com_mediatek_neuropilot_jnidemo_MainActivity_destroyModel(env, thiz, model_handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mediatek_neuropilot_jnidemo_aibox_ai_NeuroPilotLlmBridge_nativeCountTokens(
    JNIEnv *env, jobject thiz, jstring text) {
    return Java_com_mediatek_neuropilot_jnidemo_MainActivity_countTokens(env, thiz, text);
}
