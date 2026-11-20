// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Audify"
include(":shared")
include(":sharedUi")
include(":app")
project(":app").projectDir = file("core/app")
include(":tv")
project(":tv").projectDir = file("platforms/tv")
include(":wearos")
project(":wearos").projectDir = file("platforms/wearos")
