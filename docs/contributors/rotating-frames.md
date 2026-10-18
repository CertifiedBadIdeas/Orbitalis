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

# Rotating frame foundation

The current live Overworld/space route still uses `FlatPlanetFrame` and its fixed translation.
`Rotation3` and `FrameKinematics` provide independent core mathematics for the planned moving
Earth–Moon system. They do not activate platform rotation, lunar gravity or a physical Sun in a world.
The accepted Earth spin axis lies in the platform plane, allowing sunrise and sunset while surface
coordinates retain ordinary Minecraft local up/down. Orbital scale remains part of subsequent system
design. Physical daylight integration is deferred; the planned default preserves ordinary Minecraft
daylight independently of orbital simulation time.

## Planned daylight modes

The system design separates the orbital simulation clock from sky/daylight presentation. Neither a
mode switch nor physical daylight integration is implemented by this foundation.

- **Ordinary daylight (default):** preserve existing Minecraft sky, sleep and `/time` behavior.
  Changes to daylight must not jump celestial ephemerides or vessel states. Orbital time follows
  running game simulation and pauses while the server is stopped.
- **Physical sky (future optional mode):** derive sunlight from the physical Sun and body orientation.
  Sleep must use coordinated system-wide time acceleration, with consistent loaded and unloaded
  vessel evolution. Sleep arbitration between bodies and safe acceleration remain design work.

In the pinned Minecraft 1.21.1 implementation, `TimeCommand.setTime` and `addTime` iterate all server
levels; `DerivedLevelData` delegates daylight reads to the primary data and ignores `setDayTime`.
Preserving vanilla commands therefore does not imply independent per-dimension clocks, even when
commands run through `execute in`. Independent world clocks would require an explicit separate
feature. Ordinary daylight does not promise that the surface Sun matches physical orbital geometry.

## Sampled local-to-parent transform

`Rotation3` is a proper right-handed rotation with local basis columns in parent axes. It supports
axis-angle construction, vector transformation, inverse vector transformation and composition. Core
has no Minecraft, client or JOML dependency. Angles use radians, lengths metres and time seconds.

`FrameKinematics` represents a frame at one instant. `origin` contains the frame origin's parent
position and velocity; `orientation` maps local vectors to parent vectors. Origin acceleration,
angular velocity and angular acceleration are also expressed in parent axes. A caller must sample
all values and the transformed state at the same instant. This type owns no clock, ephemeris,
saved frame ID, native physics handle or background integration.

For local position r and local coordinate velocity v, parent position and velocity are:

```text
r_parent = origin.position + R r
v_parent = origin.velocity + R v + omega × (R r)
```

A construction stationary on the rotating surface therefore inherits both translation and spin
velocity at its location. The inverse transformation subtracts these transport terms, returning
zero local velocity for that construction. Simply rotating the velocity vector would be incorrect.

Acceleration transformations additionally include the origin's acceleration, Coriolis term
`2 omega × (R v)`, Euler term `alpha × (R r)` and centripetal term
`omega × (omega × (R r))`. The inverse subtracts them before transforming into local axes. Body
angular velocity transforms with `omega_parent = frame.omega + R omega_local`.

These are coordinate transforms, not forces applied directly to Sable. Subsequent integration must
use the same sampled motion for surface handoffs, loaded physics, unloaded motion, trajectory
prediction and sky presentation. A changing frame must also transport vessel orientation. Existing
persisted states and identifiers need explicit compatibility handling before a new frame interpretation
is enabled; this foundation does not reinterpret them.

## Evidence

`FrameKinematicsTest` checks right-handed rotations/composition, inherited surface velocity, every
acceleration term, state/acceleration/angular-velocity round trips, and agreement with independent
finite differences during translating, accelerating axial spin. The zero-spin case reproduces the
established native space offset. `InertialFramesTest` continues to cover the current hierarchy.
These tests establish core mathematics; physical world rotation and Sun/daylight behavior require
later native and isolated-client scenarios.
