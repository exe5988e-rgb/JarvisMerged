#ifndef LLAMA_CONTEXT_H
#define LLAMA_CONTEXT_H

#include "llama.h"
#include <string>
#include <atomic>

class LlamaContext {
public:
    LlamaContext();
    ~LlamaContext();
    
    bool load(const std::string& model_path, int n_ctx = 2048, int n_threads = 4);
    std::string generate(const std::string& prompt, int max_tokens = 256, float temp = 0.7f);
    void stopGeneration();
    void unload();
    
    bool isLoaded() const { return ctx_ != nullptr; }

private:
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    std::atomic<bool> should_stop_{false};
};

#endif
