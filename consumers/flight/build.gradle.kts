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

import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    java
    alias(orbitalis.plugins.loom)
}

group = "org.cbi.orbitalis.consumer"
version = "development"
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/snapshots")
}
apply(from = "gradle/sable-libraries.gradle.kts")
apply(from = "gradle/aeronautics-libraries.gradle.kts")
val sableLibraries = files(configurations.named("sableNestedMods"))
val veilLibraries = files(configurations.named("sableRuntimeLibraries"))
val aeronauticsLibraries = files(configurations.named("aeronauticsNestedMods"))
val physicsMods = configurations.create("physicsMods") {
    isCanBeConsumed = false
    isTransitive = false
}
val originalSable = configurations.create("originalSable") {
    isCanBeConsumed = false
    isTransitive = false
}
val orbitalisMod = files(layout.projectDirectory.file(
    "../../modules/minecraft/v1_21_1/v1_21_1-neoforge/build/devlibs/orbitalis-development-mod.jar",
)).builtBy(gradle.includedBuild("Orbitalis").task(":v1_21_1-neoforge:developmentModJar"))

dependencies {
    minecraft(orbitalis.minecraft.v1211)
    mappings(loom.officialMojangMappings())
    neoForge(orbitalis.neoforge.v1211)
    runtimeOnly(orbitalisMod)
    originalSable(orbitalis.sable.v1211)
    physicsMods("maven.modrinth:create-aeronautics:44pLdPGg")
    physicsMods("maven.modrinth:create-propulsion-simulated:H13U56dc")
    modRuntimeOnly(files(physicsMods))
    modRuntimeOnly(aeronauticsLibraries)
    modRuntimeOnly(orbitalis.sable.v1211) { isTransitive = false }
    modRuntimeOnly(sableLibraries)
    forgeRuntimeLibrary(veilLibraries)
    modRuntimeOnly("com.simibubi.create:create-1.21.1:6.0.10-280:slim") { isTransitive = false }
    modRuntimeOnly("net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6")
    modRuntimeOnly("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")
    forgeRuntimeLibrary("org.jetbrains.kotlin:kotlin-stdlib:${orbitalis.versions.kotlin.get()}") { isTransitive = false }
}
loom {
    runs {
        configureEach {
            // This consumer has no own source outputs. Group the consumed mod archive directly.
            mods {
                clear()
                maybeCreate("orbitalis").modFiles.from(orbitalisMod)
            }
        }
        named("client") { runDir("run/client"); ideConfigGenerated(true) }
        named("server") { runDir("run/server"); ideConfigGenerated(true) }
    }
}
// This build composes external mods for development; it publishes no umbrella mod.
tasks.jar { enabled = false }
tasks.named("remapJar") { enabled = false }

val launchModGroups = listOf("client", "server").associateWith { name ->
    loom.runs.named(name).get().mods
}
val verifyConsumer = tasks.register("verifyConsumer") {
    group = "verification"
    description = "Check the exact external mod set and the independently built Orbitalis development archive."
    dependsOn(orbitalisMod)
    inputs.files(physicsMods, originalSable, aeronauticsLibraries, sableLibraries, orbitalisMod)
    doLast {
        launchModGroups.forEach { (name, mods) ->
            check(mods.names == setOf("orbitalis") &&
                mods.single().modFiles.files == orbitalisMod.files) {
                "$name must load only the consumed Orbitalis archive as its development mod group"
            }
        }
        val hashes = mapOf(
            "sable-fg9dTRz9.jar" to "76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8",
            "create-aeronautics-44pLdPGg.jar" to "dc7e8e1148fa442243889ba29412207137e1bb86206e6c92bc0a589f1374a466e93de22bdbf14f753984d86c29308fbbdc0f14a4b9618b14c0ac1666a36b46f5",
            "create-propulsion-simulated-H13U56dc.jar" to "6096c9c5cb220219a0b58b56f9fefd63f8b79a0967d326727b4206d62df1813da73956aeae2fa5b839160a1bb5af8e099726d12d2d93f10e5f157beb85bb9c52",
        )
        files(physicsMods, originalSable).files.forEach { archive ->
            val hash = MessageDigest.getInstance("SHA-512").digest(archive.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == hashes[archive.name]) { "Unexpected upstream release checksum: ${archive.name}" }
        }
        val ids = files(physicsMods, aeronauticsLibraries, sableLibraries).files.flatMap { archive ->
            ZipFile(archive).use { zip ->
                val metadata = zip.getEntry("META-INF/neoforge.mods.toml")
                // Sable's native Rapier library has Fabric metadata but is not a NeoForge mod.
                if (metadata == null) {
                    check(archive.name.contains("sable_rapier") && zip.getEntry("fabric.mod.json") != null) {
                        "Unexpected non-mod library: ${archive.name}"
                    }
                    return@use emptyList<String>()
                }
                val text = zip.getInputStream(metadata).bufferedReader().use { it.readText() }
                text.split(Regex("(?m)^\\[\\[")).filter { it.startsWith("mods]]") }.map { section ->
                    checkNotNull(Regex("""(?m)^\s*modId\s*=\s*["']([^"']+)["']""").find(section)).groupValues[1]
                }
            }
        }
        val expected = setOf("aeronautics_bundled", "aeronautics", "simulated", "offroad", "createpropulsion",
            "sablecompanion", "veil")
        check(ids.size == ids.toSet().size && ids.toSet() == expected) {
            "Unexpected consumer mod set: $ids; expected $expected"
        }
        ZipFile(orbitalisMod.singleFile).use { zip ->
            listOf("org/cbi/orbitalis/core/Orbitalis.class", "org/cbi/orbitalis/neoforge/OrbitalisMod.class",
                "META-INF/neoforge.mods.toml", "META-INF/LICENSE.md", "META-INF/NOTICE").forEach { name ->
                check(zip.getEntry(name) != null) { "Missing $name in development mod" }
            }
            check(zip.entries().asSequence().none { entry ->
                entry.name.endsWith(".jar") || entry.name.startsWith("net/minecraft/") ||
                    entry.name.startsWith("dev/ryanhcode/") || entry.name.startsWith("com/simibubi/") ||
                    entry.name.startsWith("dev/propulsionteam/") || entry.name.contains("GameTest")
            }) { "External dependencies or test fixtures leaked into development mod" }
        }
        logger.lifecycle("Flight consumer verified: ${ids.sorted().joinToString()}")
    }
}
tasks.named("check") { dependsOn(verifyConsumer) }
tasks.matching { it.name in setOf("runClient", "runServer") }.configureEach { dependsOn(verifyConsumer) }
