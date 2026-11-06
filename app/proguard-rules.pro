# The sherpa-onnx native library reads these classes' fields by name over JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# commons-compress references optional codecs that are not on Android.
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
