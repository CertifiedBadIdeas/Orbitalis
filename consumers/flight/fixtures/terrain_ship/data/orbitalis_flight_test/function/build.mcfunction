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
fill ~-3 ~4 ~-3 ~3 ~4 ~3 minecraft:oak_planks
fill ~-3 ~ ~-3 ~-3 ~3 ~-3 minecraft:oak_planks
fill ~-3 ~ ~3 ~-3 ~3 ~3 minecraft:oak_planks
fill ~3 ~ ~-3 ~3 ~3 ~-3 minecraft:oak_planks
fill ~3 ~ ~3 ~3 ~3 ~3 minecraft:oak_planks
setblock ~0 ~3 ~0 createpropulsion:creative_thruster[facing=up]{ScrollValue:9}
setblock ~1 ~3 ~0 create:redstone_link[facing=east,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-1 ~3 ~0 create:redstone_link[facing=west,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~0 ~4 ~3 createpropulsion:creative_thruster[facing=north]{ScrollValue:0}
setblock ~0 ~5 ~3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:red_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~0 ~4 ~-3 createpropulsion:creative_thruster[facing=south]{ScrollValue:0}
setblock ~0 ~5 ~-3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:blue_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~3 ~4 ~0 createpropulsion:creative_thruster[facing=west]{ScrollValue:0}
setblock ~3 ~5 ~0 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:green_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-3 ~4 ~0 createpropulsion:creative_thruster[facing=east]{ScrollValue:0}
setblock ~-3 ~5 ~0 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:yellow_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-2 ~5 ~-2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~-2 ~5 ~2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~2 ~5 ~-2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~2 ~5 ~2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~ ~5 ~ simulated:physics_assembler[face=floor,facing=north]
summon create:super_glue ~-3 ~ ~-3 {From:[0d,0d,0d],To:[7d,6d,7d]}
give @s create:linked_controller[create:linked_controller_items=[{"slot":0,"item":{"id":"minecraft:red_dye","count":1}},{"slot":1,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":2,"item":{"id":"minecraft:blue_dye","count":1}},{"slot":3,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":4,"item":{"id":"minecraft:green_dye","count":1}},{"slot":5,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":6,"item":{"id":"minecraft:yellow_dye","count":1}},{"slot":7,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":8,"item":{"id":"minecraft:diamond","count":1}},{"slot":9,"item":{"id":"minecraft:cobblestone","count":1}},{"slot":10,"item":{"id":"minecraft:feather","count":1}},{"slot":11,"item":{"id":"minecraft:cobblestone","count":1}}]]
give @s create:wrench
tp @s ~0.5 ~5 ~-1.5 0 45
tellraw @s {"text":"Terrain ship ready. Assemble with the centre lever. Hold controller: right-click toggles control; Space lift, WASD translation/braking. Release lift to descend. No automatic stabilizer.","color":"gold"}
