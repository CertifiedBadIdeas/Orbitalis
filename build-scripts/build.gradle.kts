// SPDX-License-Identifier: Apache-2.0

plugins { `kotlin-dsl` }
repositories {
    mavenCentral()
    maven("https://maven.architectury.dev/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    gradlePluginPortal()
}
dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.loom.gradle)
    implementation(libs.kotlinter.gradle)
}
