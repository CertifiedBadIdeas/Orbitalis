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

# Finite-body gravity and low free flight

The first orbital mass model is a stationary homogeneous rectangular body, 10,000 x 10,000 x 128 m,
centred at physical (0,0,0), with its top at sea level Y=64. This is a coarse fixed body, independent
of terrain edits, ship masses and administrator border changes. It matches the initial surface and
reference image, not every generated mountain. One block is one metre; velocities use m/s.

## Field and compatibility layer

`RectangularGravity` in core evaluates the Newton volume integral and its specific potential, zero
at infinity. Its mass scale is calibrated by downward acceleration at the centre of the top face.
The Minecraft adapter takes that acceleration from the native downward Sable Overworld configuration
(default 11 m/s^2), rather than adding another surface gravity. Zero, tilted or upward native gravity
is outside this prototype's configuration contract.

Near the body the implementation evaluates the analytic eight-corner primitive. The published
[prism potential](https://www.fatiando.org/choclo/latest/api/generated/choclo.prism.gravity_pot.html)
and [acceleration](https://www.fatiando.org/choclo/latest/api/generated/choclo.prism.gravity_e.html)
document the underlying integrals and safe logarithm/principal-arctangent branches. The Orbitalis
implementation is independent; no library code or dependency is included. Dimensionless evaluation
reduces scaling errors. Beyond eight maximum half-extents it transitions to 8-point tensor
Gauss-Legendre volume integration, fully selected at twelve half-extents, avoiding distant corner
subtraction. Both evaluations represent the same mass integral, not a switch to a point planet.

A finite slab pulls sideways near its edges. `SurfaceGravityLayer` supplies the explicitly artificial
Minecraft compatibility region: vertical native construction gravity throughout the initial surface
footprint, from Y=-64 through the full build ceiling Y=320. Above it the field transitions to the
finite-body field by Y=3064 (3000 m above sea level). A compact side taper extends 256 m beyond each
horizontal boundary; a lower taper ends at Y=-320. Outside that finite region, including distant
points at sea level, gravity belongs entirely to the finite body. The native region is not an
infinite gravitating sheet.

The layer blends scalar potentials: Phi = Phi_body + w(Phi_native - Phi_body). Acceleration includes
both the blended field and `-(Phi_native-Phi_body)*gradient(w)`. Omitting this last term creates a
nonconservative field. The compact window is C2; the native potential constant is matched at the
upper centre to prevent an upward force in the upper transition. Native gravity remains exact near
the ground, including footprint edges/corners. Forces can be stronger in the compatibility layer
than at the surface, especially outside the lateral wall. Do not independently normalize or clamp
them: that would change the potential/energy contract. This layer is a gameplay approximation,
not the gravity of a homogeneous slab in that region.

Fluids, block neighbours and other mods' ordinary world coordinates stay native Minecraft.
Sable constructions and players share the exterior mass model. Players retain 32 m/s² near
the ground for native Minecraft jumps, independently of native construction strength; their
conservative compatibility layer joins the same exterior field by ascent altitude. `SableFlightGravity` subtracts native base gravity
rather than counting it twice; an exactly zero correction is skipped to avoid waking resting ships.
The new force correction is split before/after each native substep. In vacuum this gives the external
field symmetric kicks around Sable's own drift; Sable still owns positions, collisions, mass, sleeping
and integration of other forces. Native Overworld gravity, atmosphere and drag settings are unchanged.
No position projection, energy rescaling, artificial orbital speed or synthetic propulsion is used.

## Trajectories and guidance

Near-field trajectories are not Kepler ellipses. Angular momentum need not be conserved because the
field is not central; energy is `v^2/2 + Phi`. Negative energy alone does not establish a safe orbit:
a bound trajectory can intersect the body or its intended clearance envelope.

`BallisticOrbit` numerically shoots a periodic meridian from a point 5000 m above sea level,
using velocity Verlet in the predictor. It rejects collisions, insufficient clearance, failure to
close and excessive prediction energy error. Axial and square-diagonal meridians are covered;
this is not a general arbitrary-inclination orbit planner or a guarantee of long-term stability.
The configured hull bounding radius plus a margin determines required clearance.
The predictor runs off the game thread with an immutable field. Its output never integrates a live ship.
The finite-body consumer mission ramps the reference trajectory's time parameter to capture it with
real Propulsion engines. It then releases engine ownership and coasts with engines fully faded.
Return waits for the upper part of the orbit, brakes above the body, approaches the remembered pad
and descends. The final metre is an engines-off settling phase; native terrain must support touchdown.

## Surface and space route

Ordinary Overworld uses a separate `orbitalis:space` frame. `FlatPlanetFrame` adds 4096 to represented
Y; axes and velocity are unchanged. The smaller offset preserves more native float precision for
low orbits. It is a bounded first-body frame, not a floating origin: space has no native terrain void floor, so below-body orbital arcs may have negative represented Y.
Below-world removal is suppressed in both managed frames for underside/exterior flight; other
damage and other dimensions retain native rules. The former diagnostic frames are removed; old journals are never reinterpreted.

In the flight consumer, assemble a supported ship on a flat clear Overworld pad inside |x|,|z|<=4960,
turn manual inputs off, disable creative flight and stand on board. Run:

```mcfunction
/orbitalis flight autopilot orbit
/orbitalis flight autopilot status
```

After insertion the status reports engines-off free flight. For return, including from a reloaded
launch bookmark, run `/orbitalis flight autopilot land`. These guidance commands still require an unmounted
creative pilot; ship transfer itself also supports survival and an optional seated pilot, within its bounded native serialization and ownership checks. The consumer owns guidance, not production Orbitalis.
Manual `/orbitalis flight transfer` selects the matching surface/space route but supplies no thrust.
Automatic altitude handoff also runs independently of this mission for loaded supported ships and
individual players, including survival. Guidance reserves its own ship so both controllers cannot
start the same transfer. An independent player needs no ship or command; passengers on a ship are
transferred with their vehicle instead of being teleported separately.

Ascent handoff starts at physical Y=3064, return at Y=2064, providing 1000 m hysteresis.
Heights scale with the shared default footprint. A bounded native destination ticket starts when
the earliest directed crossing is within a measured load-time budget (initially 5 s, twice the
observed load duration plus 1 s, bounded to 2–21 s). Successive server samples estimate net vertical
acceleration, including braking and a possible apex; the horizontal crossing predicts the target
chunk. Unreachable/out-of-footprint approaches release their tickets. Tickets expire natively and
are released after travel/cancellation/shutdown. This prepares terrain only: ship reconstruction,
client bundle readiness and durable native checkpoints still temporarily pause physics. Transfer
stage timings are logged as `ORBITALIS_TRANSFER_TIMING`; renderer continuity still needs client evidence.
The orbital sky shows the existing illustrative flat body; it is not a live Overworld map or solid
space terrain. The predictor/guidance keeps this route clear of that body; general underside landing
and arbitrary collision with the distant illustration are not implemented.

Transfer journal version 4 explicitly stores `surface_dimension=minecraft:overworld` and
`orbital_dimension=orbitalis:space`. Completed retired diagnostic receipts without a pending pilot
are ignored; unfinished retired records fail closed and require recovery using the previous version.
Launch bookmarks remain version 2; retired diagnostic bookmarks are skipped while active Overworld
sites are preserved. Unknown or inconsistent routes fail closed. See [ship transfer](ship-transfer.md).
Active version-3 records remain readable with their required pilot; version 4 adds an optional pilot
and bounded entity/mount manifests. Transactions accept one onboard player; other players may stay
in Overworld/space. Empty ships do not wait for a pilot during transfer or recovery. Native physics
is temporarily paused across the participating levels during reconciliation.

## Evidence

Core tests cover symmetry, centre/faces/edges/corners, potential gradients, independent numerical
volume integration, distant gravity, compact layer boundaries and full surface-native forces.
They also check multiple unpowered revolutions, timestep convergence, small transverse perturbation,
unsafe bound trajectories and axial/diagonal shooting solutions. Native tests separately exercise
an unpowered Sable body and the complete engine-driven Overworld mission, cargo and recovery.
Use the fresh logs/reports for measured results; client camera/deck contact and frame stalls require
an interactive run and are not established by the inert server pilot.

## Automatic travel

The server examines loaded constructions first, then independent players. Surface exit starts at
Y=3064 while rising; return starts at physical Y<=2064 while descending, above the initial footprint
and above sea level. The represented space frame adds 4096. Distinct thresholds prevent immediate
bounce-back. Orbit arcs outside the surface footprint do not become surface entry. Travelling
entities keep native motion and orientation; dimension travel resets accumulated fall distance.

For approaching ships, bounded timeout tickets preload both source entity chunks and destination
terrain according to the same ETA policy in both directions. Selection waits for source entity chunks
to load, so an unloaded cart is not omitted. At most 16 approaches retain paired tickets; tickets are
released after the approach ends or on server shutdown. Unsupported craft remain at source, with a
logged reason and a retry delay. This still uses the existing reconstruction journal and pauses:
it is not a seamless renderer or full ship pre-staging implementation. Independent player travel
uses native teleport/save behavior rather than creating a ship journal or duplicating player data.

## Player motion and atmosphere

`PlayerGravity` samples the server-selected surface strength, synchronized through the bounded
`orbitalis:player_gravity` clientbound payload. Movement stays in world coordinates with native
Minecraft/Sable collisions. Native move-before-gravity integration remains exact at the surface. As the atmosphere fades,
the player integrator continuously transfers acceleration to symmetric half kicks around native
travel, becoming velocity Verlet in vacuum. This permits different jump heights/durations as the field changes. Camera orientation
is independent and unchanged. Water, lava, climbing, levitation and mounted-player movement retain
their native controllers; the ship/vehicle owns mounted displacement.

The current atmosphere is a compact gameplay approximation: full through Y=320, fading smoothly
to zero at Y=3064, with 256 m horizontal and lower tapers. Air drag, elytra lift/steering and survival
air control fade with it. In vacuum an elytra grants no lift or thrust. Creative flight retains its
controls and gravity exemption, while both horizontal and vertical damping vanish. Tiny vacuum
velocities are retained rather than rounded to rest every native tick. Ground friction remains.
This is not a pressure, oxygen or heating simulation.

`orbitalis:flight_motion` orders a tokened capture of the controlling client's velocity before
native dimension travel and restores it afterward; durable recovery uses the recorded server
fallback rather than a new login's stationary velocity. Protocol three transforms captured free-player
motion into destination axes, including lunar transport velocity, before restoration. Vectors are
bounded to 10 km/s. Earth's +4096 coordinate frame is retained; current Earth/lunar transaction formats
are documented in [ship transfer](ship-transfer.md).

Native server movement validation uses its already accepted client movement as a vacuum momentum
reference (bounded to 10 km/s), retaining speed/collision rejection. Weak-gravity vacuum coast is
not treated as illegal hovering by the native flight timer. This does not grant survival controls
or alter the global server flight setting.

## Edge and underside travel

Travel uses core `PlanetFlightEnvelope.distance`: shortest Euclidean distance to the reference
volume [-5000,5000] x [-64,64] x [-5000,5000], rather than height or an open X/Z footprint gate.
Overworld exits at 3000 m clearance and Space returns at 2000 m, giving 1000 m hysteresis around
all faces and rounded edges/corners. Directly above the surface, existing Y=3064/2064 handoffs
are preserved. Close side/underside views remain native Overworld; no surface projection is installed.
The illustrative sky underside remains separate from native bedrock and collision.

Destination tickets and optional held-frame capture forecast arrival under sampled three-axis
velocity and bounded constant net acceleration. A bounded, earliest-first search prunes intervals
using distance's 1-Lipschitz bound; very brief grazing contacts can exceed its numerical budget,
so forecasts are preparation hints, never transfer/collision authority. Actual measured position
and destination readiness decide handoff. Ticket budgets, journal/checkpoint authority and passenger
restoration are unchanged. Background orbital objects wake near any side of this same volume.
Atmospheric drag already had lateral/lower falloff; sky and atlas subscriptions now follow those
regions too, so edge flight does not retain a full daytime sky until a height-only trigger.
