import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.music.audify.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest { }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Android and desktop are both JVM, but the default hierarchy gives them no source set in
    // common.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
            dependencies {
                // Compile-only: the two applications supply the runtime artifact themselves.
                compileOnly("com.microsoft.onnxruntime:onnxruntime:1.31.1")
                // The network stack the lyrics providers and the YouTube Music
                // client run on, at the phone's versions — both applications
                // now call the same code, so they resolve the same libraries.
                api("io.ktor:ktor-client-core:3.6.1")
                api("io.ktor:ktor-client-okhttp:3.6.1")
                api("io.ktor:ktor-client-websockets:3.6.1")
                api("io.ktor:ktor-client-content-negotiation:3.6.1")
                api("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                api("org.jsoup:jsoup:1.23.2")
                // YouTube stream resolution, as the phone does it: InnerTubeX's
                // client catalog and cipher tiers, NewPipe as the fallback. NewPipe
                // is compile-only because the phone ships a trimmed jar of it.
                api("com.github.MetrolistGroup.innertubex:innertubex:v0.11.2")
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            // Models in this module intentionally have no Android or Compose dependency.
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:5.5.1")
        }
    }
}
