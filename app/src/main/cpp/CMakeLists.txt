cmake_minimum_required(VERSION 3.22.1)
project(aven C CXX)

# Vendored llama.cpp v0.1.2 (add_subdirectory keeps LLAMA_STANDALONE=OFF, so
# tools/examples/tests are not built; the llama + ggml targets are produced).
add_subdirectory(llama.cpp)

add_library(aven_llama SHARED aven_llama.cpp)
target_include_directories(aven_llama PRIVATE llama.cpp/include)
target_link_libraries(aven_llama PRIVATE llama)