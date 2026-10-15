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

package org.cbi.orbitalis.core.planet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlanetGeometryTest {
    private val planet = CubeSphere(10_000.0)

    @Test
    fun `face centres lie on their corresponding axes`() {
        for (face in CubeFace.entries) {
            assertVector(face.normal * planet.radiusMetres, planet.toCartesian(SurfacePoint(face, 0.0, 0.0, 0.0)).position)
        }
    }

    @Test
    fun `positions and physical velocities round trip across all faces and corners`() {
        val coordinates = listOf(-1.0, -0.75, 0.0, 0.75, 1.0)
        for (face in CubeFace.entries) {
            for (u in coordinates) {
                for (v in coordinates) {
                    for (altitude in listOf(-50.0, 0.0, 2_000.0)) {
                        val state = planet.toCartesian(SurfacePoint(face, u, v, altitude), ChartVelocity(0.002, -0.003, 12.0))
                        val recovered = planet.fromCartesian(state)
                        val restored = planet.toCartesian(recovered.point, recovered.velocity)
                        assertVector(state.position, restored.position, 1e-8)
                        assertVector(state.velocity, restored.velocity, 1e-9)
                    }
                }
            }
        }
    }

    @Test
    fun `adjacent chart edges describe the same place`() {
        for (v in listOf(-1.0, -0.5, 0.0, 0.5, 1.0)) {
            val a = planet.toCartesian(SurfacePoint(CubeFace.POSITIVE_Z, 1.0, v, 100.0))
            val b = planet.toCartesian(SurfacePoint(CubeFace.POSITIVE_X, -1.0, v, 100.0))
            assertVector(a.position, b.position)
        }
    }

    @Test
    fun `crossing a face boundary does not jump in space or velocity`() {
        val states =
            listOf(-1e-8, 0.0, 1e-8).map { epsilon ->
                val direction = Vector3(1.0 + epsilon, 0.2, 1.0).normalized()
                CartesianState(direction * 12_000.0, Vector3(15.0, -7.0, 31.0))
            }
        val charts = states.map(planet::fromCartesian)
        assertEquals(CubeFace.POSITIVE_Z, charts.first().point.face)
        assertEquals(CubeFace.POSITIVE_X, charts.last().point.face)
        for ((state, chart) in states.zip(charts)) {
            assertVector(state.position, planet.toCartesian(chart.point, chart.velocity).position, 1e-8)
            assertVector(state.velocity, planet.toCartesian(chart.point, chart.velocity).velocity, 1e-9)
        }
        assertTrue((states.last().position - states.first().position).length() < 0.001)
    }

    @Test
    fun `chart rates match the derivative of the position mapping`() {
        val point = SurfacePoint(CubeFace.NEGATIVE_Y, 0.3, -0.4, 500.0)
        val rate = ChartVelocity(0.02, -0.01, 7.0)
        val step = 1e-5

        fun offset(sign: Double) =
            SurfacePoint(
                point.face,
                point.u + sign * rate.uPerSecond * step,
                point.v + sign * rate.vPerSecond * step,
                point.altitudeMetres + sign * rate.radialMetresPerSecond * step,
            )
        val derivative = (planet.toCartesian(offset(1.0)).position - planet.toCartesian(offset(-1.0)).position) * (0.5 / step)
        assertVector(derivative, planet.toCartesian(point, rate).velocity, 1e-6)
    }

    @Test
    fun `face bases are right handed and orthonormal`() {
        for (face in CubeFace.entries) {
            assertVector(face.normal, face.uAxis.cross(face.vAxis))
            assertEquals(0.0, face.normal.dot(face.uAxis))
            assertEquals(0.0, face.normal.dot(face.vAxis))
        }
    }

    @Test
    fun `nonfinite coordinates invalid charts and the planet centre are rejected`() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Vector3(bad, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { CubeSphere(bad) }
            assertFailsWith<IllegalArgumentException> { SurfacePoint(CubeFace.POSITIVE_X, 0.0, 0.0, bad) }
            assertFailsWith<IllegalArgumentException> { ChartVelocity(0.0, bad, 0.0) }
        }
        assertFailsWith<IllegalArgumentException> { CubeSphere(0.0) }
        assertFailsWith<IllegalArgumentException> { SurfacePoint(CubeFace.POSITIVE_X, 1.001, 0.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { planet.fromCartesian(CartesianState(Vector3.ZERO, Vector3.ZERO)) }
        assertFailsWith<IllegalArgumentException> { planet.toCartesian(SurfacePoint(CubeFace.POSITIVE_X, 0.0, 0.0, -10_000.0)) }
    }

    @Test
    fun `central acceleration points inward and follows inverse square distance`() {
        val gravity = TwoBodyGravity(1024.0)
        assertVector(Vector3(-4.0, 0.0, 0.0), gravity.acceleration(Vector3(16.0, 0.0, 0.0)), 1e-12)
        assertVector(Vector3(0.0, 1.0, 0.0), gravity.acceleration(Vector3(0.0, -32.0, 0.0)), 1e-12)
        assertVector(Vector3(-1.536, -2.048, 0.0), gravity.acceleration(Vector3(12.0, 16.0, 0.0)), 1e-12)
        assertFailsWith<IllegalArgumentException> { gravity.acceleration(Vector3.ZERO) }
    }

    @Test
    fun `a circular orbit conserves energy and angular momentum`() {
        val gravity = TwoBodyGravity(9.81 * planet.radiusMetres * planet.radiusMetres)
        val radius = 12_000.0
        val initial = CartesianState(Vector3(radius, 0.0, 0.0), Vector3(0.0, sqrt(gravity.mu / radius), 0.0))
        val period = 2 * PI * sqrt(radius * radius * radius / gravity.mu)
        var state = initial
        val energy = gravity.specificEnergy(initial)
        val momentum = initial.position.cross(initial.velocity)
        repeat(8_000) {
            state = gravity.step(state, period / 4_000)
            assertTrue(abs((gravity.specificEnergy(state) - energy) / energy) < 1e-10)
            assertVector(momentum, state.position.cross(state.velocity), 1e-6)
        }
        assertVector(initial.position, state.position, 0.2)
    }

    @Test
    fun `a tangential burn changes the orbit`() {
        val gravity = TwoBodyGravity(9.81e8)
        val radius = 12_000.0
        val circular = CartesianState(Vector3(radius, 0.0, 0.0), Vector3(0.0, sqrt(gravity.mu / radius), 0.0))
        var boosted = circular.copy(velocity = circular.velocity + Vector3(0.0, 10.0, 0.0))
        assertTrue(gravity.specificEnergy(boosted) > gravity.specificEnergy(circular))
        repeat(2_000) { boosted = gravity.step(boosted, 0.05) }
        assertTrue(boosted.position.length() > radius + 500.0)
    }

    @Test
    fun `surface orbit and return visits every face without losing physical state`() {
        val result = runReferenceFlight()
        assertEquals(CubeFace.entries.toSet(), result.visitedFaces)
        assertTrue(result.positionReturnErrorMetres < 0.02, result.toString())
        assertTrue(result.velocityReturnErrorMetresPerSecond < 0.001, result.toString())
        assertTrue(abs(result.maximumAltitudeMetres - 4_000.0) < 0.02, result.toString())
        assertTrue(result.minimumAltitudeMetres > -0.02, result.toString())
        assertTrue(result.maximumRelativeEnergyError < 1e-6, result.toString())
        assertTrue(result.maximumChartPositionErrorMetres < 1e-8, result.toString())
        assertTrue(result.maximumChartVelocityErrorMetresPerSecond < 1e-9, result.toString())
    }

    @Test
    fun `halving the timestep reduces flight integration errors`() {
        val coarse = runReferenceFlight(steps = 6_000)
        val fine = runReferenceFlight(steps = 12_000)
        assertTrue(fine.positionReturnErrorMetres < coarse.positionReturnErrorMetres * 0.3)
        assertTrue(fine.maximumRelativeEnergyError < coarse.maximumRelativeEnergyError * 0.3)
    }

    @Test
    fun `gravity rejects singular states invalid parameters and timesteps`() {
        assertFailsWith<IllegalArgumentException> { TwoBodyGravity(0.0) }
        assertFailsWith<IllegalArgumentException> { TwoBodyGravity(Double.NaN) }
        val gravity = TwoBodyGravity(1.0)
        val state = CartesianState(Vector3(1.0, 0.0, 0.0), Vector3.ZERO)
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { gravity.step(state, bad) }
        }
        assertFailsWith<IllegalArgumentException> { gravity.step(state.copy(position = Vector3.ZERO), 1.0) }
    }
}

internal fun assertVector(
    expected: Vector3,
    actual: Vector3,
    tolerance: Double = 1e-9,
) {
    assertTrue((expected - actual).length() <= tolerance, "Expected $expected, got $actual; tolerance $tolerance")
}
