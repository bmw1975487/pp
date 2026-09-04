# Keep TensorFlow Lite Java/JNI entry points used by the on-device neural engine.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
