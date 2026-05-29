// Top-level build file. Real configuration lives in app/build.gradle.kts and
// in the version catalog at gradle/libs.versions.toml.
//
// AGP 9.0+ bundles Kotlin support; the standalone
// `org.jetbrains.kotlin.android` plugin is no longer applied here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.benchmark) apply false
    alias(libs.plugins.detekt) apply false
}
