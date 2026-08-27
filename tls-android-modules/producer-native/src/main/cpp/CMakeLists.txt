cmake_minimum_required(VERSION 3.18)
project(tls_producer_native C CXX)

get_filename_component(VE_TLS_NDK_TOOLCHAIN_BIN "${CMAKE_AR}" DIRECTORY)
if (EXISTS "${VE_TLS_NDK_TOOLCHAIN_BIN}/llvm-ar" AND EXISTS "${VE_TLS_NDK_TOOLCHAIN_BIN}/llvm-ranlib")
  set(CMAKE_AR "${VE_TLS_NDK_TOOLCHAIN_BIN}/llvm-ar" CACHE FILEPATH "" FORCE)
  set(CMAKE_RANLIB "${VE_TLS_NDK_TOOLCHAIN_BIN}/llvm-ranlib" CACHE FILEPATH "" FORCE)
endif()

if (NOT DEFINED VE_TLS_C_SDK_DIR OR "${VE_TLS_C_SDK_DIR}" STREQUAL "")
  set(VE_TLS_C_SDK_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../../ve-tls-c-sdk")
endif()

if (NOT EXISTS "${VE_TLS_C_SDK_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "Cannot find ve-tls-c-sdk at ${VE_TLS_C_SDK_DIR}")
endif()

if (NOT DEFINED VE_TLS_C_SDK_COMMIT OR "${VE_TLS_C_SDK_COMMIT}" STREQUAL "")
  message(FATAL_ERROR "VE_TLS_C_SDK_COMMIT must be provided by Gradle")
endif()

set(VE_TLS_C_SDK_EXPECTED_COMMIT "18270db61acb890f55201fb430e811e3680fb99f")
if (NOT "${VE_TLS_C_SDK_COMMIT}" STREQUAL "${VE_TLS_C_SDK_EXPECTED_COMMIT}")
  message(FATAL_ERROR
    "VE_TLS_C_SDK_COMMIT does not match the pinned ve-tls-c-sdk commit: expected ${VE_TLS_C_SDK_EXPECTED_COMMIT}, got ${VE_TLS_C_SDK_COMMIT}"
  )
endif()

string(LENGTH "${VE_TLS_C_SDK_COMMIT}" VE_TLS_C_SDK_COMMIT_LENGTH)
if (NOT VE_TLS_C_SDK_COMMIT_LENGTH EQUAL 40)
  message(FATAL_ERROR "VE_TLS_C_SDK_COMMIT must be a 40-character lowercase hex SHA")
endif()
if (NOT "${VE_TLS_C_SDK_COMMIT}" MATCHES "^[0-9a-f]+$")
  message(FATAL_ERROR "VE_TLS_C_SDK_COMMIT must be a 40-character lowercase hex SHA")
endif()

execute_process(
  COMMAND git -C "${VE_TLS_C_SDK_DIR}" rev-parse HEAD
  RESULT_VARIABLE VE_TLS_C_SDK_GIT_HEAD_RESULT
  OUTPUT_VARIABLE VE_TLS_C_SDK_HEAD
  ERROR_VARIABLE VE_TLS_C_SDK_GIT_ERROR
  OUTPUT_STRIP_TRAILING_WHITESPACE
)
if (NOT VE_TLS_C_SDK_GIT_HEAD_RESULT EQUAL 0)
  message(FATAL_ERROR "Cannot read ve-tls-c-sdk HEAD at ${VE_TLS_C_SDK_DIR}: ${VE_TLS_C_SDK_GIT_ERROR}")
endif()
if (NOT "${VE_TLS_C_SDK_HEAD}" STREQUAL "${VE_TLS_C_SDK_COMMIT}")
  message(FATAL_ERROR
    "ve-tls-c-sdk HEAD mismatch at ${VE_TLS_C_SDK_DIR}: expected ${VE_TLS_C_SDK_COMMIT}, got ${VE_TLS_C_SDK_HEAD}"
  )
endif()

execute_process(
  COMMAND git -C "${VE_TLS_C_SDK_DIR}" status --porcelain=v1 --untracked-files=no
  RESULT_VARIABLE VE_TLS_C_SDK_GIT_STATUS_RESULT
  OUTPUT_VARIABLE VE_TLS_C_SDK_STATUS
  ERROR_VARIABLE VE_TLS_C_SDK_STATUS_ERROR
  OUTPUT_STRIP_TRAILING_WHITESPACE
)
if (NOT VE_TLS_C_SDK_GIT_STATUS_RESULT EQUAL 0)
  message(FATAL_ERROR "Cannot inspect ve-tls-c-sdk tracked tree at ${VE_TLS_C_SDK_DIR}: ${VE_TLS_C_SDK_STATUS_ERROR}")
endif()
if (NOT "${VE_TLS_C_SDK_STATUS}" STREQUAL "")
  message(FATAL_ERROR "ve-tls-c-sdk tracked tree is modified at ${VE_TLS_C_SDK_DIR}: ${VE_TLS_C_SDK_STATUS}")
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

add_subdirectory("${VE_TLS_C_SDK_DIR}" ${CMAKE_CURRENT_BINARY_DIR}/ve_tls_c_sdk)

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
