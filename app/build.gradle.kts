plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.dvbstudio.dvcloud"
    compileSdk = 35          // по какой версии Android проверяется код

    defaultConfig {
        applicationId = "ru.dvbstudio.dvcloud"
        minSdk = 26          // самый старый Android, на который ставится (8.0)
        targetSdk = 35       // под какой Android рассчитано поведение
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
