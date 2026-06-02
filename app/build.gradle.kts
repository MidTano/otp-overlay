// :app — single Android application module, written entirely in
// Kotlin.
//
// Note: AGP 9.0+ no longer needs the separate
// `org.jetbrains.kotlin.android` plugin — Kotlin support ships with
// AGP itself. The Kotlin version is still pinned in
// `gradle/libs.versions.toml` (used by the kotlin-stdlib runtime
// dependency the AGP plugin pulls in transitively).
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    jacoco
}

// ── Release-signing resolution ──────────────────────────────────────
//
// Order: env vars > gradle properties > keystore.properties at repo
// root or module root. Missing material falls back to the debug
// signing config so contributors without the production keystore
// can still run `:app:assembleRelease` end-to-end.
val keystoreProps = Properties()
val keystorePropsFile = listOf(
    rootProject.file("keystore.properties"),
    file("keystore.properties"),
).firstOrNull { it.exists() }
keystorePropsFile?.inputStream()?.use { keystoreProps.load(it) }

fun resolveCredential(envName: String, propName: String, keystoreKey: String): String? =
    System.getenv(envName)
        ?: (project.findProperty(propName) as String?)
        ?: keystoreProps.getProperty(keystoreKey)

val releaseStorePath: String? = resolveCredential(
    "RELEASE_KEYSTORE_PATH",
    "RELEASE_KEYSTORE_PATH",
    "storeFile",
)
val releaseStorePass: String? = resolveCredential(
    "RELEASE_KEYSTORE_PASSWORD",
    "RELEASE_KEYSTORE_PASSWORD",
    "storePassword",
)
val releaseKeyAlias: String? = resolveCredential(
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_ALIAS",
    "keyAlias",
)
val releaseKeyPass: String? = resolveCredential(
    "RELEASE_KEY_PASSWORD",
    "RELEASE_KEY_PASSWORD",
    "keyPassword",
) ?: releaseStorePass

val hasReleaseSigning: Boolean = releaseStorePath != null &&
    file(releaseStorePath).exists() &&
    releaseStorePass != null &&
    releaseKeyAlias != null

android {
    namespace = "com.midtano.otp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.midtano.otp"
        minSdk = 23
        targetSdk = 36
        // versionCode follows the versionName encoding: MAJOR*100000
        // + MINOR*1000 + PATCH (so 1.0.0 → 100000). This keeps
        // Play Store's monotonic versionCode requirement satisfied
        // and stays aligned with the human-readable name.
        versionCode = 101000
        versionName = "1.1.0"

        // Use AndroidX test runner so connectedAndroidTest can
        // bootstrap Espresso / UiAutomator instrumentation against
        // a real device.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Reproducible BuildConfig fields:
        //   • BUILD_ID   — short git SHA of HEAD, or "local" outside a
        //                  checkout.
        //   • BUILD_TIME — wall clock at build, rounded to the hour so
        //                  Gradle's input-snapshot stays stable for
        //                  rebuilds within the same hour. Honours
        //                  SOURCE_DATE_EPOCH for reproducible release
        //                  builds (set in release.yml from the tag's
        //                  commit timestamp).
        //
        // The `git rev-parse` invocation is wrapped in `providers.exec`
        // and the result is captured up-front into a single value so
        // the configuration cache can serialise it without re-executing
        // git every configuration phase.
        val gitProvider = providers.exec {
            commandLine("git", "rev-parse", "--short=8", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() }
        val buildId = gitProvider.getOrElse("").ifEmpty { "local" }

        val epochEnv: String? = System.getenv("SOURCE_DATE_EPOCH")
        val buildDate = if (!epochEnv.isNullOrEmpty()) {
            Date(epochEnv.toLong() * 1000L)
        } else {
            Date()
        }
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:00").format(buildDate)

        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
    }

    buildFeatures {
        buildConfig = true
        // viewBinding intentionally OFF for now: AGP 9.2's resource
        // merger crashes on several of the project's layouts when
        // viewBinding is enabled (NPE inside MergeResources at
        // processSingleFile on layouts that ship a comment before
        // the root tag). The Activities and binders still use
        // findViewById<T>(R.id.X), which is type-safe enough for
        // the current scope. Re-evaluate when AGP 9.3 is out.
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Sources live under kotlin/ — AGP 9 + Kotlin plugin pick up
    // src/<variant>/kotlin automatically, so an explicit sourceSets
    // block is not needed. The previous srcDirs(vararg) form is
    // deprecated under Gradle 10 anyway.

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Suffix the package id so a debug install can sit next
            // to a release one on the same device. Final user-facing
            // package id stays `com.midtano.otp`.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // Enable test-coverage instrumentation so JaCoCo can
            // observe execution data from the debug unit-test run.
            // Off in release because the instrumentation hooks bloat
            // the APK and trip ProGuard heuristics.
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    androidResources {
        noCompress += listOf("wav")
    }

    lint {
        abortOnError = true
        textReport = true
        // textOutput intentionally omitted — AGP writes to the
        // default `app/build/reports/lint-results-<variant>.txt`
        // path, which already gets uploaded as a CI artifact.
        // Setting an explicit path here used to route through a
        // ReportingExtension API that's deprecated under Gradle 9.
        disable += listOf(
            "AppBundleLocaleChanges",
            // The project tracks compileSdk/targetSdk together with
            // the AGP version. Bumping to the next API level needs to
            // wait for AGP to ship matching support — until then the
            // "newer SDK available" hint from lint is just noise.
            "OldTargetApi",
            "GradleDependency",
            // The benchmark module deliberately exercises the
            // `@VisibleForTesting` clearForTest()/markShownAtForTest()
            // surface on OtpDeduplicator so the steady-state dedup
            // cache doesn't poison successive timing runs. Production
            // call-sites are gated by the `:benchmark` Gradle module
            // boundary; lint can't see that and flags the call as a
            // visibility violation.
            "VisibleForTests",
        )
        // Promote select hygiene checks from warning to error so
        // any regression aborts the CI build instead of slipping in.
        error += listOf(
            "SetTextI18n",
            "UnusedResources",
        )
    }

    // Resource packaging: drop META-INF cruft from third-party JARs
    // so the APK doesn't ship duplicate licence files.
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

// ── Output APK naming ─────────────────────────────────────────────────
//
// Renames the artefacts so the user-facing file is
// `otp-overlay-<version>.apk`. `versionNameSuffix="-debug"` already
// encodes the build-type suffix into versionName for debug builds, so
// we read the variant's versionName as-is.
//
// Uses the modern AndroidComponentsExtension API (the legacy
// `applicationVariants` collection was removed in AGP 9.0).
androidComponents {
    onVariants(selector().all()) { variant ->
        val versionName = variant.outputs.firstOrNull {
            it.outputType.name == "SINGLE"
        }?.versionName?.get() ?: android.defaultConfig.versionName
        variant.outputs.forEach { output ->
            output.outputFileName.set("otp-overlay-$versionName.apk")
        }
    }
}

// AGP 9.0+ exposes Kotlin compiler options through the dedicated
// `kotlin { compilerOptions {} }` block (the old
// `android.kotlinOptions` extension was removed). Pin the JVM target
// to 21 and forward our project-wide compiler flags.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.palette)
    implementation(libs.lottie)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)

    // ── Unit tests (pure JVM, no Android runtime) ────────────────
    // Tests under app/src/test cover pure-function modules:
    // extractor primitives, NotificationFilter / NotificationBodyBuilder,
    // PhraseListStore, OverlayQueue, OtpExtractorCore, the typed
    // enum decoders, LastNotification.redact and LogRedactor.
    //
    // Robolectric and the AndroidX test extensions kick in for tests
    // that need a Context, SharedPreferences, Notification channels,
    // Handlers, or any other framework class.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // ── Instrumented tests (run on a real device / emulator) ─────
    // Live in app/src/androidTest. Cover real foreground service
    // dispatch, accessibility paste, SharedPreferences round-trip
    // through the live Context and the SystemAlertWindow attach
    // path.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // ── Detekt formatting rules ──────────────────────────────────
    // The "formatting" ruleset is shipped as a separate jar and has
    // to be wired in explicitly.
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detekt.get()}")
}

// ── Detekt static analysis ───────────────────────────────────────────
//
// Configured to fail the build on errors (rules with `severity: error`
// in detekt.yml) but keep warnings as informational. The baseline
// holds pre-existing findings so the introduction of detekt did not
// require a full-codebase formatting pass up-front.
detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    baseline = rootProject.file("config/detekt/detekt-baseline.xml")
    buildUponDefaultConfig = true
    autoCorrect = true
    parallel = true
    ignoreFailures = false
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/test/kotlin",
        ),
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    autoCorrect = true
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

// ── Jacoco coverage ──────────────────────────────────────────────────
//
// Generates a coverage report for `:app:testDebugUnitTest`. Coverage
// is intentionally WARNING-ONLY (no `verifyCoverage` task wired into
// `check`). The report is uploaded as a CI artefact; raise it to a
// hard gate once the team has agreed on a realistic per-package
// threshold.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        excludes = listOf("jdk.internal.*")
    }
}

// Generated / framework-only classes excluded from the coverage
// report — coverage on those is meaningless and would dilute the
// project signal.
val coverageExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*",
    // Heavy view classes intentionally tested via visual QA on
    // device, not unit tests.
    "**/overlay/OtpRevealLayout*",
    "**/overlay/SuckInOverlayView*",
    "**/widget/SpringSwitch*",
    "**/widget/AnimatedCheckbox*",
    "**/widget/BarChartView*",
    "**/widget/FlowLayout*",
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    description = "Generate JaCoCo coverage report for the debug unit tests."
    group = "verification"

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        // AGP 9.x writes Kotlin classes under
        // `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`;
        // older layouts used `tmp/kotlin-classes/debug`. Both
        // locations are scanned so the report works across AGP
        // upgrades.
        files(
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                exclude(coverageExcludes)
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(coverageExcludes)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    // AGP 9.x writes the Jacoco execution data for unit tests under
    // `outputs/unit_test_code_coverage/<variant>UnitTest/…exec` —
    // not under `jacoco/`. We collect both locations so the report
    // works on both layouts.
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
}

// ── Coverage verification (opt-in gate) ──────────────────────────────
//
// Runs JaCoCo's verification step against the same execution data
// the report task collects. Intentionally NOT wired into `check`
// because the task is opt-in tooling for contributors who want to
// confirm a change does not erode coverage on the privacy-critical
// packages — extractor, system, service/overlay. UI / widget /
// animation packages are excluded because they are tested via visual
// QA on a physical device, not by JVM unit tests.
//
// Run locally:
//   ./gradlew :app:jacocoTestCoverageVerification
//
// Conservative thresholds — anything below the bar fails the task
// but does NOT block `./gradlew :app:check`. Raise the bar once the
// suite reaches it; lowering should require an explicit PR note.
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    description = "Verifies coverage thresholds on privacy-critical packages."
    group = "verification"

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                exclude(coverageExcludes)
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(coverageExcludes)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )

    violationRules {
        // Per-package floors. Each rule fails the task on its own
        // metric breach so a regression on one privacy-critical
        // package surfaces directly in the failure log instead of
        // as a single generic "coverage too low".
        rule {
            element = "PACKAGE"
            includes = listOf("com.midtano.otp.extractor")
            limit {
                counter = "INSTRUCTION"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com.midtano.otp.system")
            limit {
                counter = "INSTRUCTION"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com.midtano.otp.service.overlay")
            // Overlay-service helpers mix pure logic with WindowManager
            // glue; the floor is intentionally a touch lower so the
            // gate doesn't punish code that is genuinely covered by
            // the instrumented test tier.
            limit {
                counter = "INSTRUCTION"
                minimum = "0.55".toBigDecimal()
            }
        }
        // Module-wide floor — much looser, just protects against a
        // future PR that drops every test in one go.
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}
