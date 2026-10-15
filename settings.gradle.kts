// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}
check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)) {
    "Run Gradle with JDK 25; Minecraft 1.21.1 and common modules compile to Java 21."
}
includeBuild("build-scripts")
rootProject.name = "Orbitalis"
include(":core", ":v1_21_1-common", ":v1_21_1-neoforge")
project(":core").projectDir = file("modules/common/core")
project(":v1_21_1-common").projectDir = file("modules/minecraft/v1_21_1/v1_21_1-common")
project(":v1_21_1-neoforge").projectDir = file("modules/minecraft/v1_21_1/v1_21_1-neoforge")
