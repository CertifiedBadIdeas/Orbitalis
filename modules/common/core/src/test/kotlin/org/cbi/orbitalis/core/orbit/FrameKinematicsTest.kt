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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FrameKinematicsTest {
    private val x = Vector3(1.0, 0.0, 0.0)
    private val y = Vector3(0.0, 1.0, 0.0)
    private val z = Vector3(0.0, 0.0, 1.0)
    private val zero = CartesianState(Vector3.ZERO, Vector3.ZERO)

    private fun near(
        expected: Vector3,
        actual: Vector3,
        tolerance: Double = 1e-9,
    ) {
        assertTrue((expected - actual).length() < tolerance, "Expected $expected, got $actual")
    }

    @Test fun `rotation is right handed orthonormal and composes without changing handedness`() {
        val quarter = Rotation3.axisAngle(x, PI / 2.0)
        near(z, quarter.apply(y))
        near(y * -1.0, quarter.apply(z))
        val rotation = Rotation3.axisAngle(Vector3(3.0, 2.0, -5.0), 2.3) * quarter
        val vector = Vector3(123.0, -70.0, 4.0)
        near(vector, rotation.inverse(rotation.apply(vector)))
        near(rotation.zAxis, rotation.xAxis.cross(rotation.yAxis))
        assertTrue(abs(rotation.apply(vector).length() - vector.length()) < 1e-10)
        assertFailsWith<IllegalArgumentException> { Rotation3.axisAngle(Vector3.ZERO, 0.0) }
        assertFailsWith<IllegalArgumentException> { Rotation3.axisAngle(x, Double.NaN) }
    }

    @Test fun `stationary surface observer inherits translational and rotational velocity`() {
        val frame =
            FrameKinematics(
                CartesianState(Vector3(100.0, 200.0, 300.0), Vector3(5.0, -2.0, 1.0)),
                Rotation3.axisAngle(x, PI / 2.0),
                x * 0.1,
            )
        val surface = CartesianState(Vector3(0.0, 64.0, 20.0), Vector3.ZERO)
        val parent = frame.toParent(surface)
        near(Vector3(100.0, 180.0, 364.0), parent.position)
        near(Vector3(5.0, -8.4, -1.0), parent.velocity)
        val local = frame.fromParent(parent)
        near(surface.position, local.position)
        near(Vector3.ZERO, local.velocity)
    }

    @Test fun `frame forces contain Coriolis centripetal Euler and translating origin terms`() {
        val frame = FrameKinematics(zero, angularVelocity = z * 2.0, originAcceleration = z * 5.0, angularAcceleration = z * 3.0)
        val local = CartesianState(x * 10.0, y * 4.0)
        // origin (0,0,5), Coriolis (-16,0,0), Euler (0,30,0), centripetal (-40,0,0).
        near(Vector3(-56.0, 30.0, 5.0), frame.toParentAcceleration(local, Vector3.ZERO))
        near(Vector3(56.0, -30.0, -5.0), frame.fromParentAcceleration(frame.toParent(local), Vector3.ZERO))
    }

    @Test fun `arbitrary state acceleration and angular motion round trip across rotated moving frames`() {
        val frame =
            FrameKinematics(
                CartesianState(Vector3(1e7, -2e7, 3e7), Vector3(120.0, -80.0, 2.0)),
                Rotation3.axisAngle(Vector3(3.0, -1.0, 2.0), 1.2),
                Vector3(0.002, 0.003, -0.001),
                Vector3(0.1, -0.3, 0.4),
                Vector3(0.0001, 0.0003, -0.0002),
            )
        val local = CartesianState(Vector3(2500.0, 400.0, -1700.0), Vector3(15.0, 30.0, -2.0))
        val acceleration = Vector3(0.0, -11.0, 0.0)
        val parent = frame.toParent(local)
        near(local.position, frame.fromParent(parent).position, 1e-8)
        near(local.velocity, frame.fromParent(parent).velocity)
        near(acceleration, frame.fromParentAcceleration(parent, frame.toParentAcceleration(local, acceleration)))
        near(x * 0.2, frame.fromParentAngularVelocity(frame.toParentAngularVelocity(x * 0.2)))
    }

    @Test fun `transport agrees with independent position differentiation during accelerated spin`() {
        val local = CartesianState(Vector3(2.0, -3.0, 4.0), Vector3(0.4, -0.5, 0.6))
        val localAcceleration = Vector3(0.3, -0.2, 0.1)
        val axis = Vector3(1.0, 2.0, 3.0).normalized()
        val originVelocity = Vector3(4.0, -2.0, 1.0)
        val originAcceleration = Vector3(-0.1, 0.2, 0.3)
        val omega = 0.7
        val alpha = 0.2
        val frame =
            FrameKinematics(
                zero.copy(velocity = originVelocity),
                Rotation3.axisAngle(axis, 0.4),
                axis * omega,
                originAcceleration,
                axis * alpha,
            )

        fun position(t: Double): Vector3 {
            val origin = originVelocity * t + originAcceleration * (t * t * 0.5)
            val point = local.position + local.velocity * t + localAcceleration * (t * t * 0.5)
            return origin + Rotation3.axisAngle(axis, 0.4 + omega * t + alpha * t * t * 0.5).apply(point)
        }
        val dt = 1e-4
        near(frame.toParent(local).velocity, (position(dt) - position(-dt)) * (0.5 / dt), 1e-7)
        near(
            frame.toParentAcceleration(local, localAcceleration),
            (position(dt) - position(0.0) * 2.0 + position(-dt)) * (1.0 / (dt * dt)),
            1e-6,
        )
    }

    @Test fun `zero spin reproduces the established native space mapping`() {
        val frame = FrameKinematics(CartesianState(FlatPlanetFrame.origin, Vector3.ZERO))
        val native = CartesianState(Vector3(100.0, 9000.0, -200.0), Vector3(3.0, -4.0, 5.0))
        near(FlatPlanetFrame.toSurface(native.position), frame.toParent(native).position)
        near(native.velocity, frame.toParent(native).velocity)
    }
}
