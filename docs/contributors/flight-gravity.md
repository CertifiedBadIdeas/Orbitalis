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

# Cubic planet gravity on ordinary Sable ships

`orbitalis:flight_test` is an experimental terrain dimension for testing Orbitalis gravity with
player-built Sable ships and externally supplied propulsion. It does not use the laboratory's owned
probes, automatic flight controller or boarding teleports. No command registers a ship with Orbitalis:
every loaded native Sable construction in this dimension receives the field automatically.

## Enter and build

In a disposable creative world with operator permissions and Sable installed:

```mcfunction
/orbitalis flight enter
/orbitalis flight sample
/orbitalis flight leave
```

`enter` places the player near the top of the terrain at x/z = 0. It transfers the player only; ships
remain in their original dimension. Build and assemble the new ship in the test dimension. Repeating
`enter` inside the dimension is rejected. `leave` returns to the transient entry position; after a
server restart, its fallback is the Overworld spawn. Neither command changes creative flight mode.

`sample` requires native Sable contact with a ship. It reports the ship UUID, centre-of-mass position,
mass in Sable units, cube-shell altitude, speed in m/s and the current gravity vector/magnitude in m/s².
This is field telemetry, not a measurement of total acceleration while engines or collisions act.
The dimension and assembled ships persist through the ordinary Minecraft/Sable save mechanism.
Orbitalis maintains no custom ship save schema or force-loading tickets.

For the external flight stack, launch the consumer through its own wrapper:

```sh
cd consumers/flight
./gradlew runClient
```

Use the consumer [terrain ship fixture](flight-consumer.md#repeatable-terrain-ship-fixture) if desired.
It is installed only in a chosen disposable world; it is not bundled in the Orbitalis production mod.
No Create, Aeronautics or Propulsion implementation is a mandatory Orbitalis dependency.

## Field and force ownership

The reference cube has half-extent 6000 m, centre (0, -5936, 0) in Minecraft coordinates, and
face-centre surface acceleration 9.81 m/s². Its top reference face is y = 64. Gravity is exactly
normal to face interiors near the surface, blends continuously at edges, and becomes central far away.
The height transition runs from 512 to 6000 m with a 64 m edge blend width. At x/z = 0 and y = 6064,
gravity is still 2.4525 m/s². Terrain height is independent of the reference face height.
See [cubic planet gravity](cubic-planet-gravity.md) for the conservative potential and accepted geometry.

At each server physics substep, the adapter transforms the current native centre of mass to world
coordinates, computes `impulse = mass * acceleration * dt`, converts it to body axes and applies a
linear impulse at the centre of mass. It never resets position, orientation, linear or angular velocity.
Native Sable remains the only integrator. Other force producers, including Propulsion, add their own
forces normally. No attitude stabilization, thrust assistance or trajectory correction is supplied.
Uninitialized or zero-mass bodies are skipped until native mass data becomes available.

The dimension's Sable `base_gravity`, universal drag and air pressure are zero. This prevents constant
native downward gravity or atmospheric drag from masking the cubic-field experiment. It is a vacuum
force test over normal terrain, not an atmosphere simulation. The Overworld and original planet
laboratory are unaffected. A datapack overriding this dimension's physics must preserve that force
ownership or it can reintroduce double gravity/drag.

## Limits

This is ordinary Minecraft terrain used as a local patch, not a finite cubic planet.
The separate [ship transfer diagnostic](ship-transfer.md) now tests an explicit cross-Level handoff. Flying far
sideways does not wrap terrain. There is no automatic surface/orbit handoff, floating origin, planet
renderer, custom player gravity or survival progression. Players and loose entities retain vanilla
gravity; a tilted ship does not rotate the player's gravity direction. This step validates ship forces.
Existing laboratory frame-shift and small-planet orbit tests remain separate experiments.

## Evidence

The native-physics GameTest creates untagged ordinary constructions without the lab service. At one
and four substeps per tick it verifies equal gravitational acceleration for unequal masses and rotated
bodies, negligible gravitational torque, zero lateral gravity on an off-centre face, weaker high-altitude gravity, composition with an independent
impulse, and unchanged Overworld gravity. Core tests cover the cubic potential gradient, face/edge/corner continuity, far field,
energy convergence, centre and invalid parameters. These tests do not prove Propulsion visuals, interactive handling,
ship transfer between dimensions or production planetary geography.

During the 2026-10-15 consumer trial the user assembled/flew an ordinary Propulsion-powered ship.
Two samples of the same ship 2.58 seconds apart showed Y decreasing from 313.81 to 236.21, speed
increasing from 18.813 to 42.075 m/s, and gravity increasing from 9.0389 to 9.2679 m/s². The user
reported releasing lift; the observed motion is consistent with falling under the then-spherical field.
These observations precede the cubic-field revision and do not validate its interactive behavior.
