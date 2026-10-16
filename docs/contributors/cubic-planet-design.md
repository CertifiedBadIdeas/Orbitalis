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

## Logical connectivity across face coordinates

Face boundaries must not require special connector blocks. Ordinary Create pipes must connect
directly; this illustrates the broader requirement for general block connectivity across the planet. Ordinary neighbour updates, redstone, item/fluid interactions and
multiblock/network discovery must be considered at surface edges and interior ownership boundaries,
including through-tunnels. Preserving connected occupied geometry alone does not satisfy this criterion.

The current six Sable charts provide local placement axes and native neighbours within each chart;
an opt-in prototype now adapts selected cross-chart interactions. Retain these six spaces as the
preferred experiment while validating broader connectivity; a bespoke replacement is a fallback,
not a selected migration. The prototype is not yet a complete production solution for this requirement. A future topology boundary must identify the physical cell,
its authoritative native location and the neighbour's local port direction, with explicit behaviour
when a required chunk is cold. Visual contact, mirrored inventories or hidden transfer buffers are not
substitutes for direct adjacency.

A generic world-query adapter is not evidence that arbitrary mods work unchanged. Mods may compute
BlockPos offsets themselves, cache native coordinates, restrict block-state directions or traverse
multiblocks using Cartesian assumptions. The supported boundary between generic Minecraft integration
and specific mod adaptations remains unresolved. Before committing to the large-planet storage model,
prove neighbour notifications, a redstone path, item/fluid transfer and a multiblock across charts,
including break/reconnect, unload/reload, two-face boundaries, corners and interior seams. Document
unsupported algorithms instead of claiming universal compatibility from a pipe demonstration.

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

The accepted edge shape is a solid convex block corner, not an angular cleft between faces.
An earlier candidate used a one-block clearance cut; the player prototype exposed a trap along
that cut, so the direction is superseded. Never deliberately carve a trench on the gravity boundary.
Relief should meet the reference edge through shared occupied geometry; the prototype tapers its
unequal face relief back to the intact cube before the edge.

Generate terrain in common planet geometry. Adjacent faces must agree on occupied blocks, caves
and edge ownership; their interior heights need not be constant or identical. Production relief
stitching and three-face corner traversal still need dedicated tests. A solid generated edge does
not guarantee traversal after arbitrary excavation or player construction. Creative flight must
remain available to escape problematic terrain during the experiment.

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
  inspect the convex edge and shared block ownership.
- Walking, jumping and falling across edges at different speeds; camera easing, collider clearance,
  repeated boundary crossings and a player-built bridge.
- A connected through-tunnel reaching the opposite surface, including centre behavior and orientation.
- Ship trajectories across face boundaries and the altitude blend, with numerical energy/convergence
  checks and no artificial position or velocity correction.
- Adaptive preparation with acceleration, reversal, a large changing construction, slow clients and
  destination failure; prove single authority and crash recovery.
- Observed cockpit/exterior transitions in both directions, including another player watching, with
  measured stalls and camera discontinuities. Storage and client architecture are chosen after evidence.
