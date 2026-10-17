# The Orbitalis Developers
#
# Copyright 2026 Vsevolod Petrov (lazyhat)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Destructive in x/z +/-6 and y=-1..16. Invoke once on a disposable Overworld launch pad.
execute unless dimension minecraft:overworld run tellraw @s {"text":"Use a disposable Overworld launch site.","color":"red"}
execute unless dimension minecraft:overworld run return fail
execute unless predicate orbitalis_flight_test:rocket_build_height run tellraw @s {"text":"Rocket setup requires feet between Y=-63 and Y=303.","color":"red"}
execute unless predicate orbitalis_flight_test:rocket_build_height run return fail
execute align xyz run function orbitalis_flight_test:build_rocket
