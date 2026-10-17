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

# Isolated Sable flight diagnostics

The normal Overworld has native Minecraft/player behavior and native construction gravity through
Y=320 inside the first footprint. Above it, the construction-only [finite-body field](flat-gravity.md)
provides the new low orbital route. This page describes the retained spherical diagnostics.

The retained `orbitalis:flight_test`, `orbitalis:orbit_test` and `orbitalis:space_test` are diagnostic
spaces with native Sable gravity, pressure and drag disabled. Ordinary loaded ships receive an impulse
at each server physics substep from `SableFlightGravity`: mass times sampled acceleration times dt,
converted into body axes. Other force producers retain their own impulses. Sable owns integration,
collisions, assembly and saves; no enrollment tags or replacement ships are introduced.

The test field is `SphericalGravity(radius=6000, surfaceAcceleration=9.81)` around physical centre
(0,-5936,0). Outside the reference radius it is inverse-square; inside, acceleration is linear and
finite at the centre. The potential is continuous with zero at infinity. The former cube field and
cube sky renderer are removed. This mathematical reference does not select a spherical surface or
define future flat-body orbital physics.

With cheats, `/orbitalis flight enter` enters ordinary diagnostic terrain. Build and assemble a ship,
disable creative flight and stand on deck. `/orbitalis flight sample` reports mass, position, speed,
radial altitude and gravity. `/orbitalis flight leave` returns the player; ships remain in the test
space. Return points are transient; after restart use the Overworld spawn fallback.

`/orbitalis flight transfer` and `transfer space` retain the [journaled ship handoff](ship-transfer.md).
Their frames and records remain unchanged. These diagnostic commands do not select the new Overworld route when used in diagnostic terrain.
All three diagnostic borders are independent of the 2000-block Overworld border.

Real GameTests cover ordinary native acceleration and timestep/force coexistence, translated field
agreement, full-space energy and angular-momentum preservation, and ship/pilot recovery. A successful
server test does not prove camera/rendering continuity or interactive piloting.
