<!--
The Orbitalis Developers

Copyright 2026 The Compukters Developers
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

# Architecture and Minecraft version boundaries

The initial target is Minecraft 1.21.1 / NeoForge 21.1.252. Versions are pinned in
 gradle/libs.versions.toml, based on Compukters' working 1.21.1 toolchain.
Architectury Loom 1.17.491 and Kotlin 2.4.10 are deliberately pinned, not dynamically updated.
Gradle runs on JDK 25, while shared/domain and 1.21.1 code compiles to Java 21.

## Ownership

| Area | Owns | Dependencies |
| --- | --- | --- |
| modules/common/core | Domain models, simulation and independent contracts as added | Kotlin/JDK 21 only |
| modules/minecraft/shared/common | Canonical Minecraft behavior and presentation | Core and Minecraft; no NeoForge |
| modules/minecraft/shared/neoforge | Registration, events, networking and loader adapters | Core, common and NeoForge |
| modules/minecraft/v1_21_1 | Target-specific compatibility and archive composition | Shared sources and target libraries |
| build-scripts | JVM conventions and canonical shared source wiring | Gradle plugins |
| tools | Isolated builds, cache publication, worktree lifecycle and verification | Python, Git and GNU tools |

The Minecraft common layer is currently empty; the sole mod entry point uses the independent mod identity.
Core now contains an experimental finite-planet coordinate model and two-body stepper. A test-source
laboratory measures a full reference orbit through all six surface charts and back to its start.
There is no dependency on Compukters, its VM, Rust runtime or addon SDK. There is no committed choice
of production orbital physics, rendering engine, rocket mechanics or external space integration.
The exploration-first direction, Sable source investigation and proposed seamless surface/orbit
experiments are recorded in [orbital-flight-feasibility.md](orbital-flight-feasibility.md).
That note distinguishes desired behavior and proposals from implemented or runtime-verified support.

The target leaf compiles shared sources directly. When a second target is implemented, the selected
orbitalisActiveMinecraftVersion owns canonical IDE roots; inactive targets compile generated mirrors.
Do not edit mirrors or configure a leaf to consume another version's source roots. Version differences
belong behind small compatibility contracts with implementations only in target leaves; introduce those
contracts when a real difference exists rather than predicting future APIs.

NeoForge's development run groups core, common and loader source sets as one mod. Production packaging
merges project outputs and lets Loom nest Kotlin stdlib and remap the 1.21.1 JAR. verifyProductionJar
inspects the resulting archive, including Java bytecode ceilings and legal notices.

## Adding the 26.1 family

1. Confirm the exact supported 26.1 patch, NeoForge and official toolchain requirements from pinned
   sources/documentation. Compukters uses a separate no-remap Loom leaf for this family; retain that
   separation because 1.21.1's remapping pipeline is not a generic future-version contract.
2. Add modules/minecraft/v26_1/v26_1-common and v26_1-neoforge plus version-specific pins. Include them
   explicitly in settings. Keep JVM 21 for the independent core and configure the new leaf's required
   bytecode/toolchain independently.
3. Extend the supported active-version validation in orbitalis-minecraft-shared; verify canonical versus
   generated ownership in both IDE selections and compile both targets. Never copy gameplay into leaves.
4. Extract only actual API differences into explicit compatibility interfaces. Keep server authority,
   dimension/resource IDs, networking and saved-data schemas stable or supply versioned migrations.
5. Give the new leaf its own metadata ranges, run directories and archive verification. Check both
   distributions and execute equivalent real world/server scenarios before claiming dual support.

This scaffold reduces ownership and build migration work; it does not establish source/API compatibility
with 26.1. Client-only classes must not load on dedicated servers. World authority belongs to the logical
server; validate packet bounds and identifiers at receipt and keep blocking work off the tick thread.
