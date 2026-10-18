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

# Axial rotation and surface reference frames

Earth's platform rotates around its in-plane +Z axis once per 1,196.723460 simulation
seconds. Combined with the prescribed solar revolution, this gives a 1,200-second mean solar day. This clock is independent of native daylight, sleep and `/time set`.
The ordinary axis-aligned Overworld remains the surface's local chart; its stored blocks
and player collision boxes do not rotate inside Minecraft. The orbital scene represents
that chart as a rotating rigid body.

The geocentric parent has nonrotating axes. A native ground point has parent velocity
`omega × r`; a vessel crossing into space retains that velocity, its additional local
velocity and its physical attitude. In the native frame, force evaluation removes frame
transport, including Coriolis acceleration. Near-surface compatibility maintains ordinary
downward gravity for a stationary local observer. Farther away, the real moving slab field
and other celestial sources determine the trajectory.

Moon's top (+Y) points towards Earth's centre throughout its prescribed orbit. Its
synchronous spin follows the lunar mean motion independently of Earth's daily rotation.
Earth rotation does not make the Moon follow a particular terrestrial longitude.

`SolarSystemProfile.earthSurfaceFrame` owns the spin sample; `SurfaceFlightFrames` and
`CelestialDynamics` use it for transformations and forces. A null spin epoch represents
legacy fixed-Earth contracts. Spin phase survives mass-model retuning. Root-space coast
predictions intersect the moving body's local geometry at each integration instant.

Version-three profiles persist the explicit calendar preset, optional `EarthSpinEpoch` and
selected `EarthWidth` (zero denotes legacy geometry). Version-one profiles retain compact orbits
and fixed axes; version-two profiles retain calendar orbits and fixed axes, including frozen
lunar receipts. Gravity protocol four and map protocol six carry this profile and reject spin
epochs later than their simulation snapshot. Future or malformed saved profiles remain preserved unreadable.

World configuration enables rotation by default. A pre-spin save adopts its current simulation
clock as the spin epoch: its terrain orientation starts unchanged, without moving persisted
inertial orbital states. Native terrain, ship passenger plot coordinates and local sunlight retain
their own contracts. New ship journals freeze a version-seven frame for both Earth and Moon;
old translation-only Earth and frozen lunar receipts keep their original recovery interpretation.

All physical root conversions use explicit `CelestialFlight.rootState/rootPoint` and their native
inverses. `Level.OVERWORLD` denotes the rotating terrain chart, never an inertial-root sentinel.
The fixed Space offset remains compatible with existing unloaded orbital records. Renderers share
one fractional epoch for Earth relief, clouds, Moon, observer and horizon; map trajectories remain
inertial and use the planet depth buffer for occlusion.

Consumer rocket ascent uses a frozen inertial launch basis and includes the launch pad's transport
velocity. Return guidance is evaluated in Earth's moving chart, using velocity-dependent frame
forces and an RK4 free-coast forecast. Every requested acceleration is transformed back to native
Propulsion engines. Creative engines and RCS remain the only propulsion actuators. Constant
inertial energy is not an invariant in the rotating nonspherical field; native coast verification
compares actual motion with a numerical forecast instead.

New-world geometry comes from the immutable creation settings, rather than mutable client globals.
Earth width is 1,000–20,000 metres (10,000 by default); Moon width is 0.273 times Earth width.
Moon gravity is 0.165 times Earth's 11 m/s² surface value. Saves without creation settings retain
10,000/2,800-metre bodies and the established one-sixth lunar gravity. Orbital radii follow each
profile's gravitational parameters and the fixed calendar periods.

Both bodies retain at least 1,500 metres of native approach clearance and a 300-metre handoff
hysteresis. Lunar terrain voxelizes its exact physical half-size, so odd block widths can place
the outer voxel boundary within half a block of the reference slab. Atlas records retain explicit
chunk coordinates; the capacity covers the maximum 20-km Earth while sampling stops at each
save's actual finite bounds. Cached render meshes include geometry in their invalidation keys.

The separate `scaled` GameTest suite creates an actual 1,000-metre native world. Other suites
explicitly retain legacy geometry through a test-only creation fixture; this fixture is excluded
from production archives. Neither native tests nor a successful build establish visual continuity.
