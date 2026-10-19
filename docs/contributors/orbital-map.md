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
the orbital map. Drag with the left mouse button to orbit through unrestricted full turns on either axis;
the camera keeps a continuous up direction through both poles; middle-button drag or Shift+left drag
moves the scene in its current camera plane. Hold WASD or the arrow keys for continuous panning
at a zoom-relative speed; diagonal input is normalized. Keyboard panning stops on release, window
focus loss, reset, resize or closing the screen and is disabled while editing a maneuver field.
Scroll over the map to zoom; use Home or Centre on planet to restore the overview. Select a construction by its marker
or a row in the sidebar. Scroll over the list to reach additional rows; Centre on object moves the
camera focus to its current sampled position. Escape, Done or the configured keyboard binding closes
the map. The screen does not pause the server or alter flight controls/physics.

The cyan cross marks the viewer. Light markers denote loaded native constructions; muted markers
represent unloaded motion-index entries, and the selected marker is gold. Markers overlay the planet
for navigation and are selectable through it. The sidebar reports native name (or UUID label), loaded
state, signed vertical altitude relative to reference sea level Y=64, speed and physical distance from
the viewer. Unloaded names are UUID labels because the motion index does not own native names. The
scene is an overview: it shares the actual 32-metre planet relief/fallback mesh, not live ship block
geometry. Cyan lines show ballistic forecasts; the selected object's line is gold. The Flight plan sidebar adds separately coloured maneuver previews; a multi-body selector remains future work.

Forecasts use each object's current physical position and velocity and the server's actual surface
gravity. They omit thrust, future autopilot actions, drag, terrain and hull extent. A suborbital arc
ends on the uniform reference slab. A periodic orbit is traced for a complete turn and joined at its
start; a nonspherical-field orbit that precesses remains open after a complete turn. The sidebar
reports that distinction, impact, range termination or the time budget. These are numerical paths in
the real reference field, not fitted Kepler ellipses or autopilot routes.

A screen-owned worker updates up to eight nearby trajectories once per second, prioritizing the
selected object. It receives immutable snapshots and never touches a world or native physics handle.
Each forecast has at most 12,000 integration steps and 602 samples, with a 3,600-second maximum
horizon and a 100 km radius limit. Longer periods may remain incomplete; objects beyond that radius
remain selectable but have no preview. The worker is interrupted and discarded on screen close.
Lines share marker projection and viewport clipping. They render in world space against the same relief depth as the planet: hidden arcs disappear behind the visible surface. Object and maneuver markers remain selectable navigation overlays.

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

## Version-five map network contract

`orbitalis:map_request` carries a screen-local random UUID token and an open/close flag. The native
connection supplies player identity. Only Overworld/space subscribers are admitted. A 100-server-tick
lease is refreshed every 40 client ticks; close/logout/unsupported-dimension travel removes access.
An old token cannot close a newer screen. Server ticks send at most one `orbitalis:map_snapshot` per
viewer per ten ticks, even across rapid reopening. Expired subscriptions cause no object sampling.

A snapshot carries that token, a monotonic service tick, physical viewer position, native surface gravity, total count and at
most 4096 distinct object records. Records contain UUID, a plain name of at most 32 UTF-16 characters,
physical position, velocity, loaded flag and motion epoch. No native NBT, inventories or storage
addresses are sent. Finite position magnitude is bounded to 100 million metres and speed to 10,000
m/s. Counts are validated before list allocation; duplicate identities and control characters are
rejected. Gravity must be finite and within 0.000001..1,000,000 m/s². The maximum valid encoded snapshot fits Minecraft's one-megabyte client payload limit.
For overflow, nearest objects are retained and shown count differs from total. The client accepts only
the current screen token and nondecreasing snapshot ticks. It marks missing/stale responses after
three seconds and hides stale trajectory previews. Forecasts do not move the authoritative object
markers. Screen state is discarded on close. Version two added gravity, version three added the bounded flight-plan messages below, version four added the celestial catalogue and simulation epoch, and version five carries the Earth spin epoch. Mismatched clients are rejected during native protocol negotiation; existing map/atlas data is unchanged.

## Verification

`MapCameraTest` checks actual scene-matrix agreement at different aspect ratios/rotations, distant
focus, unclipped line endpoints and zoom/pole limits. `-PorbitalisGameTestSuite=map` requires `mappacketandlease` and
`mapnativeorbitalobjects`: native buffer roundtrips and maximum payload size, malformed counts,
duplicate UUID/gravity rejection, lease expiry/close/logout/token isolation/rate limiting, native Sable live
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

Trajectory preview verification on 2026-10-18 used a separate new flight-consumer world and native
framebuffer captures at GUI scales 1 and 2. A periodic motion-index fixture produced a continuous
228.9-second, 459-point closed loop, and a suborbital fixture produced an open impact arc. Sidebar
selection changed line colour/status; native screen drag and wheel handlers preserved projection and
clipping. Zoom-out fitted the full loop in the portrait viewport. Closing with the map key terminated
the screen worker, restored world/menu rendering, and reopening allowed fixture cleanup. These
fixtures verify client presentation of received orbital state; native body ownership and no-loading
behavior are covered separately by the GameTests. Temporary preview probes are not shipped.

## Maneuver nodes

Select a ship, then **Flight plan**. Use **+** to append a node; **<** / **>** choose a node and **−**
removes it. Right-click the selected gold coast line to add a node at that forecast time. The four editable fields are time in seconds after the plan epoch (T+), prograde, normal
and radial delta-v in m/s. Positive prograde follows velocity; normal follows position cross velocity;
radial is the outward component perpendicular to velocity in the orbital plane. Negative values reverse
those axes. Purely radial or stationary states have no orbital basis and cannot produce a non-zero node.
The first appended node defaults to 120 seconds ahead. Removing all nodes and adding one starts a fresh
epoch. Time continues while this screen is open and pauses when the server stops.

The gold line remains the selected object's gravity-only forecast. Purple lines and numbered markers
show the local draft after instantaneous impulses in the actual reference field. Editing previews does
not fire engines. **Save** stores the plan; **Reload** discards the draft and reads the server version.
**Execute** asks an installed engine adapter to run the saved plan, and **Stop** releases that controller.
Save or reload a draft before selecting another ship. Editing is disabled while engine control is active. Without an adapter, saving and previewing still work;
the ordinary Orbitalis mod contains no Propulsion engine controller. The flight consumer supplies the
optional adapter, alongside its existing independent orbit/land commands.

A plan contains at most eight nodes, strictly increasing by at least 0.05 s, within 0.05..3600 s of its
epoch. Each vector is finite and at most 500 m/s. Each coast segment has the existing numerical budget;
a plan adds at most nine segments. A collision/range event truncates the plan before later nodes. Only
an actually periodic final coast is closed. Preview impulses have zero duration; real engine burns
have finite duration and can leave execution error. Terrain, vessel geometry and other objects are not
collision-planned. Main-body contact ends the numerical forecast, not a guaranteed landing solution.

`orbitalis:flight_plan_command` carries the current map lease token, ship UUID, action (read/save/execute/
stop), expected revision and bounded plan. Connection identity selects personal ownership. Mutations
require creative mode and permission level 2, a directory entry and the current revision. Reads never
wake a native body; execution adapters must reject unloaded targets. `flight_plan_state` replies with
the same token/ship, Overworld game tick, current revision/plan, adapter availability, running flag and
a bounded error. Requests are limited to one per four service ticks. Screen token and selected UUID
prevent an old response replacing another ship's draft.

`orbitalis_flight_plans` is version-one Overworld SavedData with at most 256 owner/ship entries. Each
entry stores owner/ship UUIDs, positive revision, epoch tick and nodes. Empty plans retain their revision.
Unknown versions, malformed numeric fields, duplicate keys and out-of-bounds nodes are rejected rather
than silently overwritten. Plans persist across restart; active consumer execution does not resume.


Maneuver-editor verification on 2026-10-18 used a new, task-owned consumer world. Native screen button
and EditBox input created a prograde/normal node, changed the purple forecast, rejected NaN with Save
disabled, saved through the real server connection (revision 0 to 1), and rejected execution against an
unloaded index fixture without waking it. A native right-click on the coast created another timed node.
Framebuffer captures at GUI scales 1, 2 and 3 checked controls, clipping and forecast alignment. After
moving line rendering into the planet depth pass, scales 1 and 2 and a rotated camera showed both gold
and purple far-side arcs hidden by the actual relief mesh, with near-side arcs retained. Closing the
map released its worker/GPU buffers; owned fixtures were removed and the client saved/exited normally.
Headless native engine scenarios separately verify actual execution; index fixtures here only verify
presentation and command rejection. Logs, actions and framebuffer captures are retained as task evidence.

## Celestial system views

The map receives the persisted simulation epoch and compact Sun/Earth/Moon profile with the native
ship directory. The shared [celestial model](celestial-orbits.md) derives positions and closed parent
orbit curves; daylight and `/time` commands do not set their phase. Use Earth, Moon, Earth–Moon or
Solar system above the scene to frame the corresponding scale. Wheel zoom and drag orbit remain
available; Home restores the Earth view. Celestial orbit lines share the relief depth test.

These celestial views do not yet enable lunar forces or a lunar surface. Existing vessel predictions
and flight/transfer behavior remain Earth-only until the moving-field integration is complete.
Network negotiation is version four; model parameters/epoch are bounded and older peers do not
silently decode the changed snapshot layout.

Verification on 2026-10-18 covered native map codec/lease GameTests, pure celestial scene/framing
tests and `verifyLocalFull`. A disposable flight-consumer world showed closed lunar and terrestrial
orbit curves, Moon focus tracking and separate view/flight-plan controls in portrait and landscape
windows. Captures and client logs are retained with task evidence. The first preview encountered a
Voxy shutdown stall after all dimensions were saved; its thread dump was retained and that client
was terminated. The subsequent preview saved and exited normally. Lunar markers are presentation
placeholders; this observation does not verify lunar terrain or vessel encounters.

Moon view follows the lunar centre on the sampled simulation clock, so Earth revolves relative to
the stationary lunar map centre. Manual panning releases body tracking.
