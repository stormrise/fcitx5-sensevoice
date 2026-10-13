# sherpa-onnx loads libsherpa-onnx-jni.so, which looks up its Java classes and methods by name.
# The AAR ships an empty proguard.txt, so keep the whole package here.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
