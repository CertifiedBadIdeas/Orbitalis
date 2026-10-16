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

import net.fabricmc.loom.task.RemapJarTask
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("orbitalis-jvm")
    alias(libs.plugins.loom)
}
extra["orbitalisMinecraftVersion"] = libs.versions.minecraft.v1211.get()
apply(plugin = "orbitalis-minecraft-shared")
base.archivesName.set("orbitalis-neoforge-${libs.versions.minecraft.v1211.get()}")
repositories {
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    maven("https://maven.ryanhcode.dev/releases") { content { includeGroup("dev.ryanhcode.sable-companion") } }
}
dependencies {
    "modCompileOnly"(libs.sable.v1211) { isTransitive = false }
    compileOnly(libs.sable.companion) { isTransitive = false }
    minecraft(libs.minecraft.v1211)
    mappings(loom.officialMojangMappings())
    "neoForge"(libs.neoforge.v1211)
    implementation(project(":core"))
    implementation(project(path = ":v1_21_1-common", configuration = "namedElements"))
    "include"(kotlin("stdlib", libs.versions.kotlin.get()))
    "forgeRuntimeLibrary"("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}") { isTransitive = false }
}
val sableDistribution = configurations.create("sableDistribution") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies { add(sableDistribution.name, libs.sable.v1211) }
val prepareSableLab = tasks.register("prepareSableLab") {
    inputs.files(sableDistribution)
    val directories = listOf("client", "server", "gameTestServer").map { layout.projectDirectory.dir("run/$it/mods") }
    outputs.dirs(directories)
    doLast {
        val archive = sableDistribution.singleFile
        val digest = MessageDigest.getInstance("SHA-512").digest(archive.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == "76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8") {
            "Sable release checksum differs from the pinned Modrinth artifact"
        }
        directories.forEach { directory ->
            directory.asFile.mkdirs()
            archive.copyTo(directory.file("sable-neoforge-1.21.1-2.0.6.jar").asFile, overwrite = true)
        }
    }
}
evaluationDependsOn(":core")
evaluationDependsOn(":v1_21_1-common")
val coreSources = project(":core").extensions.getByType<SourceSetContainer>()
val commonSources = project(":v1_21_1-common").extensions.getByType<SourceSetContainer>()
loom {
    runs {
        configureEach {
            // Forge run-local groups override global loom.mods, so configure every run directly.
            mods {
                maybeCreate("main").apply {
                    sourceSet("main")
                    sourceSet(coreSources.named("main").get())
                    sourceSet(commonSources.named("main").get())
                }
            }
        }
        named("client") { runDir("run/client") }
        named("server") { runDir("run/server") }
    }
}
tasks.jar {
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
    from(rootProject.file("licenses/kotlin-stdlib")) { into("META-INF/licenses/kotlin-stdlib") }
    from(coreSources.named("main").map { it.output })
    from(commonSources.named("main").map { it.output })
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
// Named development classes for the independent consumer; never resolve its external runtime here.
tasks.register<Jar>("developmentModJar") {
    group = "build"
    description = "Assemble the current Orbitalis layers for an independent development consumer."
    archiveFileName.set("orbitalis-development-mod.jar")
    destinationDirectory.set(layout.buildDirectory.dir("devlibs"))
    from(sourceSets.main.map { it.output })
    from(coreSources.named("main").map { it.output })
    from(commonSources.named("main").map { it.output })
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.named<Jar>("sourcesJar") {
    from(coreSources.named("main").map { it.allSource })
    from(commonSources.named("main").map { it.allSource })
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.processResources {
    val values = mapOf("mod_version" to project.version.toString())
    inputs.properties(values)
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
}
val productionJar = tasks.named<RemapJarTask>("remapJar")
val verifyProductionJar = tasks.register<Exec>("verifyProductionJar") {
    group = "verification"
    dependsOn(productionJar)
    val archive = productionJar.flatMap { it.archiveFile }
    inputs.file(archive)
    commandLine("python3", rootProject.file("tools/verify.py"), "jar", archive.get().asFile, project.version)
}
tasks.named("check") { dependsOn(verifyProductionJar) }
tasks.register("buildProductionJar") {
    group = "build"
    dependsOn(verifyProductionJar)
}

// Isolated GameTest run, following the Compukters Loom source-set pattern.
val gameTest = sourceSets.create("gameTest")
kotlin.target.compilations.named(gameTest.name) {
    associateWith(kotlin.target.compilations.getByName("main"))
}
gameTest.compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
gameTest.runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output

val generatedGameTestResources = layout.buildDirectory.dir("generated/gameTestResources")
val generateGameTestResources = tasks.register("generateGameTestResources") {
    val template = generatedGameTestResources.map { it.file("data/orbitalis/structure/lab_empty.nbt") }
    val preset = generatedGameTestResources.map { it.file("data/minecraft/worldgen/world_preset/flat.json") }
    val laboratory = rootProject.file("modules/minecraft/shared/common/src/main/resources/data/orbitalis/dimension/planet_lab.json")
    val flightTest = rootProject.file("modules/minecraft/shared/common/src/main/resources/data/orbitalis/dimension/flight_test.json")
    val orbitTest = rootProject.file("modules/minecraft/shared/common/src/main/resources/data/orbitalis/dimension/orbit_test.json")
    val spaceTest = rootProject.file("modules/minecraft/shared/common/src/main/resources/data/orbitalis/dimension/space_test.json")
    inputs.files(laboratory, flightTest, orbitTest, spaceTest)
    outputs.files(template, preset)
    doLast {
        // GameTestServer bakes the flat preset against an empty dimension registry in 1.21.1.
        // Include the canonical laboratory definition in this test-only preset.
        val presetFile = preset.get().asFile
        presetFile.parentFile.mkdirs()
        presetFile.writeText("""
            {"dimensions": {
              "minecraft:overworld": {"type": "minecraft:overworld", "generator": {
                "type": "minecraft:flat", "settings": {"biome": "minecraft:plains", "lakes": false,
                  "features": false, "structure_overrides": [], "layers": [
                    {"height": 1, "block": "minecraft:bedrock"},
                    {"height": 2, "block": "minecraft:dirt"},
                    {"height": 1, "block": "minecraft:grass_block"}]}}},
              "orbitalis:planet_lab": ${laboratory.readText()},
              "orbitalis:flight_test": ${flightTest.readText()},
              "orbitalis:orbit_test": ${orbitTest.readText()},
              "orbitalis:space_test": ${spaceTest.readText()}
            }}
        """.trimIndent())
        val file = template.get().asFile
        file.parentFile.mkdirs()
        DataOutputStream(GZIPOutputStream(file.outputStream())).use { out ->
            out.writeByte(10)
            out.writeUTF("")
            out.writeByte(9)
            out.writeUTF("size")
            out.writeByte(3)
            out.writeInt(3)
            repeat(3) { out.writeInt(1) }
            listOf("blocks", "entities").forEach {
                out.writeByte(9)
                out.writeUTF(it)
                out.writeByte(10)
                out.writeInt(0)
            }
            out.writeByte(9)
            out.writeUTF("palette")
            out.writeByte(10)
            out.writeInt(1)
            out.writeByte(8)
            out.writeUTF("Name")
            out.writeUTF("minecraft:air")
            out.writeByte(0)
            out.writeByte(0)
        }
    }
}
gameTest.resources.srcDir(generateGameTestResources.map { generatedGameTestResources.get() })

val gameTestReport = layout.buildDirectory.file("test-results/gameTestServer/results.xml")
loom.runs.register("gameTestServer") {
    server()
    source(gameTest)
    environment("gametestserver")
    forgeTemplate("gameTestServer")
    runDir("run/gameTestServer")
    property("neoforge.enabledGameTestNamespaces", "orbitalis")
    property("neoforge.gameTestServer", "true")
    property("orbitalis.gameTestReport", gameTestReport.get().asFile.absolutePath)
    ideConfigGenerated(true)
    mods {
        maybeCreate("main").apply {
            sourceSet(gameTest.name)
        }
    }
}
tasks.matching { it.name in setOf("runClient", "runServer", "runGameTestServer") }.configureEach { dependsOn(prepareSableLab) }
tasks.named("check") { dependsOn(gameTest.classesTaskName) }
tasks.configureEach {
    if (name == "runGameTestServer") {
        dependsOn(gameTest.classesTaskName)
        doFirst {
            val report = gameTestReport.get().asFile
            report.parentFile.mkdirs()
            report.delete()
        }
        doLast {
            val report = gameTestReport.get().asFile
            check(report.isFile) { "GameTest server did not produce a fresh test report; inspect its startup log." }
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(report)
            val cases = document.getElementsByTagName("testcase")
            val minimumCases = 21
            check(cases.length >= minimumCases) { "Expected at least $minimumCases laboratory GameTests, received ${cases.length}." }
            val names = (0 until cases.length).map { (cases.item(it) as org.w3c.dom.Element).getAttribute("name") }.toSet()
            check(names.containsAll(setOf("cubecreativeflightescapesalonglocalup", "cubefixturepreservesexcavation", "cubeplayercrossesedge", "cubeframepreservesmotionandblocksunsafeturns", "sableimpulseandcoasting", "sableframeshiftpreservesstate", "sablecentralgravityorbit", "sableorbitalmanoeuvres", "sablesurfaceflightandlanding", "sableplatformservercarry", "ordinaryshipsreceivecubicgravity", "fullspaceorbitpreservesnativeenergy", "shiptransferroundtripandrecovery", "spacetransferroundtripandrecovery"))) {
                "GameTest run did not execute its required Sable physics scenarios: $names"
            }
            listOf("failure", "error", "skipped").forEach { tag ->
                check(document.getElementsByTagName(tag).length == 0) { "GameTest report contains $tag outcomes: $report" }
            }
            logger.lifecycle("Verified ${cases.length} GameTest results: $report")
        }
    }
}

val verifyGameTestRunIsolation = tasks.register("verifyGameTestRunIsolation") {
    group = "verification"
    description = "Verify that GameTest outputs are visible only in the GameTest run."
    val testFiles = objects.fileCollection().from(gameTest.output)
    val runFiles = listOf("client", "server", "gameTestServer").associateWith { name ->
        val run = loom.runs.named(name).get()
        val mods = if (run.mods.isEmpty()) loom.mods else run.mods
        objects.fileCollection().from(mods.map { it.modFiles })
    }
    inputs.files(testFiles, runFiles.values)
    doLast {
        val outputs = testFiles.files.map { it.canonicalFile }.toSet()
        runFiles.forEach { (name, files) ->
            val overlap = files.files.map { it.canonicalFile }.toSet().intersect(outputs)
            check(if (name == "gameTestServer") overlap.isNotEmpty() else overlap.isEmpty()) {
                "Unexpected GameTest source visibility in $name: $overlap"
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyGameTestRunIsolation) }

val verifyDevelopmentModGroups = tasks.register("verifyDevelopmentModGroups") {
    group = "verification"
    description = "Verify that every development run groups all production layers as one mod."
    doLast {
        val required = listOf(sourceSets.main.get(), coreSources.getByName("main"), commonSources.getByName("main"))
            .flatMap { it.output.files }.map { it.canonicalFile }.toSet()
        listOf("client", "server", "gameTestServer").forEach { name ->
            val run = loom.runs.named(name).get()
            val mods = if (run.mods.isEmpty()) loom.mods else run.mods
            check(mods.size == 1 && mods.names == setOf("main")) {
                "$name must use Loom's single main mod group; found ${mods.names}"
            }
            val mod = mods.single()
            val externalOutputs = mod.externalGroups.get().flatMap { reference ->
                project(reference.projectPath()).extensions.getByType<SourceSetContainer>()
                    .getByName(reference.sourceSetName()).output.files
            }
            val files = (mod.modFiles.files + externalOutputs).map { it.canonicalFile }.toSet()
            check(files.containsAll(required)) { "$name is missing production layer outputs: ${required - files}" }
        }
    }
}
tasks.named("check") { dependsOn(verifyDevelopmentModGroups) }
