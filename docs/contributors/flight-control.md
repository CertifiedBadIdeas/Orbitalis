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

# Flight ownership

Orbitalis supplies celestial gravity, reference frames, automatic world transfers, orbital prediction,
and navigation plans. It does not register an autopilot, own engine inputs, execute maneuvers, or retain
flight-control leases. Programmable control belongs to external integrations such as Compukters Guest
Kotlin. No Compukters dependency is introduced into Orbitalis.

The map can edit and save timed maneuver plans. Saving a plan does not command a ship. Existing plan
SavedData remains readable. Historical launch-site files are left on disk but no longer consumed.
Built-in autopilot commands, steering mixins, guidance models and engine backends have been removed.
Existing engine settings and native ship/fuel/cargo payloads remain the responsibility of their mods.

The optional Propulsion payload normalizer remains for exact Sable transfers. It compares native NBT
without importing Propulsion or Create APIs; those APIs are forbidden in production source. The
consumer retains finite fuel, native assembly and snapshot tests, not a replacement host autopilot.

Read-only integrations may sample `SableFlightGravity.reading(body)` on the server thread for position,
velocity, effective acceleration and mass in the body's current chart. Use Orbitalis frame conversion
APIs for physical root observations; do not duplicate the gravity field or apply a second force.

## Verification

```
./gradlew-sandbox-dev-parallel-summary verifyLocalFull
./gradlew-sandbox-dev-parallel-summary -PorbitalisGameTestSuite=control :v1_21_1-neoforge:runGameTestServer
./gradlew-sandbox-dev-parallel-summary -p consumers/flight check runGameTestServer
```

The control suite checks that removing the controller leaves surface gravity active and no autopilot
command registered. Physical flight and automatic transfer behavior retain their independent suites.
