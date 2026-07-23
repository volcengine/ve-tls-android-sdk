# Producer-native public API and JNI bridge entry points
-keep class com.volcengine.tls.android.producer.** { *; }
-keepclassmembers class * {
    public <methods>;
    public <fields>;
}

# Preserve JNI signatures if native method declarations are added in Task 4.
-keepclassmembers class * {
    native <methods>;
}

# Keep core model classes passed through JNI data structures.
-keep class com.volcengine.model.tls.** { *; }
-dontwarn java.lang.invoke.*
