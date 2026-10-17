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

package org.cbi.orbitalis.consumer.flight

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.joml.Vector3d
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.floor

/** Real engines capture a numerically predicted trajectory, then disengage for a free coast. */
internal class FlatOrbitalMission(
    pilot: ServerPlayer,
    var body: ServerSubLevel,
    private val site: LaunchSites.Site,
    private val transfer: SableShipTransfer,
    land: Boolean,
) : FlightMission {
    enum class Phase { PLANNING, ASCENT, CLIMB, INSERTION, COAST, RETURN, DESCENT, APPROACH, TOUCHDOWN, LANDED, FADE, TRANSFER }

    var phase = Phase.PLANNING
        private set
    override val stage: String get() = phase.name
    private val field = SableFlightGravity.flatField(body.level)
    private val planning: CompletableFuture<BallisticOrbit>
    private val preload = ShipDestinationPreload()
    private lateinit var orbit: BallisticOrbit
    private var engines: AutopilotSession? = null
    private var ticks = 0
    private var settled = 0
    private var ready = 0
    private var afterTransfer = Phase.CLIMB
    private var coastEnergy = 0.0
    private var braking = false
    private var closed = false
    private val returningAtStart = land
    private val aboveHome = Vector3(site.position.x, FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ORBIT_HEIGHT, site.position.z)

    init {
        require(site.surface == Level.OVERWORLD && site.ship == body.uniqueId && !transfer.busy)
        require(abs(site.position.x) <= FlatPlanetSpace.LAUNCH_LIMIT && abs(site.position.z) <= FlatPlanetSpace.LAUNCH_LIMIT) {
            "Launch pad must leave a 40-metre margin inside the surface footprint"
        }
        require(pilot.level() === body.level)
        if (!land) {
            require(body.level.dimension() == Level.OVERWORLD) { "Launch from ordinary Overworld terrain" }
            SableShipSnapshot.preflight(body, pilot)
            val top =
                body.level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    floor(site.position.x).toInt(),
                    floor(site.position.z).toInt(),
                )
            require(
                abs(body.boundingBox().minY() - top) < 1.0 && state().velocity.length() < 0.3,
            ) { "Let the ship settle on a flat clear launch pad" }
        } else {
            require(body.level.dimension() == FlatPlanetSpace.SPACE) { "Return from the finite-body space frame" }
        }
        val com = AutopilotSession.position(body)
        val bounds = body.boundingBox()
        val radius =
            Vector3(
                maxOf(abs(bounds.minX() - com.x), abs(bounds.maxX() - com.x)),
                maxOf(abs(bounds.minY() - com.y), abs(bounds.maxY() - com.y)),
                maxOf(abs(bounds.minZ() - com.z), abs(bounds.maxZ() - com.z)),
            ).length()
        val clearance = maxOf(32.0, radius + 8.0)
        val model = field
        planning = CompletableFuture.supplyAsync { BallisticOrbit.plan(model, FlatPlanetSpace.ORBIT_HEIGHT, clearance) }
        SpaceTravelReservations
            .reserve(body.uniqueId, this)
    }

    private fun state(): CartesianState =
        CartesianState(
            FlightSpaceCoordinates.globalPosition(body.level, AutopilotSession.position(body)),
            AutopilotSession.vector(SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body).getLinearVelocity(Vector3d())),
        )

    private fun enter(next: Phase) {
        phase = next
        ticks = 0
        settled = 0
    }

    private fun cut() {
        engines?.close()
        engines = null
    }

    private fun drive(acceleration: Vector3) {
        val engine = engines ?: AutopilotSession(body, AutopilotSession.position(body), site.attitude, true).also { engines = it }
        engine.update(acceleration)
    }

    private fun prepare(next: Phase) {
        cut()
        afterTransfer = next
        enter(Phase.FADE)
    }

    private fun near(
        state: CartesianState,
        target: Vector3,
        tolerance: Double = 0.5,
    ) = (state.position - target).length() < tolerance && state.velocity.length() < 0.25

    override fun requestLanding() {
        require(phase == Phase.COAST) { "Wait for free coast before requesting return" }
        braking = false
        enter(Phase.RETURN)
    }

    override fun tick(pilot: ServerPlayer?): Boolean {
        require(!closed && pilot != null && pilot.isAlive) { "Pilot unavailable" }
        check(++ticks < 16000) { "Finite-body mission timed out: $phase" }
        check(transfer.error == null) { "Transfer stopped: ${transfer.error}" }
        if (phase == Phase.TRANSFER) {
            if (transfer.busy) {
                ready = 0
                return false
            }
            val target = if (afterTransfer == Phase.CLIMB) FlatPlanetSpace.SPACE else Level.OVERWORLD
            val level = pilot.server.getLevel(target)
            val restored = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(site.ship) as? ServerSubLevel
            require(restored != null && pilot.level() === level) { "Destination ship/pilot missing" }
            if (++ready < 5 || Sable.HELPER.getTrackingSubLevel(pilot) !== restored) return false
            body = restored
            preload.close()
            enter(afterTransfer)
            return false
        }
        require(!body.isRemoved && pilot.level() === body.level && Sable.HELPER.getTrackingSubLevel(pilot) === body) {
            "Stay on the mission ship with creative flight disabled"
        }
        require(!SubLevelPhysicsSystem.require(body.level).paused) { "Native physics paused outside handoff" }
        body.level.resetEmptyTime()
        val s = state()
        require(s.velocity.length() < 220.0 && s.position.length() < 10000.0) { "Outside tested mission envelope" }
        when (phase) {
            Phase.PLANNING -> {
                if (!planning.isDone) return false
                orbit = planning.join()
                enter(if (returningAtStart) Phase.COAST else Phase.ASCENT)
                if (returningAtStart) {
                    coastEnergy = field.specificEnergy(s)
                    requestLanding()
                }
            }

            Phase.ASCENT -> {
                if (s.position.y >= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.PRELOAD_HEIGHT) {
                    preload.update(
                        checkNotNull(pilot.server.getLevel(FlatPlanetSpace.SPACE)),
                        FlightSpaceCoordinates.localPosition(FlatPlanetSpace.SPACE, s.position),
                    )
                }
                drive(OrbitalGuidance.point(s, aboveHome, 60.0))
                if (s.position.y >= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT) prepare(Phase.CLIMB)
            }

            Phase.FADE -> {
                require(ticks < 200) { "Engines failed to fade" }
                if (AutopilotSession.enginesFaded(body)) {
                    pilot.closeContainer()
                    transfer.begin(pilot, body, if (afterTransfer == Phase.CLIMB) FlatPlanetSpace.SPACE else Level.OVERWORLD)
                    ready = 0
                    enter(Phase.TRANSFER)
                }
            }

            Phase.CLIMB -> {
                drive(OrbitalGuidance.point(s, orbit.initial.position, 60.0))
                if (near(s, orbit.initial.position, 1.0)) enter(Phase.INSERTION)
            }

            Phase.INSERTION -> {
                // Ramp the time parameter of the same geometric orbit. At rate=1 the reference
                // acceleration is gravity itself, so guidance can relinquish thrust entirely.
                val time = ticks * 0.05
                val u = (time / 40.0).coerceIn(0.0, 1.0)
                val rate = u * u * u * (10.0 + u * (-15.0 + 6.0 * u))
                val derivative = if (time < 40.0) 30.0 * u * u * (1.0 - u) * (1.0 - u) / 40.0 else 0.0
                val elapsed = 40.0 * (2.5 * u * u * u * u - 3.0 * u * u * u * u * u + u * u * u * u * u * u) + maxOf(0.0, time - 40.0)
                val reference = orbit.stateAt(elapsed)
                var feedback = (reference.position - s.position) * 0.15 + (reference.velocity * rate - s.velocity) * 0.8
                if (feedback.length() > 3.0) feedback = feedback.normalized() * 3.0
                drive(field.acceleration(reference.position) * (rate * rate) + reference.velocity * derivative + feedback)
                val matched =
                    time >= 40.0 && (reference.position - s.position).length() < 0.75 && (reference.velocity - s.velocity).length() < 0.15
                settled = if (matched) settled + 1 else 0
                if (settled >= 60) {
                    cut()
                    coastEnergy = field.specificEnergy(s)
                    enter(Phase.COAST)
                }
            }

            Phase.COAST -> {
                require(coastEnergy < 0.0 && abs((field.specificEnergy(s) - coastEnergy) / coastEnergy) < 0.03) {
                    "Free coast energy drifted; manual control required"
                }
            }

            Phase.RETURN -> {
                if (!braking && s.position.y > 850.0 && abs(s.position.x) < 350.0 && abs(s.position.z) < 100.0) braking = true
                if (braking) {
                    drive(OrbitalGuidance.point(s, aboveHome, 60.0))
                    if (near(s, aboveHome, 1.0)) enter(Phase.DESCENT)
                }
            }

            Phase.DESCENT -> {
                preload.update(pilot.server.overworld(), s.position)
                drive(OrbitalGuidance.descent(s, site.position))
                if (s.position.y <= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT) prepare(Phase.APPROACH)
            }

            Phase.APPROACH -> {
                drive(OrbitalGuidance.descent(s, site.position))
                // Flare above the remembered support point, then let native gravity/contact
                // finish the last metre. Holding an exact COM against contact springs can hover.
                if (near(s, site.position, 1.0)) {
                    cut()
                    enter(Phase.TOUCHDOWN)
                }
            }

            Phase.TOUCHDOWN -> {
                require((s.position - site.position).length() < 1.0) { "Terrain did not support touchdown" }
                if (ticks >= 60 && AutopilotSession.enginesFaded(body) && s.velocity.length() < 0.2) {
                    enter(Phase.LANDED)
                    return true
                }
                require(ticks < 200) { "Touchdown failed to settle" }
            }

            Phase.LANDED -> {
                return true
            }

            Phase.TRANSFER -> {
                error("Unexpected phase")
            }
        }
        return false
    }

    override fun status(): String {
        if (body.isRemoved) return "Flat mission $phase; journaled handoff"
        val state = state()
        return "Flat mission $phase; height=%.1f m above sea level; speed=%.2f m/s; %s".format(
            java.util.Locale.ROOT,
            state.position.y - FlatPlanetSpace.SEA_LEVEL,
            state.velocity.length(),
            if (phase ==
                Phase.COAST
            ) {
                "Engines off; numerical free orbit."
            } else {
                "Native engines only."
            },
        )
    }

    override fun close() {
        if (!closed) {
            closed = true
            cut()
            SpaceTravelReservations
                .release(body.uniqueId, this)
            planning.cancel(false)
            preload.close()
        }
    }
}
