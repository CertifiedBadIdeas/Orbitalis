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

import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RocketGimbalAllocationTest {
    private val engines =
        listOf(-2.0, 2.0).flatMap { x ->
            listOf(-2.0, 2.0).map { z -> RocketGimbalAllocation.Engine(Vector3(x, -7.0, z), 100.0) }
        }

    @Test fun hoverAndDifferentialPitchUseBottomEnginesWithinTheirAuthority() {
        for (torque in listOf(Vector3.ZERO, Vector3(20.0, 0.0, 0.0), Vector3(0.0, 0.0, -20.0))) {
            val result = RocketGimbalAllocation.solve(engines, Vector3(0.0, 200.0, 0.0), torque)
            assertTrue(result.forceResidual.length() < 0.05, "Missing force: ${result.forceResidual}")
            assertTrue(result.torqueResidual.length() < 0.1, "Missing torque: ${result.torqueResidual}")
            checkLimits(result)
        }
    }

    @Test fun opposingGimbalsCanRollWithoutInventingLateralForce() {
        val result = RocketGimbalAllocation.solve(engines, Vector3(0.0, 200.0, 0.0), Vector3(0.0, 40.0, 0.0))
        assertTrue(result.forceResidual.length() < 0.05)
        assertTrue(result.torqueResidual.length() < 0.1)
        assertTrue(result.inputs.any { abs(it.vectorX) + abs(it.vectorY) > 0.05 })
        checkLimits(result)
    }

    @Test fun reverseThrustAndImpossibleSidewaysDemandRemainUnachievable() {
        val reverse = RocketGimbalAllocation.solve(engines, Vector3(0.0, -200.0, 0.0), Vector3.ZERO)
        assertTrue(reverse.inputs.all { it.throttle < 1e-8 })
        assertTrue(reverse.forceResidual.length() >= 199.99)
        val sideways = RocketGimbalAllocation.solve(engines, Vector3(1000.0, 0.0, 0.0), Vector3.ZERO)
        assertTrue(sideways.forceResidual.length() > 500.0)
        checkLimits(sideways)
    }

    @Test fun sharedConeBudgetNeverMultipliesAnEnginesMaximumThrust() {
        val result = RocketGimbalAllocation.solve(engines, Vector3(300.0, 1000.0, 300.0), Vector3(500.0, 500.0, 500.0))
        checkLimits(result)
        assertTrue(result.inputs.sumOf { it.force.length() } <= 400.00001)
    }

    @Test fun fixedRcsCouplesProduceAttitudeTorqueWithoutMainThrust() {
        val pods =
            listOf(
                Pair(Vector3(0.0, 4.0, -3.0), Vector3(0.0, 0.0, 1.0)),
                Pair(Vector3(0.0, -2.0, 3.0), Vector3(0.0, 0.0, -1.0)),
                Pair(Vector3(0.0, 4.0, 3.0), Vector3(0.0, 0.0, -1.0)),
                Pair(Vector3(0.0, -2.0, -3.0), Vector3(0.0, 0.0, 1.0)),
                Pair(Vector3(-3.0, 4.0, 0.0), Vector3(1.0, 0.0, 0.0)),
                Pair(Vector3(3.0, -2.0, 0.0), Vector3(-1.0, 0.0, 0.0)),
                Pair(Vector3(3.0, 4.0, 0.0), Vector3(-1.0, 0.0, 0.0)),
                Pair(Vector3(-3.0, -2.0, 0.0), Vector3(1.0, 0.0, 0.0)),
                Pair(Vector3(3.0, 2.0, 1.0), Vector3(0.0, 0.0, -1.0)),
                Pair(Vector3(-3.0, 2.0, -1.0), Vector3(0.0, 0.0, 1.0)),
                Pair(Vector3(3.0, 2.0, -1.0), Vector3(0.0, 0.0, 1.0)),
                Pair(Vector3(-3.0, 2.0, 1.0), Vector3(0.0, 0.0, -1.0)),
            ).map { (arm, direction) -> RocketGimbalAllocation.Engine(arm, 20.0, direction) }
        val result = RocketGimbalAllocation.solve(pods, Vector3.ZERO, Vector3(10.0, 15.0, -10.0))
        assertTrue(result.forceResidual.length() < 0.02)
        assertTrue(result.torqueResidual.length() < 0.05)
        assertTrue(result.inputs.all { it.throttle in 0.0..1.0 })
    }

    private fun checkLimits(result: RocketGimbalAllocation.Result) {
        for (input in result.inputs) {
            assertTrue(input.throttle in 0.0..1.0 && input.vectorX in -1.0..1.0 && input.vectorY in -1.0..1.0)
            assertTrue(input.force.y >= -1e-10 && input.force.length() <= 100.000001)
            assertTrue(abs(input.force.x) <= input.force.y * 0.57735027 + 1e-7)
            assertTrue(abs(input.force.z) <= input.force.y * 0.57735027 + 1e-7)
        }
    }
}
