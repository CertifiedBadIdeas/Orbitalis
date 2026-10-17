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

# Flight consumer provenance

These are external development dependencies of the independent `consumers/flight` build.
Production Orbitalis no longer compiles against Create or Ponder; the former cube seam adapters are retired.
No upstream implementation, models, textures, sounds or branding are copied into Orbitalis sources
or production archives. Gradle resolves the original artifacts and Loom prepares the local development
classpath. Embedded notices remain in the extracted libraries. This is not a redistributed modpack
or a grant of Apache-2.0 rights over upstream materials.

## Development build scripts

`consumers/flight/gradle/sable-libraries.gradle.kts` and `aeronautics-libraries.gradle.kts` are adapted
from the corresponding Sable and dev-stand scripts in
[Compukters a5a750df8fc2c58b81aca77758feef25a8de5ae0](https://github.com/CertifiedBadIdeas/Compukters/tree/a5a750df8fc2c58b81aca77758feef25a8de5ae0/addons).
License: Apache-2.0. Copyright 2026 The Compukters Developers and Vsevolod Petrov (lazyhat).
Changes: Orbitalis artifact-transform namespace and attribution; the exact embedded upstream releases
remain unchanged. No Compukters mod, addon, VM, compiler or assets are required.

## Original consumer adapter

The Java autopilot and its GameTests under `consumers/flight/src` are original Orbitalis Apache-2.0
code. They compile against the pinned upstream public APIs exclusively in this development build.
No upstream implementation or assets are copied. Engine capacity uses native configured/measured
output; Propulsion's private atmospheric formula is not reproduced. Unit tests reuse the repository-pinned
JUnit version as test tooling. No additional game dependency or asset was introduced for the controller.
These classes are never bundled in an Orbitalis production archive.

## Runtime inventory

| Component | Source / pin | License and holder |
| --- | --- | --- |
| Sable, Companion, Rapier, Veil and Veil libraries | [Sable 2.0.6](https://modrinth.com/mod/sable/version/fg9dTRz9) | See the existing [Sable provenance](../sable/PROVENANCE.md), including Shield, MIT and LGPL terms |
| Create | [6.0.10 source tag](https://github.com/Creators-of-Create/Create/tree/mc1.21.1-6.0.10), Maven `com.simibubi.create:create-1.21.1:6.0.10-280:slim` | Create's split code/assets license, The Create Team; [exact tag license](CREATE-LICENSE.txt) |
| Ponder | Maven `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1` | MIT, Copyright 2022 The Create Team; [embedded license](PONDER-LICENSE.txt) |
| Flywheel | Maven `dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6` | MIT, Copyright 2021–2024 Jozufozu; [embedded license](FLYWHEEL-LICENSE.txt) |
| Registrate | [upstream](https://github.com/tterrag1098/Registrate), Maven `com.tterrag.registrate:Registrate:MC1.21-1.3.0+67` | MPL-2.0, tterrag1098; [1.21.1 branch license](REGISTRATE-LICENSE.txt) (the selected library JAR has no mod metadata/license text) |
| Aeronautics bundle, Aeronautics, Simulated and Offroad | [1.3.2 / 44pLdPGg](https://modrinth.com/mod/create-aeronautics/version/44pLdPGg) | MIT code, assets All Rights Reserved; The Simulated Team / The Creators of Aeronautics; [embedded license](AERONAUTICS-LICENSE.md) |
| Create Propulsion: Simulated | [1.1.5 / H13U56dc](https://modrinth.com/mod/create-propulsion-simulated/version/H13U56dc), [source](https://github.com/Propulsion-Team/create-propulsion-simulated) | MIT; Sergey Feduk and contributors, original notice Copyright 2025 Sergey Feduk; [license](PROPULSION-LICENSE.txt) |
| Kotlin, Minecraft, NeoForge | Root catalog pins | Existing [inventory](../README.md); external runtime, not a consumer distribution |

The Create license text is taken from its release source tag; Registrate's text is from its
[1.21.1/dev branch](https://github.com/tterrag1098/Registrate/blob/1.21.1/dev/LICENSE). The Propulsion license is preserved from
upstream revision `f7591fc9d3cbd97607ffc52f9da77861071742b7`; the published JAR declares MIT but contains
no license file. No source-to-binary reproducibility claim is made. The Aeronautics bundle and all three
embedded mods carry the same license overview. Asset reservations remain in force independently of
code licensing. The table records the directly selected runtime and inspected embedded components,
not an exhaustive legal audit of Minecraft or all tooling/transitive dependencies.

## Original physics release hashes

`verifyConsumer` checks SHA-512 of the original downloaded physics distributions before launch:

- `create-aeronautics-bundled-1.21.1-1.3.2.jar`: `dc7e8e1148fa442243889ba29412207137e1bb86206e6c92bc0a589f1374a466e93de22bdbf14f753984d86c29308fbbdc0f14a4b9618b14c0ac1666a36b46f5`.
- `createpropulsion-1.1.5.jar`: `6096c9c5cb220219a0b58b56f9fefd63f8b79a0967d326727b4206d62df1813da73956aeae2fa5b839160a1bb5af8e099726d12d2d93f10e5f157beb85bb9c52`.

The Sable hash is recorded in its existing provenance and checked here as well. Extraction preserves
the complete bytes of embedded JARs; Loom performs ordinary development remapping afterwards.

## Client-only terrain rendering

These original downloads are installed automatically for `consumers/flight/run/client` only.
They are not redistributed, copied into source control or bundled in Orbitalis.
`gradle/client-visuals.gradle.kts` records the release IDs and SHA-512 digests of every selected original JAR.
Voxy, Roxy and Connector are installed unmodified. Loom prepares the native NeoForge dependencies and
byte-for-byte extracted nested modules for the development namespace, preserving upstream legal notices.
The full Forgified API supplies newer/equal versions of the four API modules also embedded in Sodium;
its development copies are selected instead of duplicate Sodium copies. Sodium's service carrier
remains a library; its separate embedded renderer remains a mod. No upstream implementation is vendored.

| Component | Original source / exact release | License and holder |
| --- | --- | --- |
| Voxy | [Cortex / 0.2.16-beta, H3w2nVdU](https://modrinth.com/mod/voxy/version/H3w2nVdU) | All Rights Reserved, MCRcortex; [upstream terms](https://github.com/MCRcortex/voxy/blob/dev/LICENSE.md) |
| Roxy | [Rasa Novum / 0.4.0, cDmwpuYr](https://modrinth.com/mod/roxy/version/cDmwpuYr) | MIT; [source and terms](https://github.com/RasaNovum/Roxy) |
| Sinytra Connector | [2.0.0-beta.17, IITF0PRC](https://modrinth.com/mod/connector/version/IITF0PRC) | MIT, Sinytra; [upstream terms](https://github.com/Sinytra/Connector/blob/master/LICENSE) |
| Forgified Fabric API | [0.116.14+2.3.0, dAxle9F7](https://modrinth.com/mod/forgified-fabric-api/version/dAxle9F7) | Apache-2.0, FabricMC and Sinytra contributors; [source and terms](https://github.com/Sinytra/ForgifiedFabricAPI) |
| Voxy World Gen V2 | [2.4.2 NeoForge, PzNUZlWw](https://modrinth.com/mod/voxy-worldgen/version/PzNUZlWw) | iSeeEthan custom license; [upstream terms](https://github.com/iSeeEthan/voxy_worldgen_v2/blob/main/LICENSE) |
| Cloth Config | [15.0.140 NeoForge, izKINKFg](https://modrinth.com/mod/cloth-config/version/izKINKFg) | LGPL-3.0-only, shedaniel; [source](https://github.com/shedaniel/ClothConfig) |
| Better ModList | [21.1.4, ZID4kkto](https://modrinth.com/mod/better-modlist/version/ZID4kkto) | MIT; [source](https://github.com/1foxy2/neo_mod_menu) |
| Sodium | [0.8.12 NeoForge, S3DUMfBo](https://modrinth.com/mod/sodium/version/S3DUMfBo) | PolyForm Shield 1.0.0, CaffeineMC; original `LICENSE.md` remains in the JAR |

Roxy explicitly supports this original Minecraft 1.21.11 Voxy binary on NeoForge 1.21.1 and lists
Sable/Create compatibility. Connector is included as requested, alongside Roxy's required version
translation. This records upstream claims, not a guarantee of every terrain/ship rendering combination.
No permission to redistribute Voxy or sublicense any upstream component under Apache-2.0 is implied.
