cmake_minimum_required(VERSION 3.18)
project(tls_producer_native C CXX)

set(VE_TLS_C_SDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../../ve-tls-c-sdk")

if (NOT EXISTS "${VE_TLS_C_SDK_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "Cannot find ve-tls-c-sdk at ${VE_TLS_C_SDK_DIR}")
endif()

set(VE_TLS_ENABLE_CURL OFF CACHE BOOL "" FORCE)
set(VE_TLS_ENABLE_ZLIB OFF CACHE BOOL "" FORCE)
set(VE_TLS_ENABLE_LZ4 ON CACHE BOOL "" FORCE)
set(VE_TLS_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(VE_TLS_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

function(apply_release_like_size_compile_flags target)
  target_compile_options(${target} PRIVATE
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-Oz>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-ffunction-sections>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-fdata-sections>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-fno-unwind-tables>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-fno-asynchronous-unwind-tables>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-fvisibility=hidden>
  )
endfunction()

function(apply_release_like_size_link_flags target)
  target_link_options(${target} PRIVATE
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-Wl,--gc-sections>
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-Wl,--exclude-libs,ALL>
  )
endfunction()

add_subdirectory(${VE_TLS_C_SDK_DIR} ${CMAKE_CURRENT_BINARY_DIR}/ve_tls_c_sdk)

add_library(tls_producer_jni SHARED
  ${VE_TLS_C_SDK_DIR}/bindings/android/src/ve_tls_android_binding.c
  tls_producer_jni.cpp
)

target_compile_features(tls_producer_jni PRIVATE cxx_std_11)

target_include_directories(tls_producer_jni PRIVATE
  ${VE_TLS_C_SDK_DIR}/bindings/android/include
  ${VE_TLS_C_SDK_DIR}/core/include
  ${VE_TLS_C_SDK_DIR}/adapters/include
)

apply_release_like_size_compile_flags(ve_tls_core)
apply_release_like_size_compile_flags(tls_producer_jni)
target_compile_options(tls_producer_jni PRIVATE
  $<$<AND:$<COMPILE_LANGUAGE:CXX>,$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>>:-fvisibility-inlines-hidden>
  $<$<AND:$<COMPILE_LANGUAGE:CXX>,$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>>:-fno-exceptions>
  $<$<AND:$<COMPILE_LANGUAGE:CXX>,$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>>:-fno-rtti>
)
apply_release_like_size_link_flags(tls_producer_jni)

target_link_libraries(tls_producer_jni PRIVATE ve_tls_core)
