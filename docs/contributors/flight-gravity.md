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

# Construction flight gravity

`SableFlightGravity` adapts construction impulses in ordinary Overworld and `orbitalis:space`.
The [finite-body field](flat-gravity.md) combines a finite rectangular body with a conservative
surface compatibility layer. Inside the initial footprint, construction gravity remains exactly
native through Y=320. Players, blocks and fluids keep Minecraft behavior.

For each native server physics substep the adapter subtracts native base gravity and applies the
remaining acceleration as mass-scaled impulses, split before and after integration. Body axes are
respected and other force producers retain their impulses. Sable owns ships, collisions, mass,
sleeping, integration and saves. The space frame adds 4096 to physical Y; force evaluation converts
back to the same physical coordinates. Native Overworld pressure and drag settings are unchanged.

With operator permission, `/orbitalis flight sample` reports the ship's mass, position, speed,
height above the Y=64 datum and gravity. `/orbitalis flight transfer` performs the
[journaled ship handoff](ship-transfer.md). The [flight consumer](flight-consumer.md) supplies test
propulsion and autopilot commands. Its fixture functions remain in `orbitalis_flight_test`; this is
a function namespace, not a dimension.

The former `flight_test`, `orbit_test` and `space_test` dimensions, their spherical gameplay field,
entry/exit commands and diagnostic autopilot have been removed. Git retains their implementation.
Saved directories are preserved; unfinished diagnostic transfers require recovery with the previous
version before upgrading. No legacy coordinates become Overworld coordinates.

Core tests cover force/potential consistency and frame translation. Native GameTests cover finite-body
free flight, cargo-preserving transfer/recovery and real consumer propulsion. Server tests do not
prove camera/rendering continuity or interactive piloting.
