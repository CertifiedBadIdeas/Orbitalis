/*
 * The Orbitalis Developers
 *
 * Copyright 2026 The Orbitalis Developers
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

plugins {
    id("orbitalis-jvm")
    alias(libs.plugins.loom)
}
extra["orbitalisMinecraftVersion"] = libs.versions.minecraft.v1211.get()
apply(plugin = "orbitalis-minecraft-shared")
base.archivesName.set("orbitalis-neoforge-${libs.versions.minecraft.v1211.get()}")
dependencies {
    minecraft(libs.minecraft.v1211)
    mappings(loom.officialMojangMappings())
    "neoForge"(libs.neoforge.v1211)
    implementation(project(":core"))
    implementation(project(path = ":v1_21_1-common", configuration = "namedElements"))
    "include"(kotlin("stdlib", libs.versions.kotlin.get()))
}
evaluationDependsOn(":core")
evaluationDependsOn(":v1_21_1-common")
val coreSources = project(":core").extensions.getByType<SourceSetContainer>()
val commonSources = project(":v1_21_1-common").extensions.getByType<SourceSetContainer>()
loom {
    mods {
        maybeCreate("orbitalis").apply {
            sourceSet(sourceSets.main.get())
            sourceSet(coreSources.named("main").get())
            sourceSet(commonSources.named("main").get())
        }
    }
    runs {
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
