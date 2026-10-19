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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPassengers
import org.joml.Vector3d
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.floor

/** Real engines capture a numerically predicted trajectory, then disengage for a free coast. */
internal class FlatOrbitalMission(
    pilot: ServerPlayer?,
    override var body: ServerSubLevel,
    private val site: LaunchSites.Site,
    private val transfer: SableShipTransfer,
    land: Boolean,
) : FlightMission {
    enum class Phase { PLANNING, ASCENT, CLIMB, INSERTION, COAST, RETURN, DESCENT, APPROACH, TOUCHDOWN, LANDED, FADE, TRANSFER }

    var phase = Phase.PLANNING
        private set
    override val stage: String get() = phase.name
    private val planningModel =
        org.cbi.orbitalis.minecraft.flight.CelestialFlight
            .sample(body.level)
            .dynamics
    private val geometry = planningModel.profile.geometry
    private val orbitAltitude = maxOf(geometry.earthHalfSize, geometry.earthEnvelope.ascentDistance + 200.0)
    private val launchBasis = planningModel.profile.earthSurfaceFrame(planningModel.epochTick).copy(angularVelocity = Vector3.ZERO)
    private var landingFrame = land
    private val field = SableFlightGravity.flatField(body.level)
    private val planning: CompletableFuture<BallisticOrbit>

    private data class ReleaseCheck(
        val model: org.cbi.orbitalis.core.orbit.CelestialDynamics,
        val state: CartesianState,
        val prediction: CoastPrediction.Result,
    )

    private var releaseCheck: CompletableFuture<ReleaseCheck>? = null
    internal val waitingForCoastCheck: Boolean get() = releaseCheck?.isDone == false
    private val clearance: Double
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
    private val server = body.level.server
    private val returningAtStart = land
    private val aboveHome = Vector3(site.position.x, FlatPlanetSpace.SEA_LEVEL + orbitAltitude, site.position.z)

    init {
        require(site.surface == Level.OVERWORLD && site.ship == body.uniqueId && !transfer.busy)
        require(abs(site.position.x) <= geometry.earthHalfSize - 40.0 && abs(site.position.z) <= geometry.earthHalfSize - 40.0) {
            "Launch pad must leave a 40-metre margin inside the surface footprint"
        }
        if (!land) {
            require(body.level.dimension() == Level.OVERWORLD) { "Launch from ordinary Overworld terrain" }
            ShipTransferPassengers.preflight(body, AutopilotPassengers.select(body, pilot))
            val top =
                body.level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    floor(site.position.x).toInt(),
                    floor(site.position.z).toInt(),
                )
            require(
                abs(body.boundingBox().minY() - top) < 1.0 && FlightGuidanceFrames.earthState(body).velocity.length() < 0.3,
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
        clearance = maxOf(32.0, radius + 8.0)
        val model = field
        planning =
            CompletableFuture.supplyAsync {
                BallisticOrbit.firstRevolution(
                    model,
                    planningModel,
                    orbitAltitude,
                    clearance,
                    direction = -1.0,
                )
            }
        SpaceTravelReservations
            .reserve(body.uniqueId, this)
    }

    private fun state() =
        if (landingFrame || phase == Phase.ASCENT) {
            FlightGuidanceFrames.earthState(body)
        } else {
            launchBasis.fromParent(FlightGuidanceFrames.rootState(body))
        }

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
        val engine =
            engines
                ?: AutopilotSession(body, AutopilotSession.position(body), site.attitude, true, earthAttitude = true).also { engines = it }
        engine.update(
            if (landingFrame || phase == Phase.ASCENT || phase == Phase.CLIMB) {
                FlightGuidanceFrames.rootAcceleration(body, acceleration)
            } else {
                launchBasis.orientation.apply(acceleration)
            },
        )
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
        speedTolerance: Double = 0.25,
    ) = (state.position - target).length() < tolerance && state.velocity.length() < speedTolerance

    override fun requestLanding() {
        require(phase == Phase.COAST) { "Wait for free coast before requesting return" }
        braking = false
        landingFrame = true
        enter(Phase.RETURN)
    }

    override fun tick(pilot: ServerPlayer?): Boolean {
        require(!closed) { "Autopilot is closed" }
        // A completed orbit insertion may coast until the pilot explicitly requests landing.
        check(
            phase == Phase.COAST || ++ticks < (16000 * kotlin.math.sqrt(org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SCALE)).toInt(),
        ) {
            "Finite-body mission timed out: $phase"
        }
        check(transfer.error == null) { "Transfer stopped: ${transfer.error}" }
        if (phase == Phase.TRANSFER) {
            if (transfer.busy) {
                ready = 0
                return false
            }
            val target = if (afterTransfer == Phase.CLIMB) FlatPlanetSpace.SPACE else Level.OVERWORLD
            val level = checkNotNull(server.getLevel(target))
            val restored = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(site.ship) as? ServerSubLevel
            require(restored != null) { "Destination ship missing" }
            if (++ready < 5 || !SableShipSnapshot.entitiesReady(restored)) return false
            body = restored
            preload.close()
            enter(afterTransfer)
            return false
        }
        require(!body.isRemoved) { "Ship unavailable" }
        if (SubLevelPhysicsSystem.require(body.level).paused) return false
        body.level.resetEmptyTime()
        val s = state()
        require(
            s.velocity.length() < 220.0 * kotlin.math.sqrt(geometry.earthHalfSize / 1000.0) &&
                s.position.length() < maxOf(10000.0, geometry.earthHalfSize * 4.0),
        ) { "Outside tested mission envelope: position=${s.position}; radius=${s.position.length()}; speed=${s.velocity.length()}" }
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
                FlightGuidanceFrames.rootState(body).let { preload.approach(body.level, it.position, it.velocity) }
                drive(OrbitalGuidance.point(s, aboveHome, 60.0))
                if (FlightGuidanceFrames.earthState(body).position.y >=
                    FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.ascentDistance
                ) {
                    prepare(Phase.CLIMB)
                }
            }

            Phase.FADE -> {
                require(ticks < 200) { "Engines failed to fade" }
                if (!transfer.busy && AutopilotSession.enginesFaded(body)) {
                    transfer.begin(
                        AutopilotPassengers.select(body, pilot),
                        body,
                        if (afterTransfer ==
                            Phase.CLIMB
                        ) {
                            FlatPlanetSpace.SPACE
                        } else {
                            Level.OVERWORLD
                        },
                    )
                    ready = 0
                    enter(Phase.TRANSFER)
                }
            }

            Phase.CLIMB -> {
                val earth = FlightGuidanceFrames.earthState(body)
                drive(OrbitalGuidance.point(earth, orbit.initial.position, 60.0))
                if (near(earth, orbit.initial.position, 1.0)) enter(Phase.INSERTION)
            }

            Phase.INSERTION -> {
                // Reparameterize in the planned moving Earth chart, then apply the live chart's
                // transport velocity and acceleration. Clearance follows the same body geometry
                // throughout the ramp; at rate=1 only the small external tides differ by epoch.
                val time = ticks * 0.05
                val u = (time / 40.0).coerceIn(0.0, 1.0)
                val rate = u * u * u * (10.0 + u * (-15.0 + 6.0 * u))
                val derivative = if (time < 40.0) 30.0 * u * u * (1.0 - u) * (1.0 - u) / 40.0 else 0.0
                val elapsed = 40.0 * (2.5 * u * u * u * u - 3.0 * u * u * u * u * u + u * u * u * u * u * u) + maxOf(0.0, time - 40.0)
                val plannedRoot = launchBasis.toParent(orbit.stateAt(elapsed))
                val plannedEarth = planningModel.profile.earthSurfaceFrame(planningModel.epochTick, elapsed)
                val local = plannedEarth.fromParent(plannedRoot)
                val localAcceleration =
                    plannedEarth.fromParentAcceleration(plannedRoot, planningModel.acceleration(plannedRoot.position, elapsed))
                val slowed = CartesianState(local.position, local.velocity * rate)
                val sample = CelestialFlight.sample(body.level)
                val liveEarth = sample.frames.profile.earthSurfaceFrame(sample.frames.tick)
                val rootReference = liveEarth.toParent(slowed)
                val reference = launchBasis.fromParent(rootReference)
                val rootAcceleration =
                    liveEarth.toParentAcceleration(slowed, localAcceleration * (rate * rate) + local.velocity * derivative)
                var feedback = (reference.position - s.position) * 0.15 + (reference.velocity - s.velocity) * 0.8
                if (feedback.length() > 3.0) feedback = feedback.normalized() * 3.0
                drive(launchBasis.orientation.inverse(rootAcceleration) + feedback)
                val matched =
                    time >= 40.0 && (reference.position - s.position).length() < 0.75 && (reference.velocity - s.velocity).length() < 0.15
                settled = if (matched) settled + 1 else 0
                if (settled >= 60 && releaseCheck == null) {
                    val root = FlightGuidanceFrames.rootState(body)
                    releaseCheck =
                        CompletableFuture.supplyAsync {
                            ReleaseCheck(sample.dynamics, root, CoastPrediction.orbit(field, root, coastLimits(), sample.dynamics))
                        }
                }
                val release = releaseCheck
                if (release != null && release.isDone) {
                    val checked = release.join()
                    require(checked.prediction.end in setOf(CoastPrediction.End.ORBIT, CoastPrediction.End.REVOLUTION)) {
                        "Insertion does not yield a safe unpowered revolution: ${checked.prediction.end}"
                    }
                    val age = (sample.frames.tick - checked.model.epochTick) * 0.05
                    if (age in 0.0..2.0) {
                        val expected =
                            if (age == 0.0) {
                                checked.state
                            } else {
                                CoastPrediction
                                    .predict(
                                        field,
                                        checked.state,
                                        coastLimits().copy(horizon = age),
                                        checked.model,
                                    ).terminal.state
                            }
                        val current = FlightGuidanceFrames.rootState(body)
                        if ((current.position - expected.position).length() < 1.0 &&
                            (current.velocity - expected.velocity).length() < 0.25
                        ) {
                            cut()
                            enter(Phase.COAST)
                        } else {
                            releaseCheck = null
                        }
                    } else {
                        releaseCheck = null
                    }
                }
            }

            Phase.COAST -> {
                // The rotating field performs work; constant inertial energy is not an invariant.
            }

            Phase.RETURN -> {
                if (!braking && s.position.y > FlatPlanetSpace.SEA_LEVEL + orbitAltitude * 0.85 &&
                    abs(s.position.x) < geometry.earthHalfSize * 0.35 &&
                    abs(s.position.z) < geometry.earthHalfSize * 0.1
                ) {
                    braking = true
                }
                if (braking) {
                    drive(OrbitalGuidance.point(s, aboveHome, 60.0))
                    // This is a high-altitude approach corridor, not the final landing target.
                    if (near(s, aboveHome, 5.0, 1.0)) enter(Phase.DESCENT)
                }
            }

            Phase.DESCENT -> {
                FlightGuidanceFrames.rootState(body).let { preload.approach(body.level, it.position, it.velocity) }
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

    private fun coastLimits() = CoastPrediction.Limits(detectEntry = false, range = geometry.earthHalfSize * 4.0, hullRadius = clearance)

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
                "Engines off; numerical free orbit. Use /orbitalis flight autopilot land to return."
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
            releaseCheck?.cancel(false)
            preload.close()
        }
    }
}
