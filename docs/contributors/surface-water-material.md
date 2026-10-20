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

# Surface water material

The surface atlas carries an explicit coarse water coverage alongside height and native map colour.
This is presentation data only: it never changes terrain, generates chunks or exposes the world seed.
`SurfaceAtlas.Sample.waterCoverage` is an integer from 0 through 255. `Cell.waterCoverage` is its
normalized 0 through 1 average over known chunk samples; unknown chunks do not contribute a dry vote.

Live chunk capture and read-only Anvil backfill share `SurfaceAtlasSampler`. Each samples sixteen
surface-height columns, counts states whose fluid is in Minecraft's WATER tag (including waterlogged
states), and rounds `count * 255 / 16` to the nearest integer. Dry blue materials do not become water.
This estimates the visible top-surface material, not buried water, water depth or a full-resolution mask.
`SavedSurfaceReader` retains its native bounded read-only region decoding; the existing
`SavedSurfaceSampler` passes decoded block states through the same sampling path.

SavedData `orbitalis_surface_atlas` now writes Version 2 with five integers per row:
`x, z, height, colour, waterCoverage`. Version 1 four-integer rows remain readable with zero water
coverage: old colours never imply water. Loaded chunk refreshes replace their old samples normally;
already-known legacy samples are not proactively regenerated or re-read by disk backfill. An old
cache can therefore retain unknown-as-dry coverage until its terrain is loaded again. Future versions,
missing/wrong-type fields and malformed array lengths remain read-only. Invalid individual rows are
omitted while valid rows survive, preserving the existing disposable-cache recovery policy.

The `surface_atlas` payload has wire Version 2, encoded before the session identifier; each sample
adds one unsigned coverage byte. The NeoForge registrar must also advertise protocol 2. Older/future
wire versions are rejected before session data is interpreted. Existing 256-sample packet, coordinate,
height, colour and revision bounds remain in force. Both ends must update together.

Core tests exercise aggregation and material-only stream changes. Common contract tests exercise V1
migration, V2 persistence, malformed/future records and bounded packets. The existing native atlas
backfill GameTest compares live and disk sampling of water, waterlogged slabs and dry blue concrete,
including fractional coverage; it does not load unrelated chunks.
