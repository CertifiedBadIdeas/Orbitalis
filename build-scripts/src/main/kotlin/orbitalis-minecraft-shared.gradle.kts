// SPDX-License-Identifier: Apache-2.0

plugins { id("orbitalis-jvm") }

val layer = when {
    name.endsWith("-common") -> "common"
    name.endsWith("-neoforge") -> "neoforge"
    else -> error("Unsupported Minecraft leaf: $path")
}
val targetVersion = extra["orbitalisMinecraftVersion"].toString()
val activeVersion = providers.gradleProperty("orbitalisActiveMinecraftVersion").get()
check(activeVersion == "1.21.1") { "Only 1.21.1 is implemented; add and verify the 26.1 leaves before selecting them." }
sourceSets.configureEach {
    if (name in setOf("main", "test", "gameTest")) {
        val canonical = rootProject.layout.projectDirectory.dir("modules/minecraft/shared/$layer/src/$name")
        if (targetVersion == activeVersion) {
            kotlin.srcDir(canonical.dir("kotlin"))
            java.srcDir(canonical.dir("java"))
            resources.srcDir(canonical.dir("resources"))
        } else {
            val segment = name.replaceFirstChar(Char::uppercase)
            val mirror = tasks.register<Sync>("sync${segment}SharedMinecraftSources") {
                from(canonical)
                into(layout.buildDirectory.dir("generated/minecraft-shared/$layer/${this@configureEach.name}"))
            }
            val generated = mirror.map { it.destinationDir }
            kotlin.srcDir(generated.map { it.resolve("kotlin") })
            java.srcDir(generated.map { it.resolve("java") })
            resources.srcDir(generated.map { it.resolve("resources") })
            tasks.named(compileJavaTaskName) { dependsOn(mirror) }
            tasks.named("compile${if (name == "main") "" else segment}Kotlin") { dependsOn(mirror) }
            tasks.named(processResourcesTaskName) { dependsOn(mirror) }
        }
    }
}

val verifyMinecraftSourceOwnership = tasks.register("verifyMinecraftSourceOwnership") {
    group = "verification"
    dependsOn(tasks.named(sourceSets.main.get().classesTaskName))
    val roots = sourceSets.flatMap { it.allSource.srcDirs }.map { it.canonicalFile }
    val ownVersionRoot = projectDir.parentFile.canonicalFile.toPath()
    val minecraftRoot = rootProject.file("modules/minecraft")
    val upstreamClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching {
        include("net/minecraft/**", "net/neoforged/**")
    }
    inputs.property("sourceRoots", roots.map { it.absolutePath })
    inputs.files(upstreamClasses)
    doLast {
        val versions = minecraftRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("v") }
            .map { it.canonicalFile.toPath() }
        val foreignRoots = roots.filter { root ->
            versions.any { root.toPath().startsWith(it) && it != ownVersionRoot }
        }
        check(foreignRoots.isEmpty()) { "Cross-version source roots in $path: $foreignRoots" }
        check(upstreamClasses.isEmpty) { "Project classes emitted in upstream-owned packages: ${upstreamClasses.files}" }
    }
}
tasks.named("check") { dependsOn(verifyMinecraftSourceOwnership) }
