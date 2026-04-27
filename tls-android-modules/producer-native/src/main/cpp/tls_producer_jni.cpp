#include <jni.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <pthread.h>

extern "C" {
#include "ve_tls_android_binding.h"
}

namespace {

constexpr bool kAddLogPerfEnabled = false;

struct JniHttpBridgeState;
struct CallbackState;
struct HttpBridgeStateNode;
struct CallbackStateNode;

JavaVM * g_jvm = nullptr;
pthread_mutex_t g_http_bridge_mutex = PTHREAD_MUTEX_INITIALIZER;
HttpBridgeStateNode * g_http_bridges = nullptr;
pthread_mutex_t g_callback_mutex = PTHREAD_MUTEX_INITIALIZER;
CallbackStateNode * g_callback_states = nullptr;
std::atomic<int64_t> g_add_log_utf_dup_wall_ns{0};
std::atomic<int64_t> g_add_log_producer_wall_ns{0};

int64_t monotonic_now_ns() {
    timespec ts = {};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + static_cast<int64_t>(ts.tv_nsec);
}

void reset_add_log_perf_stats() {
    g_add_log_utf_dup_wall_ns.store(0, std::memory_order_relaxed);
    g_add_log_producer_wall_ns.store(0, std::memory_order_relaxed);
}

void snapshot_add_log_perf_stats(int64_t * utf_dup_wall_ns, int64_t * producer_wall_ns) {
    if (utf_dup_wall_ns != nullptr) {
        *utf_dup_wall_ns = g_add_log_utf_dup_wall_ns.load(std::memory_order_relaxed);
    }
    if (producer_wall_ns != nullptr) {
        *producer_wall_ns = g_add_log_producer_wall_ns.load(std::memory_order_relaxed);
    }
}

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
    JNIEnv * env;
    int attached;
};

struct JniHttpBridgeState {
    jobject bridge;
    jclass bridge_class;
    jclass request_class;
    jclass response_class;
    jmethodID request_ctor;
    jmethodID execute;
    jmethodID response_get_status_code;
    jmethodID response_get_body;
    jmethodID response_get_request_id;
};

struct CallbackState {
    jobject dispatcher;
    jclass dispatcher_class;
    jmethodID dispatch;
};

struct HttpBridgeStateNode {
    ve_tls_producer * producer;
    JniHttpBridgeState * state;
    HttpBridgeStateNode * next;
};

struct CallbackStateNode {
    ve_tls_producer * producer;
    CallbackState * state;
    CallbackStateNode * next;
};

ve_tls_producer * producer_from_handle(jlong producer_handle) {
    return reinterpret_cast<ve_tls_producer *>(producer_handle);
}

ve_tls_android_compress_type compress_type_from_java(jint compress_type) {
    switch (compress_type) {
        case 1:
            return VE_TLS_ANDROID_COMPRESS_NONE;
        case 2:
            return VE_TLS_ANDROID_COMPRESS_LZ4;
        case 0:
        default:
            return VE_TLS_ANDROID_COMPRESS_UNSPECIFIED;
    }
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

int duplicate_log_tag_arrays(JNIEnv * env, jobjectArray keys, jobjectArray values, jint tag_count, ve_tls_kv ** out_tags) {
    if (out_tags == nullptr) {
        return -1;
    }
    *out_tags = nullptr;
    if (tag_count <= 0) {
        return 0;
    }
    if (keys == nullptr || values == nullptr) {
        return -1;
    }

    jsize key_count = env->GetArrayLength(keys);
    jsize value_count = env->GetArrayLength(values);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return -1;
    }
    if (key_count != value_count || key_count != tag_count) {
        return -1;
    }

    ve_tls_kv * tags = static_cast<ve_tls_kv *>(std::calloc(static_cast<size_t>(tag_count), sizeof(ve_tls_kv)));
    if (tags == nullptr) {
        return -1;
    }

    for (jint i = 0; i < tag_count; ++i) {
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
            free_kv_storage(tags, static_cast<size_t>(tag_count));
            return -1;
        }
        tags[static_cast<size_t>(i)].key = duplicate_utf_string_or_empty(env, key);
        tags[static_cast<size_t>(i)].value = duplicate_utf_string_or_empty(env, value);
        if (key != nullptr) {
            env->DeleteLocalRef(key);
        }
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
        if (tags[static_cast<size_t>(i)].key == nullptr || tags[static_cast<size_t>(i)].value == nullptr) {
            free_kv_storage(tags, static_cast<size_t>(tag_count));
            return -1;
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            free_kv_storage(tags, static_cast<size_t>(tag_count));
            return -1;
        }
    }
    *out_tags = tags;
    return 0;
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
    std::free(state);
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

    auto * state = static_cast<JniHttpBridgeState *>(std::calloc(1, sizeof(JniHttpBridgeState)));
    if (state == nullptr) {
        return nullptr;
    }

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
    if (state->response_get_status_code == nullptr
        || state->response_get_body == nullptr
        || state->response_get_request_id == nullptr) {
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
    std::free(state);
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

    auto * state = static_cast<CallbackState *>(std::calloc(1, sizeof(CallbackState)));
    if (state == nullptr) {
        return nullptr;
    }
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
    {
        ScopedLocalFrame frame(env, 16);
        if (frame.active()) {
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
            } else {
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
            }
        }
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
    int rc = -1;
    {
        ScopedLocalFrame frame(env, 32);
        if (!frame.active()) {
            resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
            resp->transport_code = -1;
            resp->transport_retryable = 1;
            resp->error_code = ::strdup("JavaLocalFrameError");
            resp->error_message = ::strdup("failed to allocate JNI local frame");
        } else {
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
            } else {
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
                } else {
                    jobject response = env->CallObjectMethod(state->bridge, state->execute, request);
                    if (env->ExceptionCheck()) {
                        jthrowable throwable = env->ExceptionOccurred();
                        env->ExceptionClear();
                        set_bridge_error(env, resp, throwable);
                    } else if (response == nullptr) {
                        resp->transport_kind = VE_TLS_TRANSPORT_GENERIC;
                        resp->transport_code = -1;
                        resp->transport_retryable = 1;
                        resp->error_code = ::strdup("JavaHttpBridgeEmptyResponse");
                        resp->error_message = ::strdup("java http bridge returned null response");
                    } else {
                        resp->status_code = env->CallIntMethod(response, state->response_get_status_code);
                        jbyteArray response_body = static_cast<jbyteArray>(env->CallObjectMethod(response, state->response_get_body));
                        jstring request_id = static_cast<jstring>(env->CallObjectMethod(response, state->response_get_request_id));
                        if (env->ExceptionCheck()) {
                            jthrowable throwable = env->ExceptionOccurred();
                            env->ExceptionClear();
                            set_bridge_error(env, resp, throwable);
                        } else {
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
                                    } else {
                                        env->GetByteArrayRegion(response_body, 0, body_size, reinterpret_cast<jbyte *>(resp->body));
                                        resp->body_size = static_cast<size_t>(body_size);
                                    }
                                }
                            }

                            if (resp->error_code == nullptr && resp->error_message == nullptr) {
                                resp->request_id = duplicate_utf_string(env, request_id);
                                resp->transport_kind = VE_TLS_TRANSPORT_NONE;
                                resp->transport_code = 0;
                                resp->transport_retryable = 0;
                                rc = 0;
                            }
                        }
                    }
                }
            }
        }
    }
    release_thread_env(&thread_env);
    return rc;
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
    jint retry_max_attempts,
    jint retry_total_timeout_ms,
    jint retry_initial_interval_ms,
    jint retry_max_interval_ms,
    jint connect_timeout_ms,
    jint request_timeout_ms,
    jboolean enable_time_ns,
    jint destroy_wait_ms,
    jint destroy_flusher_wait_ms,
    jint destroy_sender_wait_ms,
    jboolean destroy_wait_split_enabled,
    jobjectArray log_tag_keys,
    jobjectArray log_tag_values,
    jint log_tag_count,
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
    size_t runtime_tag_count = log_tag_count <= 0 ? 0 : static_cast<size_t>(log_tag_count);
    ve_tls_kv * log_tags = nullptr;
    if (duplicate_log_tag_arrays(env, log_tag_keys, log_tag_values, log_tag_count, &log_tags) != 0) {
        destroy_http_bridge_state(http_bridge_state);
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
    config_view.log_tags = log_tags;
    config_view.log_tag_count = runtime_tag_count;
    config_view.compress_type = compress_type_from_java(compress_type);
    config_view.send_thread_count = send_thread_count;
    config_view.log_bytes_per_package = packet_log_bytes;
    config_view.log_count_per_package = packet_log_count;
    config_view.flush_interval_ms = packet_timeout_ms;
    config_view.max_buffer_bytes = max_buffer_limit;
    config_view.retry_max_attempts = retry_max_attempts;
    config_view.retry_total_timeout_ms = retry_total_timeout_ms;
    config_view.retry_initial_interval_ms = retry_initial_interval_ms;
    config_view.retry_max_interval_ms = retry_max_interval_ms;
    config_view.connect_timeout_ms = connect_timeout_ms;
    config_view.request_timeout_ms = request_timeout_ms;
    config_view.enable_time_ns = enable_time_ns ? 1 : 0;
    config_view.use_persistent = persistent ? 1 : 0;
    config_view.persistent_file_path = persistent_file_path_chars.c_str();
    config_view.max_persistent_log_count = persistent_max_log_count;
    config_view.max_persistent_file_size = persistent_max_file_size;
    config_view.max_persistent_file_count = persistent_max_file_count;
    config_view.force_flush_disk = persistent_force_flush ? 1 : 0;
    config_view.destroy_wait_ms = destroy_wait_ms;
    config_view.destroy_flusher_wait_ms = destroy_flusher_wait_ms;
    config_view.destroy_sender_wait_ms = destroy_sender_wait_ms;
    config_view.destroy_wait_split_enabled = destroy_wait_split_enabled ? 1 : 0;
    http_client_bridge.do_request = bridge_do_request;
    http_client_bridge.free_response = bridge_free_response;
    http_client_bridge.user_data = http_bridge_state;
    config_view.http_client = &http_client_bridge;

    ve_tls_config config;
    ve_tls_android_runtime_options runtime = {};
    if (ve_tls_android_binding_build_config(&config_view, &config, &runtime) != VE_TLS_OK) {
        free_kv_storage(log_tags, runtime_tag_count);
        destroy_http_bridge_state(http_bridge_state);
        return 0;
    }
    ve_tls_producer * producer = ve_tls_producer_create(&config);
    if (producer == nullptr) {
        free_kv_storage(log_tags, runtime_tag_count);
        destroy_http_bridge_state(http_bridge_state);
        return 0;
    }
    free_kv_storage(log_tags, runtime_tag_count);
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

    constexpr size_t kStackPairLimit = 16;
    size_t pair_count = static_cast<size_t>(key_count);
    jstring key_refs_stack[kStackPairLimit] = {};
    jstring value_refs_stack[kStackPairLimit] = {};
    const char * key_chars_stack[kStackPairLimit] = {};
    const char * value_chars_stack[kStackPairLimit] = {};
    size_t key_lens_stack[kStackPairLimit] = {};
    size_t value_lens_stack[kStackPairLimit] = {};
    jstring * key_refs = key_refs_stack;
    jstring * value_refs = value_refs_stack;
    const char ** key_chars = key_chars_stack;
    const char ** value_chars = value_chars_stack;
    size_t * key_lens = key_lens_stack;
    size_t * value_lens = value_lens_stack;
    if (pair_count > kStackPairLimit) {
        key_refs = static_cast<jstring *>(std::calloc(pair_count, sizeof(jstring)));
        value_refs = static_cast<jstring *>(std::calloc(pair_count, sizeof(jstring)));
        key_chars = static_cast<const char **>(std::calloc(pair_count, sizeof(const char *)));
        value_chars = static_cast<const char **>(std::calloc(pair_count, sizeof(const char *)));
        key_lens = static_cast<size_t *>(std::calloc(pair_count, sizeof(size_t)));
        value_lens = static_cast<size_t *>(std::calloc(pair_count, sizeof(size_t)));
        if (key_refs == nullptr || value_refs == nullptr || key_chars == nullptr ||
            value_chars == nullptr || key_lens == nullptr || value_lens == nullptr) {
            std::free(key_refs);
            std::free(value_refs);
            std::free(key_chars);
            std::free(value_chars);
            std::free(key_lens);
            std::free(value_lens);
            return VE_TLS_DROP_ERROR;
        }
    }
    auto release_strings = [&]() {
        for (size_t i = 0; i < pair_count; ++i) {
            if (key_refs[i] != nullptr) {
                if (key_chars[i] != nullptr) {
                    env->ReleaseStringUTFChars(key_refs[i], key_chars[i]);
                }
                env->DeleteLocalRef(key_refs[i]);
            }
            if (value_refs[i] != nullptr) {
                if (value_chars[i] != nullptr) {
                    env->ReleaseStringUTFChars(value_refs[i], value_chars[i]);
                }
                env->DeleteLocalRef(value_refs[i]);
            }
        }
        if (pair_count > kStackPairLimit) {
            std::free(key_refs);
            std::free(value_refs);
            std::free(key_chars);
            std::free(value_chars);
            std::free(key_lens);
            std::free(value_lens);
        }
    };
    int64_t utf_dup_start_ns = kAddLogPerfEnabled ? monotonic_now_ns() : 0;
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
            release_strings();
            return VE_TLS_INVALID;
        }
        size_t idx = static_cast<size_t>(i);
        key_refs[idx] = key;
        value_refs[idx] = value;
        if (key != nullptr) {
            key_chars[idx] = env->GetStringUTFChars(key, nullptr);
            if (key_chars[idx] == nullptr) {
                env->ExceptionClear();
                release_strings();
                return VE_TLS_DROP_ERROR;
            }
            key_lens[idx] = std::strlen(key_chars[idx]);
        } else {
            key_chars[idx] = "";
            key_lens[idx] = 0;
        }
        if (key != nullptr) {
            key = nullptr;
        }
        if (value != nullptr) {
            value_chars[idx] = env->GetStringUTFChars(value, nullptr);
            if (value_chars[idx] == nullptr) {
                env->ExceptionClear();
                release_strings();
                return VE_TLS_DROP_ERROR;
            }
            value_lens[idx] = std::strlen(value_chars[idx]);
        } else {
            value_chars[idx] = "";
            value_lens[idx] = 0;
        }
        if (value != nullptr) {
            value = nullptr;
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            release_strings();
            return VE_TLS_INVALID;
        }
    }
    if (kAddLogPerfEnabled) {
        g_add_log_utf_dup_wall_ns.fetch_add(monotonic_now_ns() - utf_dup_start_ns, std::memory_order_relaxed);
    }

    ScopedUtfChars hash_key_chars(env, hash_key);
    int64_t producer_add_log_start_ns = kAddLogPerfEnabled ? monotonic_now_ns() : 0;
    if (hash_key_chars.c_str() != nullptr && hash_key_chars.c_str()[0] != '\0') {
        ve_tls_result result = ve_tls_producer_add_log_with_len_hashkey(
            producer,
            static_cast<int64_t>(log_time_ms),
            hash_key_chars.c_str(),
            key_chars,
            key_lens,
            value_chars,
            value_lens,
            pair_count,
            flush);
        if (kAddLogPerfEnabled) {
            g_add_log_producer_wall_ns.fetch_add(monotonic_now_ns() - producer_add_log_start_ns, std::memory_order_relaxed);
        }
        release_strings();
        return result;
    }
    ve_tls_result result = ve_tls_producer_add_log_with_len(
        producer,
        static_cast<int64_t>(log_time_ms),
        key_chars,
        key_lens,
        value_chars,
        value_lens,
        pair_count,
        flush);
    if (kAddLogPerfEnabled) {
        g_add_log_producer_wall_ns.fetch_add(monotonic_now_ns() - producer_add_log_start_ns, std::memory_order_relaxed);
    }
    release_strings();
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeResetAddLogPerfStats(
    JNIEnv *,
    jclass
) {
    reset_add_log_perf_stats();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeSnapshotAddLogPerfStats(
    JNIEnv * env,
    jclass
) {
    int64_t utf_dup_wall_ns = 0;
    int64_t producer_wall_ns = 0;
    int64_t key_lens_wall_ns = 0;
    int64_t persistent_path_wall_ns = 0;
    int64_t queue_path_wall_ns = 0;
    int64_t tls_batch_builder_wall_ns = 0;
    int64_t tls_batch_flush_wall_ns = 0;
    int64_t tls_batch_merge_wall_ns = 0;
    int64_t persistent_builder_wall_ns = 0;
    int64_t persistent_append_wall_ns = 0;
    int64_t persistent_enqueue_wall_ns = 0;
    int64_t persistent_enqueue_wait_buffer_wall_ns = 0;
    int64_t persistent_enqueue_builder_init_wall_ns = 0;
    int64_t persistent_enqueue_ingress_push_wall_ns = 0;
    int64_t persistent_enqueue_ingress_wait_queue_wall_ns = 0;
    int64_t persistent_enqueue_ingress_bookkeeping_wall_ns = 0;
    int64_t persistent_enqueue_ingress_notify_wall_ns = 0;
    int64_t tls_batch_preamble_wall_ns = 0;
    int64_t tls_batch_bookkeeping_wall_ns = 0;
    int64_t persistent_append_encode_wall_ns = 0;
    int64_t persistent_append_store_wall_ns = 0;
    int64_t tls_batch_flush_shell_wall_ns = 0;
    int64_t tls_batch_merge_shell_wall_ns = 0;
    int64_t persistent_append_sizing_wall_ns = 0;
    int64_t persistent_append_capacity_wall_ns = 0;
    int64_t persistent_append_post_store_wall_ns = 0;
    int64_t persistent_append_retry_shell_wall_ns = 0;
    int64_t persistent_append_precheck_wall_ns = 0;
    int64_t persistent_append_meta_wall_ns = 0;
    int64_t persistent_append_retry_persistent_mutex_wall_ns = 0;
    int64_t persistent_append_retry_sleep_wall_ns = 0;
    int64_t persistent_append_retry_producer_mutex_wall_ns = 0;
    int64_t persistent_append_store_rotate_wall_ns = 0;
    int64_t persistent_append_store_write_wall_ns = 0;
    int64_t persistent_append_retry_producer_lock_wall_ns = 0;
    int64_t persistent_append_retry_producer_notify_wall_ns = 0;
    snapshot_add_log_perf_stats(&utf_dup_wall_ns, &producer_wall_ns);

    jlong values[36] = {
        static_cast<jlong>(utf_dup_wall_ns / 1000000LL),
        static_cast<jlong>(producer_wall_ns / 1000000LL),
        static_cast<jlong>(key_lens_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_path_wall_ns / 1000000LL),
        static_cast<jlong>(queue_path_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_builder_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_flush_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_merge_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_builder_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_wait_buffer_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_builder_init_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_ingress_push_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_ingress_wait_queue_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_ingress_bookkeeping_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_enqueue_ingress_notify_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_preamble_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_bookkeeping_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_encode_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_store_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_flush_shell_wall_ns / 1000000LL),
        static_cast<jlong>(tls_batch_merge_shell_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_sizing_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_capacity_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_post_store_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_shell_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_precheck_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_meta_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_persistent_mutex_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_sleep_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_producer_mutex_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_store_rotate_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_store_write_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_producer_lock_wall_ns / 1000000LL),
        static_cast<jlong>(persistent_append_retry_producer_notify_wall_ns / 1000000LL)
    };
    jlongArray array = env->NewLongArray(36);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetLongArrayRegion(array, 0, 36, values);
    return array;
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
    jint destroy_wait_ms,
    jint destroy_flusher_wait_ms,
    jint destroy_sender_wait_ms,
    jboolean destroy_wait_split_enabled
) {
    ve_tls_producer * producer = producer_from_handle(producer_handle);
    if (producer == nullptr) {
        return;
    }
    JniHttpBridgeState * state = forget_http_bridge_state(producer);
    CallbackState * callback_state = forget_callback_state(producer);

    ve_tls_android_runtime_options runtime = {};
    runtime.destroy_wait_ms = destroy_wait_ms;
    runtime.destroy_flusher_wait_ms = destroy_flusher_wait_ms;
    runtime.destroy_sender_wait_ms = destroy_sender_wait_ms;
    runtime.destroy_wait_split_enabled = destroy_wait_split_enabled ? 1 : 0;
    ve_tls_android_binding_before_destroy(producer, &runtime);
    destroy_callback_state(callback_state);
    destroy_http_bridge_state(state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeResetSenderPerfStats(
    JNIEnv *,
    jclass
) {
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_volcengine_tls_android_producer_internal_JniNativeProducerBridge_nativeSnapshotSenderPerfStats(
    JNIEnv * env,
    jclass
) {
    int64_t send_count = 0;
    int64_t send_wall_ns = 0;
    int64_t sign_wall_ns = 0;
    int64_t http_wall_ns = 0;
    int64_t manager_task_count = 0;
    int64_t manager_build_task_wall_ns = 0;
    int64_t manager_prepare_task_wall_ns = 0;
    int64_t manager_compress_wall_ns = 0;
    int64_t manager_push_task_wall_ns = 0;

    jlong values[9];
    values[0] = static_cast<jlong>(send_count);
    values[1] = static_cast<jlong>(send_wall_ns / 1000000LL);
    values[2] = static_cast<jlong>(sign_wall_ns / 1000000LL);
    values[3] = static_cast<jlong>(http_wall_ns / 1000000LL);
    values[4] = static_cast<jlong>(manager_task_count);
    values[5] = static_cast<jlong>(manager_build_task_wall_ns / 1000000LL);
    values[6] = static_cast<jlong>(manager_prepare_task_wall_ns / 1000000LL);
    values[7] = static_cast<jlong>(manager_compress_wall_ns / 1000000LL);
    values[8] = static_cast<jlong>(manager_push_task_wall_ns / 1000000LL);
    jlongArray array = env->NewLongArray(9);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetLongArrayRegion(array, 0, 9, values);
    return array;
}
