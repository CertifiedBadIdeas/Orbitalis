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

# Cubic planet player proof of concept

This is a bounded player/terrain experiment for the [accepted design](cubic-planet-design.md), not
production planetary generation or a chosen large-world storage architecture. It shares the existing
`orbitalis:planet_lab` dimension without changing that saved-world ID. Existing chart pads and flight
diagnostics remain separate. Use an isolated disposable client world in this task checkout.

## Scene and controls

Run `./gradlew-sandbox-dev-parallel runClient` after `./worktree-task doctor`. Do not run a build while
the managed client holds the workspace slot. As an operator use:

```mcfunction
/orbitalis lab cube enter
/orbitalis lab cube sample
/orbitalis lab cube leave
```

Entry explicitly builds a solid 32-block reference cube centred at (2048,96,2048), with three blocks
of +Y relief and two blocks of +X relief around the demonstrated edge. Six face colours distinguish
surfaces; stone fills the interior. The reference cube remains solid through edges and corners.
Unequal relief tapers down before each convex edge, avoiding a trench on the gravity boundary. The fixture is a global block-occupancy function, not six independent maps.
Walk forward from the initial view to leave the raised green face and fall towards the orange +X face.
WASD and mouse look use the current collider frame; jump acts along local up. Creative flight remains available with normal double-jump activation; jump/shift lift follows
local up/down, with no gravity while flying. Game mode is not changed. Fall damage is suppressed in the bounded experiment.

Entering the scene enrolls a player until explicit `cube leave` or travel to another dimension.
Membership has no distance cutoff; moving past the former 64-metre boundary keeps the same gravity
and camera mode. Spectators bypass custom movement. Native player entity data synchronizes the
server-owned membership boolean. Optional player NBT `orbitalis:cube_player` contains integer
`version: 1` and byte boolean `enabled`; missing, malformed and unknown versions decode disabled.
A legacy player without a record inside the old 64-metre region is enrolled on the next server tick.
Older players stranded beyond that region must use `cube enter`. An explicitly disabled or unknown
record never triggers proximity migration. Membership survives save/load; return points remain transient.
Outside planet_lab, ordinary Minecraft movement remains in effect. Enrolled non-spectators bypass
vanilla's global-Y void damage because Y is not planetary altitude; other damage is unchanged. The
world's block build-height bounds remain unchanged, and this does not add terrain below them. Loose entities, fluids, Sable ships,
beds, ladders, swimming, riding, elytra, crouching/crawling, stepping up blocks and production survival
rules are not supported by this standing-player experiment. Use a disposable scene without these features.
The demonstration does not validate full-block interaction/reach or multiplayer combat while the view
is turning; camera easing and the instantaneous physical eye can differ during that interval.

The exit restores the transient entry position and view; after a runtime restart it falls back to the
Overworld spawn. Repeated entry does not refill excavations. A lodestone at the centre marks an initialized
fixture across restarts; removing it makes a populated site reject regeneration rather than overwrite edits.
To replace an older cut fixture, run `cube leave`, then `/orbitalis lab cube rebuild`, then `cube enter`.
The operator-only rebuild erases all blocks/edits in the 40×40×40 fixture volume and rejects the request
while a non-spectator player is in the scene. Ordinary entry still preserves edits.
This marker is a diagnostic convention, not a versioned planet save format. The scene is never built at startup.

## Physics and presentation boundaries

The adapter uses one block per metre and 20 game ticks per second. CubicGravity accepts an explicit zero edge width. In this mode the effective near-surface radius is
max(abs(x),abs(y),abs(z)); at exact ties it uses a symmetric subgradient, including zero at the centre.
The same potential and differentiated altitude blend preserve central inverse-square gravity far away.
Positive widths retain the existing smoothed flight diagnostic behavior and constants.

The prototype stores the usual player network position as a fixed vertical anchor 0.9 metres below the
physical centre, regardless of orientation. A standing 0.6 by 1.8 metre AABB aligns to one of six axes.
A turn reserves room for both standing frames. At a surface contact it may shift the centre outward
along the new up by at most 0.6 metres (plus numerical tolerance), the difference in collider half-extents.
Native collision sweeps find the minimum clearance, and both the old body's displacement path and
final clearance bounds must be free; an overhang or obstructed destination still delays the turn.
Unobstructed turns preserve the centre, and all turns preserve inertial velocity. This contact correction
prevents a slow player from retaining the old collider and sliding along the adjacent face, or catching
the previous face sideways immediately after a turn. It does not rotate or reset velocity.
Standing frames remain six-axis; continuous physical orientation in the altitude blend is not established.
Gravity does not wait for an obstructed turn. Collision sweeps tangential motion first, then motion along local up, with support tested along
that same axis. This removes vanilla's global-Y ordering bias at rotated corners. Custom locomotion predicts on the controlling client; the server retains Minecraft movement
packet/collision validation. Vanilla's global-Y floating counter is disabled only inside the bounded scene.
This is not a hardened production multiplayer movement protocol.

At an accepted adjacent-face turn, heading is transported by the 90-degree rotation around the
shared edge. The controlling client rebases vanilla yaw into the destination face chart, including
previous/head/body yaw, while pitch is unchanged. The server receives this yaw through ordinary
Minecraft movement packets and never rebases it again. A transient modulo-four client counter lets
the visual frame compensate the chart change before easing; no new packet or saved state is added.
Camera and visible player orientation approach the collider frame at up to 180 degrees per second.
For an isolated adjacent-face turn the visual rotation follows the shared edge, without the extra
yaw twist from interpolating independent global-Y face orientations. Looking along that edge keeps
the world view direction fixed as the image rolls. Overlapping turns may redirect unfinished easing;
opposite-face teleports have no unique shared edge. Initial visual frames start at the current face.
The camera offset is clipped against terrain, and third-person zoom uses native obstruction checks.
Physical eye/view queries use the collider frame. Dedicated servers do not load camera/renderer classes.
All physics, view and network mixins guard the explicit scene region before changing vanilla behavior.

## Verification and manual observations

`./gradlew-sandbox-dev-parallel-summary -PorbitalisGameTestSuite=cube :v1_21_1-neoforge:runGameTestServer`
runs only the seven required cubic-player scenarios, with Sable still loaded. The default suite remains
all laboratory scenarios. A selected suite must produce a fresh report containing every required name
and no failed/skipped cases; an unknown suite is rejected. The slow-edge regression covers all 24
directed face transitions at zero, 0.005 and 0.03 blocks/tick, checking terrain clearance, adjacent-face
support and stability after landing. These are isolated near-edge releases, without a running start.
The heading regression covers all 24 directed adjacent-face turns at six yaws and three pitches
(432 combinations), checks forward control, and samples all three visual basis vectors throughout
an isolated 0.5-second turn. It also checks that server players do not rebase incoming yaw. These
are native movement and numerical visual-transform checks, not observed camera-rendering evidence.

Core tests cover the sharp force/potential, far field, connected interior and solid convex edges. Native
GameTests cover block placement/edit preservation, walking off an edge and landing on another face,
velocity/centre preservation in free turns, obstructed clearance paths, a local-axis jump and creative escape along a side-face normal. An inert server pilot
is not an interactive client; these tests do not prove camera appearance or packet continuity.

Observed on 2026-10-16 in an isolated client world copied from this task's GameTest world:

- Explicit leave/rebuild/enter replaced the old cut fixture with 34,562 solid blocks.
- Ordinary forward input crossed +Y to +X; server sampling after landing reported `up=east`,
  zero velocity and `grounded=true`, with centre offset approximately (18.90, 0.29, 0.50).
  First-person images showed the orange face after the turn.
- Double-jump input enabled creative flight. Holding jump moved the player outward from the +X face;
  the client retained `flying=true` and server sampling reported `grounded=false`. A third-person
  image showed the model aligned with that face. No movement-validation correction was logged.
- Rebuild was refused while the player occupied the scene. Leaving restored the entry position;
  ordinary creative movement remained available outside the bounded adapter.
- Evidence is retained locally in `build/agent-logs/cube-client/`. The original flight-input redirect
  conflicted with Sable; the final adapter modifies the resulting velocity argument instead.

Slow-edge follow-up on 2026-10-16 used a separate copied GameTest world in survival mode. Releasing
the client player from rest just beyond support at centre offset (16.32,16.9,10.5) landed on +X at
(16.9,15.83235,10.5). Releasing at (10.5,-16.32,-16.9) landed on -Y at
(10.5,-16.9,-15.83235). For each release, two server samples one second apart reported identical
positions, zero motion and `grounded=true`. First-person +X and third-person -Y images showed the
settled view/model; no moved-wrongly or moved-too-quickly warnings were logged. Evidence is retained
in `build/agent-logs/slow-edge-client/`. This establishes these two real-client releases, not remote
multiplayer or subjective comfort during the turn.

Camera-axis follow-up on 2026-10-16 measured the actual first-person camera in an isolated copied
GameTest world. In creative flight, operator placement moved the player from the +X (YZ) chart to
+Z (XY), then back, initially looking along world +Y. Each direction recorded 130 camera samples,
including 39 samples with intermediate up vectors. Forward remained +Y (maximum vector error below
5e-7); camera up stayed in XZ (off-axis component below 2e-7) and settled at the destination normal.
No moved-wrongly or moved-too-quickly warning was logged. Traces are retained locally in
`build/agent-logs/camera-axis-client/`. This validates this rendered axis change under controlled
placement, not walking comfort, overlapping turns or remote-player interpolation.

These observations establish the listed routes and creative escape. They do not establish subjective camera
comfort, all edge directions or multiplayer behavior. Remaining manual scenarios:

1. Repeat the edge route in reverse and at different speeds; assess camera comfort while turning.
2. Repeat in both third-person modes and watch the model/collider (F3+B).
3. Jump from the orange face, reverse near the seam and approach a three-face corner.
4. Add a bridge or a narrow tunnel; inspect blocked turns and camera clipping.
5. Leave, verify ordinary movement, then re-enter and confirm excavations remain.

Camera comfort, full interaction/reach, arbitrary edited terrain and remote multiplayer remain unverified.
The native client observations above complement the GameTests; a successful client launch alone is insufficient.
