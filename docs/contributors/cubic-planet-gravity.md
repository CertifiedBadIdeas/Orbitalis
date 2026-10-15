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

# Cubic planet geometry and gravity

The accepted gameplay shape is a cube with finite flat faces. A face interior has one local up/down
axis, so a vertical launch away from the face centre must not drift sideways merely because of gravity.
Far from the planet the field becomes central and inverse-square. The existing `CubeSphere` model
and spherical laboratory remain separate mathematical experiments; they are not the selected production
surface geometry. A physical cube is not a spherical surface split into six charts.

## Implemented field

Core `CubicGravity` is a conservative gameplay model, not the Newtonian field of a uniform cuboid.
It takes planet-centred Cartesian positions in metres and returns acceleration in m/s² plus specific
potential in m²/s². The model has no Minecraft, Sable, loading or persistence dependencies.

Let `a` be the face half-extent, `g` face-centre surface gravity and `ε` the edge blend width.
A regularized cube radius `m` and its gradient are computed from the six signed coordinates `c`:

```text
m = max over w_i >= 0, sum(w_i) = 1 of [sum(w_i*c_i) + ε*(1-sum(w_i²))/2]
c = (+x, -x, +y, -y, +z, -z)
```

The maximizing weights are unique. Their signed Cartesian differences give `grad(m)`. When the largest
signed coordinate exceeds the next by at least `ε`, only that coordinate contributes: `m = max|c|`
and its gradient is exactly the outward face normal. At edges and corners the direction joins
continuously and symmetrically. This narrowly rounds the **field**, not the block geometry. Exactly
normal gravity on both sides of a perfectly sharp edge would require a direction discontinuity.
The force is continuous; its spatial derivative can change across the compact rounding boundaries.

Let `r = length(position)`, `h = m-a`. A quintic smoothstep `s(h)` grows from zero to one between
configured start/end heights, with zero first and second derivatives at both endpoints. Define:

```text
q = m + (r-m)*s
Phi(q) = g*(q²-3a²)/(2a)  when q < a
         -g*a²/q          otherwise
acceleration = -Phi'(q) * [(1-s+(r-m)*s')*grad(m) + s*grad(r)]
```

Below the transition the effective radius is the rounded cube radius. Above it, `q = r` and the field
is exactly central with `mu = g*a²`. The quadratic interior keeps the potential finite and centre force
zero. The derivative of the height blend is part of the force: interpolating force directions alone
would not implement this potential. There are no velocity corrections or thrust assistance.

Conservative does not imply constant force magnitude through the transition or conserved angular
momentum near the cube. The field is noncentral there. Only the far field has full spherical symmetry.

## Current diagnostic parameters

The flight/orbit adapter uses `a = 6000 m`, `g = 9.81 m/s²`, `ε = 64 m`, transition start height
`512 m` and end height `6000 m`. These are experimental values, not saved production defaults.
Centre remains `(0,-5936,0)` in surface-world coordinates, so the top reference face is at Y=64.
Outside edge bands, gravity is exactly normal below the transition and has magnitude
`g*(a/(a+h))²`. At x/z=0, Y=6064 still has gravity 2.4525 m/s².

`sample` reports sharp reference cube-shell height `max(abs(x),abs(y),abs(z))-a`, not radial altitude
or shortest distance to the solid near edges/corners. The potential uses rounded shell height for its
transition; these differ by at most `ε/2`. Orbit coordinates are translated back to the same global
position before evaluating either field or height, so the existing handoff does not change gravity.

Only loaded Sable constructions in the two diagnostic dimensions receive this force. Native Sable
remains the sole integrator; native base gravity/drag/pressure stay disabled there. Players and loose
entities still use vanilla gravity. Existing saves retain their dimension IDs and native ship data;
ships experience the revised experimental force on their next physics step. No save-schema migration
or new persistent planet contract is introduced.

## Evidence and next geometry work

Core tests exercise all six off-centre face normals, a vertical ballistic launch, exact far-field
acceleration, edge/corner/height continuity, sign/permutation symmetry, the potential gradient, finite
centre and conservative trajectory convergence across the complete height blend. The native flight
GameTest measures actual acceleration at one/four physics substeps on unequal-mass/rotated ships,
including an off-centre body with no lateral gravitational acceleration, and checks field equivalence
across the diagnostic frame translation. These are bounded experiments, not long-term orbital accuracy
or a manually observed flight of the revised field.

Finite block terrain, face-to-face neighbour/chunk ownership, edge traversal with a player, face frame
rotations, landing on another face and distant planet rendering are still unimplemented. The existing
terrain dimension represents one patch of the top face and does not wrap. The accepted cube shape
settles the intended surface geometry; it does not settle those implementation contracts. Automatic
height transfer and the replacement consumer ship fixture are separate upcoming steps.
