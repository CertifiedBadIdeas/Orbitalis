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
Create 6.0.10-280 slim and Ponder 1.0.82+mc1.21.1 are additionally compile-only dependencies of the
Orbitalis NeoForge leaf for optional seam address adapters. Those original mixins call native algorithms;
they do not copy upstream implementations, distribute the libraries or import their assets. Their pinned
source JARs were inspected to identify coordinate and port query boundaries. The runtime inventory and
separately retained upstream license texts below apply to these API references too.
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
