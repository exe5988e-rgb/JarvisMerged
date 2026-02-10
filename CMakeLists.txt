cmake_minimum_required(VERSION 3.22.1)
project(jarvis_ai)

set(CMAKE_CXX_STANDARD 17)

# ✅ CHANGED: -fno-exceptions → -fexceptions
# llama.cpp now requires exceptions for try/catch/throw
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -fexceptions")

# llama.cpp configuration
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(app/src/main/cpp/llama.cpp)

# Native bridge library
add_library(jarvis_native SHARED
    app/src/main/cpp/native_bridge.cpp
    app/src/main/cpp/llama_context.cpp
)

target_link_libraries(jarvis_native
    llama
    android
    log
)

target_include_directories(jarvis_native PRIVATE
    app/src/main/cpp/llama.cpp/include
    app/src/main/cpp/llama.cpp/src
    app/src/main/cpp/llama.cpp/ggml/include
)
