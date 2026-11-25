# kotlinx.serialization: keep generated serializers for @Serializable classes (card JSON, routes).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.jdevsappstudio.kept.** {
    *** Companion;
}
-keepclasseswithmembers class com.jdevsappstudio.kept.** {
    kotlinx.serialization.KSerializer serializer(...);
}
