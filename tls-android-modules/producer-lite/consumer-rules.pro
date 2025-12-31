## Keep public API classes used by apps
-keep class com.volcengine.tls.android.producer.** { *; }
-keep class com.volcengine.service.tls.** { *; }
-keep class com.volcengine.model.tls.producer.** { *; }
-keep class com.volcengine.model.tls.request.** { *; }

## OkHttp/Okio protobuf
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.protobuf.** { *; }
