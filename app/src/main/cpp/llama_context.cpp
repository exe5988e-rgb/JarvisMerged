#include "llama_context.h"
#include <android/log.h>
#include <vector>
#include <mutex>

#define TAG "LlamaContext"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ✅ FIX: Use static flag to ensure backend is only initialized ONCE
static std::once_flag backend_init_flag;
static bool backend_initialized = false;

LlamaContext::LlamaContext() {
    // ✅ FIX: Only initialize backend once across ALL instances
    std::call_once(backend_init_flag, []() {
        LOGI("Initializing llama backend (once)");
        llama_backend_init();
        llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
        backend_initialized = true;
    });
}

LlamaContext::~LlamaContext() {
    unload();
    // ✅ DON'T call llama_backend_free() - backend is shared!
}

bool LlamaContext::load(const std::string& model_path, int n_ctx, int n_threads) {
    LOGI("Loading model: %s (ctx=%d, threads=%d)", model_path.c_str(), n_ctx, n_threads);
    
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
    
    // ✅ FIX: Don't initialize sampler here - do it per generation with correct temp
    
    LOGI("Model loaded successfully");
    return true;
}

std::string LlamaContext::generate(const std::string& prompt, int max_tokens, float temp) {
    if (!ctx_) {
        LOGE("Context not loaded");
        return "";
    }
    
    LOGI("Generating with temp=%.2f, max_tokens=%d", temp, max_tokens);
    
    // Get vocab from model
    const struct llama_vocab* vocab = llama_model_get_vocab(model_);
    
    // ✅ FIX: Create sampler with CORRECT temperature for THIS generation
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temp));  // Use provided temp!
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt.size() + 128);
    
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
        llama_sampler_free(sampler);
        return "";
    }
    
    tokens.resize(n_tokens);
    LOGI("Tokenized: %d tokens", n_tokens);
    
    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_sampler_free(sampler);
        return "";
    }
    
    std::string result;
    
    // Generate tokens
    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, ctx_, -1);
        
        // Check for EOG
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }
        
        // Token to piece
        char buf[256];
        int32_t n = llama_token_to_piece(
            vocab,
            new_token,
            buf,
            sizeof(buf),
            0,
            true
        );
        
        if (n > 0) {
            result.append(buf, n);
        }
        
        batch = llama_batch_get_one(&new_token, 1);
        
        if (llama_decode(ctx_, batch) != 0) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }
    
    // ✅ FIX: Clean up sampler after generation
    llama_sampler_free(sampler);
    
    LOGI("Generated %zu bytes", result.size());
    return result;
}

void LlamaContext::unload() {
    // Note: Don't free sampler_ here anymore since we create it per-generation
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_free_model(model_);
        model_ = nullptr;
    }
}
