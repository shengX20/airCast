cmake_minimum_required(VERSION 3.22.1)
project(airplay_cast_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- Mbed TLS 3.6 (Apache-2.0) ---
set(ENABLE_PROGRAMS          OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING           OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS   OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON  CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(mbedtls
    GIT_REPOSITORY https://github.com/Mbed-TLS/mbedtls.git
    GIT_TAG        2ca6c285a0dd3f33982dd57299012dacab1ff206
    GIT_SHALLOW    TRUE)
FetchContent_MakeAvailable(mbedtls)

# --- orlp/ed25519 (zlib, vendored) ---
file(GLOB ED25519_SRC ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ed25519/src/*.c)
list(FILTER ED25519_SRC EXCLUDE REGEX "/seed\\.c$")
add_library(ed25519 STATIC ${ED25519_SRC})
target_compile_definitions(ed25519 PRIVATE ED25519_NO_SEED)
target_include_directories(ed25519 PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ed25519/src)

# --- AirPlay crypto + wire-format core ---
add_library(airplay_crypto STATIC airplay_crypto.cpp)
target_include_directories(airplay_crypto PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(airplay_crypto PUBLIC
    mbedcrypto
    ed25519)

# --- RAOP sender state machine (sans-i/o) ---
add_library(raop_sender STATIC raop_sender.cpp)
target_include_directories(raop_sender PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(raop_sender PUBLIC airplay_crypto)

# --- RAOP loop host (POSIX poll, works on Android) ---
add_library(raop_loop STATIC raop_loop.cpp)
target_include_directories(raop_loop PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(raop_loop PUBLIC raop_sender)

# --- JNI bridge ---
add_library(airplay_jni SHARED jni_bridge.cpp)
target_include_directories(airplay_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
find_library(log-lib log)
target_link_libraries(airplay_jni
    raop_loop
    ${log-lib})
