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
import net.minecraft.world.level.levelgen.Heightmap
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.joml.Vector3d
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Disposable stand mission. Commands engines; the shared journal remains the only transfer owner. */
internal class OrbitalMission(
    pilot: ServerPlayer,
    var body: ServerSubLevel,
    private val site: LaunchSites.Site,
    private val transfer: SableShipTransfer,
    land: Boolean,
) : AutoCloseable {
    enum class Phase { ASCENT, CLIMB, INSERTION, COAST, RETURN, DESCENT, APPROACH, TOUCHDOWN, LANDED, FADE, TRANSFER }

    private val crown: Vector3
    private val homeRadial: Vector3
    private val normal: Vector3
    private var engines: AutopilotSession? = null
    var phase: Phase
        private set
    private var afterTransfer = Phase.CLIMB
    private var phaseTicks = 0
    private var settled = 0
    private var readyTicks = 0
    private var previousAngle = 0.0
    private var sweptAngle = 0.0
    private var returnAngle = 0.0
    private var returning = false
    private var closed = false

    init {
        require(site.ship == body.uniqueId) { "Launch bookmark belongs to another ship" }
        require(!transfer.busy) { "Wait for transfer/recovery to finish" }
        val x = site.position.x
        val z = site.position.z
        crown = CENTRE + Vector3(x, sqrt(RADIUS * RADIUS - x * x - z * z), z)
        homeRadial = (crown - CENTRE).normalized()
        val east = Vector3(1.0, 0.0, 0.0)
        normal = homeRadial.cross((east - homeRadial * east.dot(homeRadial)).normalized()).normalized()
        if (!land) {
            require(body.level.dimension() == SPACE.SURFACE) { "Launch from the flight test surface" }
            SableShipSnapshot.preflight(body, pilot)
            require(velocity().length() < 0.3) { "Let the ship settle on its launch pad first" }
            val bounds = body.boundingBox()
            val terrainTop = body.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor(x).toInt(), floor(z).toInt())
            require(abs(bounds.minY() - terrainTop) < 1) { "Start on a flat, clear launch pad on the terrain" }
            phase = Phase.ASCENT
        } else {
            require(body.level.dimension() == SPACE.SPACE) { "Return requires the full-space frame and a saved launch bookmark" }
            phase = Phase.COAST
            requestLanding()
        }
    }

    private fun position() = SPACE.globalPosition(body.level, AutopilotSession.position(body))

    private fun velocity() =
        AutopilotSession.vector(
            SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body).getLinearVelocity(Vector3d()),
        )

    private fun state() = CartesianState(position(), velocity())

    private fun enter(next: Phase) {
        phase = next
        phaseTicks = 0
        settled = 0
    }

    private fun cut() {
        engines?.close()
        engines = null
    }

    private fun drive(acceleration: Vector3) {
        val controller = engines ?: AutopilotSession(body, AutopilotSession.position(body), site.attitude, true).also { engines = it }
        controller.update(acceleration)
    }

    private fun reached(
        target: Vector3,
        tolerance: Double,
    ) = (position() - target).length() < tolerance && velocity().length() < 0.25

    private fun prepareTransfer(next: Phase) {
        cut()
        afterTransfer = next
        enter(Phase.FADE)
    }

    fun requestLanding() {
        require(phase == Phase.COAST) { "Wait for orbital insertion to finish before requesting landing" }
        val o = OrbitalGuidance.observe(state(), CENTRE, normal)
        require(abs(o.radius - RADIUS) < 1000 && abs(o.planeError) < 500) {
            "Return requires the launch orbital plane near the 18 km orbit"
        }
        previousAngle = OrbitalGuidance.angle(o.radial, homeRadial, normal)
        sweptAngle = previousAngle
        var arc = -previousAngle
        while (arc < 0) arc += 2 * PI
        val stoppingArc = max(0.0, o.tangentialSpeed) * max(0.0, o.tangentialSpeed) / (2 * 1.2) / RADIUS
        if (arc < stoppingArc + 0.05) arc += 2 * PI
        returnAngle = sweptAngle + arc
        returning = true
        enter(Phase.RETURN)
    }

    /** True only after engines are off and the native terrain has held the craft for 60 ticks. */
    fun tick(pilot: ServerPlayer?): Boolean {
        require(!closed && pilot != null && pilot.isAlive) { "Pilot is unavailable" }
        check(++phaseTicks <= 40000) { "Mission stage timed out: $phase" }
        check(transfer.error == null) { "Transfer stopped: ${transfer.error}" }
        if (phase == Phase.TRANSFER) {
            if (transfer.busy) {
                readyTicks = 0
                return false
            }
            val target = if (afterTransfer == Phase.CLIMB) SPACE.SPACE else SPACE.SURFACE
            val level = pilot.server.getLevel(target)
            val restored = checkNotNull(SubLevelContainer.getContainer(level)).getSubLevel(site.ship) as? ServerSubLevel
            require(restored != null && pilot.level() === level) { "Transfer did not establish the destination ship and pilot" }
            // The service already fenced native tracking, teleport acknowledgment and deck contact.
            // Give newly loaded Propulsion connectivity its first resumed ticks too.
            if (++readyTicks < 5 || Sable.HELPER.getTrackingSubLevel(pilot) !== restored) return false
            body = restored
            enter(afterTransfer)
            return false
        }
        require(!body.isRemoved && pilot.level() === body.level && Sable.HELPER.getTrackingSubLevel(pilot) === body) {
            "Pilot must remain on the mission ship, with creative flight disabled"
        }
        require(!SubLevelPhysicsSystem.require(body.level).paused) { "Native physics was paused outside a mission handoff" }
        body.level.resetEmptyTime()
        val state = state()
        require(state.velocity.length() < 220) { "Mission speed exceeded 220 m/s" }
        when (phase) {
            Phase.ASCENT -> {
                // The handoff is a crossed plane, not a waypoint to settle at.
                drive(OrbitalGuidance.point(state, crown, 80.0))
                if (state.position.y >= HANDOFF_Y) prepareTransfer(Phase.CLIMB)
            }

            Phase.FADE -> {
                require(phaseTicks < 200) { "Native engines did not finish fading before handoff" }
                if (AutopilotSession.enginesFaded(body)) {
                    pilot.closeContainer()
                    transfer.begin(pilot, body, if (afterTransfer == Phase.CLIMB) SPACE.SPACE else SPACE.SURFACE)
                    readyTicks = 0
                    enter(Phase.TRANSFER)
                }
            }

            Phase.CLIMB -> {
                drive(OrbitalGuidance.point(state, crown, 80.0))
                // Exact far-field hovering stalls on the pinned float Rapier integration at large local Y.
                if ((state.position - crown).length() < 100 && state.velocity.length() < 10) enter(Phase.INSERTION)
            }

            Phase.INSERTION -> {
                val o = OrbitalGuidance.observe(state, CENTRE, normal)
                drive(OrbitalGuidance.orbit(state, CENTRE, normal, RADIUS, CIRCULAR_SPEED))
                val ready =
                    abs(o.radius - RADIUS) < 5 && abs(o.radialSpeed) < 0.1 &&
                        abs(o.tangentialSpeed - CIRCULAR_SPEED) < 0.15 && abs(o.planeError) < 2 &&
                        SubLevelPhysicsSystem
                            .require(body.level)
                            .getPhysicsHandle(body)
                            .getAngularVelocity(Vector3d())
                            .length() < 0.001
                settled = if (ready) settled + 1 else 0
                if (settled >= 60) {
                    cut()
                    enter(Phase.COAST)
                }
            }

            Phase.COAST -> {
                val o = OrbitalGuidance.observe(state, CENTRE, normal)
                require(abs(o.radius - RADIUS) < 500) { "Coast drifted outside the tested orbit; manual control required" }
            }

            Phase.RETURN -> {
                val o = OrbitalGuidance.observe(state, CENTRE, normal)
                val angle = OrbitalGuidance.angle(o.radial, homeRadial, normal)
                sweptAngle += OrbitalGuidance.angleStep(previousAngle, angle)
                previousAngle = angle
                val remaining = (returnAngle - sweptAngle) * RADIUS
                drive(OrbitalGuidance.orbit(state, CENTRE, normal, RADIUS, OrbitalGuidance.returnSpeed(remaining, CIRCULAR_SPEED)))
                // As at insertion, begin the next vertical leg without an exact far-field hover.
                if ((state.position - crown).length() < 10 && state.velocity.length() < 2) enter(Phase.DESCENT)
            }

            Phase.DESCENT -> {
                // Brake for the actual landing site, never for the dimension boundary.
                drive(OrbitalGuidance.descent(state, site.position))
                if (state.position.y <= HANDOFF_Y) prepareTransfer(Phase.APPROACH)
            }

            Phase.APPROACH -> {
                // Remembered COM/attitude preserve landing-leg clearance; native Sable owns terrain collisions.
                drive(OrbitalGuidance.descent(state, site.position))
                // Native support/friction can settle a fraction of a block from the launch COM.
                // Touchdown must still settle with engines off; do not hover forever at a strict 0.2 m threshold.
                if (reached(site.position, 0.5)) {
                    cut()
                    enter(Phase.TOUCHDOWN)
                }
            }

            Phase.TOUCHDOWN -> {
                require((position() - site.position).length() < 1) { "Terrain did not support touchdown at the launch point" }
                if (phaseTicks >= 60 && AutopilotSession.enginesFaded(body) && velocity().length() < 0.2) {
                    enter(Phase.LANDED)
                    return true
                }
                require(phaseTicks < 200) { "Touchdown did not settle on native terrain" }
            }

            Phase.LANDED -> {
                return true
            }

            Phase.TRANSFER -> {
                error("Unexpected mission stage")
            }
        }
        return false
    }

    fun status(): String {
        if (body.isRemoved) return "Mission $phase; waiting for journaled handoff"
        val p = position() - CENTRE
        val v = velocity()
        val radius = p.length()
        val energy = v.dot(v) / 2 - MU / radius
        val eccentricity = (v.cross(p.cross(v)) * (1 / MU) - p * (1 / radius)).length()
        return String.format(
            Locale.ROOT,
            "Mission %s%s; radius=%.1f m; speed=%.2f m/s; radial=%.2f m/s; far-field energy=%.2f J/kg; e=%.5f; launch error=%.1f m. %s",
            phase,
            if (returning) " (return to launch)" else "",
            radius,
            v.length(),
            v.dot(p) / radius,
            energy,
            eccentricity,
            (position() - site.position).length(),
            if (phase ==
                Phase.COAST
            ) {
                "Engines off; free orbit."
            } else {
                "Real Propulsion engines only."
            },
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        cut()
    }

    companion object {
        private val SPACE = FlightTestSpace
        private val CENTRE = SPACE.CENTRE
        private const val RADIUS = OrbitalGuidance.RADIUS
        private val MU = SPACE.gravity.surfaceAcceleration * SPACE.gravity.radius.pow(2)
        private val CIRCULAR_SPEED = sqrt(MU / RADIUS)
        private const val HANDOFF_Y = 1000.0
    }
}
