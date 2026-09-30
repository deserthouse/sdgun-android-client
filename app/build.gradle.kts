// SDGun 社区第三方客户端 — :app 模块
// WebView 壳 + Material 3 Expressive 设计系统 + 网页主题注入
// 非官方客户端：数据全部来自 bbs.sdgun.com.cn 触屏版，本应用不内置任何论坛资源

import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    // AGP 9.2+ auto-applies kotlin-android — do NOT apply manually
    id("org.jetbrains.kotlin.plugin.compose")
}

val APP_VERSION_NAME = "1.0.0"

android {
    namespace = "io.github.deserthouse.sdgun"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.deserthouse.sdgun"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = APP_VERSION_NAME
    }

    signingConfigs {
        create("release") {
            // keystore outside repo via ~/.gradle/gradle.properties:
            // SDGUN_STORE_FILE / SDGUN_STORE_PASS / SDGUN_KEY_ALIAS / SDGUN_KEY_PASS
            // absent -> falls back to debug keystore so CI/dev builds never break
            val storeFilePath = providers.gradleProperty("SDGUN_STORE_FILE").orNull
            storeFile = if (storeFilePath != null) file(storeFilePath)
                else file("C:/Users/deser/.android/debug.keystore")
            storePassword = providers.gradleProperty("SDGUN_STORE_PASS")
                .getOrElse("android")
            keyAlias = providers.gradleProperty("SDGUN_KEY_ALIAS")
                .getOrElse("androiddebugkey")
            keyPassword = providers.gradleProperty("SDGUN_KEY_PASS")
                .getOrElse("android")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ━━━ APK Output Naming: SDGun-vX.Y.Z-YYYYMMDD-HHMMSS.apk ━━━
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val buildTime = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
            output.outputFileName.set("SDGun-v$APP_VERSION_NAME-$buildTime.apk")
        }
    }
}

dependencies {
    // ━━━ AndroidX Core ━━━
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")

    // ━━━ Compose + Material 3 Expressive ━━━
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 1.5+ includes Expressive APIs
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("androidx.compose.material:material-icons-extended")

    // ━━━ Navigation ━━━
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // ━━━ Coroutines ━━━
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // ━━━ Debug ━━━
    debugImplementation("androidx.compose.ui:ui-tooling")
}
