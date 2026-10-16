-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
-keep class org.vosk.** { *; }
-keep class org.kaldi.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
