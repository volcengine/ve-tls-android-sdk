cmake_minimum_required(VERSION 3.18)
project(tls_producer_native C)

set(VE_TLS_C_SDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../../ve-tls-c-sdk")

if (NOT EXISTS "${VE_TLS_C_SDK_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "Cannot find ve-tls-c-sdk at ${VE_TLS_C_SDK_DIR}")
endif()

set(VE_TLS_ENABLE_CURL OFF CACHE BOOL "" FORCE)
set(VE_TLS_ENABLE_ZLIB OFF CACHE BOOL "" FORCE)
set(VE_TLS_ENABLE_LZ4 ON CACHE BOOL "" FORCE)
set(VE_TLS_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(VE_TLS_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

add_subdirectory(${VE_TLS_C_SDK_DIR} ${CMAKE_CURRENT_BINARY_DIR}/ve_tls_c_sdk)

add_library(tls_producer_jni SHARED
  ${VE_TLS_C_SDK_DIR}/bindings/android/src/ve_tls_android_binding.c
)

target_include_directories(tls_producer_jni PRIVATE
  ${VE_TLS_C_SDK_DIR}/bindings/android/include
  ${VE_TLS_C_SDK_DIR}/core/include
  ${VE_TLS_C_SDK_DIR}/adapters/include
)

target_link_libraries(tls_producer_jni PRIVATE ve_tls_core)
