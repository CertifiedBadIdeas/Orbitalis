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

# Sparse orbital world

`orbitalis:space` is a native Minecraft dimension used for players, networking and Sable's
physical constructions. In new worlds its ordinary terrain is regenerable vacuum, rather than
an invisible landscape that follows each ship. The surface remains a normal finite Overworld.
Asteroid generation remains future work. Independent ballistic constructions now have a separate
[unloaded motion index](unloaded-orbital-objects.md) over native Sable storage.

## Construction and collision

The dedicated `orbitalis:vacuum` generator has no layers, structures or features, and requires
`minecraft:the_void`. The server rejects non-air block writes into ordinary full terrain chunks
in object mode. Native Sable plot chunks accept blocks normally: build on a physical construction,
or assemble it on the surface and transfer it. This does not introduce automatic assembly when
placing a block into empty space. Clearing blocks and ordinary surface building keep native behavior.

For this model only, the Sable adapter sets its native dimension `ignoreChunks` policy. Native
physics therefore stops requesting terrain sections around moving constructions. Ship/ship collision,
ship blocks, cargo, native object storage and Orbitalis gravity remain active. Player view chunks
still exist; this change does not replace Minecraft's client chunk protocol. The setting is reapplied
after datapack synchronization and the previous native policy is restored when the server stops.
It is independent of the consumer autopilot and does not depend on Create or Propulsion.

## Persistence and existing saves

The dimension's `data/orbitalis_space_world.dat` contains SavedData version 1 with `Model` equal
to `objects` or `legacy_terrain`. On first initialization, objects mode requires the dedicated
vacuum generator and no existing terrain-region payload. Native header-only region files (8192 bytes)
are allowed; any larger region file selects legacy mode conservatively. This intentionally keeps
old worlds with even empty saved terrain in legacy mode. No automatic conversion or deletion occurs.
A different generator also disables objects mode, even if a previous marker says `objects`.
Unknown/missing versions remain conservative and their original payload is preserved.

Only the exact terrain region folder of an active objects-mode space dimension is eligible for
write omission. The native region worker checks the fully serialized chunk after mod save callbacks.
A write is omitted only when it is recognized as regenerable vacuum **and no region entry already
exists**. Unknown tags/attachments, pending ticks, entities, real blocks, changed biomes, nonzero
inhabited time and nonzero block light retain ordinary writes. Derived vanilla height/light caches
can be regenerated. Before the biome-generation stage, the native plains placeholder also counts as
regenerable; after that stage, a plains biome is retained as an edit. Existing entries always write, including clearing old content back to vacuum;
otherwise that content could reappear after restart.

The hook preserves native IO ordering, futures, flush and synchronous-write configuration. Entity,
POI and Sable object stores are not registered as disposable terrain. Empty region headers may still
be created by native reads, so an `.mca` file's existence does not mean terrain chunks were saved.
Unknown mod state deliberately costs storage rather than being discarded. This is not a new save
format or an attempt to make all dimensions sparse.

## Verification

Run `:v1_21_1-neoforge:runGameTestServer -PorbitalisGameTestSuite=sparse` for five required native
cases. They compare the default terrain-ticket policy with vacuum flight, collide two physical
ships without terrain, exercise the actual region worker through close/reopen, reject static building,
and check new/legacy/datapack/future-version model selection and SavedData disk roundtrip. The storage
case checks unknown payloads and attachments, entity-folder exclusion and replacement of an existing
entry after clearing its content. All five also belong to the default suite.

A bounded native probe with the same 117.15-metre flight observed one loaded space chunk with
terrain ignored, versus over 800 with terrain enabled. These are fixture measurements, not a universal
performance ratio. The full suite separately covers native free orbit, cargo roundtrip/recovery,
entity checkpoints, mounted passengers and player travel. A real client flight/rendering observation
remains separate from headless tests.
