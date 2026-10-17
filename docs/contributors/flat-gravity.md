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

The first orbital mass model is a stationary homogeneous rectangular body, 2000 x 2000 x 128 m,
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
finite-body field by Y=664 (600 m above sea level). A compact side taper extends 256 m beyond each
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

Players, fluids, block neighbours and other mods' ordinary world coordinates stay native Minecraft.
Only Sable construction impulses are adapted. `SableFlightGravity` subtracts native base gravity
rather than counting it twice; an exactly zero correction is skipped to avoid waking resting ships.
The new force correction is split before/after each native substep. In vacuum this gives the external
field symmetric kicks around Sable's own drift; Sable still owns positions, collisions, mass, sleeping
and integration of other forces. Native Overworld gravity, atmosphere and drag settings are unchanged.
No position projection, energy rescaling, artificial orbital speed or synthetic propulsion is used.
The retained spherical diagnostic routes keep their old force schedule and meanings.

## Trajectories and guidance

Near-field trajectories are not Kepler ellipses. Angular momentum need not be conserved because the
field is not central; energy is `v^2/2 + Phi`. Negative energy alone does not establish a safe orbit:
a bound trajectory can intersect the body or its intended clearance envelope.

`BallisticOrbit` numerically shoots a periodic meridian from a point 1000 m above sea level,
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
low orbits. It is a bounded first-body frame, not a floating origin: physical Y below -4032 is outside
the safe represented Y>=64 region. The existing diagnostic `space_test` still adds 32000 and its
legacy journals are never reinterpreted.

In the flight consumer, assemble a supported ship on a flat clear Overworld pad inside |x|,|z|<=960,
turn manual inputs off, disable creative flight and stand on board. Run:

```mcfunction
/orbitalis flight autopilot orbit
/orbitalis flight autopilot status
```

After insertion the status reports engines-off free flight. For return, including from a reloaded
launch bookmark, run `/orbitalis flight autopilot land`. These commands still require an unmounted
creative pilot and the bounded transfer allowlist. The consumer owns guidance, not production Orbitalis.
Manual `/orbitalis flight transfer` selects the matching surface/space route but supplies no thrust.
Automatic altitude handoff currently belongs to this mission; arbitrary manually flown ships do not
transfer just by crossing a height.

On ascent a bounded vanilla terrain ticket starts at physical Y=464 (400 above sea), and transfer
starts after crossing Y=664 while moving. On descent transfer starts at Y=464, after destination
terrain preloading. Separate thresholds provide hysteresis. The ticket is released on completion,
cancellation or failure and has a native timeout. It preloads terrain only: journal staging still
loads native plot content and temporarily pauses physics. This is not seamless ship preloading.
The orbital sky shows the existing illustrative flat body; it is not a live Overworld map or solid
space terrain. The predictor/guidance keeps this route clear of that body; general underside landing
and arbitrary collision with the distant illustration are not implemented.

Transfer journal version 3 explicitly stores `surface_dimension` and `orbital_dimension`, accepting
only Overworld/space or the existing diagnostic pairs. Version 1/2 retain implicit `flight_test`
surface semantics and migrate explicitly on write. Launch bookmarks are version 2 with a surface
identifier; version 1 still means diagnostic terrain. Unknown or inconsistent routes fail closed.
The transaction remains single-pilot, with other players excluded from participating levels and
native physics paused across supported flight levels (including Overworld) during reconciliation.

## Evidence

Core tests cover symmetry, centre/faces/edges/corners, potential gradients, independent numerical
volume integration, distant gravity, compact layer boundaries and full surface-native forces.
They also check multiple unpowered revolutions, timestep convergence, small transverse perturbation,
unsafe bound trajectories and axial/diagonal shooting solutions. Native tests separately exercise
an unpowered Sable body and the complete engine-driven Overworld mission, cargo and recovery.
Use the fresh logs/reports for measured results; client camera/deck contact and frame stalls require
an interactive run and are not established by the inert server pilot.
