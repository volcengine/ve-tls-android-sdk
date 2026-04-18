#include <jni.h>

#include <string>

extern "C" {
#include "ve_tls_android_binding.h"
}

namespace {

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv * env, jstring value)
        : env_(env), value_(value), chars_(value == nullptr ? nullptr : env->GetStringUTFChars(value, nullptr)) {
    }

    ~ScopedUtfChars() {
        if (value_ != nullptr && chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char * c_str() const {
        return chars_;
    }

private:
    JNIEnv * env_;
    jstring value_;
    const char * chars_;
};

ve_tls_producer * producer_from_handle(jlong producer_handle) {
    return reinterpret_cast<ve_tls_producer *>(producer_handle);
}

ve_tls_android_compress_type compress_type_from_java(jint compress_type) {
    return compress_type == 0 ? VE_TLS_ANDROID_COMPRESS_NONE : VE_TLS_ANDROID_COMPRESS_LZ4;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeCreate(
    JNIEnv * env,
    jclass,
    jstring endpoint,
    jstring region,
    jstring project_id,
    jstring topic_id,
    jstring access_key_id,
    jstring access_key_secret,
    jstring security_token,
    jstring source,
    jstring hash_key,
    jint compress_type,
    jint send_thread_count,
    jboolean persistent,
    jstring persistent_file_path,
    jboolean persistent_force_flush,
    jint persistent_max_file_count,
    jint persistent_max_file_size,
    jint persistent_max_log_count,
    jint packet_log_bytes,
    jint packet_log_count,
    jint packet_timeout_ms,
    jint max_buffer_limit,
    jint retry_count,
    jint connect_timeout_ms,
    jint request_timeout_ms,
    jboolean enable_time_ns,
    jint destroy_wait_ms
) {
    ScopedUtfChars endpoint_chars(env, endpoint);
    ScopedUtfChars region_chars(env, region);
    ScopedUtfChars project_id_chars(env, project_id);
    ScopedUtfChars topic_id_chars(env, topic_id);
    ScopedUtfChars access_key_id_chars(env, access_key_id);
    ScopedUtfChars access_key_secret_chars(env, access_key_secret);
    ScopedUtfChars security_token_chars(env, security_token);
    ScopedUtfChars source_chars(env, source);
    ScopedUtfChars hash_key_chars(env, hash_key);
    ScopedUtfChars persistent_file_path_chars(env, persistent_file_path);

    ve_tls_android_config_view config_view = {};
    config_view.endpoint = endpoint_chars.c_str();
    config_view.region = region_chars.c_str();
    config_view.project_id = project_id_chars.c_str();
    config_view.topic_id = topic_id_chars.c_str();
    config_view.access_key_id = access_key_id_chars.c_str();
    config_view.access_key_secret = access_key_secret_chars.c_str();
    config_view.security_token = security_token_chars.c_str();
    config_view.source = source_chars.c_str();
    config_view.hash_key = hash_key_chars.c_str();
    config_view.compress_type = compress_type_from_java(compress_type);
    config_view.send_thread_count = send_thread_count;
    config_view.use_persistent = persistent ? 1 : 0;
    config_view.destroy_wait_ms = destroy_wait_ms;

    ve_tls_config config;
    ve_tls_android_runtime_options runtime = {};
    if (ve_tls_android_binding_build_config(&config_view, &config, &runtime) != VE_TLS_OK) {
        return 0;
    }

    config.persistent_file_path = persistent_file_path_chars.c_str();
    config.force_flush_disk = persistent_force_flush ? 1 : 0;
    config.max_persistent_file_count = persistent_max_file_count;
    config.max_persistent_file_size = persistent_max_file_size;
    config.max_persistent_log_count = persistent_max_log_count;
    config.log_bytes_per_package = packet_log_bytes;
    config.log_count_per_package = packet_log_count;
    config.flush_interval_ms = packet_timeout_ms;
    config.max_buffer_bytes = max_buffer_limit;
    config.retry_max_attempts = retry_count;
    config.connect_timeout_ms = connect_timeout_ms;
    config.request_timeout_ms = request_timeout_ms;
    config.enable_time_ns = enable_time_ns ? 1 : 0;

    ve_tls_producer * producer = ve_tls_producer_create(&config);
    if (producer == nullptr) {
        return 0;
    }

    if (ve_tls_android_binding_after_create(producer, &runtime) != VE_TLS_OK) {
        ve_tls_android_binding_before_destroy(producer, &runtime);
        return 0;
    }

    return reinterpret_cast<jlong>(producer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeUpdateEndpoint(
    JNIEnv * env,
    jclass,
    jlong producer_handle,
    jstring endpoint,
    jstring region,
    jstring topic_id
) {
    ve_tls_producer * producer = producer_from_handle(producer_handle);
    if (producer == nullptr) {
        return VE_TLS_INVALID;
    }

    ScopedUtfChars endpoint_chars(env, endpoint);
    ScopedUtfChars region_chars(env, region);
    ScopedUtfChars topic_id_chars(env, topic_id);
    return ve_tls_producer_update_endpoint(
        producer,
        endpoint_chars.c_str(),
        region_chars.c_str(),
        topic_id_chars.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeResetSecurityToken(
    JNIEnv * env,
    jclass,
    jlong producer_handle,
    jstring access_key_id,
    jstring access_key_secret,
    jstring security_token
) {
    ve_tls_producer * producer = producer_from_handle(producer_handle);
    if (producer == nullptr) {
        return VE_TLS_INVALID;
    }

    ScopedUtfChars access_key_id_chars(env, access_key_id);
    ScopedUtfChars access_key_secret_chars(env, access_key_secret);
    ScopedUtfChars security_token_chars(env, security_token);
    return ve_tls_producer_update_static_credentials(
        producer,
        access_key_id_chars.c_str(),
        access_key_secret_chars.c_str(),
        security_token_chars.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeDestroy(
    JNIEnv *,
    jclass,
    jlong producer_handle,
    jint destroy_wait_ms
) {
    ve_tls_producer * producer = producer_from_handle(producer_handle);
    if (producer == nullptr) {
        return;
    }

    ve_tls_android_runtime_options runtime = {};
    runtime.destroy_wait_ms = destroy_wait_ms;
    ve_tls_android_binding_before_destroy(producer, &runtime);
}
