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

# Lunar surface and ship arrival

`orbitalis:moon` is a finite native Minecraft surface, 2,800 × 2,800 blocks, with gray rolling terrain
and deterministic crater bowls/rims. Its footprint is X/Z [-1400,1400), underside Y=-64 and reference
height Y=64. Native terrain heights range from 24 to 88. Andesite, tuff and basalt provide the initial
geology; there are no ores, structures, mobs, atmosphere or oxygen gameplay in this prototype.
Exterior columns are empty. Runtime placement is still native Minecraft behavior.

## One rotating surface frame

The Moon follows a prescribed circular orbit lasting 27.32 solar days; its centre separation is derived
from Earth's gravitational parameter (about 168.94 km at native gravity 11 m/s²). See the
[physical calendar and migration contract](celestial-orbits.md). Its local +Y
always faces Earth and +Z follows orbital angular momentum. Native terrain and local gravity remain
upright in the lunar dimension; the whole frame translates and rotates in Earth-relative space.
Earth should remain overhead when viewed from the lunar surface. This synchronous lunar spin does
not rotate the Overworld, bind vanilla daylight to the physical Sun, or change `/time set` and sleep.

`SurfaceFlightFrames` owns rigid point/state/direction/angular-velocity transforms. `CelestialFlight`
samples one catalogue and simulation epoch on the server. Transfer snapshots use that frozen epoch,
including surface spin velocity; moving a position alone is insufficient. Common physics, unloaded
orbital evolution and coast/maneuver previews use `CelestialDynamics` at their actual integration time.
It combines finite-body Earth/Moon fields and solar/lunar tidal terms in Earth-relative axes, with
surface compatibility regions. Lunar-local dynamics include Coriolis and centrifugal acceleration.
Massive bodies remain prescribed and do not react to a ship or their satellite.

Maneuver node execution times retain Minecraft game-time semantics. Forecast elapsed times are
explicitly anchored to the current simulation epoch; old node epochs are not reinterpreted as orbital
clock values. Downtime pauses the system. Historical replay across administrator mass changes remains
unsupported.

## Arrival and departure

Entry and exit use the same rounded volume-distance envelope for Earth and Moon, in each body's
surface frame. The Moon reference slab has half-extents (1400,64,1400) m. Space enters its native
world within 1500 m of that volume; the native world returns to space at 1800 m. These bands cover the
top, all four edges, underside and rounded corners, independent of velocity direction. For example,
centre-line top/bottom entry is Y=±1564 and exit Y=±1864; side entry is X/Z=±2900 and exit ±3200.
The gap between entry and exit avoids immediate transfer loops. Preload forecasts and unloaded-object
wake-up use the same envelope, rather than separate height-only rules.

The orbital Sable height-cleanup bounds include the supported 100 km flight volume plus hull margin,
so the Moon and its visitors can pass beneath Earth without being deleted at native surface-oriented
Y=-10000. Other dimensions retain native cleanup thresholds. These routes do not rotate native player
walking or allow ordinary Minecraft blocks to be attached to the underside; native surface up remains +Y.

Destination terrain and entity chunks are preloaded before handoff. Source and destination tickets
add a bounded entity-ticking margin at high speed (radius 4–6 chunks; at most 16 active destination
preloads per coordinator). Up to eight previous ticket centres remain for at most 20 ticks to avoid
dropping entity visibility while loading catches up. They are released on world changes or close.
Objects-mode space skips source/destination terrain-chunk readiness entirely: it has no fixed terrain.
Native Sable plot/passenger/entity readiness and the durable transaction remain required. For native
surfaces, source-area entity loading completes after durable PREPARED while the craft is paused; the
ordinary preflight then runs again before staging, rejecting newly loaded loose entities. Explicit
entity/recovery leases still load space chunks when world-space passenger data is required. Legacy
space worlds and both native surfaces retain terrain loading checks. The existing native Sable
snapshot/transaction protocol moves hull, cargo, entities and an optional seated pilot. Its size,
connected-body and passenger limits still apply; see [ship transfer](ship-transfer.md). This does not
add a Moon-targeting consumer autopilot: pilot manually or plan maneuvers on the orbital map.

Earth receipts remain journal version five (legacy versions three/four remain readable). Lunar transactions
use version six and persist the sampled profile/epoch with explicit Moon/space IDs. Recovery validates
against that saved frame, never the current moving Moon. Unknown/incomplete metadata is preserved
and rejected. Position, orientation, linear/angular motion and world-space passenger direction are
transported together; plot-space passengers retain native plot coordinates and mount identity.
Rapier's nearest-float velocity representation is accepted exactly; arbitrary speed errors are not.

Native Sable's default two contact substeps can miss terrain at a 44 m/s impact. The surface adapter
therefore raises the per-dimension step count temporarily when a loaded hull can reach native terrain
heights, budgeting about 0.25 m of linear/rotational travel per substep, capped at 64. It respects a
higher configured count, skips objects-mode vacuum, and restores its temporary setting after each tick.
This reduces discrete contact tunneling at tested flight speeds; it is not a general continuous-collision
guarantee at extreme speeds or a crash/damage model. Bodies still collide through native Sable/Rapier.

## Terrain and distant presentation

The native generator derives a lunar-only seed from the save seed with a versioned SHA-256 namespace.
The client receives that derived seed, not the Overworld seed. Native generation and the coarse lunar
mesh use the same `LunarSurface` sampler. The map and space scene draw seeded relief and the finite
underside in the sampled rotating frame without requesting lunar chunks. The distant mesh uses
16-metre cells, shared top/step/perimeter geometry, stratified cliff colors and a shaded finite
underside. It uses 512-metre sectors, at most four background jobs and two uploads per update, with
coarse immediate coverage while fine sectors build. CPU buffer ownership/upload code is shared with
Earth. The Moon has its own gray palette and no atmosphere/cloud layer. Its proxy also remains
visible in the native lunar world once the camera clears the reference volume by 32 m, including
side and underside views; native terrain draws in front. Ground-level sky excludes the coarse slab. This seeded overview does not
yet reflect runtime excavation or construction and is not a block-exact substitute for native terrain.

## Verification

Core checks cover terrain bounds/seams, synchronous spin, state/angular round trips, finite-difference
frame derivatives, handoff hysteresis and supported-ground/Coriolis force behavior. The focused native
`moon` suite requires real finite terrain, a native high-speed ground-contact control and automatic space descent, seated survival-player/cargo
transport, physical ground contact and automatic ascent back to space. Additional real Sable roundtrips
enter and leave beside and beneath the Moon, retaining the seated pilot and chest cargo. The full suite additionally
checks Earth transfer/recovery, unloaded stations and native coast/force agreement. Client rendering
and camera continuity require a separate observed client scenario.

## Free-player approach continuity

Flight-motion protocol three carries the frozen source-to-destination rotation and transport-velocity
boost with the exact unclamped motion. A captured client velocity is transformed through that map;
it must never replace a lunar destination velocity with a vector in old axes. The authoritative server
velocity remains the fallback. The transform includes lunar origin motion and synchronous spin.
Dimension changes refresh the celestial clock/profile immediately, including its paused state.
Client interpolation uses a bounded client-tick age, not a new dimension's initially reset game time.
The same player compatibility gravity field is sampled on both sides of the handoff.

The free-player camera gradually aligns its horizon between 6,000 and 2,000 metres of clearance from
the lunar reference volume, covering top, edges, underside and corners. Both entry and exit bands
are inside the fully aligned region. This changes camera roll while preserving the physical look
ray; mouse turns follow the rolled screen axes. It does not torque a ship or rotate Minecraft
collision boxes and walking controls. A straight
look along the reference up axis has no unique horizon and retains the native roll at that pole.
Moon/space transitions use the same bounded last-frame loading bridge as Earth/space; this remains
a brief held image with readiness/fade, rather than a second continuously rendered native world.
Lunar observers continue receiving Earth's saved terrain atlas without loading Earth terrain.

The orbital map's Moon button locks its moving centre to the Moon. Celestial meshes and the camera
follow the same sampled simulation clock between map snapshots; Earth moves relative to that centre.
Manual panning releases the lock. Earth and system views restore their respective reference centres.

The rendered lunar frame uses the camera partial tick, including map focus and the native-to-root
view transform. Physical handoffs still use an immutable integer server epoch. The presentation
clock stops extrapolating after ten client ticks without an authoritative update.

Native approach bands have a shared lower bound of 1500 m with at least 300 m of hysteresis,
so small bodies retain enough approach and loading room instead of scaling the bands down.
