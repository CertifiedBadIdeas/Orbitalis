// SPDX-License-Identifier: Apache-2.0

plugins {
    id("orbitalis-jvm")
    alias(libs.plugins.loom)
}
extra["orbitalisMinecraftVersion"] = libs.versions.minecraft.v1211.get()
apply(plugin = "orbitalis-minecraft-shared")
dependencies {
    minecraft(libs.minecraft.v1211)
    mappings(loom.officialMojangMappings())
    implementation(project(":core"))
}
