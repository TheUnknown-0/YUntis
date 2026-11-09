import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Release signing with key rotation (APK Signature Scheme v3). YUntis 0.9.0 and 0.10.0 were signed
 * with this PC's debug key; later releases carry a lineage "debug key -> YUntis key", so installed
 * apps keep updating. Keys, lineage and password live outside the repository and are named in
 * ~/.gradle/gradle.properties:
 *   yuntis.releaseKeystore, yuntis.releaseAlias, yuntis.releasePassword, yuntis.oldKeystore, yuntis.lineage
 */
fun signingProperty(name: String): String? = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val rotationSigning = listOf("yuntis.releaseKeystore", "yuntis.releasePassword", "yuntis.oldKeystore", "yuntis.lineage")
    .all { signingProperty(it) != null && (it == "yuntis.releasePassword" || file(signingProperty(it)!!).exists()) }

android {
    namespace = "sh.lsx.untisx"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.theunknown.yuntis"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "0.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (rotationSigning) {
            // the YUntis key; also used for debug builds so they install over a rotated release
            create("yuntis") {
                storeFile = file(signingProperty("yuntis.releaseKeystore")!!)
                storePassword = signingProperty("yuntis.releasePassword")
                keyAlias = signingProperty("yuntis.releaseAlias") ?: "yuntis"
                keyPassword = signingProperty("yuntis.releasePassword")
            }
            // the key of the first releases (this PC's former debug key)
            create("legacy") {
                storeFile = file(signingProperty("yuntis.oldKeystore")!!)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            if (rotationSigning) signingConfig = signingConfigs.getByName("yuntis")
        }

        release {
            optimization {
                enable = true
            }

            signingConfig = if (rotationSigning) {
                // signed with the old key first; signRotatedRelease adds the YUntis key and the lineage
                signingConfigs.getByName("legacy")
            } else {
                // -PallowDebugSigning=true answers the question below for builds without a console
                val allowDebugSigning = project.findProperty("allowDebugSigning") == "true"

                if (!allowDebugSigning && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
                    val answer = System.console()?.readLine(
                        "WARNING: yuntis.* signing properties missing - this release will be signed with the DEBUG key only. Continue? [y/N] "
                    )

                    if (answer?.trim()?.lowercase() != "y") {
                        throw GradleException("Release build aborted: yuntis.* signing properties missing in ~/.gradle/gradle.properties.")
                    }
                }

                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            all { test ->
                test.jvmArgs(
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.material)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

if (rotationSigning) {
    // newest installed build-tools, which brings apksigner
    val sdkDir: File = androidComponents.sdkComponents.sdkDirectory.get().asFile
    val buildTools: File? = File(sdkDir, "build-tools").listFiles()
        ?.filter { it.isDirectory }
        ?.maxWithOrNull(compareBy<File>({ it.name.substringBefore('.').toIntOrNull() ?: 0 }, { it.name }))
    val windows = System.getProperty("os.name").startsWith("Windows")
    val apksigner: File? = buildTools?.let { File(it, if (windows) "apksigner.bat" else "apksigner") }
    val releaseDir: File = layout.buildDirectory.dir("outputs/apk/release").get().asFile
    val versionName = android.defaultConfig.versionName
    val oldKeystore = signingProperty("yuntis.oldKeystore")!!
    val newKeystore = signingProperty("yuntis.releaseKeystore")!!
    val newAlias = signingProperty("yuntis.releaseAlias") ?: "yuntis"
    val lineage = signingProperty("yuntis.lineage")!!
    val password = signingProperty("yuntis.releasePassword")!!

    val signRotatedRelease = tasks.register<Exec>("signRotatedRelease") {
        description = "Signs the release APK with the YUntis key and the rotation lineage (YUntis-<version>.apk)."
        val input = File(releaseDir, "app-release.apk")
        val output = File(releaseDir, "YUntis-$versionName.apk")
        onlyIf { apksigner?.exists() == true && input.exists() }
        // the password goes through the environment, so it never shows up in the process list
        environment("YUNTIS_KS_PASS", password)
        commandLine(
            apksigner?.absolutePath ?: "apksigner", "sign",
            "--ks", oldKeystore, "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android",
            "--next-signer",
            "--ks", newKeystore, "--ks-key-alias", newAlias, "--ks-pass", "env:YUNTIS_KS_PASS",
            "--lineage", lineage,
            "--rotation-min-sdk-version", "28",
            "--out", output.absolutePath,
            input.absolutePath,
        )
        doLast { logger.lifecycle("Signed release: ${output.absolutePath}") }
    }

    tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(signRotatedRelease) }
}
