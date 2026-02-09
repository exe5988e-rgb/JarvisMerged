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
    
    // New sampler API
    auto sparams = llama_sampler_chain_default_params();
    sampler_ = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler_, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler_, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    LOGI("Model loaded successfully");
    return true;
}

std::string LlamaContext::generate(const std::string& prompt, int max_tokens, float temp) {
    if (!ctx_) {
        LOGE("Context not loaded");
        return "";
    }
    
    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt.size() + 16);
    int n_tokens = llama_tokenize(model_, prompt.c_str(), prompt.size(), 
                                   tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(model_, prompt.c_str(), prompt.size(),
                                 tokens.data(), tokens.size(), true, true);
    }
    tokens.resize(n_tokens);
    
    // Create batch
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    
    // Decode prompt
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("Failed to decode prompt");
        return "";
    }
    
    std::string result;
    int n_cur = n_tokens;
    
    // Generate tokens
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler_, ctx_, -1);
        
        if (llama_token_is_eog(model_, new_token)) {
            break;
        }
        
        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(model_, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }
        
        // Prepare next iteration
        batch = llama_batch_get_one(&new_token, 1);
        n_cur++;
        
        if (llama_decode(ctx_, batch) != 0) {
            break;
        }
    }
    
    return result;
}

void LlamaContext::unload() {
    if (sampler_) {
        llama_sampler_free(sampler_);
        sampler_ = nullptr;
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
