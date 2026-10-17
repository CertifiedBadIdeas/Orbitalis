<!--
The Orbitalis Developers

Copyright 2026 The Compukters Developers
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

# Space presentation

`space`, `orbit_test` and `space_test` use black vacuum fog, a fixed star field and a fixed Sun. Clouds,
rain rendering and sunrise/sunset gradients are suppressed. Exposed skylight has constant daytime
brightness; native sky occlusion and warm block lights remain, followed by vanilla gamma, night vision
and darkness handling. Minecraft's ordinary face shading remains: this is not directional shadow
mapping or a physical sunlight simulation. Server time, surface weather and the ordinary Overworld
sky are unchanged.

A single client-only `ClientLevel.effects()` selector supplies NeoForge's native dimension-effects
extension for these dimension IDs. It works with existing diagnostic saves whose dimension type
is still `minecraft:overworld`, without changing dimension registries, saved types or transfer journals.
NeoForge owns the hook and effects adapter; shared/common owns the actual sky and body mesh. No Sable
or Create APIs participate in rendering, and the selector never loads on a dedicated server.

The reference body is a 2000-by-2000-block flat slab, centred at surface X/Z zero, with its top at
surface Y=64 and bottom at Y=-64. Surface Y=64 is the diagnostic reference altitude, not sampled
Overworld terrain. A deterministic coloured mosaic suggests water, coast and land; side strata and
the underside make the slab visible from every external direction. This is an illustrative model,
not a map of the generated Overworld or a collision surface. Administrator changes to the native
Overworld border are not yet synchronized to this reference model.

The camera position is converted through `FlightSpaceCoordinates.globalPosition`, using the same existing
frame translations as transfer and force evaluation. Body vertices and the observer use surface
coordinates. A single uniform scale fits the entire camera-relative mesh within a 60-unit sky shell,
preserving every viewing angle and perspective size. The model therefore remains visible beyond
chunk render distance and shrinks naturally with distance. A visible-face test prevents the far side
from painting over the near side. Stars and Sun are drawn first, the body afterward, and normal
world geometry afterward, so ships occlude the background. The renderer restores its shader,
shader colour and the incoming depth/culling/blend state. Sky is hidden in fluids, powder snow and under blindness/darkness;
medium-specific fog is retained.

The new [finite-body route](flat-gravity.md) uses `orbitalis:space`, with represented Y equal to
physical Y+4096. Its geometry shares the initial surface size and sea-level constants with this mesh.
The retained spherical diagnostic force field still governs diagnostic ships; both kinds of orbit can
view the slab from above, alongside and below. Live terrain imagery, atmosphere transitions, eclipses
and directional ship shadows remain separate work.

## Verification

`SpaceSceneTest` exercises angular preservation at near/far and opposite-side viewpoints, side and
underside visibility, and distance-dependent apparent size. Run target checks and inspect a disposable
client world; compilation alone does not demonstrate appearance. Suggested manual views:

- `/gamemode spectator`, then `/execute in orbitalis:orbit_test run tp @s 0 1808 0 0 90`
  shows the surface from physical Y=2064.
- `/execute in orbitalis:orbit_test run tp @s 2400 160 2400 135 15`
  shows the top and two sides.
- `/execute in orbitalis:space_test run tp @s 0 29936 0 0 -90`
  shows the underside from physical Y=-2064.
- Compare `/time set noon` and `/time set midnight`: vacuum sky, Sun, body and skylight stay fixed.
- `/execute in orbitalis:space_test run tp @s 0 34064 0 0 90` reproduces the first view
  through the other transfer frame.
- `/execute in orbitalis:space run tp @s 0 5160 0 0 90` shows the body from 1000 m above sea level
  through the new low frame.
- Return to `minecraft:overworld` to confirm the ordinary sky still follows time and weather.
