// Top-level build file. Plugins are declared here (apply false) and applied in :app.
//
// AGP 9.x has built-in Kotlin support, so the standalone `org.jetbrains.kotlin.android`
// plugin must NOT be applied. Declaring the Compose compiler plugin here (version = Kotlin
// version) also puts the matching Kotlin Gradle Plugin on the build classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
