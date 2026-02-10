#include "llama_context.h"
#include <android/log.h>
#include <vector>
#include <cstring>

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
    
    // Minimal sampler chain
    auto sparams = llama_sampler_chain_default_params();
    sampler_ = llama_sampler_chain_init(sparams);
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
    
    // Allocate buffer for tokens
    std::vector<llama_token> tokens(prompt.size() + 128);
    
    // Try tokenization with different possible signatures
    int n_tokens = -1;
    
    // Attempt 1: Modern API (text, text_len, tokens, n_tokens_max, add_special, parse_special)
    n_tokens = llama_tokenize(
        model_,
        prompt.c_str(),
        static_cast<int>(prompt.length()),
        tokens.data(),
        static_cast<int>(tokens.size()),
        true,  // add_special
        true   // parse_special
    );
    
    // If buffer too small, resize and retry
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            model_,
            prompt.c_str(),
            static_cast<int>(prompt.length()),
            tokens.data(),
            static_cast<int>(tokens.size()),
            true,
            true
        );
    }
    
    if (n_tokens <= 0) {
        LOGE("Tokenization failed with n_tokens=%d", n_tokens);
        return "";
    }
    
    tokens.resize(n_tokens);
    LOGI("Tokenized: %d tokens", n_tokens);
    
    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("Failed to decode prompt");
        return "";
    }
    
    std::string result;
    result.reserve(max_tokens * 4); // Pre-allocate space
    
    // Generate tokens
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler_, ctx_, -1);
        
        // Check for end-of-generation token
        // Try different API variants
        bool is_eog = false;
        
        // Method 1: Try llama_token_is_eog with model
        is_eog = llama_token_is_eog(model_, new_token);
        
        if (is_eog) {
            LOGI("EOG token detected at position %d", i);
            break;
        }
        
        // Convert token to text
        char buf[256];
        int piece_len = llama_token_to_piece(
            model_,
            new_token,
            buf,
            sizeof(buf),
            0,     // lstrip
            true   // special
        );
        
        if (piece_len > 0) {
            result.append(buf, piece_len);
        } else if (piece_len < 0) {
            // Buffer too small, try again with exact size
            std::vector<char> large_buf(-piece_len);
            piece_len = llama_token_to_piece(
                model_,
                new_token,
                large_buf.data(),
                large_buf.size(),
                0,
                true
            );
            if (piece_len > 0) {
                result.append(large_buf.data(), piece_len);
            }
        }
        
        // Prepare next decode
        batch = llama_batch_get_one(&new_token, 1);
        
        if (llama_decode(ctx_, batch) != 0) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }
    
    LOGI("Generated %zu bytes", result.size());
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
