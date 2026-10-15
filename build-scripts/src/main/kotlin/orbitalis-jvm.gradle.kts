// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jmailen.kotlinter")
}
group = rootProject.providers.gradleProperty("modGroup").get()
version = rootProject.providers.gradleProperty("modVersion").get()
repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}
kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    testImplementation(platform("org.junit:junit-bom:${catalog.findVersion("junit").get()}"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<Test>().configureEach { useJUnitPlatform() }
tasks.withType<ConfigurableKtLintTask>().configureEach {
    exclude { it.file.path.contains("build/generated") }
}
