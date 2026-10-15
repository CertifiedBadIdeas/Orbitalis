# Third-party material and project licensing

Original Orbitalis sources are licensed under Apache-2.0 (LICENSE.md). Adapted Compukters tooling retains
its upstream copyright and SPDX headers; NOTICE records the source revision. Keep those notices when
refactoring. Include LICENSE.md and NOTICE in production archives; metadata declares Apache-2.0.
Do not apply original project headers to vendored/generated third-party files.

## Current inventory

| Material | Origin / version | License | Distribution |
| --- | --- | --- | --- |
| Gradle/worktree tooling and tests | Compukters d2a6dccd11aae3c1ed801a0d40bf9542da61389c | Apache-2.0 | Source; upstream notices preserved |
| Gradle wrapper | Existing project wrapper; Gradle 9.8.1 distribution | Apache-2.0 | Wrapper source/binary; Gradle's notices retained by the distribution |
| Kotlin stdlib | org.jetbrains.kotlin:kotlin-stdlib:2.4.10 | Apache-2.0 and upstream third-party notices | Nested by Loom; legal files packaged beside it from licenses/kotlin-stdlib; verified in JAR |
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
