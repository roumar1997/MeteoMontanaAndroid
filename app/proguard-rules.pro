# Reglas R8 del proyecto. Mantener la lista corta: la mayoría de libs
# (Compose, Hilt, Firebase, Coil, Ktor/OkHttp) ya traen sus consumer rules.

# Stacktraces legibles en producción (R8 genera mapping.txt para des-ofuscar).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx-serialization — los serializers se buscan por reflexión.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# Nuestros DTOs serializables: conservar nombres de campos (mapean al JSON
# del backend tal cual; sin esto R8 los renombra y la (de)serialización rompe).
-keepclassmembers class com.meteomontana.android.data.api.** { <fields>; }

# MapLibre usa JNI — los nombres de clases/métodos nativos no pueden cambiar.
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# SQLDelight driver
-keep class app.cash.sqldelight.** { *; }
