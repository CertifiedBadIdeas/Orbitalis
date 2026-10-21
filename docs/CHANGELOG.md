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

# Changelog

## 0.2.0 — In development

- Support independent standard liquid Propulsion thrusters in the existing flight controller, with
  native fractional throttle, finite fuel preflight and automatic cutoff on fuel depletion. Survival
  pilots can use the same operator-gated commands. Solid, vector and liquid multiblock layouts retain
  their existing restrictions.
- Add a guarded flight-consumer expedition ship fixture with a glazed cabin, boarding stairs, four
  landing legs, native manual radio controls and 40 liquid thrusters. Four coal-powered native pump
  banks supply a finite 192-bucket turpentine reserve through real pipes and native gearing.
- Align orbital mission main thrust against gravity and budget insertion acceleration against the
  supported engine authority; share native attitude conversion between preflight and active guidance.
- Count occupied ship chunks separately from native read-created air caches. Preserve meaningful
  metadata and the existing four-chunk transfer journal limit while omitting proven-empty cache
  chunks from transfer snapshots.

- Preserve walking, landing/hurt and nausea camera transforms in the compressed celestial projection;
  stepping onto terrain with view bobbing enabled no longer rejects a valid perspective matrix.
- Preserve physical day/night lighting in the cosmic Overworld at high altitude, including native
  terrain and optional Voxy, instead of blending towards constant daylight above the atmosphere.

- Replace finite Earth's vanilla cloud sheets with seeded procedural block volumes of varying
  thickness. Ground, orbital and map views share the field, wind and Earth weather, including
  directional face lighting, transmitted edges and atmospheric attenuation. Both Earth sky modes
  use these clouds; the native cloud quality/off option remains available.
- Keep the full cloud distance visible from ground height, independent of the native chunk render
  distance, while preserving depth occlusion by nearby terrain and optional Voxy.
- Build cloud sector meshes on two bounded workers and upload incrementally into a bounded GPU
  cache; choose a consistent detail level per view and limit surface cloud distance.

- Allow up to 10 GiB of Java heap for both development clients, including the flight consumer.

- Bound optional Voxy WorldGenV2 work and remaining-work statistics to each planet's configured
  finite terrain footprint, including Moon, Mercury and Venus; finished surfaces no longer schedule
  hundreds of thousands of empty exterior chunks.
- Share finite-atmosphere solar attenuation across Earth surface, clouds and atmospheric glow;
  use bounded Rayleigh/Mie single scattering for the limb and linear-light surface shading.
- Add rough Fresnel water highlights from explicit native water coverage in the surface atlas;
  dry blue blocks stay matte. Read older atlas records with unknown/dry material until refreshed.
- Illuminate cloud faces and transmitted edges from the physical Sun, with warmer grazing light.
  Geometry, cloud altitude and existing native/celestial depth composition are preserved.

- Mercury and Venus with scaled circular orbits, gravity, spin, separate finite surface dimensions
  and terrain-derived LOD in space and on the orbital map. Map views can focus either planet.
- Local heliocentric orbital sectors keep native physics coordinates bounded during interplanetary
  travel. Explicit journal routes preserve ships, cargo and mounted passengers between sectors and
  surfaces; old Earth/Moon routes remain readable.
- Synchronize derived Mercury/Venus terrain seeds through the updated client ephemeris protocol.
- Use a shared celestial depth domain with an adaptive sky near plane and separate native-depth
  projection for deferred clouds, avoiding clipped nearby surfaces at astronomical distances.
- Keep free-player horizons continuous near inner planets and across sector handoffs; keep native
  culling depth local so astronomical distances cannot stall Minecraft's frustum update.
- Frame map close-ups above each body's current surface and identify Earth alongside its Moon;
  preserve the visible Sun when near-surface sky projection has an infinite far plane.
- Illuminate exposed players and hands below a planet's native chunk floor using physical solar
  visibility, preserving celestial shadows, local block lights and Sable cabin occlusion.
- Add restrained, smoothly animated solar glare rays and keep distant night-side terrain and rock
  readable with material-specific fill light; direct sunlight and celestial occlusion remain separate.
- Dynamic external skylight accounts for overlapping celestial shadows and penumbrae. Native Sable
  cabin skylight and local block lights are preserved; distant candidates are culled before testing
  the finite solar source, and client samples are cached per tick.

- Procedural Sun in orbital/lunar skies and the orbital map, using the existing celestial ephemeris.
  A ray-intersected cubic photosphere has animated cellular granulation, paired sunspot groups with
  umbrae/penumbrae, wavelength-dependent limb darkening, a volumetric white streamer corona
  and small red prominence loops above active regions. Surface cells evolve, coronal flows travel
  outwards and bright plasma knots move along prominence strands, on a shared simulation clock.
  Local prominence eruptions rise and fade independently, while the star rotates slowly.
  Photographic exposure preserves map detail and a white-hot distant cube with soft coronal glare.
  Solar half-edge scales with the system to retain a face-on width near 0.53 degrees.
  Earth/Moon LOD surfaces, overview clouds and atmosphere now follow the physical Sun at draw time.
- `/orbitalis sky` reports the world-owned Overworld sky mode; operators select `vanilla` (default)
  or `cosmic`. Cosmic mode renders the system Sun/Moon and derives native daylight from Earth spin,
  independently of sleep and `/time`. The setting is saved and synchronized; space has no mode switch.
- Lunar eclipses in the cosmic Overworld sky, space and map: a finite cubic source casts
  Earth's rotating rectangular shadow onto the Moon, with penumbra and a muted red umbra.
- Forward time acceleration: `/orbitalis time add <seconds>`, `set <solar time>` and `stop` process
  real native ticks, with no backward clock assignments. Initially requires no loaded flight ships.
- Bound client ephemeris smoothing during accelerated time so the sky catches up promptly.
- Prevent the physical Sun from occluding Overworld terrain through compressed sky depth writes.
- Preserve solar coverage and premultiplied coronal light in the transparent Overworld sky target;
  composite faint emission without vanilla's alpha-test cutoff, avoiding a visible ring in the glare.
- Share the offscreen celestial depth pass with the cosmic Overworld sky, preserving visible lunar
  relief and preventing the underside from painting over the illuminated surface.

## 0.1.0 — Alpha

First Alpha. Version frozen on 2026-10-20; publication is a separate step.

### Worlds and physics

- Finite Overworld terrain with a per-world Earth width of 1,000, 2,000, 5,000, 10,000 or 20,000 blocks.
  Native Minecraft block, fluid and neighbour logic is retained. The terrain footprint is separate
  from the movement border, allowing flight around the sides and underneath the world.
- A scaled Sun–Earth–Moon catalogue with hierarchical moving frames, physical Earth rotation,
  a tidally locked Moon and a simulation clock independent of vanilla daylight.
- Finite-body gravity with a near-surface compatibility layer and weaker distant attraction for
  players and Sable constructions. Atmospheric effects fade into vacuum; creative flight retains momentum.
- A finite lunar dimension with seeded craters and surface, side and underside approaches.

### Travel and constructions

- Automatic two-way surface/space travel for individual players and supported Sable constructions,
  including unmanned ships, seated passengers and cargo.
- Journaled ship reconstruction and interrupted-transfer recovery, preserving native motion,
  serialized blocks, supported entities, glue and cargo. Occupied plot addresses can be relocated.
- Sparse orbital space and background ballistic evolution of eligible unloaded independent stations.
- Optional Propulsion engine control in the main mod: hold, target altitude, orbital insertion,
  return to the launch pad, maneuver execution, ship selection and saved launch bookmarks.
  Active controllers retain their ships across handoffs and release their loading tickets on exit.

### Navigation and presentation

- An orbital map (M) with Earth, Moon and system views, rotation, zoom and camera-relative panning.
  Select loaded/unloaded constructions and inspect brief telemetry and predicted coast trajectories.
- Personal navigation markers and timed maneuver plans with prograde, normal and radial delta-v.
- External ship camera (F6), local-gravity navball with velocity markers and switchable nose axes (F7).
- Shared planetary relief in space and on the map, sampled from saved terrain at a 16-block Earth
  render grid, with sector updates and bounded background disk reads. Unknown regions use a fallback.
- Layered cliffs, shaded undersides, drifting native-texture clouds and a finite atmospheric halo.
- A shared high-altitude sky and held-frame world transitions that fade into the ready destination.

### Compatibility and limits

- Minecraft 1.21.1 and NeoForge 21.1.252+ within the 21.1 series; Kotlin runtime included.
- Optional Sable 2.0.6 construction integration and Propulsion 1.1.5 engine adapter. External mods
  and their dependencies are installed separately and are not bundled.
- Optional client-only Voxy 0.2.16-beta / WorldGenV2 2.4.2 compatibility for finite-terrain searches
  and shutdown. The supported preview stack uses Roxy and has separate installation requirements.
- Ship transfer remains bounded to four native plot chunks with size, passenger and connected-body
  restrictions. Autopilot targets tested creative-engine layouts and Earth launch/return routes;
  it does not provide survival fuel management, lunar landing guidance or obstacle avoidance.
- World transitions may pause. Planetary lighting is illustrative; physical solar lighting is pending.
  Overview terrain does not represent unexplored chunks exactly.
