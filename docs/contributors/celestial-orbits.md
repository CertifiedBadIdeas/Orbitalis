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

# Prescribed celestial orbits

The accepted first solar-system model uses prescribed trajectories for massive bodies and numerical
motion for vessels. The intended hierarchy is Sun → Earth → Moon. Daylight remains ordinary
Minecraft behavior: `/time set`, sleep and local sky presentation do not advance orbital simulation.
Physical sunlight, platform spin and sleep-driven system acceleration remain deferred.

## Current scope

Core now provides `KeplerOrbit`, `CelestialBody`, `CelestialSystem` and `CelestialSnapshot`. These
now have a native clock/catalogue adapter. The existing orbital index owns the shared simulation
clock and the compact profile is persisted alongside it. Native gravity, saved frames, ship handoffs
and sky still use the existing single-Earth route. The orbital map now displays this catalogue,
including celestial markers and closed parent-relative orbit curves. There is no lunar dimension or
active lunar force.

The accepted scale is a compact gameplay system with transfers lasting several minutes. A preliminary
Earth–Moon centre separation of 20 km gives a point-mass transfer estimate of about 5.5 minutes from
radius 5,064 m for the existing Earth mass at native ship gravity 11 m/s². This is a transfer estimate, not a validated
transfer in the finite-body field. The version-one catalogue now uses this 20 km circular lunar orbit,
a 2,000 km circular Earth orbit, and a solar mass parameter 10,000 times Earth's. The Moon mass
parameter comes from a 2.8 km flat body with top-centre gravity one sixth of Earth's. These are
compact gameplay parameters, not real Solar System proportions. Native gravity remains authoritative;
changing it must change the orbital parameters derived from it.

## Orbit contract

`KeplerOrbit` represents a bound ellipse with a parent focus. Its inputs are parent gravitational
parameter `mu` (m³/s²), semi-major axis (m), eccentricity `0 <= e < 1`, orbital-plane rotation, mean
anomaly at an absolute simulation epoch (radians), and a nonnegative epoch tick. Time runs at 20 Hz.
Period and speed follow the same mass and distance; period cannot be independently tuned:

```text
period = 2 pi sqrt(a³ / mu)
M(t) = M(epoch) + 2 pi (t - epoch) / period
E - e sin(E) = M
```

Sampling solves Kepler's equation with a bounded Newton/bisection method and returns analytic
position, velocity and acceleration as a `FrameKinematics` value. It does not integrate previous
samples or advance time. Epochs may be later than the queried tick. Unbound trajectories and
unrepresentable periods, apsides, speeds and accelerations are rejected. These bounds apply to body
ephemerides, not to vessel trajectories, which may escape.

The orbital plane starts in XY, with periapsis on positive X and prograde motion toward positive Y.
The plane rotation maps this orbit into parent axes. It does not rotate the body's surface axes:
orbital frames keep identity orientation and zero angular velocity. `lunarSurfaceFrame(tick)`
adds the accepted synchronous surface spin separately: local +Y points at Earth, +Z follows the
orbital angular-momentum axis, and one prograde rotation occurs per circular orbit. Its origin,
velocity and acceleration come from the same lunar ephemeris. Frame transport includes spin speed,
Coriolis and centrifugal terms; the native lunar route is still under development.

## Hierarchy and relative coordinates

A `CelestialSystem` owns a copied, bounded catalogue of 1–1,024 bodies with unique namespaced IDs,
positive finite gravitational parameters, one stationary root, valid parents and no cycles. Every non-root body has an
orbit whose `mu` must match its parent's mass parameter. The root has no orbit. IDs supplied by callers
are domain keys; this stage does not assign new saved frame or Minecraft resource identifiers.

`sample(tick)` evaluates all relative orbits at the same tick. `snapshot.frame(body, reference)`
returns the body's origin position, velocity and acceleration relative to the reference, in parallel
axes. It cancels shared ancestors before adding/subtracting local values, preserving metre-scale
precision near a distant solar origin. The returned frame can transport vessel states and
accelerations using the existing [frame mathematics](rotating-frames.md).

The root is fixed, not a barycentre. A moon does not perturb its parent, and bodies do not react to
vessel forces. Each child follows the prescribed Kepler path around its parent's moving focus. This
is an explicit modelling choice, not an approximation of a mutually integrated N-body solver.

## Native integration boundary

`OrbitalSimulation` reads the existing `OrbitalObjectData.Clock` and advances it once at highest
server-post-tick priority. Sable consumes that clock without a second increment. Its optional native
adapter supplies surface gravity and a shared pause/transfer gate. Without Sable the metadata clock
still advances; no native construction classes are loaded by common code. Server downtime leaves
the persisted clock unchanged, and daylight commands do not modify it.

The top-level orbital index is now version two, reading version one in place. Its optional
version-one `Celestial` profile stores gravity, epoch and both mean anomalies atomically with clock
and motion records. Motion records and native user-data remain version one; body/frame IDs and
coordinates are unchanged. Unknown/malformed profile or index versions preserve the entire raw tag
and disable its simulation. Changing native gravity rebases current phases before retuning masses,
avoiding an immediate position jump. Historical mass-change force replay is not implemented.

Legacy maneuver-plan epochs/consumer execution still use Minecraft game time. They require an
explicit migration before enabling moving-field vessel predictions; this metadata stage does not
reinterpret those saved plans. Moving-source gravity, loaded and unloaded vessels, previews and visual snapshots
must share that clock and catalogue. `OrbitalEvolution.advanceTimed` can evaluate deterministic
position/time forces at each endpoint; no native caller has switched to it yet.

When evaluating motion in a body's translating frame, subtract its origin acceleration consistently.
A sum of source forces in solar axes cannot be used unchanged in Earth-relative coordinates. Surface
compatibility gravity and existing transfers also need explicit adaptation. These requirements must
be resolved together before activating moving bodies; this stage does not silently reinterpret the
existing `FlatPlanetFrame` or persisted states.

## Evidence

`KeplerOrbitTest` covers circular closure, periapsis/apoapsis in an inclined plane, energy/angular
momentum, analytic derivatives against independent finite differences, epochs/query order, near-
parabolic bound cases and invalid parameters. `CelestialSystemTest` covers a three-level hierarchy,
relative accelerations, distant-origin precision, input isolation and invalid graphs/mass parameters.
These tests establish domain behavior, not Minecraft lunar travel, rendering or native force agreement.

Native `celestialClockAndDaylight` checks `/time set`, pause gating and exactly one increment per
server tick. `celestialProfilePersistence` checks version-one migration, fresh disk restoration of
clock/phase and preservation of malformed/future profiles. The existing unloaded-station/cargo and
metadata compatibility scenarios also pass with the shared clock lifecycle.

## Orbital map synchronization

Map protocol negotiation is version four. Each screen snapshot retains its independent sequence
counter and ship directory, and adds the persisted simulation tick plus optional version-one
`SolarSystemProfile` parameters. The server sends the profile rather than a separately authored
position/path list; shared core mathematics derives all celestial positions and curves on the client.
The decoder bounds gravity/phase/epoch and rejects future model versions, future profile epochs and
profile/native gravity mismatches. This adds only a fixed-size profile, not terrain or native payloads.

`CelestialMapScene` derives an Earth-relative scene from that immutable profile and epoch. Celestial
curves begin at the current body position and close after one geometric ellipse; parent positions
place them in the same scene as the native Earth relief. GPU lines share its depth buffer, so Earth
occludes far-side arc segments. Celestial markers/names remain readable overlays. Earth, Moon,
Earth–Moon and solar-system framing buttons cover surface, satellite and astronomical scales. Moon
and solar focus follow their body as new snapshots arrive; Home restores the Earth overview.

The map catalogue is active independently of lunar vessel physics. Vessel coast/maneuver previews
still use the existing Earth field. Clock-domain migration for old maneuver plans, moving-source
forces, multiple-body encounter prediction and native lunar handoffs remain required before lunar
flight can be advertised. Opening the map creates no terrain tickets and wakes no constructions.

## Lunar terrain foundation

`LunarSurface` version one samples deterministic finite heights from a saved generator seed, with
low rolling relief and crater bowls/rims. The footprint is [-1400,1400) on X/Z, the reference top
is Y=64 and the underside is Y=-64. Exterior columns are absent, rather than capped by an invisible
floor. The generator adapter and actual ship arrival/departure are the next integration steps.

Core tests cover seed/query-order reproducibility, finite edges, negative-coordinate/chunk seams,
Earth-facing normal through a full orbit, rigid-state round trips and independent finite-difference
checks of the velocity/acceleration of a point fixed to the spinning surface.
