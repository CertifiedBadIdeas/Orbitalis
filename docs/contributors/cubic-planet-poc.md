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
`orbitalis:planet_lab` dimension without changing that saved-world ID. Earlier chart, particle-orbit and Sable platform/orbit/flight scenes and commands are retired.
The flight/orbit/space dimensions and ship transfers remain available. Existing saved blocks and
constructions are preserved; no automatic cleanup of world content occurs. Use an isolated disposable client world in this task checkout.

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
WASD and mouse look use the accepted gravity frame; jump acts along local up. Creative flight remains available with normal double-jump activation; jump/shift lift follows
local up/down, with no gravity while flying. Above the face-normal zone, up follows the sampled gravity
continuously instead of choosing a face axis. Elytra retain native activation, equipment and durability;
planing lift, pitch steering and drag are integrated in the local gravity frame. Unpowered aerodynamic
steering is bounded by the kinetic energy after gravity and before dissipative drag: lifting the nose
while already moving upward cannot create energy as the gravity frame turns. This is an energy budget,
not a speed or distance ceiling; a falling player can gain speed and native rockets can add energy.
Native fireworks use
the same physical look direction. This is game-style gliding without an atmosphere/density model;
fireworks can escape this small reference planet. Use `cube leave` to exit the scene at any distance. Game mode is not changed. Fall damage is suppressed in the bounded experiment.

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
world's block build-height bounds remain unchanged, and this does not add terrain below them. Loose entities, fluids,
beds, ladders, swimming, riding, crouching/crawling, stepping up blocks and production survival
rules are not supported by this player experiment. Use a disposable scene without these features.
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
physical centre, regardless of orientation. A standing 0.6 by 1.8 metre body, or a glider 0.6-metre cube, follows the accepted gravity quaternion.
Native collision uses a conservative world-axis AABB envelope, not an exact rotating OBB. On the
face-normal surface this reduces to the original six-axis standing boxes. At arbitrary tilt it may
reject narrow passages that an exact OBB could traverse. A turn reserves room for both body frames. At a surface contact it may shift the centre outward
along the new up by the projected growth of its envelope (plus numerical tolerance). A perpendicular
standing turn or a glider-to-standing resize needs at most 0.6 metres.
Native collision sweeps find the minimum clearance, and both the old body's displacement path and
final clearance bounds must be free; an overhang or obstructed destination still delays the turn.
Unobstructed turns preserve the centre, and all turns preserve inertial velocity. This contact correction
prevents a slow player from retaining the old collider and sliding along the adjacent face, or catching
the previous face sideways immediately after a turn. It does not rotate or reset velocity.
Continuous free-space frames follow the force throughout the altitude blend and central far field.
Collider growth is delayed when obstructed, including a glider-to-standing resize below an overhang.
Vanilla's small per-axis resting-velocity cutoff is disabled only for active scene players, so weak
high-altitude acceleration accumulates over full entity ticks rather than resetting every tick.
Native pose dimension changes restore the accepted envelope; a delayed resize retains the small body
and eye until clearance is available. Exact model/terrain clearance in arbitrary edited tunnels is not established.
Gravity does not wait for an obstructed turn. Collision sweeps tangential motion first, then motion along local up, with support tested along
that same axis. This removes vanilla's global-Y ordering bias at rotated corners. Custom locomotion predicts on the controlling client; the server retains Minecraft movement
packet/collision validation. Vanilla's global-Y floating counter is disabled only inside the bounded scene.
This is not a hardened production multiplayer movement protocol.

At an accepted turn, heading is transported by the shortest rotation between the old and new up.
Near the surface an adjacent-face turn is the 90-degree rotation around the shared edge. The controlling client rebases vanilla yaw into the destination face chart, including
previous/head/body yaw, while pitch is unchanged. The server receives this yaw through ordinary
Minecraft movement packets and never rebases it again. A transient client counter in radians, modulo 2 pi, lets
the visual frame compensate the chart change before easing; no new packet or saved state is added.
Camera and visible player orientation approach the collider frame at up to 180 degrees per game second.
The client advances visual endpoints once per game tick and samples their quaternion interpolation
with native render partial ticks. Camera and model sample the same endpoints without advancing them;
both endpoints are compensated when vanilla yaw is rebased into the next chart. This follows the
one-tick interpolation used for entity position and prevents arrival-and-wait holds between ticks.
Small angles use a relative quaternion and atan2 rather than float dot/acos, whose rounding previously
discarded tiny far-field turns.
For an isolated adjacent-face turn the visual rotation follows the shared edge, without the extra
yaw twist from interpolating independent global-Y face orientations. Looking along that edge keeps
the world view direction fixed as the image rolls. Overlapping turns may redirect unfinished easing;
opposite-face teleports have no unique shared edge. Fresh/reloaded frames and authoritative teleports initialize from destination gravity and preserve
the vanilla destination-chart yaw. Teleports reset visual easing; ordinary flight transports heading.
The camera offset is clipped against terrain, and third-person zoom uses native obstruction checks.
Physical eye, view and native look-angle queries use the accepted gravity frame; model glider banking
compares local rather than world-horizontal vectors. Elytra collision impact damage is outside this
experiment's survival rules, as is ordinary fall damage. Dedicated servers do not load camera/renderer classes.
All physics, view and network mixins guard the explicit scene membership and dimension before changing vanilla behavior.

## Sable constructions

Every loaded construction in this dimension receives gravity from the same centre and field as
players. No owner tags or distance cutoff are required. Gravity applies at the transformed centre
of mass; it does not align the hull or reset other forces. Sable integrates the body and resolves
terrain impacts. Native base gravity, drag and pressure are zero to avoid counting them twice.

Active cube players use Sable's native OBB ship contact and native tracking packets alongside
planetary terrain collision. Accepted physical orientation and dimensions feed that solver; the
network anchor remains unchanged. Deck displacement is applied once, with inertial velocity
retained when departing. Standing on a ship does not replace planetary gravity or orient the
player to its deck. A steep or inverted deck therefore cannot act as an artificial floor.
Crouch edge protection, step-up and vehicle seats are outside this prototype's support contract.

## Cube/Sable integration observations (2026-10-16)

The focused native cube suite passed twelve scenarios, including attraction on all six faces,
native Rapier terrain landing on all six faces, and controlling-player landing, stable standing,
deck translation, departure and underside ceiling contact on all six faces. The controlling-player fixture intentionally is
not a ServerPlayer: Sable bypasses its collision solver for server players and trusts client tracking.

In a copied disposable CubeFlight client world, an ordinary 11-by-11 iron deck settled on +Y.
The local and integrated-server player tracked the same native body. Normal input walked about
2.94 blocks without losing support; jump removed support, then gravity returned the player to the
deck. A world-space native impulse lifted and translated the deck, carrying the player through
its ascent and landing. A second deck settled on +X; first-person camera and third-person model
were inspected, walking retained support, and creative ascent moved outward along +X until native
tracking cleared. No moved-wrongly/too-quickly warning occurred. One native Sable movement packet
arrived before its newly created body bundle; subsequent client/server tracking matched.
These bounded observations do not establish arbitrary rotating concave hulls, seats, exact tilted
terrain clearance, multiplayer latency or production survival behavior.

## Verification and manual observations

`./gradlew-sandbox-dev-parallel-summary -PorbitalisGameTestSuite=cube :v1_21_1-neoforge:runGameTestServer`
runs twelve required cube scenarios, including native Sable attraction and six-face deck contact.
The default suite also includes the four retained flight/orbit/ship-transfer scenarios. A selected suite must produce a fresh report containing every required name
and no failed/skipped cases; an unknown suite is rejected. The slow-edge regression covers all 24
directed face transitions at zero, 0.005 and 0.03 blocks/tick, checking terrain clearance, adjacent-face
support and stability after landing. These are isolated near-edge releases, without a running start.
The heading regression covers all 24 directed adjacent-face turns at six yaws and three pitches
(432 combinations), checks forward control, and samples all three visual basis vectors at five render
substeps per game tick throughout an isolated 0.5-second turn. It also checks that server players do not rebase incoming yaw. Flight regressions cover 201 altitude
samples, a 361-sample full central-field circuit, stationary heading stability, obstructed body resizing,
native elytra activation and 180 glider steps from each of six sides, native rocket thrust direction,
mode save/load, cutoff crossing, six-direction weak-gravity accumulation through full entity AI ticks,
and the scene-local void exemption. These
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
  ordinary creative movement remained available outside the scene.
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

Flight follow-up on 2026-10-16 used another isolated copied world, native commands and a temporary
input fixture excluded from production sources. A controlled creative ascent recorded 500 actual-camera
samples from cube-centre offset approximately (18.9,10,3) to (40.5,13.8,4.1), through the altitude blend.
Camera up differed from accepted physical up by at most 0.0011 in vector distance; the largest sampled
up change was 0.42 degrees. A second 500-sample ascent crossed radius 64 and reached approximately 84
without disabling membership. After stopping flight and resetting velocity, a 300-sample free fall near
radius 62 decreased radius, with velocity aligned with the inward force in every nonzero sample.
A later return from offset (28,10,10) landed on +X at (16.9,9.25464,9.25464) and settled.

Native elytra activation and a subsequent real firework boost each retained gliding, membership and
20 health through 500 samples, including a high-altitude change from X-dominant to Z-dominant position.
Creative flight below the ordinary void floor at world Y=-204.9 retained membership and 20 health.
Reloading the copied world restored scene membership away from the surface. Explicit leave disabled
membership and restored ordinary global-Y movement. No moved-wrongly or moved-too-quickly warning
was logged. First- and third-person framebuffer images showed the planet and gravity-aligned model.
The reported abrupt black half-face was not reproduced in these views and remains unverified;
these observations do not establish absence of rendering artifacts at arbitrary locations. Traces,
client log and images are retained locally in `build/agent-logs/cube-flight-client/`. Earlier exploratory
traces with unconstrained input are retained as diagnostics, not camera-continuity evidence. A repeat
elytra activation while already gliding was rejected by vanilla; the successful activation is the first
`glide` trace. The initial client launch also exposed an inherited-field Mixin shadow failure; the
teleport adapter now obtains the native client instance directly and the final client loaded successfully.

Elytra-energy and camera follow-up on 2026-10-16 reproduced unpowered energy creation in the old
pitch-steering step and a discarded 0.0001-radian turn in native tests. Core regressions now cover
zero-gravity steering at varied climb/descent velocities and a 500-step bound glide under turning
gravity, checking mechanical energy. Native tests also check small-angle interpolation, repeated
camera/model sampling, consecutive tick continuity and chart rebase continuity.

An isolated copied-world client recorded one sample per actual rendered frame, with diagnostic data
buffered until the end of each measurement. An eight-second creative transverse flight near radius
308 produced 479 frames. After the initial 30 frames, all 298 consecutive frame pairs within the same
game tick showed changing camera up (vector difference above 1e-8), with no angular holds. A separate
12-second native elytra flight without rockets produced 720 frames, retaining gliding and membership.
Specific mechanical energy (blocks squared per tick squared) began at -0.04870, never exceeded that
initial value, and ended at -0.09625. Radius rose from 59.56 to a maximum of 69.56, then fell to 64.54;
this was a bound climb, not an escape. No moved-wrongly or moved-too-quickly warning was logged.
Evidence is retained locally in `build/agent-logs/glider-camera-client/`. These measurements establish
the listed routes and rendered orientation continuity, not general frame-time performance or arbitrary
multiplayer behavior. Escape with enough initial or rocket-supplied energy remains possible because
this small planet has finite gravitational potential; no artificial confinement was added.

These observations establish the listed routes and creative escape. They do not establish subjective camera
comfort, all edge directions or multiplayer behavior. Remaining manual scenarios:

1. Repeat the edge route in reverse and at different speeds; assess camera comfort while turning.
2. Repeat in both third-person modes and watch the model/collider (F3+B).
3. Jump from the orange face, reverse near the seam and approach a three-face corner.
4. Add a bridge or a narrow tunnel; inspect blocked turns and camera clipping.
5. Leave, verify ordinary movement, then re-enter and confirm excavations remain.

Camera comfort, full interaction/reach, arbitrary edited terrain and remote multiplayer remain unverified.
The native client observations above complement the GameTests; a successful client launch alone is insufficient.
