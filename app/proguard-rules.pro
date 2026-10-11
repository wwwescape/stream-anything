# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable

# Strip verbose/debug/info logging from release builds (smaller dex, no diagnostic strings —
# e.g. the video links being resolved — written to logcat). Warnings and errors are kept.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
