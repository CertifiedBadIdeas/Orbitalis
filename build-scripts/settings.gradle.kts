// SPDX-License-Identifier: Apache-2.0

pluginManagement { repositories { gradlePluginPortal() } }
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "orbitalis-build-scripts"
