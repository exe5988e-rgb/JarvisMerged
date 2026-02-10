#include <jni.h>
#include "llama_context.h"
#include <android/log.h>

#define TAG "JarvisNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// ✅ Backend init/free are now separate from instance creation
extern "C" JNIEXPORT void JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeBackendInit(JNIEnv*, jobject) {
    LOGI("Initializing llama backend (global)");
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeBackendFree(JNIEnv*, jobject) {
    LOGI("Freeing llama backend (global)");
    llama_backend_free();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeInit(JNIEnv*, jobject) {
    auto* ctx = new LlamaContext();
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jstring modelPath, jint nCtx, jint nThreads
) {
    auto* ctx = reinterpret_cast<LlamaContext*>(handle);
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    bool result = ctx->load(path, nCtx, nThreads);
    env->ReleaseStringUTFChars(modelPath, path);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeGenerate(
    JNIEnv* env, jobject, jlong handle, jstring prompt, jint maxTokens, jfloat temp
) {
    auto* ctx = reinterpret_cast<LlamaContext*>(handle);
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string result = ctx->generate(prompt_str, maxTokens, temp);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeStopGeneration(JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<LlamaContext*>(handle);
    ctx->stopGeneration();
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvismini_engine_ai_LlamaNative_nativeRelease(JNIEnv*, jobject, jlong handle) {
    auto* ctx = reinterpret_cast<LlamaContext*>(handle);
    delete ctx;
}
