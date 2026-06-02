// Self-instrumenting microbenchmark module.
//
// `androidx.benchmark:benchmark-junit4` runs each method N times,
// throws away warmup iterations, then reports min/median/p90/p99 in
// nanoseconds. Numbers are device-specific — the same benchmark
// gives different absolute values on different hardware — but they
// are stable enough on a single device to detect a 10..20 %
// regression between commits.
//
// Layout: a `com.android.library` whose ONLY purpose is hosting
// `src/androidTest/kotlin` benchmarks. The library compiles against
// `:app`'s source set so benchmarks can poke private internal
// helpers directly without an exported API surface.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.benchmark)
}

android {
    namespace = "com.midtano.otp.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        // benchmark-junit4 ships its own runner; using the AGP
        // default would skip the warmup / measurement phases.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    buildTypes {
        // The benchmark plugin auto-creates a "benchmark" build
        // type with R8 enabled — production-shaped bytecode is what
        // we want to measure. The `release` build type is
        // intentionally absent because we never ship this module.
        debug {
            enableUnitTestCoverage = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("androidTest") {
            java.srcDir("src/androidTest/kotlin")
        }
    }

    // Benchmarks must run on a debuggable APK so the framework can
    // disable the JIT mid-loop to keep numbers stable. The plugin
    // emits a warning if the variant doesn't satisfy this contract,
    // which is what we want during development.
    testBuildType = "debug"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // The :app classpath provides every type we benchmark — the
    // extractor primitives, the queue, the redactor, etc.
    androidTestImplementation(project(":app"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.benchmark.junit4)
    androidTestImplementation(libs.junit)
}
