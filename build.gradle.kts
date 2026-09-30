// SDGun 社区第三方客户端 — 根构建脚本
// Gradle 9.5.1 + AGP 9.2.1 + Kotlin 2.2.10 + compileSdk 37（对齐本机 Android 工具链基线）

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
