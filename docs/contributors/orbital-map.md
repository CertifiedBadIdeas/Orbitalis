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

# Orbital map

Press **M** (rebindable in the Orbitalis controls category) in Overworld or `orbitalis:space` to open
the orbital map. Drag the map with the left mouse button to orbit the camera, scroll over the map
to zoom, use Home or Centre on planet to restore the overview. Select a construction by its marker
or a row in the sidebar. Scroll over the list to reach additional rows; Centre on object moves the
camera focus to its current sampled position. Escape, Done or the configured keyboard binding closes
the map. The screen does not pause the server or alter flight controls/physics.

The cyan cross marks the viewer. Light markers denote loaded native constructions; muted markers
represent unloaded motion-index entries, and the selected marker is gold. Markers overlay the planet
for navigation and are selectable through it. The sidebar reports native name (or UUID label), loaded
state, signed vertical altitude relative to reference sea level Y=64, speed and physical distance from
the viewer. Unloaded names are UUID labels because the motion index does not own native names. The
scene is an overview: it shares the actual 32-metre planet relief/fallback mesh, not live ship block
geometry. Trajectory lines, manoeuvres, ship commands and a multi-body selector are separate work.

## Ownership and lifecycle

Shared/common owns map contracts, server subscriptions, camera math, the screen and shared planet
scene. NeoForge owns key registration, native network wiring and logical client/server events. The
optional Sable adapter reads the space container's loaded bodies and existing `OrbitalObjectData`;
live UUIDs override stale motion records. Both are converted to the physical surface frame. Viewing
never requests terrain chunks, opens native holding payloads, wakes a station or creates a second
orbital simulation. Sable retains native payload ownership and loaded physics authority.

`SurfacePlanetRenderer.renderScene` submits the same cached mesh to either the existing sky renderer
or an independently projected map viewport. The screen restores viewport, depth, shader and blending/
culling enables after drawing. Marker projection uses the same camera basis as the scene. Opening a
map admits that player to the existing bounded atlas stream; closing leaves the shared client atlas
cache available for later map/orbital views. Atlas SavedData and its existing protocol are unchanged.

## Version-one network contract

`orbitalis:map_request` carries a screen-local random UUID token and an open/close flag. The native
connection supplies player identity. Only Overworld/space subscribers are admitted. A 100-server-tick
lease is refreshed every 40 client ticks; close/logout/unsupported-dimension travel removes access.
An old token cannot close a newer screen. Server ticks send at most one `orbitalis:map_snapshot` per
viewer per ten ticks, even across rapid reopening. Expired subscriptions cause no object sampling.

A snapshot carries that token, a monotonic service tick, physical viewer position, total count and at
most 4096 distinct object records. Records contain UUID, a plain name of at most 32 UTF-16 characters,
physical position, velocity, loaded flag and motion epoch. No native NBT, inventories or storage
addresses are sent. Finite position magnitude is bounded to 100 million metres and speed to 10,000
m/s. Counts are validated before list allocation; duplicate identities and control characters are
rejected. The maximum valid encoded snapshot fits Minecraft's one-megabyte client payload limit.
For overflow, nearest objects are retained and shown count differs from total. The client accepts only
the current screen token and nondecreasing snapshot ticks. It marks missing/stale responses after
three seconds; it does not invent motion or extrapolate the orbit. Screen state is discarded on close.

## Verification

`MapCameraTest` checks actual scene-matrix agreement at different aspect ratios/rotations, distant
focus and zoom/pole limits. `-PorbitalisGameTestSuite=map` requires `mappacketandlease` and
`mapnativeorbitalobjects`: native buffer roundtrips and maximum payload size, malformed counts,
duplicate UUID rejection, lease expiry/close/logout/token isolation/rate limiting, native Sable live
state overriding a stale record and unloaded-state reads without terrain/body loading. Native test
players deliberately have no real login; client receipt, GPU state, pointer input and visual layout
require the isolated client scenario as well.

The isolated flight consumer was observed in a newly created world on 2026-10-18: M opened the map
from Overworld, received the real atlas (289–314 samples), selected a loaded named Sable station and
an unloaded index fixture, and displayed their telemetry. Native mouse drag and wheel changed camera
rotation/range; both GUI scales 1 and 2 fitted a 948×511 window. Centre on object moved the selected
marker to the viewport centre. Closing with M removed the server subscription; reopening used a new
token and reset the camera. The screen also opened in space, and the existing in-world planet remained
visible after the shared-renderer extraction. Fixture objects were removed, then the world saved and
the client exited normally. The unloaded client fixture exercised index presentation; the native
GameTest separately proves reads without loading its native body. Marker hit projection is covered
by the camera tests; this client run selected objects through the sidebar. Logs and screenshots are
retained with the task's archived verification evidence.
