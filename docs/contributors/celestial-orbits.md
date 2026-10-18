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
are independent mathematics, not an installed solar system. Native gravity, saved frames, ship
handoffs, orbital map and sky still use the existing single-Earth route. There is no lunar dimension,
new saved-data schema or active celestial clock introduced by this stage.

The accepted scale is a compact gameplay system with transfers lasting several minutes. A preliminary
Earth–Moon centre separation of 20 km gives a point-mass transfer estimate of about 5.5 minutes from
radius 5,064 m for the existing Earth mass at native ship gravity 11 m/s². This is neither a validated
transfer in the finite-body field nor a fixed runtime preset. Native gravity remains authoritative;
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
orientation stays identity and angular velocity stays zero in this stage.

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

The caller must supply the authoritative simulation time, paused during server downtime, rather than
Minecraft daylight. Moving-source gravity, loaded and unloaded vessels, previews and visual snapshots
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
