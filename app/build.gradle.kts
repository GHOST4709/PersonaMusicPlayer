import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Note: no kotlin-android plugin (AGP 9 built-in Kotlin).
}

// The Maps API key lives in local.properties (never committed): MAPS_API_KEY=your-key
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "").trim()

android {
    namespace = "com.example.personabgm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.personabgm"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Track manifest location (spec 7.1: configurable per build type). Replace the placeholder
        // with your own host. Must be https; cleartext traffic is disabled.
        buildConfigField("String", "MANIFEST_URL", "\"https://cdn.example.com/manifest.json\"")

        // Google Maps key for the zone map. Without one the Zones screen shows a note instead of a blank map.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_API_KEY", mapsApiKey.isNotEmpty().toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Minified exactly like release, but signed with the debug key so it can be installed and tested.
        // Use this to verify the keep rules before shipping: `./gradlew :app:installReleaseCheck`.
        create("releaseCheck") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room: export schemas so migrations can be tested (spec 7.2: no destructive fallback).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Audio (Phase 3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.work.runtime.ktx)

    // Location + geofencing (Phase 4), map (Phase 5)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Kotlin libs / network
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
