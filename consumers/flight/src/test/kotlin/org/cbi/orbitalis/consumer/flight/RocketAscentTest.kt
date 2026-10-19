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

import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.RectangularGravity
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketAscentTest {
    private val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 11.0), 11.0)

    @Test fun connectionPreservesBothMovingEndpointsAndAccelerations() {
        val start = CartesianState(Vector3(200.0, 140.0, -300.0), Vector3(0.0, 5.0, 0.0))
        val end = CartesianState(Vector3(500.0, 800.0, 0.0), Vector3(60.0, -20.0, 0.0))
        val a = Vector3(1.0, 3.0, 0.0)
        val b = Vector3(-2.0, -3.0, 0.0)
        val path = RocketConnection(start, end, a, b, 32.0)
        for ((sample, state, acceleration) in listOf(Triple(path.at(0.0), start, a), Triple(path.at(32.0), end, b))) {
            assertTrue((sample.state.position - state.position).length() < 1e-8)
            assertTrue((sample.state.velocity - state.velocity).length() < 1e-8)
            assertTrue((sample.acceleration - acceleration).length() < 1e-8)
        }
    }

    @Test fun arbitraryPadsAccelerateHorizontallyBeforeReachingOrbitAndJoinItWhileMoving() {
        val orbit = BallisticOrbit.plan(field, 900.0)
        for (pad in listOf(
            Vector3(0.0, 90.0, 0.0),
            Vector3(850.0, 160.0, 500.0),
            Vector3(-850.0, 280.0, -700.0),
            Vector3(300.0, -35.0, -800.0),
        )) {
            val course = RocketCourse(orbit, RocketCourse.launchAxis(pad))
            val ascent = RocketAscent.plan(field, course, CartesianState(pad, Vector3(0.0, 5.0, 0.0)), 60.0)
            val early = ascent.path.at(5.0).state
            assertTrue(early.velocity.dot(course.axis) > 0.5, "No early horizontal acceleration from $pad")
            assertTrue(early.position.y < orbit.initial.position.y - 100.0)
            val end = ascent.path.at(ascent.path.duration)
            val expected = course.stateAt(ascent.orbitPhase)
            assertTrue((end.state.position - expected.position).length() < 1e-7)
            assertTrue((end.state.velocity - expected.velocity).length() < 1e-7 && end.state.velocity.length() > 50.0)
            assertTrue((end.acceleration - field.acceleration(expected.position)).length() < 1e-7)
        }
    }

    @Test fun returnWindowIncludesTurnLeadAvoidsPadOvershootAndSelectsLowDeltaV() {
        val field =
            FlatPlanetDefinition
                .gravity(11.0)
        val orbit = BallisticOrbit.plan(field, FlatPlanetDefinition.ORBIT_HEIGHT)
        for (home in listOf(Vector3(0.5, 29.0, 0.5), Vector3(4000.0, 140.0, 2500.0), Vector3(-4000.0, 260.0, -3500.0))) {
            val course = RocketCourse(orbit, RocketCourse.launchAxis(home))
            val initial = course.stateAt(orbit.period * 0.4)
            val departure = RocketReturn.departure(field, initial, home, 60.0, course.axis, orbit.period + RocketReturn.TURN_LEAD)
            val path = departure.path
            assertTrue(departure.delay >= RocketReturn.TURN_LEAD)
            // Exercise the current production footprint, including near-edge pads.
            // This is a bounded feasibility budget; pre-scaling fuel comparisons do not apply.
            assertTrue(
                path.deltaV < 600.0 * FlatPlanetDefinition.SCALE,
                "Return wastes excessive delta-v: ${path.deltaV} from $home",
            )
            val start = path.at(0.0).state
            val direction = course.axis
            val remaining = (home - start.position).dot(direction)
            assertTrue(remaining > 20.0, "Burn starts after passing the pad")
            var t = 0.0
            while (t <= path.duration) {
                val sample = path.at(t)
                assertTrue((sample.state.position - start.position).dot(direction) <= remaining + 0.51, "Return overshoots the pad")
                t += 0.1
            }
            for ((left, right) in path.segments.zipWithNext()) {
                val before = left.at(left.duration)
                val after = right.at(0.0)
                assertTrue((before.state.position - after.state.position).length() < 1e-7)
                assertTrue((before.state.velocity - after.state.velocity).length() < 1e-7)
                assertTrue((before.acceleration - after.acceleration).length() < 1e-7)
                assertTrue(after.state.velocity.length() > 5.0, "Approach must not stop at a waypoint")
            }
            println("ROCKET_RETURN_PLAN home=$home delay=${departure.delay} duration=${path.duration} deltaV=${path.deltaV}")
        }
    }

    @Test fun tenKilometreAscentAndUpperReturnRemainWithinEngineAuthority() {
        val scaled =
            FlatPlanetDefinition
                .gravity(11.0)
        val orbit = BallisticOrbit.plan(scaled, 5000.0)
        for (home in listOf(Vector3(0.5, 29.0, 0.5), Vector3(800.5, 89.0, 500.5))) {
            val course = RocketCourse(orbit, RocketCourse.launchAxis(home))
            val ascent = RocketAscent.plan(scaled, course, CartesianState(home + Vector3(0.0, 10.0, 0.0), Vector3(0.0, 6.0, 0.0)), 60.0)
            assertTrue(
                ascent.path
                    .at(ascent.path.duration)
                    .state.position.y >= 4064.0,
                "Ascent joined the low descending orbit arc",
            )
            val upper =
                (80..98)
                    .map { course.stateAt(orbit.period * it / 100.0) }
                    .first { it.position.y > 4000.0 && it.velocity.y > 0.0 }
            val returning = RocketReturn.departure(scaled, upper, home, 60.0, course.axis, orbit.period + RocketReturn.TURN_LEAD).path
            assertTrue(returning.deltaV.isFinite() && returning.deltaV > 0.0)
            assertTrue((returning.at(returning.duration).state.position - (home + Vector3(0.0, 0.6, 0.0))).length() < 1e-7)
            var t = 0.0
            while (t <= returning.duration) {
                val sample = returning.at(t)
                assertTrue((sample.acceleration - scaled.acceleration(sample.state.position)).length() <= 25.0 + 1e-6)
                t += 0.25
            }
        }
    }

    @Test fun rotatingEarthPlansTrackMovingPadWithPhysicalThrust() {
        val field =
            FlatPlanetDefinition
                .gravity(11.0)
        val model =
            CelestialDynamics(
                SolarSystemProfile(11.0, earthSpinEpochTick = 0L),
                6000L,
            )
        val orbit = BallisticOrbit.plan(field, FlatPlanetDefinition.ORBIT_HEIGHT)
        val home = Vector3(800.5, 89.0, 500.5)
        val course = RocketCourse(orbit, RocketCourse.launchAxis(home))
        val ascent =
            RocketAscent.plan(
                field,
                course,
                CartesianState(home + Vector3(0.0, 10.0, 0.0), Vector3(0.0, 6.0, 0.0)),
                60.0,
                RocketReturn.earthForces(model),
            )
        for (t in 0..ascent.path.duration.toInt()) {
            val sample = ascent.path.at(t.toDouble())
            assertTrue((sample.acceleration - model.earthAcceleration(sample.state, t.toDouble())).length() <= 25.0 + 1e-6)
        }
        val initial = course.stateAt(orbit.period * 0.4)
        val departure =
            RocketReturn.departure(
                field,
                initial,
                home,
                60.0,
                course.axis,
                orbit.period + RocketReturn.TURN_LEAD,
                RocketReturn.earthForces(model),
            )
        val terminal = departure.path.at(departure.path.duration)
        assertTrue((terminal.state.position - home - Vector3(0.0, 0.6, 0.0)).length() < 1e-7)
        val seconds = departure.delay + departure.path.duration
        val frame = model.profile.earthSurfaceFrame(model.epochTick, seconds)
        val root = frame.toParent(terminal.state)
        val pad = frame.toParent(CartesianState(home, Vector3.ZERO))
        assertTrue((root.position - pad.position).length() < 0.601)
        assertTrue((root.velocity - pad.velocity).length() < 0.21)
        assertTrue(
            (
                pad.position -
                    model.profile
                        .earthSurfaceFrame(
                            model.epochTick,
                        ).toParent(CartesianState(home, Vector3.ZERO))
                        .position
            ).length() >
                100.0,
        )
        println("ROTATING_RETURN delay=${departure.delay} duration=${departure.path.duration} deltaV=${departure.path.deltaV}")
    }

    @Test fun inertialAscentUsesPadTransportAndARealRotatingFieldReference() {
        val model =
            CelestialDynamics(
                SolarSystemProfile(11.0, earthSpinEpochTick = 0L),
                260L,
            )
        val basis = model.profile.earthSurfaceFrame(model.epochTick).copy(angularVelocity = Vector3.ZERO)
        for (home in listOf(Vector3(0.5, 29.0, 0.5), Vector3(800.5, 113.0, 500.5), Vector3(-799.5, 89.0, -499.5))) {
            val sign = -1.0
            val orbit = BallisticOrbit.firstRevolution(model.earth, model, clearance = 64.0, direction = sign)
            val course = RocketCourse(orbit, Vector3(sign, 0.0, 0.0), alreadyOriented = true)
            val start =
                basis.fromParent(
                    model.profile.earthSurfaceFrame(model.epochTick).toParent(
                        CartesianState(home + Vector3(0.0, 10.0, 0.0), Vector3(0.0, 6.0, 0.0)),
                    ),
                )
            val path =
                RocketAscent.plan(model.earth, course, start, 56.0, acceleration = {
                    state,
                    seconds,
                    ->
                    basis.orientation.inverse(model.acceleration(basis.toParent(state).position, seconds))
                })
            assertTrue(path.path.duration > 0.0)
            println("ROTATING_ASCENT home=$home duration=${path.path.duration} phase=${path.orbitPhase}")
        }
    }

    @Test fun captureReferenceUsesTheInsertionEpochAndPausesWithSimulationTime() {
        val profile =
            SolarSystemProfile(11.0, earthSpinEpochTick = 0L)
        val model =
            CelestialDynamics(profile, 17000L)
        val initial = CartesianState(Vector3(3000.0, 6000.0, 200.0), Vector3(-120.0, 40.0, 0.0))
        val reference = RocketCaptureReference(model, initial)
        org.junit.jupiter.api.Assertions
            .assertEquals(initial, reference.at(17000L))
        val first = reference.at(17040L)
        org.junit.jupiter.api.Assertions
            .assertEquals(first, reference.at(17040L))
        val actual = reference.at(17080L)
        val expected =
            CoastPrediction
                .predict(
                    model.earth,
                    initial,
                    CoastPrediction
                        .Limits(horizon = 4.0, range = profile.flightRange),
                    model,
                ).terminal.state
        assertTrue((actual.position - expected.position).length() < 1e-6)
        assertTrue((actual.velocity - expected.velocity).length() < 1e-6)
        org.junit.jupiter.api.Assertions
            .assertThrows(IllegalArgumentException::class.java) { reference.at(17079L) }
    }

    @Test fun returnSearchCoversTheMovingPadRatherThanOnlyAnInertialTurn() {
        val period = 570.5
        val spin = 2.0 * Math.PI / SolarSystemProfile.SIDEREAL_DAY_SECONDS
        org.junit.jupiter.api.Assertions
            .assertEquals(period + RocketReturn.TURN_LEAD, RocketReturn.searchHorizon(period, 0.0), 1e-9)
        assertTrue(RocketReturn.searchHorizon(period, spin) > 1000.0)
        org.junit.jupiter.api.Assertions
            .assertEquals(3600.0, RocketReturn.searchHorizon(2.0 * Math.PI / spin, spin))
    }

    @Test fun nativeProgradeReturnFindsWindowsAboveAndBelowTheRotatingPlanet() {
        val profile = SolarSystemProfile(11.0, earthSpinEpochTick = 0L, selectedEarthWidth = 10000)
        val observations =
            listOf(
                Triple(
                    2478L,
                    CartesianState(
                        Vector3(-2205.2289412114305, 5993.805935012666, -0.1015387065),
                        Vector3(-144.606890525355, 29.140440224922, -0.0072408393),
                    ),
                    Vector3(800.5, 88.98526000976562, 500.48651123046875),
                ),
                Triple(
                    83331L,
                    CartesianState(
                        Vector3(-887.568952284121, 6094.307161721396, -0.4571411727),
                        Vector3(-138.671741956576, 53.486568346714, -0.0305004567),
                    ),
                    Vector3(-799.5, 88.985260009766, -499.513488769531),
                ),
                Triple(
                    57810L,
                    CartesianState(
                        Vector3(-4035.085965597941, -10536.129740163718, 2.976567490549),
                        Vector3(33.374041489511, -68.609428999422, 0.02579380013),
                    ),
                    Vector3(0.5, 28.9851779938, 0.4867082834),
                ),
                Triple(
                    83676L,
                    CartesianState(
                        Vector3(-882.014335899964, 6087.2994733832, -0.474495184244),
                        Vector3(-138.954326044361, 53.428453438498, -0.03177678585),
                    ),
                    Vector3(-799.5, 88.985260009766, -499.513488769531),
                ),
                Triple(
                    58059L,
                    CartesianState(
                        Vector3(-4076.2829757539885, -10613.496927447282, 0.2532222107),
                        Vector3(31.928658983561782, -68.32138924666864, -0.014495492),
                    ),
                    Vector3(0.5000012517, 28.9851779938, 0.4867082834),
                ),
            )
        for ((epoch, initial, home) in observations) {
            val model = CelestialDynamics(profile, epoch)
            val departure =
                RocketReturn.departure(
                    model.earth,
                    initial,
                    home,
                    55.8,
                    Vector3(-1.0, 0.0, 0.0),
                    RocketReturn.searchHorizon(model, initial),
                    RocketReturn.earthForces(model),
                    surfaceDrag = 0.09,
                    entryHeight = 2064.0,
                )
            assertTrue(departure.delay >= RocketReturn.TURN_LEAD)
            val start = departure.path.at(0.0).state
            val approach = Vector3(start.velocity.x, 0.0, start.velocity.z).normalized()
            assertTrue((home - start.position).dot(approach) > 20.0, "Burn starts after passing the pad at epoch $epoch")
            assertTrue(
                (
                    departure.path
                        .at(departure.path.duration)
                        .state.position - home - Vector3(0.0, 0.6, 0.0)
                ).length() < 1e-6,
            )
        }
    }
}
