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

# World creation system settings

The native **More** tab in Create World contains **Earth size**, with 1000, 2000, 5000, 10000
(default), and 20000 block presets. It selects the initial physical system, rather than editing
Minecraft's administrative movement border. Values are owned by each screen instance, survive
native tab rebuilds and child dialogs, and carry through native recreation of a world.

Shared/common `WorldSystemSettings` validates an Earth width in 1000..20000. Its `encode`/`decode`
format is the root `level.dat` key `OrbitalisWorldSettings`, a compound containing integer `Version=1`
and integer `EarthWidth`. New native `LevelSettings` start with an explicit default selection;
parsing an existing save without this key retains absence. The native copy, game-mode, difficulty
and data-pack configuration paths preserve the setting. A narrow `PrimaryLevelData` save hook
writes it beside native generation settings. No global pending selection or JVM override is used.

`WorldSystemSettings.get(server)` returns the immutable selection, or null for a legacy save.
Malformed/unsupported settings throw on interpretation; their opaque native tag survives copies
and saving unchanged. Consumers must preserve legacy body geometry when the setting is absent,
and handle incompatible metadata without silently selecting new dimensions. This setting is
creation intent: runtime geometry and bodies must not be resized from a menu or `/worldborder`.

The physical-model adapter consumes the selection in the solar-system profile and owns dimensional,
force, orbital and client synchronization behavior. The creation lifecycle itself does not derive
or duplicate those values. See the system-scaling work for the complete adapter and its verification;
a standalone settings UI is not evidence of a working smaller physical system.
