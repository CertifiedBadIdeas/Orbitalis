/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
