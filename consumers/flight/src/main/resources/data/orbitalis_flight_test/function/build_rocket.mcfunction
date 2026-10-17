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

fill ~-6 ~ ~-6 ~6 ~16 ~6 minecraft:air
fill ~-6 ~-1 ~-6 ~6 ~-1 ~6 minecraft:stone
function orbitalis_flight_test:rocket
give @s create:wrench
tp @s ~0.5 ~10 ~1.5 180 20
tellraw @s {"text":"Vector rocket ready. Assemble with the cabin lever; leave manual throttle and steering off. Creative vector engines need no fuel or FE.","color":"gold"}
