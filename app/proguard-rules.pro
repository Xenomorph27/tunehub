# R8 rules. Most are NewPipe's (https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro):
# the extractor runs YouTube's player JavaScript through Rhino, which works by reflection.

# readable stack traces from user reports are worth more than the few KB obfuscation saves
-dontobfuscate

## NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

## Rhino and its script engine
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## Our saved library (kotlinx.serialization)
-keep,includedescriptorclasses class com.tunehub.app.**$$serializer { *; }
-keepclassmembers class com.tunehub.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.tunehub.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
