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

# Destructive within x/z +/-10 and y -1..10 of the caller; invoke once only.
# Both supported dimensions use the vanilla -64..319 build range; validate the whole fixture first.
execute unless predicate orbitalis_flight_test:build_height run tellraw @s {"text":"Ship setup requires a launch position between Y=-63 and Y=309. Return to terrain first; no ship or items were created.","color":"red"}
execute unless predicate orbitalis_flight_test:build_height run return fail
fill ~-10 ~ ~-10 ~10 ~10 ~10 minecraft:air
fill ~-10 ~-1 ~-10 ~10 ~-1 ~10 minecraft:stone
function orbitalis_flight_test:shuttle
give @s create:linked_controller[create:linked_controller_items=[{"slot":0,"item":{"id":"minecraft:red_dye","count":1}},{"slot":1,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":2,"item":{"id":"minecraft:blue_dye","count":1}},{"slot":3,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":4,"item":{"id":"minecraft:green_dye","count":1}},{"slot":5,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":6,"item":{"id":"minecraft:yellow_dye","count":1}},{"slot":7,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":8,"item":{"id":"minecraft:diamond","count":1}},{"slot":9,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":10,"item":{"id":"minecraft:feather","count":1}},{"slot":11,"item":{"id":"minecraft:cobblestone","count":1}}]]
give @s create:wrench
tp @s ~0.5 ~4 ~-1.5 0 35
tellraw @s {"text":"Shuttle ready. Assemble with the cabin lever. Toggle manual controller OFF before external programmable control. Manual: Space lift, Shift down, WASD translation. Four cargo chests: 16 cobblestone each.","color":"gold"}
