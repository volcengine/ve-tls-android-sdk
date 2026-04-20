-keep class com.volcengine.model.tls.pb.** { *; }
 -keep class com.volcengine.model.tls.pb.** { *; }
 -keep class com.google.protobuf.** { *; }
 -dontwarn com.google.protobuf.**
-keep class com.volcengine.service.tls.** { *; }
-keep class com.volcengine.model.tls.producer.** { *; }
-keep class com.volcengine.http.** { *; }
-keep class com.volcengine.util.** { *; }
-keep class com.volcengine.model.tls.exception.LogException { *; }

# OkHttp / Okio (prevent obfuscation of internal reflection paths)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature,*Annotation*
 -keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Fastjson (reflection on field names)
-keep class com.alibaba.fastjson.** { *; }
-keepclassmembers class ** { @com.alibaba.fastjson.annotation.JSONField *; }
-keepclassmembers class com.volcengine.model.tls.** { *; }

# Suppress optional platform integrations referenced by OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Suppress optional codecs referenced by fastjson not available on Android
-dontwarn java.awt.**
-dontwarn javax.money.**
-dontwarn org.javamoney.**
-dontwarn org.joda.time.**
-dontwarn org.joda.time.format.**


# LZ4
-keep class net.jpountz.** { *; }
-dontwarn net.jpountz.**
-dontwarn sun.misc.**

# Suppress Swagger/Springfox optional serializers referenced by fastjson
-dontwarn springfox.documentation.**

# Suppress JAX-RS/Jersey service files referenced from META-INF/services
-dontwarn javax.ws.rs.**
-dontwarn org.glassfish.jersey.**

# Suppress Spring/Servlet/Retrofit optional integrations referenced by fastjson
-dontwarn javax.servlet.**
-dontwarn javax.servlet.http.**
-dontwarn org.springframework.**
-dontwarn org.springframework.core.**
-dontwarn org.springframework.http.**
-dontwarn org.springframework.http.converter.**
-dontwarn org.springframework.http.server.**
-dontwarn org.springframework.messaging.**
-dontwarn org.springframework.util.**
-dontwarn org.springframework.web.**
-dontwarn retrofit2.**
