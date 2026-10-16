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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GliderFlightTest {
    @Test
    fun liftSlowsDescentAndNoseUpTradesSpeedForHeight() {
        val velocity = Vector3(0.0, -0.1, 0.6)
        val gravity = Vector3(0.0, -9.81 / 400, 0.0)
        val level = GliderFlight.step(velocity, 0.0, 0.0, gravity)
        val climb = GliderFlight.step(velocity, 0.0, -30.0, gravity)
        assertTrue(level.y > velocity.y + gravity.y && level.z > 0.0)
        assertTrue(climb.y > level.y && climb.z < level.z)
        val turn = GliderFlight.step(velocity, 90.0, 0.0, gravity)
        assertTrue(turn.x < 0.0 && turn.z > 0.0) // Steering changes velocity progressively.
        val coast = GliderFlight.step(Vector3(0.0, 0.0, 0.6), 0.0, 0.0, Vector3.ZERO)
        assertTrue(coast.y == 0.0 && coast.z in 0.0..0.6)
    }

    @Test
    fun steeringCannotCreateKineticEnergyWithoutGravityOrRockets() {
        for (vertical in listOf(-2.0, -0.4, 0.0, 0.4, 1.0, 2.0)) {
            for (pitch in listOf(-80.0, -45.0, -10.0, 0.0, 45.0)) {
                val velocity = Vector3(0.0, vertical, 1.0)
                val next = GliderFlight.step(velocity, 0.0, pitch, Vector3.ZERO)
                assertTrue(next.dot(next) <= velocity.dot(velocity), "Steering created energy at vertical=$vertical pitch=$pitch")
            }
        }
    }

    @Test
    fun turningGravityCannotPumpABoundGlideIntoEscape() {
        val field = CubicGravity(16.0, 9.81, 0.0, 4.0, 20.0)
        var position = Vector3(60.0, 0.0, 0.0)
        var velocity = Vector3(0.0, 0.35, 0.0)
        val initialEnergy = velocity.dot(velocity) / 2 + field.sample(position).potential / 400
        assertTrue(initialEnergy < 0.0)
        repeat(500) {
            val force = field.acceleration(position) * (1.0 / 400)
            val up = force.normalized() * -1.0
            val right = Vector3(up.y, -up.x, 0.0)
            val local = Vector3(velocity.dot(right), velocity.dot(up), velocity.z)
            val next = GliderFlight.step(local, 90.0, -80.0, Vector3(0.0, -force.length(), 0.0))
            velocity = right * next.x + up * next.y + Vector3(0.0, 0.0, next.z)
            position += velocity
            val energy = velocity.dot(velocity) / 2 + field.sample(position).potential / 400
            assertTrue(energy <= initialEnergy + 1e-6, "Changing gravity created escape energy at step $it: $energy")
        }
        assertTrue(position.length() < 60.0, "An unpowered bound glide escaped")
    }

    @Test
    fun verticalAndInvalidViewsAreBounded() {
        for (pitch in listOf(-90.0, 90.0)) {
            val step = GliderFlight.step(Vector3(0.0, -1.0, 0.0), 0.0, pitch, Vector3(0.0, -0.02, 0.0))
            assertTrue(step.length() < 2.0)
        }
        assertFailsWith<IllegalArgumentException> { GliderFlight.step(Vector3.ZERO, 0.0, 91.0, Vector3.ZERO) }
    }
}
