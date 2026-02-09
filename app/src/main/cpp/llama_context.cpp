#include "llama_context.h"
#include <android/log.h>
#include <vector>

#define TAG "LlamaContext"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

LlamaContext::LlamaContext() {
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
}

LlamaContext::~LlamaContext() {
    unload();
    llama_backend_free();
}

bool LlamaContext::load(const std::string& model_path, int n_ctx, int n_threads) {
    LOGI("Loading model: %s", model_path.c_str());
    
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    
    model_ = llama_load_model_from_file(model_path.c_str(), model_params);
    if (!model_) {
        LOGE("Failed to load model");
        return false;
    }
    
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    
    ctx_ = llama_new_context_with_model(model_, ctx_params);
    if (!ctx_) {
        LOGE("Failed to create context");
        llama_free_model(model_);
        model_ = nullptr;
        return false;
    }
    
    llama_sampling_params sampling_params = llama_sampling_default_params();
    sampling_params.temp = 0.7f;
    sampling_params.top_k = 40;
    sampling_params.top_p = 0.95f;
    sampling_ = llama_sampling_init(sampling_params);
    
    LOGI("Model loaded successfully");
    return true;
}

std::string LlamaContext::generate(const std::string& prompt, int max_tokens, float temp) {
    if (!ctx_) {
        LOGE("Context not loaded");
        return "";
    }
    
    std::vector<llama_token> tokens = llama_tokenize(ctx_, prompt, true, true);
    
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;
    
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_batch_free(batch);
        return "";
    }
    
    std::string result;
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampling_sample(sampling_, ctx_, nullptr);
        llama_sampling_accept(sampling_, ctx_, new_token, true);
        
        if (llama_token_is_eog(model_, new_token)) {
            break;
        }
        
        char buf[256];
        int n = llama_token_to_piece(model_, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
        }
        
        batch.n_tokens = 0;
        llama_batch_add(batch, new_token, tokens.size() + i, {0}, true);
        
        if (llama_decode(ctx_, batch) != 0) {
            break;
        }
    }
    
    llama_batch_free(batch);
    return result;
}

void LlamaContext::unload() {
    if (sampling_) {
        llama_sampling_free(sampling_);
        sampling_ = nullptr;
    }
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_free_model(model_);
        model_ = nullptr;
    }
}
