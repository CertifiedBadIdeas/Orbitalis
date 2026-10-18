/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cbi.orbitalis.minecraft.flight

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.PlanetAtmosphere
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.moon.MoonWorld

/** Player acceleration uses native world coordinates, including while tracking a Sable deck. */
object PlayerGravity {
    var serverAcceleration: (ServerLevel) -> Double = { 11.0 }
    var clientAcceleration = 11.0
    private val fields = java.util.concurrent.ConcurrentHashMap<Double, SurfaceGravityLayer>()

    fun field(g: Double): SurfaceGravityLayer =
        fields.computeIfAbsent(g) {
            SurfaceGravityLayer(
                FlatPlanetDefinition.gravity(g).body,
                maxOf(32.0, g),
                fullHeight = FlatPlanetSpace.ASCENT_HEIGHT,
            )
        }

    @JvmStatic
    fun active(entity: LivingEntity): Boolean =
        entity is Player &&
            FlatPlanetSpace.contains(entity.level()) && CelestialFlight.available(entity.level()) && entity.isControlledByLocalInstance &&
            !entity.isPassenger && !entity.isInWater && !entity.isInLava && !entity.onClimbable() &&
            !entity.isNoGravity && !entity.hasEffect(MobEffects.LEVITATION)

    private fun position(entity: LivingEntity): Vector3 =
        FlightSpaceCoordinates.globalPosition(
            entity.level(),
            Vector3(entity.x, entity.y, entity.z),
        )

    @JvmStatic
    fun acceleration(entity: LivingEntity): Vec3 {
        val field = field((entity.level() as? ServerLevel)?.let(serverAcceleration) ?: clientAcceleration)
        val sample = CelestialFlight.sample(entity.level())
        val local = Vector3(entity.x, entity.y, entity.z)
        val velocity = Vector3(entity.deltaMovement.x, entity.deltaMovement.y, entity.deltaMovement.z) * 20.0
        // The same player compatibility field on both sides of a handoff. Applying a lunar
        // correction only inside the native Moon creates an artificial force step at entry.
        val dynamics =
            org.cbi.orbitalis.core.orbit.CelestialDynamics(
                sample.frames.profile,
                sample.frames.tick,
                field.nativeAcceleration,
                field.nativeAcceleration * sample.frames.profile.geometry.moonGravityRatio,
            )
        val acceleration =
            if (entity.level().dimension() == MoonWorld.KEY) {
                dynamics.lunarAcceleration(CartesianState(local, velocity))
            } else if (entity.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                dynamics.earthAcceleration(CartesianState(local, velocity))
            } else {
                dynamics.acceleration(position(entity))
            }
        // m/s² -> blocks/tick². Native travel owns collisions and displacement.
        return Vec3(acceleration.x / 400.0, acceleration.y / 400.0, acceleration.z / 400.0)
    }

    @JvmStatic
    fun vacuum(entity: LivingEntity): Double =
        if (entity.level().dimension() ==
            MoonWorld.KEY
        ) {
            1.0
        } else {
            PlanetAtmosphere.vacuum(
                CelestialFlight.sample(entity.level()).nativePoint(position(entity), net.minecraft.world.level.Level.OVERWORLD),
                CelestialFlight
                    .sample(entity.level())
                    .frames.profile.geometry,
            )
        }

    @JvmStatic
    fun creative(entity: LivingEntity): Boolean = entity is Player && entity.abilities.flying
}
