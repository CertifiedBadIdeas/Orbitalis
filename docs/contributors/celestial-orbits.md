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

Core provides `KeplerOrbit`, `CelestialBody`, `CelestialSystem` and `CelestialSnapshot`, backed by
the persisted native clock/catalogue. Loaded and unloaded flight, coast/maneuver forecasts and
[lunar surface travel](lunar-surface.md) now sample this model. The map displays synchronized bodies,
closed parent-relative celestial curves and seeded lunar relief. Earth coordinates and old saved
orbital-frame identifiers retain their established meanings.

The accepted physical calendar uses 1,200 simulation seconds per **mean solar day**, 365.24 solar
days per Earth year and 27.32 solar days per sidereal lunar revolution. At 20 TPS these are
24,000, 8,765,760 and 655,680 ticks respectively. Server downtime pauses the system; daylight
commands, ordinary sleep and the native sky remain independent. Prograde Earth spin must use
`1 / (1/1200 + 1/year)` = 1,196.723460026 seconds per sidereal turn so that the Sun returns after
exactly 1,200 seconds. This period is defined in core; physical Earth spin activation remains work
in progress on the separate axial-rotation branch, and is not enabled by this calendar change.
The sidereal/solar distinction is described by [NASA](https://science.nasa.gov/learn/basics-of-space-flight/chapter2-1/).

Sizes and gravitational parameters are preserved: Earth is 10 by 10 km, Moon is 2.8 by 2.8 km,
Moon top-centre gravity is one sixth of Earth's and `sunMu = 10000 * earthMu`. For each prescribed
parent focus, derive `a = cbrt(mu * (T / (2*pi))^2)` rather than selecting an arbitrary distance or
forcing a non-Keplerian speed. At native gravity 11 m/s² this gives Earth–Moon 168,941.646 m and
Earth–Sun 20,502,263.641 m, measured centre to centre. Other gravity settings change these derived
distances while retaining the periods. The hierarchy still neglects parent recoil; no two-body
mass sum or barycentric dynamics is silently introduced.

The lunar surface remains synchronously locked to Earth. Its synodic phase period follows
`1 / (1/lunarPeriod - 1/year)` = 29.528754735 solar days; it is not a separate adjustable constant.
See [NASA's explanation of sidereal versus synodic months](https://starchild.gsfc.nasa.gov/docs/StarChild/questions/question32.html).
These mean periods describe the circular preset, rather than a recreation of Earth's eccentricity,
precession or real-world calendar conventions. One lunar revolution takes about 9.11 real hours
and a year about 121.75 real hours at uninterrupted 20 TPS.

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
Coriolis and centrifugal terms; the native lunar route uses this sampled frame.

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
version-two `Celestial` calendar profile stores gravity, epoch and both mean anomalies atomically with clock
and motion records. Motion records and native user-data remain version one; body/frame IDs and
coordinates are unchanged. Unknown/malformed profile or index versions preserve the entire raw tag
and disable its simulation. On first configuration, an existing version-one compact catalogue migrates to the calendar at the
saved current clock, preserving both orbital phases. The Moon and solar origin move to their new
radii. Native surface coordinates, lunar terrain and inventories stay unchanged; free-space ships
and persisted inertial station states are not relocated to follow the Moon. Changing native gravity
also preserves phase but changes calendar radii to maintain fixed periods. Historical force replay
across a mass/scale change is not implemented. Frozen transfer journals decode/write their own exact
profile version: compact version-one receipts retain 20 km/2,000 km distances and are never migrated
as live world catalogues. Unknown/malformed versions are preserved unreadable.

Maneuver-plan epochs and consumer execution retain Minecraft game-time semantics. Prediction offsets
are anchored explicitly to the current simulation epoch, rather than migrating or reinterpreting saved
node times. `OrbitalEvolution.advanceTimed` samples moving forces at absolute integration times;
loaded physics samples each native substep. `CelestialDynamics` supplies the same Earth-relative field
to live/unloaded vessels and previews. Its differential lunar/solar terms preserve the prescribed
Earth origin; surface compatibility and rotating local dynamics are described in
[lunar surface](lunar-surface.md).

## Evidence

`KeplerOrbitTest` covers circular closure, periapsis/apoapsis in an inclined plane, energy/angular
momentum, analytic derivatives against independent finite differences, epochs/query order, near-
parabolic bound cases and invalid parameters. `CelestialSystemTest` covers a three-level hierarchy,
relative accelerations, distant-origin precision, input isolation and invalid graphs/mass parameters.
These tests establish domain behavior. Separate native lunar/force scenarios cover game integration;
visual correctness still requires an observed client.

Native `celestialClockAndDaylight` checks `/time set`, pause gating and exactly one increment per
server tick. `celestialProfilePersistence` checks clock/profile migration, inertial-state preservation, frozen journal presets and fresh disk restoration of
clock/phase and preservation of malformed/future profiles. The existing unloaded-station/cargo and
metadata compatibility scenarios also pass with the shared clock lifecycle.

## Orbital map synchronization

Map protocol negotiation is version five. Each screen snapshot retains its independent sequence
counter and ship directory, and adds the persisted simulation tick plus optional version-two
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

Vessel coast/maneuver previews use the same time-dependent field as native flight. Opening the map
creates no terrain tickets and wakes no constructions. The rendered Moon has the same seed-owned
relief as its native surface; far-side paths share the scene depth buffer.

## Lunar terrain foundation

`LunarSurface` version one samples deterministic finite heights from a saved generator seed, with
low rolling relief and crater bowls/rims. The footprint is [-1400,1400) on X/Z, the reference top
is Y=64 and the underside is Y=-64. Exterior columns are absent, rather than capped by an invisible
floor. The native generator and automatic ship arrival/departure use this definition; see
[lunar surface](lunar-surface.md).

Core tests cover seed/query-order reproducibility, finite edges, negative-coordinate/chunk seams,
Earth-facing normal through a full orbit, rigid-state round trips and independent finite-difference
checks of the velocity/acceleration of a point fixed to the spinning surface.

## Camera capture boundary

Use the circular Hill approximation `rH = a * cbrt(mu_body / (3 * mu_parent))` from the same
profile, rather than a fixed camera distance. This follows the standard restricted-three-body
[Hill scaling](https://ntrs.nasa.gov/citations/19930009947), not a hard cutoff of gravity.
The lunar approach horizon starts blending only inside the Moon's Hill sphere and reaches the
native surface orientation before every entry/exit face. Outside that sphere the lunar orientation
has no effect on the free-player camera. All force sources continue to contribute independently
of this presentation choice. Earth axial camera transport remains part of the separate spin work.
