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
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

// Keep original Fabric/loader JARs for Roxy/Connector; prepare native mods with Loom for development.
data class ClientVisualMod(val slug: String, val versionId: String, val filename: String, val sha512: String)
val clientVisualPins = listOf(
    ClientVisualMod("voxy", "H3w2nVdU", "voxy-0.2.16-beta+1.21.11.jar",
        "d5159e2e745a9c50f16c67c54303b8a991dc5a1954e5cd7edc235c6f24ad2a045cb493e128717ed4e97352366ae83b03b3d73815342421e631c2a8995c48cccb"),
    ClientVisualMod("roxy", "cDmwpuYr", "roxy-1.21.1-NeoForge-0.4.0.jar",
        "9dc2c87447b2d43b186eba4957be25a08833a937aadd4f836ba86ad64328a1ae819c696044ba55d3efc9cd7b9f8022e3dbc3d362d355486e90b179674eeae1da"),
    ClientVisualMod("connector", "IITF0PRC", "connector-2.0.0-beta.17+1.21.1-full.jar",
        "cb92b662047208792e61191d372d1e255766200406c35769007101f8c578c1018938610c3b90352afd93ec42d7963acf3607e35c50e669df50ac85d7c5529560"),
    ClientVisualMod("forgified-fabric-api", "dAxle9F7", "forgified-fabric-api-0.116.14+2.3.0+1.21.1.jar",
        "9dbb0018e68b4efd65abf57f5fc41bb5d3b812e4a3cb75164937cb28ab541225b939fdeac0477ec2a7a75507791f3c9fc5592229279c68a9db04854a36ab5d72"),
    ClientVisualMod("sodium", "S3DUMfBo", "sodium-neoforge-0.8.12+mc1.21.1.jar",
        "2a689e1b1e16acc790c011b42ec21958956207c3b49cb99cb5c006e639786adf1cc36559f9840e2c1d27485c00da9712d34020fa4279eb6810eaa54bf20b19b0"),
    ClientVisualMod("voxy-worldgen", "PzNUZlWw", "Voxy World Gen V2-neoforge-1.21.1-2.4.2.jar",
        "1407723c46d6320eac110f7d31fb047ed08b5c654b83f9b77b8f19c90b351b497bf9a1180718dbf647579f3566b1b2a4f85f356c0d0de975a909c965d82bff5a"),
    ClientVisualMod("cloth-config", "izKINKFg", "cloth-config-15.0.140-neoforge.jar",
        "aaf9b010955b8cd294e5a92f069985b18729fd5e2cf22d351f1dff9680f15488688803ec41e77e941cbde130ceb535014ca4c868047d80ab69c2d508e216654d"),
    ClientVisualMod("better-modlist", "ZID4kkto", "better_modlist-21.1.4.jar",
        "932ab8d8b095fd3ce5a61d1ce9103174e36bf5b1ce2975b24b5722965f0755be20c6d4d9501f42b4b692317b87f0b36a21cfcefd258e2f00fcfd9665de71fbbe"),
)
val clientVisualMods = configurations.create("clientVisualMods") {
    isCanBeConsumed = false
    isTransitive = false
}
clientVisualPins.forEach { pin ->
    dependencies.add(clientVisualMods.name, "maven.modrinth:${pin.slug}:${pin.versionId}")
}
fun clientVisualHash(file: File) = MessageDigest.getInstance("SHA-512").digest(file.readBytes())
    .joinToString("") { "%02x".format(it) }
val verifyClientVisualMods = tasks.register("verifyClientVisualMods") {
    group = "verification"
    description = "Verify pinned raw Voxy client JARs and their isolation from server/compile classpaths."
    inputs.files(clientVisualMods)
    doLast {
        val artifacts = clientVisualMods.resolvedConfiguration.resolvedArtifacts
        clientVisualPins.forEach { pin ->
            val artifact = artifacts.single { it.moduleVersion.id.name == pin.slug }
            check(clientVisualHash(artifact.file) == pin.sha512) { "Unexpected client visual JAR: ${pin.slug}" }
        }
        listOf("compileClasspath", "runtimeClasspath", "testRuntimeClasspath", "gameTestRuntimeClasspath").forEach { name ->
            check(clientVisualMods !in configurations.getByName(name).hierarchy) {
                "Client-only visual mods leaked into $name"
            }
        }
        logger.lifecycle("Verified ${clientVisualPins.size} pinned client visual mods; server and test classpaths stay independent.")
    }
}

// NeoForge production lambda names differ from Loom's development names, including nested API mods.
abstract class ClientVisualNestedMods : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifact: Provider<FileSystemLocation>
    override fun transform(outputs: TransformOutputs) {
        ZipFile(artifact.get().asFile).use { archive ->
            archive.entries().asSequence().filter { (it.name.startsWith("META-INF/jarjar/") || it.name.startsWith("META-INF/jars/")) && it.name.endsWith(".jar") }
                // The pinned Forgified API supplies newer/equal versions of Sodium's four API modules.
                .filterNot { artifact.get().asFile.name.startsWith("sodium-") && it.name.substringAfterLast('/').startsWith("fabric-") }
                .forEach { entry ->
                    outputs.file(entry.name.substringAfterLast('/')).writeBytes(archive.getInputStream(entry).use { it.readBytes() })
                }
        }
    }
}
val nativeOriginalMods = configurations.create("clientVisualNativeOriginal") {
    isCanBeConsumed = false
    isTransitive = false
}
val rawSlugs = setOf("voxy", "roxy", "connector")
clientVisualPins.filter { it.slug !in rawSlugs }.forEach { pin ->
    dependencies.add(nativeOriginalMods.name, "maven.modrinth:${pin.slug}:${pin.versionId}")
}
val nestedType = "orbitalis-client-visual-nested"
dependencies.registerTransform(ClientVisualNestedMods::class.java) {
    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, nestedType)
}
val nativeNestedMods = nativeOriginalMods.incoming.artifactView {
    attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, nestedType)
}.files
dependencies.add("modClientVisualRuntimeOnly", files(nativeOriginalMods, nativeNestedMods))
val nativeMappedMods = provider { configurations.getByName(extra["clientVisualMappedConfiguration"] as String) }
fun nativeClientModIds(archives: Set<File>): List<String> = archives.flatMap { file ->
    ZipFile(file).use { zip ->
        // Sodium's service carrier also contains a mods.toml; FML deliberately loads it as a library.
        val manifest = zip.getEntry("META-INF/MANIFEST.MF")
        if (manifest != null && zip.getInputStream(manifest).use { java.util.jar.Manifest(it) }
            .mainAttributes.getValue("FMLModType") == "LIBRARY") return@use emptyList<String>()
        val metadata = zip.getEntry("META-INF/neoforge.mods.toml") ?: return@use emptyList<String>()
        val text = zip.getInputStream(metadata).bufferedReader().use { it.readText() }
        text.split(Regex("(?m)^\\[\\[")).filter { it.startsWith("mods]]") }.map { section ->
            checkNotNull(Regex("""(?m)^\s*modId\s*=\s*["']([^"']+)["']""").find(section)).groupValues[1]
        }
    }
}
verifyClientVisualMods.configure {
    inputs.files(nativeMappedMods)
    doLast {
        val prepared = nativeMappedMods.get().files
        val expectedIds = nativeClientModIds(files(nativeOriginalMods, nativeNestedMods).files)
        val actualIds = nativeClientModIds(prepared)
        check(actualIds.size == actualIds.toSet().size && actualIds.toSet() == expectedIds.toSet()) {
            "Prepared client mods lost or duplicated upstream modules: expected=$expectedIds actual=$actualIds"
        }
        listOf("compileClasspath", "runtimeClasspath", "testRuntimeClasspath", "gameTestRuntimeClasspath").forEach { name ->
            check(configurations.getByName(name).files.intersect(prepared).isEmpty()) {
                "Prepared client-only mods leaked into $name"
            }
        }
    }
}
// Separate IDE/managed Loom caches can emit identical entries with different ZIP metadata.
// Apply this only to prepared native mods; original release JARs retain exact-byte verification.
fun samePreparedClientMod(first: File, second: File): Boolean = ZipFile(first).use { left ->
    ZipFile(second).use { right ->
        val leftEntries = left.entries().asSequence().toList()
        val rightEntries = right.entries().asSequence().toList()
        val names = leftEntries.map { it.name }
        names.size == names.toSet().size && rightEntries.size == names.size &&
            rightEntries.map { it.name }.toSet() == names.toSet() &&
            leftEntries.all { entry ->
                val other = right.getEntry(entry.name)
                val leftBytes = left.getInputStream(entry).use { it.readBytes() }
                val rightBytes = right.getInputStream(other).use { it.readBytes() }
                entry.isDirectory == other.isDirectory &&
                    (leftBytes.contentEquals(rightBytes) ||
                        // Loom emits converted access rules in nondeterministic map iteration order.
                        (entry.name == "META-INF/accesstransformer.cfg" &&
                            leftBytes.toString(Charsets.UTF_8).lines().sorted() ==
                            rightBytes.toString(Charsets.UTF_8).lines().sorted()))
            }
    }
}
val prepareFlightClientMods = tasks.register("prepareFlightClientMods") {
    group = "application"
    description = "Install the Voxy client stack, with native dependencies prepared for the Loom namespace."
    dependsOn(verifyClientVisualMods)
    inputs.files(clientVisualMods, nativeMappedMods)
    // The set includes extracted upstream nested mods, so track the exact installed paths as outputs.
    val destinations = provider {
        val raw = clientVisualPins.filter { it.slug in rawSlugs }.map { it.filename }
        raw + nativeMappedMods.get().files.map { "orbitalis-client-dev-${it.name}" }
    }
    outputs.files(destinations.map { names -> names.map { layout.projectDirectory.file("run/client/mods/$it") } })
    doLast {
        val artifacts = clientVisualMods.resolvedConfiguration.resolvedArtifacts
        val sources = clientVisualPins.filter { it.slug in rawSlugs }.map { pin ->
            artifacts.single { it.moduleVersion.id.name == pin.slug }.file to pin.filename
        } + nativeMappedMods.get().files.map { it to "orbitalis-client-dev-${it.name}" }
        sources.forEach { (source, name) ->
            val destination = layout.projectDirectory.file("run/client/mods/$name").asFile
            check(!destination.exists() || (destination.isFile &&
                (clientVisualHash(destination) == clientVisualHash(source) ||
                    (name.startsWith("orbitalis-client-dev-") && samePreparedClientMod(destination, source))))) {
                "Preserving unexpected existing client mod: $destination"
            }
        }
        sources.forEach { (source, name) ->
            val destination = layout.projectDirectory.file("run/client/mods/$name").asFile
            destination.parentFile.mkdirs()
            if (!destination.exists()) source.copyTo(destination)
        }
        logger.lifecycle("Installed Voxy/WorldGen client stack: ${sources.size} prepared mod files.")
    }
}
tasks.named("check") { dependsOn(verifyClientVisualMods) }
tasks.named("runClient") { dependsOn(prepareFlightClientMods) }
