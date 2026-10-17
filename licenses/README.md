<!--
The Orbitalis Developers

Copyright 2026 Vsevolod Petrov (lazyhat)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Third-party material and project licensing

Original Orbitalis sources are licensed under Apache-2.0 (LICENSE.md). Adapted Compukters tooling retains
its upstream copyright and adaptation notices in full Apache-2.0 headers; NOTICE records the source revision. Keep those notices when
refactoring. Include LICENSE.md and NOTICE in production archives; metadata declares Apache-2.0.
Original source, build scripts and executable/build configuration use the full Apache-2.0 header,
with `The Orbitalis Developers` and `Copyright 2026 Vsevolod Petrov (lazyhat)` for current original files.
See build.gradle.kts for block comments and tools/verify.py for hash comments. For future files, use
their actual creation year. SPDX alone is not the repository's source header format. Keep script
shebangs on the first line. Adapted Compukters files retain their upstream holder and adaptation notice.
Do not apply original project headers to vendored/generated third-party files.
For original JSON resources, which cannot contain comments, place the same full plain-text header in an
adjacent `<filename>.license` sidecar and include it with the resource in archives.

## Current inventory

| Material | Origin / version | License | Distribution |
| --- | --- | --- | --- |
| Gradle/worktree tooling and tests | Compukters d2a6dccd11aae3c1ed801a0d40bf9542da61389c | Apache-2.0 | Source; upstream notices preserved |
| Gradle wrapper | Existing project wrapper; Gradle 9.8.1 distribution | Apache-2.0 | Wrapper source/binary; Gradle's notices retained by the distribution |
| Kotlin stdlib | org.jetbrains.kotlin:kotlin-stdlib:2.4.10 | Apache-2.0 and upstream third-party notices | Nested by Loom; legal files packaged beside it from licenses/kotlin-stdlib; verified in JAR |
| Sable API / development runtime | Sable 2.0.6 (Modrinth fg9dTRz9), Companion 1.6.0 API | Sable: PolyForm Shield 1.0.0; Companion: MIT | Compile-only APIs; original Sable release installed automatically in standard dev game run directories; never bundled by Orbitalis; see [provenance](sable/PROVENANCE.md) |
| Flight consumer API/runtime and adapted extraction scripts | Create 6.0.10, Aeronautics bundle 1.3.2, Propulsion 1.1.5, client-only Voxy/WorldGen/Roxy/Connector/Forgified Fabric API/Sodium and supporting libraries | Separate upstream code/asset terms | Development-only independent consumer; never bundled by Orbitalis; see [inventory and licenses](flight-consumer/PROVENANCE.md) |
| Minecraft and NeoForge | Minecraft 1.21.1 / NeoForge 21.1.252 | Upstream terms | Development/runtime dependencies; not bundled in mod JAR |

No third-party textures, fonts, sounds, models or Compukters branding are imported. Gradle plugins and
build/test transitive dependencies remain tooling, not mod payload. The inventory above records directly
redistributed material; it is not a complete transitive dependency audit or blanket relicensing claim.

Before adding redistributed code or assets, record exact source URL, version/revision, rights holder,
license identifier/text, modifications and attribution requirements. Store necessary license texts and
provenance beneath licenses/<component>/ and package the required notices. For fonts, textures, sounds
and models verify their own licenses independently of the repository code license. Unknown rights are
not evidence of permission. If adding a bundled dependency, inspect its transitive contents and verify
archive/license composition; preserve upstream META-INF notices. Do not copy another project's complete
licenses tree for components that Orbitalis does not distribute.
