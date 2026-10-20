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

# Local orbital sectors

`OrbitalSector` addresses an immutable heliocentric grid with 1,048,576 metre cells.
Native chart coordinates are centred at (0, 1,048,576, 0), with 32,768 metres of
hysteresis outside each half-cell. Normal native coordinates remain below two
million metres even while astronomical coordinates grow. Sector axes are inertial;
origin position, velocity and acceleration relative to the existing geocentric
physical root come from the sampled Earth ephemeris. Native chart velocity is
never treated as geocentric velocity without this transport.

Earth, Moon and `orbitalis:space` identifiers remain unchanged. Near Earth, the
legacy space chart remains available. Its exit radius is the lesser of the saved
profile flight range and 262,144 metres, plus hysteresis; entry uses the lesser
radius without hysteresis. Surface charts hand off into the orbital chart covering
the crossing position. Other surface worlds use the same explicit frame contract.

Sector IDs are `orbitalis:sector/x_y_z`, with canonical signed decimal integers
(no leading zeros or negative zero), each bounded to ±1,000,000. The finite physical
travel range is two billion metres from Earth. Client frame reconstruction uses
the ID and already synchronized celestial sample; it requires no per-sector packet.

`orbitalis_orbital_sectors` is version-one SavedData in the Overworld. It records
at most 4,096 unique keys. Unknown/malformed records are retained unchanged and
refuse new sector creation. The catalogue is an asynchronous index, not sole world authority; a canonical key in
a durable player or transfer record can reconstruct a missing index entry.
The loader creates a separate native ServerLevel from the existing sparse vacuum
stem, installs it into the native server level map, marks its level cache dirty,
and emits the ordinary level-load event. Each level owns its normal independent
save directory, entity storage, chunks and Sable container.

Startup validates the catalogue without constructing every level. A server-thread
native `getLevel` fallback lazily materializes canonical sector keys for login and
recovery, with a recursion guard. A maximum of 256 native sectors can be loaded in
one server session; exceeding it refuses travel before source authority is removed.
There is no automatic loaded-sector eviction in this version.

Ship journal version eight records explicit source and destination dimension IDs
and a frozen sampled catalogue. It supports orbital-to-orbital and moving-surface
routes without inferring direction from an orbit boolean. Versions three through
seven retain their original readers and frame semantics. PREPARED/COMMITTED/DONE,
passenger manifests, cargo snapshots and native checkpoints retain their existing
authority order. Ordinary player travel uses the same chart transforms and native
teleport acknowledgment/motion restoration.

Every materialized sparse sector has its own native motion index, sharing the
existing authoritative simulation clock. Native load translates saved geocentric
motion into the current sector chart, including velocity. Live background coasts
approaching another chart wake for a journalled transfer. Cold, very long catch-up
can move an unloaded object's physical position beyond the old chart's safe native
volume. That case retains the native holding payload and rejects unsafe native
reconstruction; automatic holding-to-holding migration across cold sectors remains
unimplemented. This is a recoverable limitation, not successful inter-sector
background simulation. Native bodies are never reconstructed at astronomical
coordinates to bypass it.
