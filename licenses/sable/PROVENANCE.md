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

# Sable integration provenance

- Author / licensor: RyanHCode; upstream credits remain in the original distribution.
- Source: https://github.com/ryanhcode/sable/tree/d501fb3103c3247e20cb2151a99182ad5dd61adb
- Inspected source version: 2.0.6, Minecraft 1.21.1, NeoForge 21.1.228 minimum.
- Tested distribution: Modrinth `fg9dTRz9`, `sable-neoforge-1.21.1-2.0.6.jar`.
- Download: https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar
- SHA-512: `76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8`.
- License: PolyForm Shield License 1.0.0; unmodified upstream text is in [LICENSE.md](LICENSE.md).
  This is a restricted license containing a Noncompete condition, not Apache-2.0 or a permissive
  open-source license. Retain and assess these upstream terms independently of Orbitalis licensing.

Orbitalis contains an original API adapter, not imported Sable implementation. No Sable source,
textures, branding or binaries are redistributed in the Orbitalis JAR. The optional development
profile installs the checksum-verified original release in separate run directories. Its embedded
licenses/notices are preserved unchanged; the profile does not relicense or patch upstream software.
The Modrinth build is pinned independently of the inspected source revision; no reproducible
source-to-binary equivalence claim is made.

The inspected release embeds Sable Companion 1.6.0, Sable Rapier 2.0.6 and Veil 4.3.2. Veil embeds
GLSL Processor 0.2.3 and Molang Compiler 3.1.1.19. These are upstream runtime contents, not dependencies
nested by Orbitalis. Create and Flywheel are optional in Sable metadata and are not installed by
this experiment. Companion 1.6.0 is also a compile-only API dependency from the upstream Maven.
Its embedded MIT text (Copyright 2026 RyanHCode) is retained in [COMPANION-LICENSE.txt](COMPANION-LICENSE.txt).
The release's Rapier wrapper carries PolyForm Shield, its native Rapier license file is Apache-2.0,
and Veil's embedded LICENSE_Veil is LGPL-3.0. These upstream terms remain in the original external
JARs; their presence does not change Orbitalis source licensing. This inventory is based on the
pinned binary's embedded notices, not a complete audit of its transitive/native components.

Loom's remapped mod dependency removes nested runtime JARs. Consequently compilation uses the
remapped compile-only artifact, while the optional dev profile stages the intact original mod in
`run/*-sable/mods`. Normal profile run directories contain no automatically installed Sable mod.
No manual Gradle cache copies or modified upstream distributions are required.
