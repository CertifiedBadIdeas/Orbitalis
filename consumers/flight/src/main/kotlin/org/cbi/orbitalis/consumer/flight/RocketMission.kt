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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/** Disposable consumer guidance: every manoeuvre uses native main engines or native RCS. */
internal class RocketMission(
    pilot: ServerPlayer,
    var body: ServerSubLevel,
    private val site: LaunchSites.Site,
    private val transfer: SableShipTransfer,
    private val returningAtStart: Boolean,
) : FlightMission {
    enum class Phase {
        PLANNING,
        LIFTOFF,
        ASCENT,
        CAPTURE,
        ALIGN,
        FREE,
        COAST,
        RETURN,
        FLIP,
        BRAKE,
        APPROACH,
        TOUCHDOWN,
        LANDED,
        FADE,
        TRANSFER,
    }

    var phase = Phase.PLANNING
        private set
    override val stage get() = phase.name
    private val field = SableFlightGravity.flatField(body.level)
    private val preload = ShipDestinationPreload()
    private val planning = CompletableFuture.supplyAsync { BallisticOrbit.plan(field, FlatPlanetSpace.ORBIT_HEIGHT, 32.0) }
    private var connectionPlanning: CompletableFuture<*>? = null
    private lateinit var course: RocketCourse
    private lateinit var ascent: RocketAscent
    private var returning: RocketReturn.Path? = null
    private var drive: RocketDrive? = null
    private var ticks = 0
    private var settled = 0
    private var ready = 0
    private var clock = 0.0
    private var coastEnergy = 0.0
    private var angle = 0.0
    var coastAngle = 0.0
        private set
    private var afterTransfer = Phase.ASCENT
    private var closed = false

    init {
        require(site.surface == Level.OVERWORLD && site.ship == body.uniqueId && !transfer.busy)
        require(
            abs(site.position.x) <= 960.0 && abs(site.position.z) <= 960.0,
        ) { "Leave a 40-metre launch-pad margin inside the finite surface" }
        require(pilot.level() === body.level)
        if (!returningAtStart) {
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
            ) { "Let the rocket settle on a flat clear pad" }
            RocketDrive(body, true).close()
        } else {
            require(body.level.dimension() == FlatPlanetSpace.SPACE) { "Return from the finite-body space frame" }
        }
        SpaceTravelReservations.reserve(site.ship, this)
    }

    private fun state() =
        CartesianState(
            FlightSpaceCoordinates.globalPosition(body.level, AutopilotSession.position(body)),
            AutopilotSession.vector(SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body).getLinearVelocity(Vector3d())),
        )

    private fun engine() = drive ?: RocketDrive(body).also { drive = it }

    private fun cut() {
        drive?.close()
        drive = null
    }

    private fun enter(next: Phase) {
        phase = next
        ticks = 0
        settled = 0
    }

    private fun prepare(next: Phase) {
        cut()
        afterTransfer = next
        enter(Phase.FADE)
    }

    private fun follow(sample: RocketConnection.Sample) {
        val s = state()
        val feedback =
            OrbitalGuidance.limited(
                (sample.state.position - s.position) * 0.4 + (sample.state.velocity - s.velocity) * 1.1,
                10.0,
            )
        val referenceThrust = sample.acceleration - field.acceleration(sample.state.position)
        if (referenceThrust.length() > 1.5) {
            engine().update(sample.acceleration + feedback, referenceThrust, true)
        } else {
            val corrective = sample.acceleration + feedback - field.acceleration(s.position)
            val direction = if (corrective.length() > 1.0) corrective else sample.state.velocity
            engine().update(sample.acceleration + feedback, direction, true)
        }
    }

    override fun requestLanding() {
        require(phase == Phase.COAST) { "Wait for unpowered coast before requesting return" }
        enter(Phase.RETURN)
    }

    override fun tick(pilot: ServerPlayer?): Boolean {
        require(!closed && pilot != null && pilot.isAlive) { "Pilot unavailable" }
        check(++ticks < (16000 * kotlin.math.sqrt(org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SCALE)).toInt()) {
            "Rocket mission timed out: $phase"
        }
        check(transfer.error == null) { "Transfer stopped: ${transfer.error}" }
        if (phase == Phase.TRANSFER) {
            if (transfer.busy) {
                ready = 0
                return false
            }
            val target = if (afterTransfer == Phase.ASCENT) FlatPlanetSpace.SPACE else Level.OVERWORLD
            val level = checkNotNull(pilot.server.getLevel(target))
            val restored = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(site.ship) as? ServerSubLevel
            require(restored != null && pilot.level() === level) { "Destination rocket/pilot missing" }
            if (++ready < 5 || Sable.HELPER.getTrackingSubLevel(pilot) !== restored) return false
            body = restored
            clock += ready * 0.05
            preload.close()
            enter(afterTransfer)
            return false
        }
        require(!body.isRemoved && pilot.level() === body.level && Sable.HELPER.getTrackingSubLevel(pilot) === body) {
            "Stay inside the rocket with creative flight disabled"
        }
        require(!SubLevelPhysicsSystem.require(body.level).paused) { "Native physics paused outside handoff" }
        body.level.resetEmptyTime()
        val s = state()
        require(
            s.velocity.length() < 220.0 * kotlin.math.sqrt(org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SCALE) &&
                s.position.length() < maxOf(10000.0, FlatPlanetSpace.HALF_SIZE * 4.0),
        ) { "Rocket left the tested envelope" }
        when (phase) {
            Phase.PLANNING -> {
                if (!planning.isDone) return false
                course = RocketCourse(planning.join(), RocketCourse.launchAxis(site.position))
                if (returningAtStart) {
                    coastEnergy = field.specificEnergy(s)
                    angle = atan2(s.position.dot(course.axis), s.position.y)
                    enter(Phase.RETURN)
                } else {
                    // Plan before launch; the short liftoff joins this moving state without a hover leg.
                    val start = CartesianState(site.position + Vector3(0.0, 10.0, 0.0), Vector3(0.0, 6.0, 0.0))
                    val authority = engine().maximumAcceleration
                    connectionPlanning = CompletableFuture.supplyAsync { RocketAscent.plan(field, course, start, authority) }
                    enter(Phase.LIFTOFF)
                }
            }

            Phase.LIFTOFF -> {
                engine().update(Vector3(0.0, if (s.velocity.y < 6.0) 3.0 else 0.0, 0.0))
                if (s.position.y >= site.position.y + 10.0 && connectionPlanning!!.isDone) {
                    ascent = connectionPlanning!!.join() as RocketAscent
                    clock = 0.0
                    enter(Phase.ASCENT)
                }
            }

            Phase.ASCENT -> {
                clock += 0.05
                preload.approach(body.level, s.position, s.velocity)
                if (clock <= ascent.path.duration) follow(ascent.path.at(clock)) else enter(Phase.CAPTURE)
                if (body.level.dimension() == Level.OVERWORLD &&
                    s.position.y >= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT
                ) {
                    prepare(Phase.ASCENT)
                }
            }

            Phase.CAPTURE -> {
                clock += 0.05
                val ref = course.stateAt(ascent.orbitPhase + clock - ascent.path.duration)
                val error = (ref.position - s.position).length()
                val velocityError = (ref.velocity - s.velocity).length()
                if (ticks % 200 ==
                    0
                ) {
                    println("ORBITALIS_ROCKET_CAPTURE positionError=$error velocityError=$velocityError ${engine().status()}")
                }
                follow(RocketConnection.Sample(ref, field.acceleration(ref.position)))
                if (error < 1.5 && velocityError < 0.4) settled++ else settled = 0
                if (settled >= 10) {
                    cut()
                    enter(Phase.ALIGN)
                }
            }

            Phase.ALIGN -> {
                engine().turn(s.velocity)
                if (engine().alignment(s.velocity) > 0.995 && ticks > 160) {
                    cut()
                    enter(Phase.FREE)
                }
            }

            Phase.FREE -> {
                if (AutopilotSession.enginesFaded(body)) {
                    coastEnergy = field.specificEnergy(s)
                    angle = atan2(s.position.dot(course.axis), s.position.y)
                    coastAngle = 0.0
                    enter(Phase.COAST)
                }
                require(ticks < 200) { "RCS failed to fade before free coast" }
            }

            Phase.COAST, Phase.RETURN -> {
                require(
                    coastEnergy < 0.0 && abs((field.specificEnergy(s) - coastEnergy) / coastEnergy) < 0.03,
                ) { "Unpowered orbit energy drifted" }
                val current = atan2(s.position.dot(course.axis), s.position.y)
                coastAngle += OrbitalGuidance.angleStep(angle, current)
                angle = current
                if (phase == Phase.COAST && coastAngle >= 2 * PI) enter(Phase.RETURN)
                if (phase == Phase.RETURN && coastAngle >= 2 * PI &&
                    s.position.y > FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ORBIT_HEIGHT * 0.7 &&
                    s.position.dot(course.axis) < 0.0 &&
                    s.velocity.dot(course.axis) > 0.0
                ) {
                    enter(Phase.FLIP)
                }
            }

            Phase.FLIP -> {
                engine().turn(s.velocity * -1.0)
                if (engine().alignment(s.velocity * -1.0) > 0.995 && ticks > 100) {
                    val initial = s
                    val authority = engine().maximumAcceleration
                    connectionPlanning = CompletableFuture.supplyAsync { RocketReturn.plan(field, initial, site.position, authority) }
                    enter(Phase.BRAKE)
                    clock = 0.0
                }
            }

            Phase.BRAKE -> {
                clock += 0.05
                if (returning == null) {
                    engine().turn(s.velocity * -1.0)
                    if (!connectionPlanning!!.isDone) return false
                    returning = connectionPlanning!!.join() as RocketReturn.Path
                }
                follow(checkNotNull(returning).at(clock))
                preload.approach(body.level, s.position, s.velocity)
                if (body.level.dimension() == FlatPlanetSpace.SPACE &&
                    s.position.y <= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT
                ) {
                    prepare(Phase.BRAKE)
                } else if (clock >= checkNotNull(returning).duration) {
                    enter(Phase.APPROACH)
                }
            }

            Phase.APPROACH -> {
                engine().update(OrbitalGuidance.descent(s, site.position))
                if ((s.position - site.position).length() < 1.0 && s.velocity.length() < 0.35 &&
                    engine().alignment(Vector3(0.0, 1.0, 0.0)) > 0.999
                ) {
                    cut()
                    enter(Phase.TOUCHDOWN)
                }
            }

            Phase.TOUCHDOWN -> {
                require((s.position - site.position).length() < 1.5) { "Terrain failed to support the rocket" }
                if (ticks >= 60 && AutopilotSession.enginesFaded(body) && s.velocity.length() < 0.2) {
                    enter(Phase.LANDED)
                    return true
                }
                require(ticks < 240) { "Rocket touchdown failed to settle" }
            }

            Phase.FADE -> {
                clock += 0.05
                require(ticks < 200) { "Rocket engines failed to fade" }
                if (AutopilotSession.enginesFaded(body)) {
                    pilot.closeContainer()
                    transfer.begin(
                        pilot,
                        body,
                        if (afterTransfer ==
                            Phase.ASCENT
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

            Phase.LANDED -> {
                return true
            }

            Phase.TRANSFER -> {
                error("Unexpected transfer")
            }
        }
        return false
    }

    override fun status(): String =
        if (body.isRemoved) {
            "Rocket $phase; journaled handoff"
        } else {
            "Rocket $phase; height=%.1f m; speed=%.2f m/s; coast=%.2f turns".format(
                java.util.Locale.ROOT,
                state().position.y - 64.0,
                state().velocity.length(),
                coastAngle / (2 * PI),
            )
        }

    override fun close() {
        if (closed) return
        closed = true
        cut()
        preload.close()
        planning.cancel(false)
        connectionPlanning?.cancel(false)
        SpaceTravelReservations.release(site.ship, this)
    }
}
