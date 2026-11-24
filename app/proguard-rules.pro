-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses

-keep class com.sonora.music.** { *; }
-keep class androidx.media3.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class coil.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
