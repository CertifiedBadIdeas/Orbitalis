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

# Coast trajectory navigation

Orbitalis can forecast the tracked Sable construction's centre-of-mass trajectory with all thrust
removed. This is independent of the consumer autopilot, propulsion mods, navball and external ship
camera. It does not steer a construction or change its motion. The only supported frames are ordinary
Overworld and `orbitalis:space`.

## Use

Use the existing operator command root (permission level 2):

```mcfunction
/orbitalis navigation mark
/orbitalis navigation trajectory on
/orbitalis navigation trajectory status
/orbitalis navigation trajectory off
/orbitalis navigation unmark
```

`mark` stores your current Overworld location inside the initial 2000×2000 footprint and Y=-64..320.
Mark your pad before departure. This personal navigation marker is independent of the development
consumer's autopilot launch bookmark and does not retarget its controller. The marker survives restart;
the trajectory subscription does not. One marker per player, at most 1024 markers per save.

After `trajectory on`, stay in native contact with a loaded Sable construction (or a tracked vehicle
on board). A cyan line shows the next 120 seconds of coast. A yellow cross is the first predicted
entry into the Overworld return region; a red cross is intersection of the centre of mass with the
uniform reference body. A green cross marks your personal saved location. The line ends at entry,
body intersection, a 30 km range bound or the forecast horizon. A horizon/range termination does not
prove a stable orbit or escape. Use `status` for terminal event, time and physical coordinates.
F1 hides the overlay. The line is a navigation overlay visible through geometry, not a rendered ship
trail. Distant vertices preserve viewing direction inside the native far plane.

The forecast omits working engines, native drag, collisions, terrain, hull extent, attitude and other
constructions. Engine fade can still exert force after cutoff. The reference body is the illustrative
2000×2000×128 slab, not sampled Overworld terrain or an actual collision surface in space. A yellow
cross predicts the height-trigger region, not atmospheric drag, safe landing or autopilot transfer
reservation behavior. Guidance may deliberately reserve a different handoff time. Near the surface,
the line can therefore extend into terrain before the reference-body warning.

## Ownership and budgets

`CoastPrediction` in core uses velocity Verlet with 0.05 s integration steps, 0.5 s output samples,
swept line/box clipping and finite limits. It accepts the same immutable `SurfaceGravityLayer` that
supplies live construction forces. It is a general initial-state forecast, independent of the periodic
meridian shooting in `BallisticOrbit`. No spherical Kepler fit is used. The pure API optionally expands
the reference body by a conservative hull radius; the live display uses the centre of mass only.

`ShipCoastForecast.capture` copies position, velocity, field and identity on the server thread.
The worker retains no Minecraft level, native body or physics handle. Physical positions use Overworld
axes/metres; the space-frame +4096 Y translation is removed before integration. Velocities are native
metres per second, with no blocks-per-tick conversion.

`ShipTrajectoryService` owns at most 16 subscriptions per server, one running calculation and a bounded
one-entry executor queue. Requests are selected fairly, at most once per subscriber per 20 ticks.
Disable interrupts the calculation and purges cancelled queued work; server shutdown closes the worker.
No path chunks are loaded. Server publication rejects changed ship/frame/session and samples older
than 40 ticks. Loss of native tracking or frame change clears the old path; logout removes subscriptions.
The client also rejects wrong-frame/old samples, binds results to the current ClientLevel and expires
the display after three wall-clock seconds without an update. All client classes remain behind physical
client subscribers. Presentation uses the camera position and works independently of its orientation.

## Contracts

The clientbound `orbitalis:coast_trajectory` play payload uses registrar version `1`. There is no
serverbound packet; commands are the sole subscription input. It contains an optional personal marker,
an optional forecast (null clears), ship UUID, source dimension ID, source game tick, terminal event
and bounded time/position/velocity samples. Decode validates enum, count (1..602), finite vectors,
physical position/speed bounds, increasing time (0..300 s), time-zero initial sample and supported
frames. The configured live horizon normally emits at most 242 points. Wrong protocol versions are
rejected by NeoForge negotiation. This is a new protocol, with no existing client migration.

`orbitalis_navigation_markers` SavedData in Overworld writes version `1`, an NBT list of player UUIDs
and finite physical surface coordinates. Missing data creates an empty store. Unknown versions,
invalid list types, duplicate UUIDs, out-of-footprint coordinates and nonfinite components are rejected.
No existing transfer journals, consumer bookmarks or save directories are modified or migrated.

## Evidence

Core tests cover analytic native-gravity fall, swept high-speed intersection, entry direction and
open edge/corner rules, ascent arming, numerical convergence/energy and range/work budgets.
The `navigation` native GameTest suite compares a copied forecast with 1.5 seconds of real Sable
coast, checks physical frame and packet round trips/rejection, and reads a personal marker through a
fresh native SavedData reader after the I/O barrier. Run:

```sh
./gradlew-sandbox-dev-parallel-summary :v1_21_1-neoforge:runGameTestServer -PorbitalisGameTestSuite=navigation
```

The default `all` suite includes these cases. Server tests and archive checks do not prove line
visibility, colour, camera alignment or compatibility with a navball/external-camera implementation.
Those require an observed client flight: mark a pad, enable on a ship, coast, change frames, disable,
and reconnect. Until performed, visual acceptance remains pending.

Local server verification on 2026-10-17 passed all seven `CoastPredictionTest` cases and both native
navigation cases. The sampled native coast differed from the forecast by approximately 0.021 m after
1.5 s; the forecast packet contained 56 points. This short-coast result is not a long-term accuracy
bound or client visual acceptance.
