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
    
    // Minimal sampler chain - temperature + greedy
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
    
    // Get vocab from model
    const struct llama_vocab* vocab = llama_model_get_vocab(model_);
    
    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt.size() + 128);
    
    // llama_tokenize(vocab, text, text_len, tokens, n_tokens_max, add_special, parse_special)
    int32_t n_tokens = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.length()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );
    
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.length()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
    }
    
    if (n_tokens <= 0) {
        LOGE("Tokenization failed");
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
    
    // Generate tokens
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler_, ctx_, -1);
        
        // Check for EOG - use llama_vocab_is_eog
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }
        
        // Token to piece - it's llama_token_to_piece (NOT llama_vocab_token_to_piece!)
        // But first parameter is vocab, not model
        char buf[256];
        int32_t n = llama_token_to_piece(
            vocab,              // vocab (not model!)
            new_token,          // token
            buf,                // buf
            sizeof(buf),        // length
            0,                  // lstrip
            true                // special
        );
        
        if (n > 0) {
            result.append(buf, n);
        }
        
        batch = llama_batch_get_one(&new_token, 1);
        
        if (llama_decode(ctx_, batch) != 0) {
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
