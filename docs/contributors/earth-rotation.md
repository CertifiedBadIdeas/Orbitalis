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

Earth's platform rotates around its in-plane +Z axis once per 24,000 simulation ticks
(1,200 seconds). This clock is independent of native daylight, sleep and `/time set`.
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

The core model is implemented first. Runtime activation, profile persistence/networking,
old receipt compatibility, native-world adapters, renderers and consumer guidance must be
wired together before enabling this model in ordinary worlds. Existing orbital root states
must remain unchanged, and pending old receipts must retain their recorded frame semantics.
