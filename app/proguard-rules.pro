-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.albatora.app.model.** { *; }
-dontwarn top.yukonga.miuix.**
