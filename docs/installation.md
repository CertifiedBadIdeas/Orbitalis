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

# Installation and first flight

The first Orbitalis Alpha, 0.1.0, is being prepared. Do not install a development/common JAR in a
normal game. The distributable is the verified, remapped NeoForge production archive.

## Base installation

1. Use Java 21, Minecraft 1.21.1 and NeoForge 21.1.252 or newer in the 21.1 series.
2. Place the same Orbitalis production JAR in the client and server `mods` directories.
   Kotlin is nested in Orbitalis; a separate Kotlin loader is not required by this mod.
3. Create a new world. In **More → Earth size**, choose 1,000, 2,000, 5,000, 10,000 (default), or
   20,000 blocks. The width determines the initial Earth/Moon system and cannot be resized later.
   On a dedicated server, initial creation uses the default width.
4. Open the orbital map with **M**. Drag to rotate, scroll to zoom, and use middle-button drag,
   Shift+left drag or WASD/arrow keys to pan. Bindings are configurable.

The Overworld has finite generated terrain, with ordinary native block and fluid behavior.
`/worldborder` is the administrative movement boundary and does not resize the terrain footprint.
Existing generated terrain is not deleted or trimmed. Use a new save for the first release;
experimental cube saves have no supported conversion.

## Optional construction flight

Install **Sable 2.0.6** separately, together with its upstream-required dependencies, on both sides.
It enables native construction physics, automatic ship travel and unloaded-station simulation.
Orbitalis does not include Sable or its assets.

For engines and the current autopilot backend, install **Propulsion 1.1.5** and its required
Create/physics dependencies on both sides. The tested development combination is documented in
[the flight stand](contributors/flight-consumer.md); its preparation scripts are not a player installer.
Aeronautics assembly tools are supplied by the external mod stack, not Orbitalis.

Automatic travel is available to independent players and supported ships without an autopilot command.
Fly far enough from the finite body's surface to cross its distance-based handoff envelope;
its size depends on world settings. Independent Creative flight is useful for a first visual visit.
A transition may briefly hold the last frame while the destination loads.

The current autopilot is an experimental Creative/operator tool, requiring a creative owner with
permission level 2 and a supported engine layout. Commands start at `/orbitalis flight autopilot`;
use command completion for `hold`, `altitude`, `orbit`, `land`, `execute`, `status`, `stop`, and `list`.
Orbit insertion and landing are separate requests. See [control limits](contributors/flight-control.md).
Ship travel has bounded plot, size, passenger and connected-body requirements; do not assume that
an arbitrary large station or every third-party entity can transfer.

**F6** toggles the ship camera; **F7** switches navball nose axes between +Y and +Z.
In vacuum, momentum persists and gravity weakens with distance. Atmospheric steering and braking
should not be assumed to work as they do near the ground.

## Optional distant-terrain renderer

Voxy is not needed for Orbitalis' orbital planet model or map. Orbitalis includes optional hooks
for the pinned Voxy/WorldGenV2 combination; these mods and their compatibility stack remain separate.
The tested preview uses Voxy 0.2.16-beta, WorldGenV2 2.4.2, Roxy, Connector, Forgified Fabric API
and Sodium. Its unusual cross-version arrangement is documented in
[the exact preview inventory](contributors/flight-consumer.md#client-terrain-lods).
Do not infer support for arbitrary Voxy versions or install ordinary Fabric API in place of Forgified
Fabric API. Treat this as an advanced optional preview, separate from the minimum installation.

## Current limitations

- Physical solar lighting, survival fuel/progression, lunar autopilot landing and terrain avoidance
  are not part of this version.
- The orbital overview samples saved terrain. Unknown areas use illustrative terrain; opening the
  map does not generate the entire planet.
- The decorative distant underside is not a replacement for native Overworld bedrock or build limits.
- Active engine commands are not resumed after a server restart. Back up worlds before updates.
- Automated server tests do not establish all real-client, multiplayer or third-party-mod behavior.
