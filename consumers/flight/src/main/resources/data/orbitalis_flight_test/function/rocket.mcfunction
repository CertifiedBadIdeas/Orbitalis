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

# Rocket geometry only: four vector engines below the COM; clear exhaust and landing legs.
fill ~-2 ~4 ~-1 ~2 ~13 ~1 minecraft:white_concrete
fill ~-1 ~4 ~-2 ~1 ~13 ~2 minecraft:white_concrete
fill ~-3 ~4 ~-3 ~3 ~4 ~3 minecraft:gray_concrete
fill ~-1 ~5 ~-1 ~1 ~12 ~1 minecraft:air
fill ~-2 ~8 ~-1 ~2 ~9 ~1 minecraft:black_concrete
fill ~-1 ~8 ~-2 ~1 ~9 ~2 minecraft:black_concrete
fill ~-1 ~8 ~-1 ~1 ~8 ~1 minecraft:air
fill ~-1 ~11 ~-2 ~1 ~12 ~-2 minecraft:light_blue_stained_glass
setblock ~0 ~10 ~2 minecraft:birch_door[facing=south,half=lower,open=false,hinge=left,powered=false]
setblock ~0 ~11 ~2 minecraft:birch_door[facing=south,half=upper,open=false,hinge=left,powered=false]
fill ~-1 ~14 ~-1 ~1 ~14 ~1 minecraft:white_concrete
setblock ~0 ~15 ~0 minecraft:red_concrete
fill ~-3 ~0 ~-3 ~-3 ~3 ~-3 minecraft:iron_bars
setblock ~-3 ~0 ~-3 minecraft:iron_block
fill ~-3 ~0 ~3 ~-3 ~3 ~3 minecraft:iron_bars
setblock ~-3 ~0 ~3 minecraft:iron_block
fill ~3 ~0 ~-3 ~3 ~3 ~-3 minecraft:iron_bars
setblock ~3 ~0 ~-3 minecraft:iron_block
fill ~3 ~0 ~3 ~3 ~3 ~3 minecraft:iron_bars
setblock ~3 ~0 ~3 minecraft:iron_block
setblock ~-1 ~3 ~-1 createpropulsion:creative_vector_thruster[facing=up]{ScrollValue:49}
setblock ~-1 ~10 ~-1 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~-1 ~3 ~1 createpropulsion:creative_vector_thruster[facing=up]{ScrollValue:49}
setblock ~-1 ~10 ~1 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~1 ~3 ~-1 createpropulsion:creative_vector_thruster[facing=up]{ScrollValue:49}
setblock ~1 ~10 ~-1 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~1 ~3 ~1 createpropulsion:creative_vector_thruster[facing=up]{ScrollValue:49}
setblock ~1 ~10 ~1 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
# Native RCS pods: opposed couples provide pitch, yaw and roll with main engines off.
setblock ~0 ~6 ~-3 createpropulsion:creative_thruster[facing=south]{ScrollValue:2}
setblock ~0 ~6 ~3 createpropulsion:creative_thruster[facing=north]{ScrollValue:2}
setblock ~-3 ~6 ~0 createpropulsion:creative_thruster[facing=east]{ScrollValue:2}
setblock ~3 ~6 ~0 createpropulsion:creative_thruster[facing=west]{ScrollValue:2}
setblock ~0 ~12 ~-3 createpropulsion:creative_thruster[facing=south]{ScrollValue:2}
setblock ~0 ~12 ~3 createpropulsion:creative_thruster[facing=north]{ScrollValue:2}
setblock ~-3 ~12 ~0 createpropulsion:creative_thruster[facing=east]{ScrollValue:2}
setblock ~3 ~12 ~0 createpropulsion:creative_thruster[facing=west]{ScrollValue:2}
setblock ~3 ~10 ~1 createpropulsion:creative_thruster[facing=north]{ScrollValue:2}
setblock ~-3 ~10 ~-1 createpropulsion:creative_thruster[facing=south]{ScrollValue:2}
setblock ~3 ~10 ~-1 createpropulsion:creative_thruster[facing=south]{ScrollValue:2}
setblock ~-3 ~10 ~1 createpropulsion:creative_thruster[facing=north]{ScrollValue:2}
setblock ~0 ~10 ~0 simulated:physics_assembler[face=floor,facing=north]
setblock ~0 ~11 ~0 minecraft:lever[face=floor,facing=north,powered=false]
summon create:super_glue ~0.5 ~0 ~0.5 {From:[-3.5d,0d,-3.5d],To:[3.5d,16d,3.5d]}
