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

# Diagnostic surface/orbit ship transfer

The optional Sable adapter provides `/orbitalis flight transfer` for one independent construction
and its unmounted creative pilot. Use a disposable world and the isolated flight consumer. The
command toggles between `orbitalis:flight_test` terrain and the new stable `orbitalis:orbit_test` void
level. These IDs are saved-world contracts. This is a reconstruction/ownership experiment; automatic
height triggers, seamless rendering and production planet geography are outside this step.

## Manual scenario

Launch from `consumers/flight` using `./gradlew runClient`. Install the optional
[terrain ship fixture](flight-consumer.md#repeatable-terrain-ship-fixture) in the selected disposable
world and enter with `/orbitalis flight enter`. Assemble the craft, disable creative flight and use
its controller to climb above y = 350 near x/z = 0. Stop applying thrust, toggle the controller off,
remain on deck and run:

```mcfunction
/orbitalis flight sample
/orbitalis flight transfer
```

Wait for the completion message before using controls. Inspect the entire ship and chest contents,
then use `sample` and the external controller again. Run `transfer` on deck to return. A return at
very low orbit coordinates is rejected; both represented pilot and ship positions must remain above
y = 64. `/orbitalis flight leave` remains player-only and leaves the ship in its current dimension.

The orbit representation subtracts (0, 256, 0) metres from surface positions. Axes are identical,
the frame does not rotate or move, and linear/angular velocities and player view direction are unchanged.
The same spherical gravity evaluates the reconstructed surface/global position in both levels. Radial
altitude in `sample` therefore remains continuous even though Minecraft Y changes by 256. This offset
is an experimental diagnostic constant, not an automatic ascent altitude or an orbital insertion.
The patch is limited to |x|, |z| <= 2048 m; distant locations are rejected rather than wrapped.

## Supported boundary

The first diagnostic accepts a ship with at most four native plot chunks and a 4096-block bounding
volume, at most 32 m wide in x/z. Native plot allocation is preserved. An occupied destination slot,
including one reserved by an unloaded ship, causes rejection without deleting or remapping anything.
Destination terrain/nearby entities must be clear. Other players must leave both diagnostic levels.

Connected/overlapping native chains, kinematic contraptions, mounted pilots, other onboard players,
mobs, loose entities, general plot-space entities and pending block/fluid ticks are rejected.
Create Super Glue (`create:super_glue`) and Simulated Honey Glue (`simulated:honey_glue`) are the
supported metadata-entity exceptions: their NBT/UUID is captured and reconstructed
in the unchanged native plot coordinates. Preflight also recognizes this exception in Sable's world-space
entity queries, which include projected plot entities. Glue outside the selected plot remains an obstruction.
Native entity chunks must finish loading before cleanup. Supported block
entities are vanilla chests/barrels/signs, Create Redstone Links, the pinned Propulsion creative thruster
(`createpropulsion:creative_thruster_block_entity`, its block-entity registry ID rather than its block ID)
and the pinned Simulated Physics Assembler. Other block entities, including fuel-powered engines,
are rejected before preparation. A block-entity rejection lists every unsupported registry type on the
ship, sorted by ID with a count per type, so one attempt reports the complete block-entity inventory
requiring compatibility work. Other preflight conditions must pass before this inventory check runs.
Native dependency chains are checked; arbitrary third-party constraints
created outside Sable's reported actor connections are unsupported. Do not use this diagnostic for them.
The allowlist establishes a bounded experiment, not blanket compatibility with external mods.

## Authority and persistence

`ShipTransferJournal` writes version 1 compressed NBT records to
`<save>/data/orbitalis-transfers/<ship-uuid>.nbt`. A record contains transaction/ship/pilot UUIDs, source
side, native source/destination snapshots (including glue metadata), exact linear/angular velocities, pilot pose/motion, the
source's previous native save pointer, phase and offline-pilot recovery flags. Unknown versions,
missing state, identity mismatches and invalid numeric state fail closed. Compressed and decompressed
records are bounded to 16 MiB. Native payloads remain tied to the pinned Sable serialization format;
future Sable/Orbitalis migrations must handle unfinished records before changing that format.
The version 1 manifest accepts both glue types. Existing Super Glue records remain readable; older
Orbitalis builds that only accept Super Glue reject unfinished Honey Glue records rather than discarding them.

Journal compression, reads and atomic file replacement/fsync run on one dedicated worker. Only copied
NBT crosses threads. Minecraft world, player, plot and native storage calls remain on the server thread.
Both diagnostic levels' native physics are paused while a transaction is active. Existing pause states
are restored afterward. Player interactions are blocked during the operation and source content/pilot
position are revalidated after asynchronous preparation. Block-entity ticks in the held source/staged
plot chunks are suspended by the loader's Minecraft ticker hook. This includes persisted engine counters;
the complete payload comparison remains strict. Other chunks and clients are unaffected by that hook.
Ordinary scheduled/random block ticks are not globally stopped; a changed payload still cancels preparation.
Cancellation reports distinguish block-data changes, native save-pointer changes and pilot movement.
Transfer-specific native loading tickets and block-entity tick leases are released on completion.

1. `PREPARED`: the source remains authoritative. A full durable snapshot precedes staging. Reconstruct
   the destination with the same ship UUID and verify block/cargo data. Restore exact motion separately
   from Sable's save/load retention and small-velocity thresholds. Preparation failures retain the source.
2. `COMMITTED`: durable destination authority precedes moving the pilot and deleting the source. Native
   Sable owns both reconstruction and removal; no direct edits to its region files or copied implementation
   are used. A native save checkpoint flushes both diagnostic worlds and verifies the saved destination's
   identity and cargo before completion. These existing native checkpoint APIs require the server thread
   and can cause a visible stall. This prototype does not claim a latency suitable for seamless flight.
3. `DONE`: the checkpoint is complete. Replace payload snapshots with a small receipt so a stale cargo
   backup cannot rewind normal gameplay. Normal ship persistence returns entirely to Minecraft/Sable.
   The journal is an authority/recovery record, not a second continuously updated ship simulation.

At startup an unfinished record selects the winner by phase. Remove staged/stale copies, reconstruct
that authoritative snapshot, checkpoint and finish the record. Recovery looks at the frozen snapshot
anchor and the recorded prior native storage pointer, covering a ship saved before or during staging.
A crash during the transaction rolls the ship back to that transaction snapshot; it does not replay later
ordinary gameplay. A pilot who is offline follows the selected winner on login; only pose/motion is
restored, never a copied player inventory. The pending-login flag remains durable until reconciliation.
Both diagnostic worlds stay paused and the recovered ship stays loaded until that pilot logs in, keeping
the recovery anchor stable. This first prototype has no offline flight mode; ordinary Overworld play
continues. A missing completed native checkpoint is an error, never an excuse to replay an old payload.
An unreadable journal or uncertain I/O failure retains paused diagnostic physics and stops further
transfers for inspection/restart, instead of guessing ownership. Preserve the journal when investigating.

## Evidence and remaining work

The native GameTest exercises a round trip with a rotated ship, nonzero small linear/angular velocity,
a chest and pilot pose, occupied-slot rejection, PREPARED rollback, COMMITTED recovery with a stale
occupancy bitmap, offline pilot
reconciliation and preservation of post-DONE cargo changes. Core tests cover the fixed-frame round trip
and continuous gravitational field. A synthetic interrupted journal is not a power-loss/fsync fault test
or a full separate-JVM restart. The Sable-only GameTest does not load Create and therefore does not verify actual Super Glue NBT
round trips. Interactive Propulsion/glue operation, packet ordering, cockpit continuity,
second observers and frame-time stalls need their own client evidence. Scheduled ticks, arbitrary
passenger graphs, connected ships and global surface mapping remain later extensions.
