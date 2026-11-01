// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Compose UI both applications draw from — the player first.
 *
 * Keep the Android and desktop Compose graphs on the same current CMP release.
 * In particular, compiling the desktop UI against the same Skiko generation
 * that runs it avoids the Matrix33 ABI mismatch seen with older desktop bytecode.
 */
val cmp = "1.12.2"

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.music.audify.sharedui"
        compileSdk = 37
        minSdk = 26
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Both targets are JVMs, so the UI is written once against the JDK here
        // rather than in commonMain.
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
            dependencies {
                // The domain models and data APIs live in :shared's JVM-shared
                // source set, not its common source set.
                api(project(":shared"))
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:$cmp")
            api("org.jetbrains.compose.foundation:foundation:$cmp")
            api("org.jetbrains.compose.animation:animation:$cmp")
            api("org.jetbrains.compose.ui:ui:$cmp")
            api("org.jetbrains.compose.material3:material3:1.9.0")
            api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            api("org.jetbrains.compose.components:components-resources:$cmp")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            api("io.coil-kt.coil3:coil-compose:3.6.3")
            api("dev.chrisbanes.haze:haze:2.0.1")
            api("dev.chrisbanes.haze:haze-blur:2.0.1")
            api("dev.chrisbanes.haze:haze-blur-materials:2.0.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        // Compiled against what the desktop actually resolves, not the phone's
        // pin — see the CMP_DESKTOP comment at the top of this file. Skia's
        // Matrix33 changed shape between skiko 0.9 and 0.15, and bytecode built
        // against 0.9 NoSuchMethodError'd the desktop's 0.15 at draw time.
        jvmMain.dependencies {
            api("org.jetbrains.compose.ui:ui-desktop:$cmp")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
        }
        androidMain.dependencies {
            // The phone's own versions, so nothing here moves them.
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.core:core-ktx:1.19.1")
            implementation("androidx.appcompat:appcompat:1.8.0")
            implementation("androidx.palette:palette-ktx:1.1.0")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.music.audify.sharedui.resources"
    generateResClass = always
}
