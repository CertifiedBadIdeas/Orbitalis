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

# Surface/orbit ship transfer

The optional Sable adapter transfers one independent construction and an optional pilot, including a seated survival player
between ordinary Overworld and `orbitalis:space`. Use a disposable world and the isolated flight
consumer. See [finite-body flight](flat-gravity.md) for gravity, altitude-triggered mission handoffs
and the current low orbit. Journal staging pauses physics; this does not establish seamless rendering.

## Manual scenario

Launch from `consumers/flight` using `./gradlew runClient`. Install the
[terrain ship fixture](flight-consumer.md#repeatable-terrain-ship-fixture) on a clear Overworld pad,
assemble it, disable creative flight and stand on deck. With manual controls off, use:

```mcfunction
/orbitalis flight sample
/orbitalis flight transfer
```

Wait for completion before using controls. Inspect the ship and chest contents. Run `transfer` again
on deck to return. The manual command supplies no propulsion; the consumer autopilot provides a
powered ascent and return. There are no `flight enter`, `flight leave` or `transfer space` commands.

`FlatPlanetFrame` adds 4096 to physical Y in space. Axes, inertial linear/angular velocities and
player view direction are unchanged. Both worlds sample the same physical finite-body field.
Transfers require |x|,|z|<=5032 (the 5000 m half-footprint plus hull margin), physical Y=64..16000 and represented pilot/ship Y>=64. Space flight can
leave this transfer patch but must return above it before a handoff. The consumer calls
`SableFlightIntegration.transferService(server).begin(player, body, targetDimension)` on the server
thread. It releases engine inputs and waits for fade, remains idle while `busy`, fails on `error` and
reacquires the ship by UUID after readiness. Both overloads select only the Overworld/space pair.
Guidance remains consumer-owned.

## Supported boundary

The adapter accepts a ship with at most four native plot chunks and a 4096-block bounding
volume, at most 32 m wide in x/z. Prefer the original native plot address when free; otherwise choose
an available address from Sable's occupancy bitmap, including reservations for unloaded ships.
A full destination rejects before modifying the source. Persist the selected address in PREPARED,
then recheck it before reconstruction; a slot claimed during asynchronous preparation cancels safely.
Foreign ships are never moved to make room. Physical destination obstructions still reject transfer.
Destination terrain/nearby entities must be clear. Other players may stay in either participating level.

Connected/overlapping native chains, kinematic contraptions, more than one onboard player and
pending block/fluid ticks are rejected. Up to 32 native serializable entities can travel, including
a minecart and its player passenger. The manifest stores UUIDs, native entity NBT, explicit plot/world
coordinate ownership and mount edges. Cycles, duplicate identities and external mount references
are rejected. Plot-space positions follow the selected plot relocation; world-space positions follow
the frame translation. Non-player entity ticks are held during staging, and changed source entity
data cancels preparation. Players use their existing native player data, never an NBT clone.
After client ship tracking and teleport acknowledgment, passenger edges are restored with native
`startRiding`. Server GameTests exercise this with an inert connection; visual/client riding still
needs an interactive check. A modded entity must accept dimension changes and native serialization;
this does not certify every third-party entity or its external references.
Create Super Glue (`create:super_glue`) and Simulated Honey Glue (`simulated:honey_glue`) are the
supported metadata-entity exceptions: their NBT/UUID is captured and reconstructed
in the selected destination plot coordinates. Preflight also recognizes this exception in Sable's world-space
entity queries, which include projected plot entities. Glue outside the selected plot remains an obstruction.
Native entity chunks must finish loading before cleanup. Supported block
entities are vanilla chests/barrels/signs, Create Redstone Links, the pinned Propulsion creative thruster
(`createpropulsion:creative_thruster_block_entity`, its block-entity registry ID rather than its block ID)
and the pinned Simulated Physics Assembler. Other block entities, including fuel-powered engines,
are rejected before preparation. A block-entity rejection lists every unsupported registry type on the
ship, sorted by ID with a count per type, so one attempt reports the complete block-entity inventory
requiring compatibility work. Other preflight conditions must pass before this inventory check runs.
Native dependency chains are checked; arbitrary third-party constraints
created outside Sable's reported actor connections are unsupported. Do not transfer them.
The allowlist establishes a bounded experiment, not blanket compatibility with external mods.

## Authority and persistence

`ShipTransferJournal` writes version 4 compressed NBT records to
`<save>/data/orbitalis-transfers/<ship-uuid>.nbt`. A record contains transaction/ship/pilot UUIDs, source
side, native source/destination snapshots (including glue metadata), exact linear/angular velocities, pilot pose/motion, the
source's previous native save pointer, phase, orbital dimension and offline-pilot recovery flags. Unknown versions,
missing state, identity mismatches and invalid numeric state fail closed. Compressed and decompressed
records are bounded to 16 MiB. Native payloads remain tied to the pinned Sable serialization format;
future Sable/Orbitalis migrations must handle unfinished records before changing that format.
Version 4 requires explicit `surface_dimension=minecraft:overworld` and
`orbital_dimension=orbitalis:space`. The 4096-m translation remains unchanged.
Active version-3 records remain readable; version 4 adds optional pilots and entity/mount manifests.

The retired `flight_test`, `orbit_test` and `space_test` dimensions are no longer registered. Their
save directories are not deleted or converted. Legacy version-1/2 journals and version-3/4 diagnostic
pairs are recognized only for retirement: a DONE receipt with no pending pilot is ignored without
rewriting the file. An unfinished transaction or pending pilot fails closed with an explicit recovery
message. Restore a backup and finish recovery using the previous version before upgrading; never
rename an old dimension or edit its journal into the active pair. Unknown routes/versions remain errors.

Journal compression, reads and atomic file replacement/fsync run on one dedicated worker. Only copied
NBT crosses threads. Minecraft world, player, plot and native storage calls remain on the server thread.
Both participating levels' native physics are paused while a transaction is active. Existing pause states
are restored afterward. Player interactions are blocked during the operation and source content/pilot
position are revalidated after asynchronous preparation. Block-entity ticks in the held source/staged
plot chunks are suspended by the loader's Minecraft ticker hook. This includes persisted engine counters;
payload comparison preserves cargo and configuration. Block entities are matched by plot coordinates because
Sable serializes an unordered position set; list order carries no meaning. Missing, extra or duplicate
positions and NBT field changes still reject reconstruction, except for the pinned Propulsion 1.1.5
creative thruster's `UpdateConnectivity` work flag. Its native `read()` re-arms connectivity rebuilding
for single engines. The comparison accepts an absent/true flag without changing either snapshot or
the live destination, so the first resumed engine tick still rebuilds links. Engine configuration,
controller offsets, thrust, counters and all other payload fields remain exact; no other block type
receives this exception. A payload rejection identifies the
block type, plot position and changed fields. Other chunks and clients are unaffected by that hook.
Ordinary scheduled/random block ticks are not globally stopped; a changed payload still cancels preparation.
Cancellation reports distinguish block-data changes, native save-pointer changes and pilot movement.
Transfer-specific native loading tickets and block-entity tick leases are released on completion.

1. `PREPARED`: the source remains authoritative. A full durable snapshot precedes staging. Reconstruct
   the destination with the same ship UUID and verify block/cargo data. Restore exact motion separately
   from Sable's save/load retention and small-velocity thresholds. Preparation failures retain the source.
2. `COMMITTED`: durable destination authority precedes deleting the source and moving the pilot. Native
   Sable owns both reconstruction and removal; no direct edits to its region files or copied implementation
   are used. Source removal happens before dimension travel because Sable's tracking-removal packet carries
   plot coordinates without a dimension identifier. Once native destination tracking has queued the complete
   ship bundle, a second teleport places the pilot back on the recorded deck position. Physics and block-entity
   ticks remain paused until vanilla acknowledges that teleport and Sable reports the pilot attached to the
   destination ship. The acknowledgment follows the ship bundle on TCP; no new network payload is introduced.
   Each readiness phase is bounded to 200 server ticks. An unavailable pilot or timeout retains COMMITTED
   destination authority and paused physics for reconciliation. The same barrier protects recovery/login handoffs.
   A native checkpoint calls Sable's holding-map `saveAll` (the same entry point as its ServerLevel
   save hook), flushes its storage and saves allocation/ticket SavedData plus players. It verifies the
   destination's native saved identity and cargo before completion. Ordinary terrain is unchanged by
   a handoff and remains under Minecraft's regular save lifecycle; the handoff does not synchronously
   flush every vanilla chunk visited along an orbit. These native checkpoint APIs still require the
   server thread. This prototype does not claim a latency suitable for seamless flight.
3. `DONE`: the checkpoint is complete. Replace payload snapshots with a small receipt so a stale cargo
   backup cannot rewind normal gameplay. Normal ship persistence returns entirely to Minecraft/Sable.
   The journal is an authority/recovery record, not a second continuously updated ship simulation.

At startup an unfinished record selects the winner by phase. Remove staged/stale copies, reconstruct
that authoritative snapshot, checkpoint and finish the record. Recovery looks at the frozen snapshot
anchor and the recorded prior native storage pointer, covering a ship saved before or during staging.
A crash during the transaction rolls the ship back to that transaction snapshot; it does not replay later
ordinary gameplay. A pilot who is offline follows the selected winner on login; only pose/motion is
restored, never a copied player inventory. The pending-login flag remains durable until reconciliation.
The route's supported flight worlds stay paused and the recovered ship stays loaded until that pilot logs in, keeping
the recovery anchor stable. This first prototype has no offline flight mode; ordinary block/entity simulation continues, but native construction physics and interactions in the
participating Overworld are held during this bounded recovery. A missing completed native checkpoint is an error, never an excuse to replay an old payload.
An unreadable journal or uncertain I/O failure retains paused flight physics and stops further
transfers for inspection/restart, instead of guessing ownership. Preserve the journal when investigating.

Plot relocation shifts the serialized local rotation point, absolute block-entity x/z and attached
entity `Pos`, plus the pinned redstone link's absolute `LastKnownPosition`. Physical world pose/bounds,
orientation, exact velocities, ship/entity UUIDs and payloads
remain unchanged apart from the existing world-frame height translation. Chunk keys are native
plot-relative coordinates. Pinned Super Glue/Honey Glue `From`/`To` bounds and Propulsion controller
offsets are relative, so they remain exact. This is a bounded adapter for the existing allowlist,
not a generic recursive NBT coordinate rewrite or support for arbitrary mod block entities.
Active version-3/4 journals store independent source and destination plot snapshots; recovery uses
each recorded address. Plot relocation requires no schema change.

## Evidence and remaining work

Native GameTests exercise the Overworld/space round trip using a rotated ship, nonzero small linear/angular velocity,
a chest and pilot pose, relocation around loaded/unloaded foreign ships on outbound/return, full-plot
rejection, PREPARED rollback, COMMITTED recovery across unequal plot addresses with a stale
occupancy bitmap, offline pilot
reconciliation and preservation of post-DONE cargo changes. It also checks unordered block-entity
comparison, rejection of cargo/type/position/duplicate changes and the narrowly scoped engine work-flag exception.
The inert test pilot models delayed ship delivery and deck contact, handles the actual vanilla teleport
acknowledgment packet and exercises the loader accessor. It checks that physics stays paused during both
waits and that source removal precedes world travel. This is server-side evidence, not an observed client login.
An isolated headless flight-consumer probe on 2026-10-15 reconstructed a copy of a saved player ship
with its actual Create/Propulsion/Simulated block entities and three Honey Glue entities, then completed
surface-to-orbit and return transactions with cargo checks. This reproduced the unordered-list and
engine-connectivity-flag failures before their fixes; it does not establish client rendering or control continuity.
Retirement tests verify that completed old receipts remain untouched, unfinished old records are
rejected, and unknown pairs fail closed. Core tests cover the fixed-frame round trip
and continuous gravitational field. A synthetic interrupted journal is not a power-loss/fsync fault test
or a full separate-JVM restart. The Sable-only GameTest does not load Create and therefore does not verify actual Super Glue NBT
round trips. The separate consumer mission forces an unloaded destination-slot collision and carries
an unpowered native Redstone Link, Super Glue and Honey Glue through both handoffs, checking glue
identity/plot positions and native engine flight through orbital insertion and return/landing. The link
fixture is installed after its neighbours without creating scheduled ticks; the production pending-tick
restriction remains unchanged. Interactive Propulsion/glue operation, packet ordering, cockpit continuity,
second observers and frame-time stalls need their own client evidence. Scheduled ticks, arbitrary
passenger graphs, connected ships and global surface mapping remain later extensions.

Journal v4 retains active v3 explicit routes; retired diagnostics follow the retirement policy above. A missing pilot is
valid only in v4 and cannot have a pending-pilot receipt. Source/destination passenger manifests
join the same PREPARED/COMMITTED authority decision as the ship. Recovery removes both entity
copies before reconstructing the winner and its mount graph. DONE strips entity payloads as well
as ship cargo: receipts never become a permanent vehicle/inventory backup. Pending-player recovery
retains the existing paused-anchor behavior until the player returns; general offline passenger
flight and multiple onboard players are not implemented.

For travelling entities, the checkpoint includes vanilla entity storage before the player save and
DONE. A narrow `ServerLevelEntityCheckpointMixin` exposes the native entity-only save barrier; it
does not change entity serialization or flush all visited terrain chunks. Mounted player vehicles
retain vanilla's player/RootVehicle ownership rules. Sable may create a LoginPoint during player
saving, so SavedData is flushed again afterwards. Source/losing entity copies use
`RemovalReason.CHANGED_DIMENSION`, which avoids the inventory drops caused by `discard()` on
container vehicles. These native save barriers run on the server thread and can still pause a busy
world; seamless/asynchronous native entity checkpoints are not claimed.

Transfer stage costs are logged as `ORBITALIS_TRANSFER_TIMING`, including asynchronous journal writes,
native checkpoints and total paused duration. ETA-based terrain preparation does not remove this
transaction's physics pause or prove seamless client rendering. Live player handoff preserves the
controlling client's captured velocity through a bounded custom payload; durable recovery instead
uses the recorded server velocity. The fixed +4096 frame and version-four journal layout are retained.
