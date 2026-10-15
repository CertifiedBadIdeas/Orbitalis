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

# Shuttle geometry, cargo and glue only; called by guarded setup and native assembly tests.
# Bow points north (-Z). All engine controllers are separated; glue excludes the ground.
fill ~-2 ~3 ~-4 ~2 ~7 ~4 minecraft:smooth_quartz
fill ~-1 ~4 ~-3 ~1 ~6 ~3 minecraft:air
# Glazed cockpit and an open aft boarding hatch.
fill ~-1 ~4 ~-4 ~1 ~6 ~-4 minecraft:cyan_stained_glass
fill ~-2 ~5 ~-3 ~-2 ~6 ~-1 minecraft:cyan_stained_glass
fill ~2 ~5 ~-3 ~2 ~6 ~-1 minecraft:cyan_stained_glass
fill ~0 ~4 ~4 ~0 ~5 ~4 minecraft:air
fill ~-1 ~3 ~-5 ~1 ~3 ~-5 minecraft:light_blue_concrete
fill ~-1 ~7 ~-3 ~1 ~7 ~3 minecraft:light_blue_concrete
# Swept wings, corner pods and landing legs.
fill ~-4 ~3 ~1 ~4 ~3 ~3 minecraft:smooth_quartz
fill ~-3 ~3 ~-2 ~3 ~3 ~0 minecraft:smooth_quartz
fill ~-4 ~3 ~-3 ~-3 ~3 ~-3 minecraft:light_blue_concrete
fill ~3 ~3 ~-3 ~4 ~3 ~-3 minecraft:light_blue_concrete
fill ~-4 ~3 ~-3 ~-4 ~6 ~-3 minecraft:light_blue_concrete
fill ~-4 ~3 ~3 ~-4 ~6 ~3 minecraft:light_blue_concrete
fill ~4 ~3 ~-3 ~4 ~6 ~-3 minecraft:light_blue_concrete
fill ~4 ~3 ~3 ~4 ~6 ~3 minecraft:light_blue_concrete
fill ~-3 ~0 ~-2 ~-3 ~2 ~-2 minecraft:polished_andesite
fill ~-3 ~0 ~2 ~-3 ~2 ~2 minecraft:polished_andesite
fill ~3 ~0 ~-2 ~3 ~2 ~-2 minecraft:polished_andesite
fill ~3 ~0 ~2 ~3 ~2 ~2 minecraft:polished_andesite
# Four independently controlled engines per force direction. Facing is thrust direction.
setblock ~-4 ~2 ~-3 createpropulsion:creative_thruster[facing=up]{ScrollValue:9}
setblock ~-4 ~7 ~-3 createpropulsion:creative_thruster[facing=down]{ScrollValue:9}
setblock ~-5 ~2 ~-3 create:redstone_link[facing=west,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-4 ~8 ~-3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:feather",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-4 ~2 ~3 createpropulsion:creative_thruster[facing=up]{ScrollValue:9}
setblock ~-4 ~7 ~3 createpropulsion:creative_thruster[facing=down]{ScrollValue:9}
setblock ~-5 ~2 ~3 create:redstone_link[facing=west,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-4 ~8 ~3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:feather",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~4 ~2 ~-3 createpropulsion:creative_thruster[facing=up]{ScrollValue:9}
setblock ~4 ~7 ~-3 createpropulsion:creative_thruster[facing=down]{ScrollValue:9}
setblock ~5 ~2 ~-3 create:redstone_link[facing=east,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~4 ~8 ~-3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:feather",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~4 ~2 ~3 createpropulsion:creative_thruster[facing=up]{ScrollValue:9}
setblock ~4 ~7 ~3 createpropulsion:creative_thruster[facing=down]{ScrollValue:9}
setblock ~5 ~2 ~3 create:redstone_link[facing=east,receiver=true]{FrequencyFirst:{id:"minecraft:diamond",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~4 ~8 ~3 create:redstone_link[facing=up,receiver=true]{FrequencyFirst:{id:"minecraft:feather",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-5 ~4 ~-3 createpropulsion:creative_thruster[facing=east]{ScrollValue:9}
setblock ~-5 ~6 ~-3 createpropulsion:creative_thruster[facing=east]{ScrollValue:9}
setblock ~-5 ~5 ~-3 create:redstone_link[facing=west,receiver=true]{FrequencyFirst:{id:"minecraft:yellow_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-5 ~4 ~3 createpropulsion:creative_thruster[facing=east]{ScrollValue:9}
setblock ~-5 ~6 ~3 createpropulsion:creative_thruster[facing=east]{ScrollValue:9}
setblock ~-5 ~5 ~3 create:redstone_link[facing=west,receiver=true]{FrequencyFirst:{id:"minecraft:yellow_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~5 ~4 ~-3 createpropulsion:creative_thruster[facing=west]{ScrollValue:9}
setblock ~5 ~6 ~-3 createpropulsion:creative_thruster[facing=west]{ScrollValue:9}
setblock ~5 ~5 ~-3 create:redstone_link[facing=east,receiver=true]{FrequencyFirst:{id:"minecraft:green_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~5 ~4 ~3 createpropulsion:creative_thruster[facing=west]{ScrollValue:9}
setblock ~5 ~6 ~3 createpropulsion:creative_thruster[facing=west]{ScrollValue:9}
setblock ~5 ~5 ~3 create:redstone_link[facing=east,receiver=true]{FrequencyFirst:{id:"minecraft:green_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-2 ~4 ~-5 createpropulsion:creative_thruster[facing=south]{ScrollValue:9}
setblock ~-2 ~6 ~-5 createpropulsion:creative_thruster[facing=south]{ScrollValue:9}
setblock ~-2 ~5 ~-5 create:redstone_link[facing=north,receiver=true]{FrequencyFirst:{id:"minecraft:blue_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~2 ~4 ~-5 createpropulsion:creative_thruster[facing=south]{ScrollValue:9}
setblock ~2 ~6 ~-5 createpropulsion:creative_thruster[facing=south]{ScrollValue:9}
setblock ~2 ~5 ~-5 create:redstone_link[facing=north,receiver=true]{FrequencyFirst:{id:"minecraft:blue_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~-2 ~4 ~5 createpropulsion:creative_thruster[facing=north]{ScrollValue:9}
setblock ~-2 ~6 ~5 createpropulsion:creative_thruster[facing=north]{ScrollValue:9}
setblock ~-2 ~5 ~5 create:redstone_link[facing=south,receiver=true]{FrequencyFirst:{id:"minecraft:red_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
setblock ~2 ~4 ~5 createpropulsion:creative_thruster[facing=north]{ScrollValue:9}
setblock ~2 ~6 ~5 createpropulsion:creative_thruster[facing=north]{ScrollValue:9}
setblock ~2 ~5 ~5 create:redstone_link[facing=south,receiver=true]{FrequencyFirst:{id:"minecraft:red_dye",count:1},FrequencyLast:{id:"minecraft:cobblestone",count:1}}
# Separated chests retain the original four cargo sentinels.
setblock ~-1 ~4 ~-2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~-1 ~4 ~2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~1 ~4 ~-2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~1 ~4 ~2 minecraft:chest[facing=north,type=single]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}
setblock ~0 ~4 ~0 simulated:physics_assembler[face=floor,facing=north]
# Create positions glue at its bottom-centre, not the minimum corner.
summon create:super_glue ~0.5 ~0 ~0.5 {From:[-5.5d,0d,-5.5d],To:[5.5d,9d,5.5d]}
