plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.stoa.droid"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.stoa.droid"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // The committed corpus ships as-is in the APK.
    sourceSets
        .getByName("main")
        .assets.directories
        .add(rootProject.file("corpus").path)
}

dependencies {
    implementation(project(":core:rag"))
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
}
