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

plugins { id("orbitalis-jvm") }

tasks.register<JavaExec>("runPlanetExperiment") {
    group = "verification"
    description = "Run the finite-planet mathematics experiment; no Minecraft or Sable runtime claim."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.cbi.orbitalis.core.planet.PlanetExperimentKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    val outputDirectory = layout.buildDirectory.dir("experiments/planet")
    outputs.dir(outputDirectory)
    doFirst { setArgs(listOf(outputDirectory.get().asFile.absolutePath)) }
}
