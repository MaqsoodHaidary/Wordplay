plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.wordplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wordplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }   // no signingConfig -> unsigned APK
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
