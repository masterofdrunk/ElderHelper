cmake_minimum_required(VERSION 3.22.1)

project("elderhelper_minicpm" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "ElderHelper MiniCPM-V runtime currently supports arm64-v8a only")
endif()

set(GGML_SYSTEM_ARCH "ARM")
set(GGML_CPU_KLEIDIAI ON)
set(GGML_OPENMP OFF)

if(NOT DEFINED LLAMA_SRC)
    set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp-omni")
endif()
get_filename_component(LLAMA_SRC "${LLAMA_SRC}" ABSOLUTE)
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp-omni is missing at ${LLAMA_SRC}. "
        "Initialize third_party/llama.cpp-omni at commit da241979b95b13e747f7c3f8d6821930e0263d33.")
endif()

add_subdirectory("${LLAMA_SRC}" "${CMAKE_BINARY_DIR}/build-llama")

add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
    ${LLAMA_SRC}/tools/mtmd
    ${LLAMA_SRC}/vendor
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    llama-common
    mtmd
    android
    log
)
