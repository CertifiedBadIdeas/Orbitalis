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

# Finite cubic planet and surface/orbit continuity

Status: accepted design direction, 2026-10-16. This records intended behavior, not implemented
planet generation or a claim of seamless client flight. The [gravity experiment](cubic-planet-gravity.md)
and [ship transfer diagnostic](ship-transfer.md) describe current implementation and evidence.

## Planet volume and topology

The planet is one finite, closed, solid cubic body with six habitable faces. There is no bedrock
floor terminating its interior. A tunnel can pass through the interior and emerge on the opposite
face; paths and excavations must agree in one planet-centred geometry. Walking across an edge and
continuing onto a neighbouring face is intended to be possible without a travel command or loading screen.

The storage layout is undecided. A single Minecraft Level, multiple face representations or another
adapter must preserve the same physical volume, block ownership and connectivity. Six unrelated flat
worlds do not satisfy this requirement. Ordinary Overworld compatibility remains as described in
[flight feasibility](orbital-flight-feasibility.md); it is not silently converted into this planet.
Planet size, terrain depth and the treatment of the central interior remain open.

## Gravity and motion

Near a face, gravity points inward along that face's normal. With increasing height it transitions
towards the planet centre, becoming central and inverse-square in the far field. Directly above a
face centre the directions already coincide. This is a gameplay field, not a claim to reproduce the
Newtonian field of a uniform solid cube.

Sharp changes of gravity direction between face regions near the surface are accepted. For a
planet-centred position, the largest absolute coordinate identifies the face region; boundaries occur
where dominant coordinates tie. On the reference cube these meet at edges and corners; within the
volume they extend along separating planes. An ordinary diagonal across a face is not a boundary.
Terrain excavation and construction do not move these regions. A reference cube-shell height and the
height above actual terrain must be distinguished.

Crossing a region boundary changes acceleration, not position or velocity. An unsupported player can
leave an edge, retain momentum, enter the neighbouring gravity region and fall towards its surface.
There is no requirement for automatic adhesion around an edge. Actual terrain determines loss of
support; crossing the field boundary determines the force change. Those events need not coincide.
The directional jump must diminish through the height transition and disappear in the central far field.

A regular interior field should weaken towards zero at the centre and reverse direction after passing
through it: the second half of a through-tunnel is an ascent towards the opposite surface. Exact
boundary/corner force rules, centre regularization and force magnitudes remain to be specified.
The sharp field must be derived consistently, including its potential/energy behavior; simply replacing
the current smooth direction selector is not an approved numerical implementation.

The existing CubicGravity experiment deliberately smooths edge directions. Acceptance of sharp near-surface
changes supersedes that smoothing as the intended gameplay direction; it does not change today's code,
parameters or tests. Whether a negligible numerical transition is needed remains an experimental question.

## Player orientation

The local up direction changes with the gravity region. Camera and visible player orientation should
turn towards it over time, with a bounded angular speed or an easing curve rather than an instantaneous
90-degree view jump. Quaternion interpolation can use a Bezier timing curve; no particular curve or
turn duration is selected yet.

Physical motion, collision-body orientation and visual orientation require explicit coordination.
Visual easing must not rotate velocity, move the camera through solid blocks or let a turning collider
intersect terrain. Tight tunnels, grounded movement and the three-face corner require dedicated tests.

## Terrain at edges

The accepted generation direction is a narrow clearance cut along the reference cube edges: a one-block
offset/gap is the initial candidate. Where terrain rises above an edge, the neighbouring faces should
remain separated by an angular cleft rather than forming a solid bridge across the transition.
This clearance is intended to let a player lose support and fall into the adjacent face region.

Generate the cut and surrounding terrain in the common planet geometry. Adjacent faces must agree
on occupied blocks, caves and edge ownership; their heights need not be constant or identical.
The exact cut cross-section, block discretization, width measurement and three-face corner shape
are unresolved. One block is a prototype value, not a proven clearance for a rotating player collider.

The cut is natural terrain, not an indestructible boundary. Players may excavate it or bridge it;
normal collision and the unchanged gravity field then determine movement. Generation cannot guarantee
the same fall after arbitrary player edits.

## Adaptive surface/orbit preparation

The surface and orbit may use separate Minecraft representations while sharing one physical state.
The intended transition should preserve motion, ship contents, passengers, controls and view continuity.
Sable constructions include potential orbital factories; the current diagnostic allowlist and size limits
do not establish support for arbitrary machinery, passenger graphs or connected ships.

Prepare the destination before crossing a handoff boundary. Start preparation from predicted time to
crossing, using velocity towards the boundary, relevant acceleration, estimated server/client loading
cost and a safety margin. A simple forecast is d = v*T + 0.5*a*T*T plus a distance margin; curved paths
and variable acceleration require a better crossing prediction. Full speed alone is insufficient.
The discussed 20–21 km band is illustrative, not a selected altitude or world-height configuration.
Prefer moving the start of preparation while keeping the handoff boundary predictable.

Preparation may reserve destination storage, load surroundings and stage ship/client data. The source
remains the only active authority; a staged factory must not tick or produce items. Reconcile edits,
inventories and exact motion at a common physics timestamp before committing the ownership change.
Actual readiness, including client readiness, is required; a time estimate is not confirmation.
Use distinct ascent/descent thresholds and retain useful preparation briefly during manoeuvres to avoid
repeated loading and cancellation. Exact thresholds, retention limits and the fallback when readiness
is late remain open; crossing into an unready destination is not acceptable.

Server preparation alone does not establish visual continuity. Client destination rendering, camera,
ship tracking and observer behavior need a separate experiment. Both representations must evaluate the
same physical gravity field. Crossing the boundary does not itself insert a ship into orbit.

## Required experiments before implementation claims

- Cross-sections through a flat edge, unequal-height mountains, a ravine, a cave and a three-face corner;
  inspect the generated gap and shared block ownership.
- Walking, jumping and falling across edges at different speeds; camera easing, collider clearance,
  repeated boundary crossings and a player-built bridge.
- A connected through-tunnel reaching the opposite surface, including centre behavior and orientation.
- Ship trajectories across face boundaries and the altitude blend, with numerical energy/convergence
  checks and no artificial position or velocity correction.
- Adaptive preparation with acceleration, reversal, a large changing construction, slow clients and
  destination failure; prove single authority and crash recovery.
- Observed cockpit/exterior transitions in both directions, including another player watching, with
  measured stalls and camera discontinuities. Storage and client architecture are chosen after evidence.
