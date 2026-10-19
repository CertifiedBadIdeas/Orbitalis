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

plugins {
    id("orbitalis-jvm")
    alias(libs.plugins.loom)
}
extra["orbitalisMinecraftVersion"] = libs.versions.minecraft.v1211.get()
apply(plugin = "orbitalis-minecraft-shared")
repositories {
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/snapshots")
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    maven("https://maven.ryanhcode.dev/releases") { content { includeGroup("dev.ryanhcode.sable-companion") } }
}
dependencies {
    "modCompileOnly"(libs.propulsion.v1211) { isTransitive = false }
    "modCompileOnly"(variantOf(libs.create.v1211) { classifier("slim") }) { isTransitive = false }
    "modCompileOnly"(libs.ponder.v1211) { isTransitive = false }
    "modCompileOnly"(libs.registrate.v1211) { isTransitive = false }
    "modCompileOnly"(libs.sable.v1211) { isTransitive = false }
    compileOnly(libs.sable.companion) { isTransitive = false }
    minecraft(libs.minecraft.v1211)
    mappings(loom.officialMojangMappings())
    implementation(project(":core"))
}
