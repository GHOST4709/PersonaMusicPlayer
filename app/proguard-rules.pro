# R8 / ProGuard rules for release builds.
#
# Most libraries (Retrofit, OkHttp, Room, Hilt/Dagger, WorkManager, Media3, Play services, Maps Compose,
# coroutines, DataStore) ship their own consumer rules. What is listed here is only what this app adds.
# After enabling minification for the first time, install the `releaseCheck` build and walk the device
# checklists in docs/TEST_PLAN.md: a missing keep rule shows up as a crash in a code path, not at build time.

# --- Readable crash reports: keep line numbers, hide the original file name. Upload mapping.txt with each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization (the manifest and weather DTOs are decoded through generated serializers) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.personabgm.**$$serializer { *; }
-keepclassmembers class com.example.personabgm.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.personabgm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit interfaces are used through reflection-created proxies; keep their method signatures ---
-keep interface com.example.personabgm.data.remote.ManifestApi { *; }
-keep interface com.example.personabgm.data.remote.WeatherApi { *; }
