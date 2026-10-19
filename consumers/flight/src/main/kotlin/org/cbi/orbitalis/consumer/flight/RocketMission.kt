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
import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPassengers
import java.util.concurrent.CompletableFuture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/** Disposable consumer guidance: every manoeuvre uses native main engines or native RCS. */
internal class RocketMission(
    pilot: ServerPlayer?,
    override var body: ServerSubLevel,
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
    private val launchBasis =
        CelestialFlight
            .sample(
                body.level,
            ).frames.profile
            .earthSurfaceFrame(
                CelestialFlight
                    .sample(body.level)
                    .frames.tick,
            ).copy(angularVelocity = Vector3.ZERO)
    private var landingFrame = returningAtStart
    private val field = SableFlightGravity.flatField(body.level)
    private val preload = ShipDestinationPreload()
    private val planningModel =
        CelestialFlight
            .sample(body.level)
            .dynamics
    private val geometry = planningModel.profile.geometry
    private val orbitAltitude = maxOf(geometry.earthHalfSize, geometry.earthEnvelope.ascentDistance + 200.0)

    // At the top face, prograde +Z spin carries the launch pad towards local -X.
    private val orbitDirection = -1.0
    private val planning =
        CompletableFuture.supplyAsync {
            BallisticOrbit.firstRevolution(field, planningModel, orbitAltitude, 64.0, orbitDirection)
        }

    private var connectionPlanning: CompletableFuture<*>? = null
    private var capturePlanning: CompletableFuture<Boolean>? = null
    private var captureReference: RocketCaptureReference? = null
    private lateinit var course: RocketCourse
    private lateinit var ascent: RocketAscent
    private var ascentEpochTick = 0L
    private var returning: RocketReturn.Path? = null

    /** Immutable guidance sample for the consumer's native flight recorder. */
    val returnReference: RocketConnection.Sample? get() = returning?.at(clock)
    private var returnPlanning: CompletableFuture<RocketReturn.Departure>? = null
    val waitingForAscentPlan get() = connectionPlanning?.isDone == false
    private var returnRefining: CompletableFuture<RocketReturn.Path>? = null
    private var returnRefined = false
    private var returnEpochTick = 0L
    val waitingForReturnPlan get() = returnPlanning?.isDone == false || returnRefining?.isDone == false
    private var departure: RocketReturn.Departure? = null
    private var returnClock = 0.0
    private var drive: RocketDrive? = null
    private var ticks = 0
    private var settled = 0
    private var ready = 0
    private var clock = 0.0
    private var angle = 0.0
    var coastAngle = 0.0
        private set
    private var afterTransfer = Phase.ASCENT
    private var closed = false
    private val server = body.level.server

    init {
        require(site.surface == Level.OVERWORLD && site.ship == body.uniqueId && !transfer.busy)
        require(
            abs(site.position.x) <= field.body.halfWidth - 40.0 && abs(site.position.z) <= field.body.halfDepth - 40.0,
        ) { "Leave a 40-metre launch-pad margin inside the finite surface" }
        if (!returningAtStart) {
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
            ) { "Let the rocket settle on a flat clear pad" }
            RocketDrive(body, true).close()
        } else {
            require(body.level.dimension() == FlatPlanetSpace.SPACE) { "Return from the finite-body space frame" }
        }
        SpaceTravelReservations.reserve(site.ship, this)
    }

    private fun state() =
        if (landingFrame) FlightGuidanceFrames.earthState(body) else launchBasis.fromParent(FlightGuidanceFrames.rootState(body))

    private fun rootDirection(value: Vector3) =
        if (landingFrame) FlightGuidanceFrames.rootDirection(body, value) else launchBasis.orientation.apply(value)

    private fun rootAcceleration(value: Vector3) =
        if (landingFrame) FlightGuidanceFrames.rootAcceleration(body, value) else launchBasis.orientation.apply(value)

    private fun engine() = drive ?: RocketDrive(body).also { drive = it }

    private fun turn(direction: Vector3) = engine().turn(rootDirection(direction))

    private fun alignment(direction: Vector3) = engine().alignment(rootDirection(direction))

    private fun command(
        acceleration: Vector3,
        nose: Vector3? = null,
        track: Boolean = false,
    ) = engine().update(
        rootAcceleration(acceleration),
        nose?.let { rootDirection(it) },
        track,
    )

    private fun gravity(
        state: CartesianState,
        seconds: Double = 0.0,
    ): Vector3 {
        val dynamics =
            CelestialFlight
                .sample(server)
                .dynamics
        return if (landingFrame) {
            dynamics.earthAcceleration(
                state,
                seconds,
            )
        } else {
            launchBasis.orientation.inverse(dynamics.acceleration(launchBasis.toParent(state).position, seconds))
        }
    }

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
        val acceleration = sample.acceleration + feedback
        // Attitude must follow the complete force request. Following feed-forward alone can
        // point the hull away from position feedback until the gimbals saturate their cone.
        val rootDrag = CelestialFlight.sample(body.level).rootDirection(FlightEnvironment.nativeDrag(body), body.level.dimension())
        val drag =
            if (landingFrame) {
                CelestialFlight.sample(body.level).nativeDirection(rootDrag, Level.OVERWORLD)
            } else {
                launchBasis.orientation.inverse(rootDrag)
            }
        val corrective = acceleration - gravity(s) - drag
        val direction = if (corrective.length() > 1.0) corrective else sample.state.velocity
        command(acceleration, direction, true)
    }

    override fun requestLanding() {
        require(phase == Phase.COAST) { "Wait for unpowered coast before requesting return" }
        landingFrame = true
        enter(Phase.RETURN)
    }

    override fun tick(pilot: ServerPlayer?): Boolean {
        require(!closed) { "Autopilot is closed" }
        // A completed orbit insertion may coast until the pilot explicitly requests landing.
        check(
            phase == Phase.COAST || ++ticks <
                if (phase == Phase.RETURN) {
                    // One bounded pad-relative forecast plus planning time; other powered phases
                    // retain the tighter flight watchdog.
                    ((3600.0 + RocketReturn.TURN_LEAD) * 20.0).toInt()
                } else {
                    (16000 * kotlin.math.sqrt(geometry.earthHalfSize / 1000.0)).toInt()
                },
        ) {
            "Rocket mission timed out: $phase"
        }
        check(transfer.error == null) { "Transfer stopped: ${transfer.error}" }
        if (phase == Phase.TRANSFER) {
            if (transfer.busy) {
                ready = 0
                return false
            }
            val target = if (afterTransfer == Phase.ASCENT) FlatPlanetSpace.SPACE else Level.OVERWORLD
            val level = checkNotNull(server.getLevel(target))
            val restored = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(site.ship) as? ServerSubLevel
            require(restored != null) { "Destination rocket missing" }
            if (++ready < 5 || !SableShipSnapshot.entitiesReady(restored)) return false
            body = restored
            clock += ready * 0.05
            preload.close()
            enter(afterTransfer)
            return false
        }
        require(!body.isRemoved) { "Rocket unavailable" }
        if (SubLevelPhysicsSystem.require(body.level).paused) {
            return false
        }
        body.level.resetEmptyTime()
        val s = state()
        require(
            s.velocity.length() < 220.0 * kotlin.math.sqrt(geometry.earthHalfSize / 1000.0) &&
                s.position.length() < maxOf(10000.0, geometry.earthHalfSize * 4.0),
        ) { "Rocket left the tested envelope" }
        when (phase) {
            Phase.PLANNING -> {
                // Reconnecting to a flying rocket must retain attitude authority while the immutable
                // reference orbit is calculated. Main engines remain off during this ballistic wait.
                if (returningAtStart) turn(s.velocity)
                if (!planning.isDone) return false
                course = RocketCourse(planning.join(), Vector3(orbitDirection, 0.0, 0.0), alreadyOriented = true)
                if (returningAtStart) {
                    angle = atan2(s.position.dot(course.axis), s.position.y)
                    enter(Phase.RETURN)
                } else {
                    enter(Phase.LIFTOFF)
                }
            }

            Phase.LIFTOFF -> {
                val local = FlightGuidanceFrames.earthState(body)
                if (connectionPlanning == null) {
                    val lift = Vector3(0.0, if (local.velocity.y < 6.0) 3.0 else 0.0, 0.0)
                    engine().update(FlightGuidanceFrames.rootAcceleration(body, lift))
                } else {
                    // Every candidate shares this initial acceleration. Begin its pitch while the
                    // worker evaluates the rest of the curve instead of waiting in a vertical leg.
                    command(RocketAscent.initialAcceleration(course))
                }
                if (local.position.y >= site.position.y + 10.0) {
                    if (connectionPlanning == null) {
                        val start = s
                        val authority = engine().maximumAcceleration
                        val model = CelestialFlight.sample(server).dynamics
                        ascentEpochTick = model.epochTick
                        connectionPlanning =
                            CompletableFuture.supplyAsync {
                                RocketAscent.plan(
                                    field,
                                    course,
                                    start,
                                    authority,
                                    acceleration = { state, seconds ->
                                        launchBasis.orientation.inverse(model.acceleration(launchBasis.toParent(state).position, seconds))
                                    },
                                    safeOrbit = { state, seconds ->
                                        val future = CelestialDynamics(model.profile, model.epochTick + (seconds * 20.0).toLong())
                                        val frame = future.profile.earthSurfaceFrame(future.epochTick)
                                        val native = frame.fromParent(launchBasis.toParent(state))
                                        RocketOrbitCapture.safe(field, native, course, future)
                                    },
                                )
                            }
                    } else if (connectionPlanning!!.isDone) {
                        val planned = connectionPlanning!!.join() as RocketAscent
                        val elapsed = (CelestialFlight.sample(server).frames.tick - ascentEpochTick) / 20.0
                        val join = planned.path.at(elapsed).state
                        if (elapsed >= planned.path.duration ||
                            (join.position - s.position).length() > 5.0 || (join.velocity - s.velocity).length() > 0.5
                        ) {
                            // Keep moving while planning, but never shift a validated path to a later
                            // Earth epoch. A stale join needs a new snapshot of the actual flight state.
                            connectionPlanning = null
                        } else {
                            ascent = planned
                            clock = elapsed
                            enter(Phase.ASCENT)
                        }
                    }
                }
            }

            Phase.ASCENT -> {
                clock = (CelestialFlight.sample(server).frames.tick - ascentEpochTick) / 20.0
                FlightGuidanceFrames.rootState(body).let { preload.approach(body.level, it.position, it.velocity) }
                if (clock <= ascent.path.duration) {
                    follow(ascent.path.at(clock))
                } else {
                    val ref = course.stateAt(ascent.orbitPhase + clock - ascent.path.duration)
                    val error = (ref.position - s.position).length()
                    val velocityError = (ref.velocity - s.velocity).length()
                    require(error < 200.0 && velocityError < 30.0) {
                        "Ascent missed the bounded orbit capture: position=$error m, velocity=$velocityError m/s"
                    }
                    clock = ascent.orbitPhase + clock - ascent.path.duration
                    captureReference =
                        RocketCaptureReference(
                            CelestialDynamics(
                                CelestialFlight.sample(server).dynamics.profile,
                                ascentEpochTick + (ascent.path.duration * 20.0).toLong(),
                            ),
                            launchBasis.toParent(ascent.path.at(ascent.path.duration).state),
                        )
                    enter(Phase.CAPTURE)
                }
                if (body.level.dimension() == Level.OVERWORLD &&
                    FlightGuidanceFrames.earthState(body).position.y >= FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.ascentDistance
                ) {
                    prepare(Phase.ASCENT)
                }
            }

            Phase.CAPTURE -> {
                require(ticks < 1200) { "Ballistic insertion failed to settle" }
                clock += 0.05
                val modelNow =
                    CelestialFlight
                        .sample(server)
                        .dynamics
                val referenceRoot = checkNotNull(captureReference).at(modelNow.epochTick)
                val ref = launchBasis.fromParent(referenceRoot)
                val acceleration = launchBasis.orientation.inverse(modelNow.acceleration(referenceRoot.position))
                if (capturePlanning == null && ticks > 1) follow(RocketConnection.Sample(ref, acceleration))
                val matched = (ref.position - s.position).length() < 2.0 && (ref.velocity - s.velocity).length() < 0.5
                settled = if (matched) settled + 1 else 0
                if ((ticks == 1 || settled >= 20) && capturePlanning == null) {
                    val model =
                        CelestialFlight
                            .sample(server)
                            .dynamics
                    val native = model.profile.earthSurfaceFrame(model.epochTick).fromParent(launchBasis.toParent(s))
                    cut()
                    capturePlanning = CompletableFuture.supplyAsync { RocketOrbitCapture.safe(field, native, course, model) }
                }
                capturePlanning?.takeIf { it.isDone }?.let { forecast ->
                    if (forecast.join()) {
                        cut()
                        enter(Phase.ALIGN)
                    } else {
                        capturePlanning = null
                    }
                }
            }

            Phase.ALIGN -> {
                turn(s.velocity)
                if (alignment(s.velocity) > 0.995 && ticks > 160) {
                    cut()
                    enter(Phase.FREE)
                }
            }

            Phase.FREE -> {
                if (AutopilotSession.enginesFaded(body)) {
                    angle = atan2(s.position.dot(course.axis), s.position.y)
                    coastAngle = 0.0
                    enter(Phase.COAST)
                }
                require(ticks < 200) { "RCS failed to fade before free coast" }
            }

            Phase.COAST, Phase.RETURN -> {
                // Main engines remain off; balanced native RCS damps residual spin and tracks
                // prograde continuously rather than leaving the hull uncontrolled for a full orbit.
                turn(if (phase == Phase.RETURN) s.velocity * -1.0 else s.velocity)
                // The rotating nonspherical field exchanges inertial energy with the vessel.
                // Bound position/speed above; energy is not conserved by this moving potential.
                val current = atan2(s.position.dot(course.axis), s.position.y)
                coastAngle += OrbitalGuidance.angleStep(angle, current)
                angle = current
                if (phase == Phase.RETURN) {
                    if (returnPlanning == null) {
                        val initial = s
                        val authority = engine().maximumAcceleration
                        returnClock = 0.0
                        val model =
                            CelestialFlight
                                .sample(server)
                                .dynamics
                        returnEpochTick = model.epochTick
                        val surfaceDrag = FlightEnvironment.linearDrag(server.overworld())
                        val entryHeight = FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.descentDistance
                        returnPlanning =
                            CompletableFuture.supplyAsync {
                                RocketReturn.departure(
                                    field,
                                    initial,
                                    site.position,
                                    authority,
                                    // An orbit's horizontal velocity reverses beneath the planet.
                                    // The top-face landing approach retains the launch-plane axis.
                                    course.axis,
                                    RocketReturn.searchHorizon(model, initial),
                                    RocketReturn.earthForces(model),
                                    surfaceDrag = surfaceDrag,
                                    entryHeight = entryHeight,
                                )
                            }
                    } else {
                        returnClock = (CelestialFlight.sample(server).frames.tick - returnEpochTick) / 20.0
                        if (returnPlanning!!.isDone) {
                            departure = returnPlanning!!.join()
                            val window = checkNotNull(departure)
                            require(returnClock < window.delay) { "Return planner missed its burn window; coast and retry return" }
                            if (returnClock >= window.delay - RocketReturn.TURN_LEAD) {
                                val initial = s
                                val model = CelestialFlight.sample(server).dynamics
                                val delay = (window.delay - returnClock).coerceIn(0.0, RocketReturn.TURN_LEAD)
                                val authority = engine().maximumAcceleration
                                val drag = FlightEnvironment.linearDrag(server.overworld())
                                val entry = FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.descentDistance
                                returnRefining =
                                    CompletableFuture.supplyAsync {
                                        RocketReturn.refine(
                                            field,
                                            initial,
                                            site.position,
                                            authority,
                                            course.axis,
                                            delay,
                                            RocketReturn.earthForces(model),
                                            drag,
                                            entry,
                                        )
                                    }
                                enter(Phase.FLIP)
                            }
                        }
                    }
                }
            }

            Phase.FLIP -> {
                returnClock = (CelestialFlight.sample(server).frames.tick - returnEpochTick) / 20.0
                returnRefining?.takeIf { it.isDone }?.let { future ->
                    departure = checkNotNull(departure).copy(path = future.join())
                    returnRefining = null
                    returnRefined = true
                }
                val window = checkNotNull(departure)
                val direction =
                    window.path
                        .at(0.0)
                        .state.velocity * -1.0
                turn(direction)
                require(returnClock <= window.delay + 2.0) { "RCS missed the planned return attitude; manual control required" }
                if (returnRefined && returnClock >= window.delay && alignment(direction) > 0.995 && ticks > 100) {
                    returning = window.path
                    clock = returnClock - window.delay
                    enter(Phase.BRAKE)
                }
            }

            Phase.BRAKE -> {
                clock += 0.05
                follow(checkNotNull(returning).at(clock))
                FlightGuidanceFrames.rootState(body).let { preload.approach(body.level, it.position, it.velocity) }
                if (body.level.dimension() == FlatPlanetSpace.SPACE &&
                    s.position.y <= FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.descentDistance &&
                    s.velocity.y < 0.0 &&
                    abs(s.position.x) < geometry.earthHalfSize - 40.0 && abs(s.position.z) < geometry.earthHalfSize - 40.0
                ) {
                    prepare(Phase.BRAKE)
                } else if (clock >= checkNotNull(returning).duration) {
                    enter(Phase.APPROACH)
                }
            }

            Phase.APPROACH -> {
                command(OrbitalGuidance.descent(s, site.position))
                if ((s.position - site.position).length() < 1.0 && s.velocity.length() < 0.35 &&
                    alignment(Vector3(0.0, 1.0, 0.0)) > 0.999
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
                if (!transfer.busy && AutopilotSession.enginesFaded(body)) {
                    transfer.begin(
                        AutopilotPassengers.select(body, pilot),
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
            ) + if (phase == Phase.COAST) "; orbit reached; use /orbitalis flight autopilot land to return" else ""
        }

    override fun close() {
        if (closed) return
        closed = true
        cut()
        preload.close()
        planning.cancel(false)
        connectionPlanning?.cancel(true)
        capturePlanning?.cancel(true)
        returnPlanning?.cancel(true)
        returnRefining?.cancel(true)
        SpaceTravelReservations.release(site.ship, this)
    }
}
