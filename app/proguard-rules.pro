# Aven R8 rules.
# Keep llama.cpp JNI entry points (added in Phase 5).
-keepclasseswithmembers class com.aven.ai.ai.local.** { native <methods>; }
-dontwarn org.jetbrains.annotations.**
# kotlinx-serialization keep rules (generated for @Serializable classes by the plugin)