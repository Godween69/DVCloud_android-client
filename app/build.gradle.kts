plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace = "ru.dvbstudio.dvcloud"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.dvbstudio.dvcloud"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Конфигурация сборки: URL сервера можно переопределить
        buildConfigField("String", "API_BASE_URL", "\"https://cloud.dvbstudio.ru\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Ядро Android
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Навигация
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Сеть: OkHttp + kotlinx-serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Корутины для асинхронной работы
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Хранилище настроек (DataStore вместо SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Фоновая работа (WorkManager для автовыгрузки)
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // Безопасность: Keystore API (напрямую, без deprecated security-crypto)
    // Android Keystore встроен в Android, дополнительных зависимостей нет
}
