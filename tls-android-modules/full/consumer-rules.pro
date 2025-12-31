# Keep public API and internal services used by TLS client
-keep class com.volcengine.service.tls.** { *; }
-keep class com.volcengine.model.tls.** { *; }
-keep class com.volcengine.http.** { *; }
-keep class com.volcengine.util.** { *; }
-keep class com.volcengine.model.tls.exception.LogException { *; }

# OkHttp/Okio network stack
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature,*Annotation*

# Fastjson core (reflection on field names)
-keep class com.alibaba.fastjson.** { *; }
-keepclassmembers class ** {
    @com.alibaba.fastjson.annotation.JSONField *;
}
