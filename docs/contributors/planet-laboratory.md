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

# Minecraft planet laboratory

This opt-in diagnostic scene targets Minecraft 1.21.1 / NeoForge. It connects the independent
cube-sphere coordinates and two-body stepper to Minecraft commands and server ticks. It is an
experiment, not the starting planet or a seamless flight implementation. Use a disposable world.

## Try the scene

Launch `./gradlew :v1_21_1-neoforge:runClient`, create a fresh world with cheats enabled, then run:

```text
/orbitalis lab enter
/orbitalis lab sample
/orbitalis lab orbit
/orbitalis lab leave
```

On Windows, replace `./gradlew` with `gradlew.bat`. Commands require permission level 2.
The laboratory does not change the player's game mode. Creative mode is useful for inspecting its
void surroundings; stepping off a pad in survival can cause a fall into the void.

| Command | Behavior |
| --- | --- |
| `/orbitalis lab enter [face]` | Prepare a bounded pad and enter it; default face is `positive_z` |
| `/orbitalis lab sample` | Report chart u/v, radial altitude, Cartesian position and physical speed |
| `/orbitalis lab orbit` | Move to the observation platform and start one reference orbit |
| `/orbitalis lab stop` | Stop the shared reference orbit while in the laboratory |
| `/orbitalis lab leave` | Return to the recorded entry location, or the Overworld spawn fallback |

Faces are `positive_x`, `negative_x`, `positive_y`, `negative_y`, `positive_z`, `negative_z`.
Each is a separate 33 by 33 block pad in the same `orbitalis:planet_lab` void dimension. Pads are spaced
128 blocks apart; entering a face fills only air cells and preserves existing blocks. A blocked arrival
point rejects entry. Nothing automatically moves players into this dimension or replaces Overworld terrain.
The dimension is available when world data is loaded; its pads are prepared only on request.

The reference scene uses a 16 m radius, nonrotating body with surface gravity 9.81 m/s² and an ellipse
reaching 6.4 m above its surface. Green particle rings mark the body, and bright particles trace the
orbit. One server tick advances 0.05 simulated seconds in four substeps. The shared demonstration
stops after one period; another operator can restart or stop it. There is no physical probe entity.
These scene parameters are different from the independent 10 km mathematical accuracy experiment.

The surface adapter treats one Minecraft horizontal block as one local chart metre and converts
Minecraft velocity from blocks/tick to chart rates and metres/second before applying the projection
Jacobian. At chart edges, those local chart distances do not equal distances on the sphere. `sample`
rejects positions outside a chart rather than wrapping or clamping them.

Return points and the active orbit exist only in server memory. Re-entering another face from inside
the laboratory preserves the original outside return point. After a server restart, or when that
source dimension is unavailable, `leave` uses the Overworld heightmap at its shared spawn. Pads remain
ordinary saved blocks. Return locations can change while a player is away; this is a diagnostic tool,
not a production safe-travel system. There is no new persistent ship data or network protocol.

## Automated evidence

```sh
./gradlew verifyLocalFull
./gradlew :v1_21_1-neoforge:runGameTestServer
```

The first command checks modules, source boundaries and the production archive. The second starts an
isolated headless Minecraft server in the target's `run/gameTestServer` directory and executes seven
required scenarios: void dimension loading; bounded pads and edit preservation; command entry/sample/return
with unchanged game mode; blocked entry; operator permission; coordinate bounds and velocity units;
and orbit progression through server ticks, stop and runtime disposal.

Results are written to the target's `build/test-results/gameTestServer/results.xml`. The Gradle run
removes the previous report before starting and fails if a fresh report is missing, has fewer than
seven cases, or contains failure/error/skipped outcomes. This detects launcher startup failures that
may otherwise return exit code zero. CI runs both commands and retains the XML and logs.

Test classes, the empty template and the test-only flat preset belong to a separate source set.
Ordinary client/server runs exclude them, and production archive checks reject leaked fixtures.
The generated preset embeds the canonical lab dimension definition to accommodate the 1.21.1
GameTest server's world creation path; it does not override presets in the distributed mod.

## Evidence still needed

A headless pass does not establish client particle visibility, camera direction, multiplayer presentation
or discovery of the dimension through ordinary world creation. Manually enter all six pads, walk and
sample coordinates, inspect one complete orbit from the observation platform, stop/restart it and leave.
Record the client revision and observations before claiming visual correctness. Repeat with a second
observer to check the shared demonstration, and restart while in the laboratory to inspect fallback exit.

The pads do not join at their edges. Blocks, collisions, gravity and the player camera remain normal
Minecraft behavior. There is no powered ascent, orbital player movement, wrapping terrain, custom world
renderer, ship transport or Sable runtime integration. Those experiments follow after this diagnostic
scene is observed. See [orbital-flight-feasibility.md](orbital-flight-feasibility.md) for the unresolved
surface/orbit and construction-transfer requirements.
