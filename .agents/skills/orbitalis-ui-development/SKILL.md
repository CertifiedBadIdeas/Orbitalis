---
name: orbitalis-ui-development
description: Create or modify player-facing Orbitalis screens, HUD controls, layout, fonts and input. Excludes world rendering and standalone texture work.
---

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

# Shared presentation and input

Inspect existing components before adding new ones; this scaffold has no established UI toolkit or
visual theme. Do not import Compukters IDE controls or claim they exist here. Settle material design
choices with the user and keep reusable theme, geometry, font metrics and controls in shared code.
Version leaves adapt actual drawing/input API differences. Draw bounds and hit bounds must agree;
check scale transforms, clipping, focus, tooltips, disabled states and paint order where relevant.
Keep server authority separate from local UI state. Use the integration skill for Minecraft lifecycle.
Verify pure layout/input transforms at the lowest useful layer, then observe a bounded in-client
scenario for GPU/rendering and subjective changes. Record target, world/screen, scale, actions and
actual result; client launch alone does not establish appearance or interaction correctness.
