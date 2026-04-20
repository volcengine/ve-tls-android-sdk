#include <jni.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <pthread.h>

extern "C" {
#include "ve_tls_android_binding.h"
}

namespace {

struct JniHttpBridgeState;
struct CallbackState;
struct HttpBridgeStateNode;
struct CallbackStateNode;

JavaVM * g_jvm = nullptr;
pthread_mutex_t g_http_bridge_mutex = PTHREAD_MUTEX_INITIALIZER;
HttpBridgeStateNode * g_http_bridges = nullptr;
pthread_mutex_t g_callback_mutex = PTHREAD_MUTEX_INITIALIZER;
CallbackStateNode * g_callback_states = nullptr;

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

class ScopedLocalFrame {
public:
    ScopedLocalFrame(JNIEnv * env, jint capacity)
        : env_(env), active_(env != nullptr && env->PushLocalFrame(capacity) == JNI_OK) {
    }

    ~ScopedLocalFrame() {
        if (active_) {
            env_->PopLocalFrame(nullptr);
        }
    }

    bool active() const {
        return active_;
    }

private:
    JNIEnv * env_;
    bool active_;
};

struct ThreadEnvAttachment {
    JNIEnv * env = nullptr;
    int attached = 0;
};

struct JniHttpBridgeState {
    jobject bridge = nullptr;
    jclass bridge_class = nullptr;
    jclass request_class = nullptr;
    jclass response_class = nullptr;
    jmethodID request_ctor = nullptr;
    jmethodID execute = nullptr;
    jmethodID response_get_status_code = nullptr;
    jmethodID response_get_body = nullptr;
    jmethodID response_get_request_id = nullptr;
    jmethodID response_get_error_code = nullptr;
    jmethodID response_get_error_message = nullptr;
};

struct CallbackState {
    jobject dispatcher = nullptr;
    jclass dispatcher_class = nullptr;
    jmethodID dispatch = nullptr;
};

struct HttpBridgeStateNode {
    ve_tls_producer * producer = nullptr;
    JniHttpBridgeState * state = nullptr;
    HttpBridgeStateNode * next = nullptr;
};

struct CallbackStateNode {
    ve_tls_producer * producer = nullptr;
    CallbackState * state = nullptr;
    CallbackStateNode * next = nullptr;
};

ve_tls_producer * producer_from_handle(jlong producer_handle) {
    return reinterpret_cast<ve_tls_producer *>(producer_handle);
}

ve_tls_android_compress_type compress_type_from_java(jint compress_type) {
    return compress_type == 0 ? VE_TLS_ANDROID_COMPRESS_NONE : VE_TLS_ANDROID_COMPRESS_LZ4;
}

ThreadEnvAttachment current_thread_env() {
    ThreadEnvAttachment attachment = {};
    if (g_jvm == nullptr) {
        return attachment;
    }

    JNIEnv * env = nullptr;
    jint rc = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (rc == JNI_OK) {
        attachment.env = env;
        return attachment;
    }
    if (rc != JNI_EDETACHED) {
        return attachment;
    }
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        return attachment;
    }
    attachment.env = env;
    attachment.attached = 1;
    return attachment;
}

void release_thread_env(ThreadEnvAttachment * attachment) {
    if (attachment == nullptr) {
        return;
    }
    if (attachment->attached && g_jvm != nullptr) {
        g_jvm->DetachCurrentThread();
    }
    attachment->env = nullptr;
    attachment->attached = 0;
}

void free_response_fields(ve_tls_http_response * resp) {
    if (resp == nullptr) {
        return;
    }
    free(resp->body);
    free(resp->request_id);
    free(resp->error_code);
    free(resp->error_message);
    resp->body = nullptr;
    resp->request_id = nullptr;
    resp->error_code = nullptr;
    resp->error_message = nullptr;
    resp->body_size = 0;
    resp->status_code = 0;
    resp->transport_kind = VE_TLS_TRANSPORT_NONE;
    resp->transport_code = 0;
    resp->transport_retryable = 0;
}

char * duplicate_utf_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return nullptr;
    }
    ScopedUtfChars chars(env, value);
    if (chars.c_str() == nullptr) {
        return nullptr;
    }
    size_t length = std::strlen(chars.c_str());
    char * copy = static_cast<char *>(std::malloc(length + 1));
    if (copy == nullptr) {
        return nullptr;
    }
    std::memcpy(copy, chars.c_str(), length + 1);
    return copy;
}

char * duplicate_c_string(const char * value) {
    const char * source = value == nullptr ? "" : value;
    size_t length = std::strlen(source);
    char * copy = static_cast<char *>(std::malloc(length + 1));
    if (copy == nullptr) {
        return nullptr;
    }
    std::memcpy(copy, source, length + 1);
    return copy;
}

char * duplicate_utf_string_or_empty(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return duplicate_c_string("");
    }
    return duplicate_utf_string(env, value);
}

void free_kv_storage(ve_tls_kv * kvs, size_t kv_count) {
    if (kvs == nullptr) {
        return;
    }
    for (size_t i = 0; i < kv_count; ++i) {
        std::free(const_cast<char *>(kvs[i].key));
        std::free(const_cast<char *>(kvs[i].value));
    }
    std::free(kvs);
}

void set_bridge_error(JNIEnv * env, ve_tls_http_response * resp, jthrowable throwable) {
    if (resp == nullptr) {
        return;
    }
    free_response_fields(resp);
    resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
    resp->transport_code = -1;
    resp->transport_retryable = 1;
    resp->error_code = ::strdup("JavaHttpBridgeError");
    if (throwable == nullptr || env == nullptr) {
        resp->error_message = ::strdup("native http bridge failed");
        return;
    }

    jclass throwable_class = env->GetObjectClass(throwable);
    if (throwable_class == nullptr) {
        resp->error_message = ::strdup("native http bridge failed");
        return;
    }
    jmethodID to_string = env->GetMethodID(throwable_class, "toString", "()Ljava/lang/String;");
    if (to_string == nullptr) {
        env->DeleteLocalRef(throwable_class);
        resp->error_message = ::strdup("native http bridge failed");
        return;
    }
    jstring message = static_cast<jstring>(env->CallObjectMethod(throwable, to_string));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        resp->error_message = ::strdup("native http bridge failed");
    } else {
        char * message_copy = duplicate_utf_string(env, message);
        resp->error_message = message_copy == nullptr ? ::strdup("native http bridge failed") : message_copy;
    }
    if (message != nullptr) {
        env->DeleteLocalRef(message);
    }
    env->DeleteLocalRef(throwable_class);
}

void destroy_http_bridge_state(JniHttpBridgeState * state) {
    if (state == nullptr) {
        return;
    }
    ThreadEnvAttachment thread_env = current_thread_env();
    JNIEnv * env = thread_env.env;
    if (env != nullptr) {
        if (state->bridge != nullptr) {
            env->DeleteGlobalRef(state->bridge);
        }
        if (state->bridge_class != nullptr) {
            env->DeleteGlobalRef(state->bridge_class);
        }
        if (state->request_class != nullptr) {
            env->DeleteGlobalRef(state->request_class);
        }
        if (state->response_class != nullptr) {
            env->DeleteGlobalRef(state->response_class);
        }
    }
    release_thread_env(&thread_env);
    delete state;
}

int remember_http_bridge_state(ve_tls_producer * producer, JniHttpBridgeState * state) {
    if (producer == nullptr || state == nullptr) {
        return 0;
    }
    if (pthread_mutex_lock(&g_http_bridge_mutex) != 0) {
        return 0;
    }
    for (HttpBridgeStateNode * node = g_http_bridges; node != nullptr; node = node->next) {
        if (node->producer == producer) {
            node->state = state;
            pthread_mutex_unlock(&g_http_bridge_mutex);
            return 1;
        }
    }
    auto * node = static_cast<HttpBridgeStateNode *>(std::malloc(sizeof(HttpBridgeStateNode)));
    if (node == nullptr) {
        pthread_mutex_unlock(&g_http_bridge_mutex);
        return 0;
    }
    node->producer = producer;
    node->state = state;
    node->next = g_http_bridges;
    g_http_bridges = node;
    pthread_mutex_unlock(&g_http_bridge_mutex);
    return 1;
}

JniHttpBridgeState * forget_http_bridge_state(ve_tls_producer * producer) {
    if (producer == nullptr) {
        return nullptr;
    }
    if (pthread_mutex_lock(&g_http_bridge_mutex) != 0) {
        return nullptr;
    }
    HttpBridgeStateNode ** cursor = &g_http_bridges;
    while (*cursor != nullptr) {
        if ((*cursor)->producer == producer) {
            HttpBridgeStateNode * node = *cursor;
            JniHttpBridgeState * state = node->state;
            *cursor = node->next;
            std::free(node);
            pthread_mutex_unlock(&g_http_bridge_mutex);
            return state;
        }
        cursor = &(*cursor)->next;
    }
    pthread_mutex_unlock(&g_http_bridge_mutex);
    return nullptr;
}

JniHttpBridgeState * create_http_bridge_state(JNIEnv * env) {
    if (env == nullptr) {
        return nullptr;
    }
    ScopedLocalFrame frame(env, 32);
    if (!frame.active()) {
        return nullptr;
    }

    auto * state = new JniHttpBridgeState();

    jclass local_bridge_class = env->FindClass("com/volcengine/tls/android/producer/internal/NativeHttpBridge");
    if (local_bridge_class == nullptr) {
        env->ExceptionClear();
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->bridge_class = static_cast<jclass>(env->NewGlobalRef(local_bridge_class));
    if (state->bridge_class == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }

    jmethodID bridge_ctor = env->GetMethodID(state->bridge_class, "<init>", "()V");
    state->execute = env->GetMethodID(
        state->bridge_class,
        "execute",
        "(Lcom/volcengine/tls/android/producer/internal/NativeHttpBridge$Request;)Lcom/volcengine/tls/android/producer/internal/NativeHttpResponse;");
    if (bridge_ctor == nullptr || state->execute == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }

    jobject local_bridge = env->NewObject(state->bridge_class, bridge_ctor);
    if (local_bridge == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->bridge = env->NewGlobalRef(local_bridge);
    if (state->bridge == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }

    jclass local_request_class = env->FindClass("com/volcengine/tls/android/producer/internal/NativeHttpBridge$Request");
    if (local_request_class == nullptr) {
        env->ExceptionClear();
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->request_class = static_cast<jclass>(env->NewGlobalRef(local_request_class));
    if (state->request_class == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->request_ctor = env->GetMethodID(
        state->request_class,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BIIIILjava/net/Proxy;Ljava/lang/String;Ljava/lang/String;)V");
    if (state->request_ctor == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }

    jclass local_response_class = env->FindClass("com/volcengine/tls/android/producer/internal/NativeHttpResponse");
    if (local_response_class == nullptr) {
        env->ExceptionClear();
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->response_class = static_cast<jclass>(env->NewGlobalRef(local_response_class));
    if (state->response_class == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }
    state->response_get_status_code = env->GetMethodID(state->response_class, "getStatusCode", "()I");
    state->response_get_body = env->GetMethodID(state->response_class, "getBody", "()[B");
    state->response_get_request_id = env->GetMethodID(state->response_class, "getRequestId", "()Ljava/lang/String;");
    state->response_get_error_code = env->GetMethodID(state->response_class, "getErrorCode", "()I");
    state->response_get_error_message = env->GetMethodID(state->response_class, "getErrorMessage", "()Ljava/lang/String;");
    if (state->response_get_status_code == nullptr
        || state->response_get_body == nullptr
        || state->response_get_request_id == nullptr
        || state->response_get_error_code == nullptr
        || state->response_get_error_message == nullptr) {
        destroy_http_bridge_state(state);
        return nullptr;
    }

    return state;
}

void destroy_callback_state(CallbackState * state) {
    if (state == nullptr) {
        return;
    }
    ThreadEnvAttachment thread_env = current_thread_env();
    JNIEnv * env = thread_env.env;
    if (env != nullptr) {
        if (state->dispatcher != nullptr) {
            env->DeleteGlobalRef(state->dispatcher);
        }
        if (state->dispatcher_class != nullptr) {
            env->DeleteGlobalRef(state->dispatcher_class);
        }
    }
    release_thread_env(&thread_env);
    delete state;
}

int remember_callback_state(ve_tls_producer * producer, CallbackState * state) {
    if (producer == nullptr || state == nullptr) {
        return 0;
    }
    if (pthread_mutex_lock(&g_callback_mutex) != 0) {
        return 0;
    }
    for (CallbackStateNode * node = g_callback_states; node != nullptr; node = node->next) {
        if (node->producer == producer) {
            node->state = state;
            pthread_mutex_unlock(&g_callback_mutex);
            return 1;
        }
    }
    auto * node = static_cast<CallbackStateNode *>(std::malloc(sizeof(CallbackStateNode)));
    if (node == nullptr) {
        pthread_mutex_unlock(&g_callback_mutex);
        return 0;
    }
    node->producer = producer;
    node->state = state;
    node->next = g_callback_states;
    g_callback_states = node;
    pthread_mutex_unlock(&g_callback_mutex);
    return 1;
}

CallbackState * forget_callback_state(ve_tls_producer * producer) {
    if (producer == nullptr) {
        return nullptr;
    }
    if (pthread_mutex_lock(&g_callback_mutex) != 0) {
        return nullptr;
    }
    CallbackStateNode ** cursor = &g_callback_states;
    while (*cursor != nullptr) {
        if ((*cursor)->producer == producer) {
            CallbackStateNode * node = *cursor;
            CallbackState * state = node->state;
            *cursor = node->next;
            std::free(node);
            pthread_mutex_unlock(&g_callback_mutex);
            return state;
        }
        cursor = &(*cursor)->next;
    }
    pthread_mutex_unlock(&g_callback_mutex);
    return nullptr;
}

CallbackState * create_callback_state(JNIEnv * env, jobject dispatcher) {
    if (env == nullptr || dispatcher == nullptr) {
        return nullptr;
    }

    ScopedLocalFrame frame(env, 8);
    if (!frame.active()) {
        return nullptr;
    }

    auto * state = new CallbackState();
    state->dispatcher = env->NewGlobalRef(dispatcher);
    if (state->dispatcher == nullptr) {
        destroy_callback_state(state);
        return nullptr;
    }

    jclass local_dispatcher_class = env->GetObjectClass(dispatcher);
    if (local_dispatcher_class == nullptr) {
        destroy_callback_state(state);
        return nullptr;
    }
    state->dispatcher_class = static_cast<jclass>(env->NewGlobalRef(local_dispatcher_class));
    if (state->dispatcher_class == nullptr) {
        destroy_callback_state(state);
        return nullptr;
    }

    state->dispatch = env->GetMethodID(
        state->dispatcher_class,
        "dispatch",
        "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIJJ)V");
    if (state->dispatch == nullptr) {
        destroy_callback_state(state);
        return nullptr;
    }

    return state;
}

void bridge_on_send_done_v2(
    ve_tls_result result,
    size_t log_bytes,
    size_t compressed_bytes,
    const ve_tls_error * error,
    const unsigned char * raw_buffer,
    void * user_param,
    int64_t start_id,
    int64_t end_id
) {
    (void)raw_buffer;
    (void)start_id;
    (void)end_id;

    auto * state = static_cast<CallbackState *>(user_param);
    if (state == nullptr) {
        return;
    }

    ThreadEnvAttachment thread_env = current_thread_env();
    JNIEnv * env = thread_env.env;
    if (env == nullptr) {
        release_thread_env(&thread_env);
        return;
    }

    ScopedLocalFrame frame(env, 16);
    if (!frame.active()) {
        release_thread_env(&thread_env);
        return;
    }

    jstring request_id = error != nullptr && error->request_id != nullptr
        ? env->NewStringUTF(error->request_id)
        : nullptr;
    jstring error_code = error != nullptr && error->error_code != nullptr
        ? env->NewStringUTF(error->error_code)
        : nullptr;
    jstring error_message = error != nullptr && error->error_message != nullptr
        ? env->NewStringUTF(error->error_message)
        : nullptr;

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        release_thread_env(&thread_env);
        return;
    }

    env->CallVoidMethod(
        state->dispatcher,
        state->dispatch,
        static_cast<jint>(result),
        static_cast<jint>(error == nullptr ? 0 : error->http_code),
        request_id,
        error_code,
        error_message,
        static_cast<jint>(error == nullptr ? 0 : error->transport_kind),
        static_cast<jint>(error == nullptr ? 0 : error->transport_code),
        static_cast<jlong>(log_bytes),
        static_cast<jlong>(compressed_bytes));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    release_thread_env(&thread_env);
}

int bridge_do_request(
    ve_tls_http_client * client,
    const ve_tls_http_request * req,
    ve_tls_http_response * resp
) {
    if (client == nullptr || req == nullptr || resp == nullptr) {
        return -1;
    }

    auto * state = static_cast<JniHttpBridgeState *>(client->user_data);
    if (state == nullptr) {
        resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
        resp->transport_code = -1;
        resp->transport_retryable = 1;
        resp->error_code = ::strdup("JavaHttpBridgeUnavailable");
        resp->error_message = ::strdup("java http bridge state is missing");
        return -1;
    }

    ThreadEnvAttachment thread_env = current_thread_env();
    JNIEnv * env = thread_env.env;
    if (env == nullptr) {
        resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
        resp->transport_code = -1;
        resp->transport_retryable = 1;
        resp->error_code = ::strdup("JavaVmUnavailable");
        resp->error_message = ::strdup("failed to attach native sender thread to JVM");
        release_thread_env(&thread_env);
        return -1;
    }

    ScopedLocalFrame frame(env, 32);
    if (!frame.active()) {
        resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
        resp->transport_code = -1;
        resp->transport_retryable = 1;
        resp->error_code = ::strdup("JavaLocalFrameError");
        resp->error_message = ::strdup("failed to allocate JNI local frame");
        release_thread_env(&thread_env);
        return -1;
    }

    jstring method = req->method == nullptr ? nullptr : env->NewStringUTF(req->method);
    jstring url = req->url == nullptr ? nullptr : env->NewStringUTF(req->url);
    jstring headers = req->headers == nullptr ? nullptr : env->NewStringUTF(req->headers);
    jstring ca_cert_path = req->ca_cert_path == nullptr ? nullptr : env->NewStringUTF(req->ca_cert_path);
    jstring user_agent = req->user_agent == nullptr ? nullptr : env->NewStringUTF(req->user_agent);
    jbyteArray body = nullptr;
    if (req->body != nullptr && req->body_size > 0) {
        body = env->NewByteArray(static_cast<jsize>(req->body_size));
        if (body != nullptr) {
            env->SetByteArrayRegion(body, 0, static_cast<jsize>(req->body_size), reinterpret_cast<const jbyte *>(req->body));
        }
    }
    if (env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        env->ExceptionClear();
        set_bridge_error(env, resp, throwable);
        release_thread_env(&thread_env);
        return -1;
    }

    jobject request = env->NewObject(
        state->request_class,
        state->request_ctor,
        method,
        url,
        headers,
        body,
        static_cast<jint>(req->connect_timeout_ms),
        static_cast<jint>(req->timeout_ms),
        static_cast<jint>(req->tls_verify_peer),
        static_cast<jint>(req->tls_verify_host),
        nullptr,
        ca_cert_path,
        user_agent);
    if (request == nullptr || env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        env->ExceptionClear();
        set_bridge_error(env, resp, throwable);
        release_thread_env(&thread_env);
        return -1;
    }

    jobject response = env->CallObjectMethod(state->bridge, state->execute, request);
    if (env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        env->ExceptionClear();
        set_bridge_error(env, resp, throwable);
        release_thread_env(&thread_env);
        return -1;
    }
    if (response == nullptr) {
        resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
        resp->transport_code = -1;
        resp->transport_retryable = 1;
        resp->error_code = ::strdup("JavaHttpBridgeEmptyResponse");
        resp->error_message = ::strdup("java http bridge returned null response");
        release_thread_env(&thread_env);
        return -1;
    }

    resp->status_code = env->CallIntMethod(response, state->response_get_status_code);
    jbyteArray response_body = static_cast<jbyteArray>(env->CallObjectMethod(response, state->response_get_body));
    jstring request_id = static_cast<jstring>(env->CallObjectMethod(response, state->response_get_request_id));
    jint error_code = env->CallIntMethod(response, state->response_get_error_code);
    jstring error_message = static_cast<jstring>(env->CallObjectMethod(response, state->response_get_error_message));
    if (env->ExceptionCheck()) {
        jthrowable throwable = env->ExceptionOccurred();
        env->ExceptionClear();
        set_bridge_error(env, resp, throwable);
        release_thread_env(&thread_env);
        return -1;
    }

    if (response_body != nullptr) {
        jsize body_size = env->GetArrayLength(response_body);
        if (body_size > 0) {
            resp->body = static_cast<unsigned char *>(std::malloc(static_cast<size_t>(body_size)));
            if (resp->body == nullptr) {
                resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
                resp->transport_code = -1;
                resp->transport_retryable = 1;
                resp->error_code = ::strdup("JavaHttpBridgeOom");
                resp->error_message = ::strdup("failed to allocate native response body");
                release_thread_env(&thread_env);
                return -1;
            }
            env->GetByteArrayRegion(response_body, 0, body_size, reinterpret_cast<jbyte *>(resp->body));
            resp->body_size = static_cast<size_t>(body_size);
        }
    }

    resp->request_id = duplicate_utf_string(env, request_id);
    if (error_code != 0) {
        char code_text[32];
        int code_text_length = std::snprintf(code_text, sizeof(code_text), "%d", static_cast<int>(error_code));
        if (code_text_length > 0) {
            resp->error_code = static_cast<char *>(std::malloc(static_cast<size_t>(code_text_length) + 1));
            if (resp->error_code != nullptr) {
                std::memcpy(resp->error_code, code_text, static_cast<size_t>(code_text_length) + 1);
            }
        }
    }
    resp->error_message = duplicate_utf_string(env, error_message);
    resp->transport_kind = VE_TLS_TRANSPORT_NONE;
    resp->transport_code = 0;
    resp->transport_retryable = 0;
    release_thread_env(&thread_env);
    return 0;
}

void bridge_free_response(ve_tls_http_client *, ve_tls_http_response * resp) {
    free_response_fields(resp);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

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
    jint destroy_wait_ms,
    jobject callback_dispatcher
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
    JniHttpBridgeState * http_bridge_state = create_http_bridge_state(env);
    if (http_bridge_state == nullptr) {
        return 0;
    }

    ve_tls_android_config_view config_view = {};
    ve_tls_android_http_client_bridge http_client_bridge = {};
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
    http_client_bridge.do_request = bridge_do_request;
    http_client_bridge.free_response = bridge_free_response;
    http_client_bridge.user_data = http_bridge_state;
    config_view.http_client = &http_client_bridge;

    ve_tls_config config;
    ve_tls_android_runtime_options runtime = {};
    if (ve_tls_android_binding_build_config(&config_view, &config, &runtime) != VE_TLS_OK) {
        destroy_http_bridge_state(http_bridge_state);
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
        destroy_http_bridge_state(http_bridge_state);
        return 0;
    }
    CallbackState * callback_state = create_callback_state(env, callback_dispatcher);
    if (callback_dispatcher != nullptr && callback_state == nullptr) {
        ve_tls_android_runtime_options cleanup_runtime = runtime;
        ve_tls_android_binding_before_destroy(producer, &cleanup_runtime);
        destroy_http_bridge_state(http_bridge_state);
        return 0;
    }
    if (!remember_http_bridge_state(producer, http_bridge_state)) {
        ve_tls_android_runtime_options cleanup_runtime = runtime;
        ve_tls_android_binding_before_destroy(producer, &cleanup_runtime);
        destroy_callback_state(callback_state);
        destroy_http_bridge_state(http_bridge_state);
        return 0;
    }
    if (callback_state != nullptr) {
        ve_tls_producer_set_send_done_v2(producer, bridge_on_send_done_v2, callback_state);
        if (!remember_callback_state(producer, callback_state)) {
            JniHttpBridgeState * state = forget_http_bridge_state(producer);
            ve_tls_android_runtime_options cleanup_runtime = runtime;
            ve_tls_android_binding_before_destroy(producer, &cleanup_runtime);
            destroy_callback_state(callback_state);
            destroy_http_bridge_state(state);
            return 0;
        }
    }

    if (ve_tls_android_binding_after_create(producer, &runtime) != VE_TLS_OK) {
        JniHttpBridgeState * state = forget_http_bridge_state(producer);
        CallbackState * callback = forget_callback_state(producer);
        ve_tls_android_binding_before_destroy(producer, &runtime);
        destroy_callback_state(callback);
        destroy_http_bridge_state(state);
        return 0;
    }

    return reinterpret_cast<jlong>(producer);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeAddLog(
    JNIEnv * env,
    jclass,
    jlong producer_handle,
    jlong log_time_ms,
    jstring hash_key,
    jobjectArray keys,
    jobjectArray values,
    jint flush
) {
    ve_tls_producer * producer = producer_from_handle(producer_handle);
    if (producer == nullptr) {
        return VE_TLS_INVALID;
    }

    jsize key_count = keys == nullptr ? 0 : env->GetArrayLength(keys);
    jsize value_count = values == nullptr ? 0 : env->GetArrayLength(values);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return VE_TLS_INVALID;
    }
    if (key_count != value_count) {
        return VE_TLS_INVALID;
    }

    ve_tls_kv * kvs = key_count == 0
        ? nullptr
        : static_cast<ve_tls_kv *>(std::calloc(static_cast<size_t>(key_count), sizeof(ve_tls_kv)));
    if (key_count > 0 && kvs == nullptr) {
        return VE_TLS_DROP_ERROR;
    }
    for (jsize i = 0; i < key_count; ++i) {
        jstring key = static_cast<jstring>(env->GetObjectArrayElement(keys, i));
        jstring value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            if (key != nullptr) {
                env->DeleteLocalRef(key);
            }
            if (value != nullptr) {
                env->DeleteLocalRef(value);
            }
            free_kv_storage(kvs, static_cast<size_t>(key_count));
            return VE_TLS_INVALID;
        }
        kvs[static_cast<size_t>(i)].key = duplicate_utf_string_or_empty(env, key);
        kvs[static_cast<size_t>(i)].value = duplicate_utf_string_or_empty(env, value);
        if (key != nullptr) {
            env->DeleteLocalRef(key);
        }
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
        if (kvs[static_cast<size_t>(i)].key == nullptr || kvs[static_cast<size_t>(i)].value == nullptr) {
            free_kv_storage(kvs, static_cast<size_t>(key_count));
            return VE_TLS_DROP_ERROR;
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            free_kv_storage(kvs, static_cast<size_t>(key_count));
            return VE_TLS_INVALID;
        }
    }

    ScopedUtfChars hash_key_chars(env, hash_key);
    const ve_tls_kv * kv_ptr = key_count == 0 ? nullptr : kvs;
    if (hash_key_chars.c_str() != nullptr && hash_key_chars.c_str()[0] != '\0') {
        ve_tls_result result = ve_tls_producer_add_log_kv_hashkey(
            producer,
            static_cast<int64_t>(log_time_ms),
            hash_key_chars.c_str(),
            kv_ptr,
            static_cast<size_t>(key_count),
            flush);
        free_kv_storage(kvs, static_cast<size_t>(key_count));
        return result;
    }
    ve_tls_result result = ve_tls_producer_add_log_kv(
        producer,
        static_cast<int64_t>(log_time_ms),
        kv_ptr,
        static_cast<size_t>(key_count),
        flush);
    free_kv_storage(kvs, static_cast<size_t>(key_count));
    return result;
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
    JniHttpBridgeState * state = forget_http_bridge_state(producer);
    CallbackState * callback_state = forget_callback_state(producer);

    ve_tls_android_runtime_options runtime = {};
    runtime.destroy_wait_ms = destroy_wait_ms;
    ve_tls_android_binding_before_destroy(producer, &runtime);
    destroy_callback_state(callback_state);
    destroy_http_bridge_state(state);
}
