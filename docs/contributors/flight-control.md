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

# Flight control and optional engine integration

Orbitalis owns guidance independently of the development consumer. The existing flight commands
remain `/orbitalis flight autopilot hold|altitude|orbit|land|execute|status|stop|list`, including UUID
selection. They are registered when Sable is present. Directory/status lookup does not wake dormant
ships or generate terrain. Missing engines reject execution explicitly; the map's execute hook stays
unavailable when no backend is installed.

## Ownership

- `core/flight` owns force/torque allocation, direction/rate control, ascent/return references,
  orbit-capture validation and measured non-gravitational delta-v. It has no Minecraft, loader,
  Sable, JOML or engine dependency.
- Shared/common `flight/control` owns transient server missions, flight ownership, native frame
  sampling, launch bookmarks, loading tickets and commands. `FlightEngineBackend`, `HoldFlightDrive`
  and `RocketFlightDrive` separate guidance requests from native engine inputs.
- Shared/common `integration/propulsion` implements those requests using independently installed
  Propulsion 1.1.5. This adapter and its shared/neoforge liquid-capacity bridge compile against
  Create/Propulsion APIs. Native engines apply
  every force; guidance never substitutes virtual thrust or writes live velocities.
- Shared/neoforge installs lifecycle listeners with Sable present and the backend only when Propulsion
  is also loaded. A string-targeted `@Pseudo` vector-engine mixin provides transient steering ownership;
  it is absent when its target is absent. The base mod and ship directory remain usable without engines.
- `SableShipDirectory` enumerates already loaded native bodies for both map snapshots and flight
  commands; the existing orbital-motion SavedData remains the authority for unloaded estimates.
- `consumers/flight` owns only the external runtime composition, build-pad/ship functions and native
  integration GameTests. Its minimal mod entry point registers no flight controllers or mixins.

Propulsion, Create, Ponder and Registrate are compile-only API inputs to the production build, pinned
in the root version catalogue. No third-party implementation, engine model, texture or consumer
fixture is nested in Orbitalis. Propulsion metadata is optional and version-pinned. The source guard
allows engine API references only in the named adapter package, loader fuel/config bridge and steering mixin; archive checks
continue to reject bundled upstream implementations and required consumer dependencies.

## Retained behaviour and save compatibility

This promotion preserves the bounded existing controller, not a claim of arbitrary-vessel support.
The Propulsion backend supports Creative engines and independent standard liquid thrusters with usable
native fuel. Commands require an owner with permission level 2, including Survival pilots. Rocket layout, engine count, thrust, preflight and speed bounds still
apply. Launch/return guidance remains Earth-specific and uses a clear launch pad; it does not yet
plan a lunar landing or avoid arbitrary terrain/buildings. Maneuver execution remains orbital and
requires turn/burn lead time. These limits are documented in [the flight scenarios](flight-consumer.md).

`data/orbitalis_flight_launch_sites.dat` retains version 3 and the existing owner/ship keys and
Overworld surface semantics. The reader preserves the existing v2 migration and rejects retired,
malformed or duplicate records. No file rename or reinterpretation is introduced.
The native ticket type `orbitalis_flight_consumer:active_autopilot` deliberately keeps its historical
ID so Sable can read existing ticket registries. A fresh session removes orphaned tickets of that
type. Active controls/throttle are not resumed on restart. Tickets follow reconstructed ships through
handoff and release on completion, stop, failure and server shutdown. Reserved travel and the
existing journal remain authoritative during dimension transfers.

The engine adapter owns only the controls it acquires after complete preflight. Stop clears native
commands and releases ownership; native engine fade remains in effect. The consumer's former
vector mixin is removed so native steering has exactly one owner.

## Verification

Use the managed wrappers from the task checkout:

```
./gradlew-sandbox-dev-parallel-summary verifyLocalFull
./gradlew-sandbox-dev-parallel-summary -PorbitalisGameTestSuite=control :v1_21_1-neoforge:runGameTestServer
./gradlew-sandbox-dev-parallel-summary -p consumers/flight check runGameTestServer
```

The control suite exercises a real Sable-only server without Propulsion: engine absence, unavailable
map execution and available ship-directory commands. The `shuttle` consumer suite isolates native force/assembly checks; its gravity fixtures lease
automatic travel during measurement and compare forces in the physical root frame. The complete
consumer suite runs actual native hold/cutoff,
preflight rejection, uncrewed control/loading, bookmark persistence, maneuver execution and complete
rocket/shuttle trips. Mathematical tests moved with their models to core. Build success alone is
not evidence of native flight, cockpit continuity or performance on large factories.
