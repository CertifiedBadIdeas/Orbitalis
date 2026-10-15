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

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class FlightMetrics(
    val steps: Int,
    val durationSeconds: Double,
    val visitedFaces: Set<CubeFace>,
    val positionReturnErrorMetres: Double,
    val velocityReturnErrorMetresPerSecond: Double,
    val minimumAltitudeMetres: Double,
    val maximumAltitudeMetres: Double,
    val maximumRelativeEnergyError: Double,
    val maximumChartPositionErrorMetres: Double,
    val maximumChartVelocityErrorMetresPerSecond: Double,
)

/** Idealized ballistic ellipse touching a smooth, nonrotating, airless sphere at periapsis. */
internal fun runReferenceFlight(
    steps: Int = 12_000,
    sample: (Double, CartesianState, SurfaceState) -> Unit = { _, _, _ -> },
): FlightMetrics {
    require(steps > 0)
    val planet = CubeSphere(10_000.0)
    val gravity = TwoBodyGravity(9.81 * planet.radiusMetres * planet.radiusMetres)
    val apoapsis = 14_000.0
    val semiMajorAxis = (planet.radiusMetres + apoapsis) * 0.5
    val period = 2 * PI * sqrt(semiMajorAxis * semiMajorAxis * semiMajorAxis / gravity.mu)
    val direction = Vector3(1.0, -1.0, 0.0).normalized()
    val tangent = Vector3(1.0, 1.0, 1.0).normalized().cross(direction).normalized()
    val speed = sqrt(gravity.mu * (2 / planet.radiusMetres - 1 / semiMajorAxis))
    val initial = CartesianState(direction * planet.radiusMetres, tangent * speed)
    var state = initial
    val initialEnergy = gravity.specificEnergy(initial)
    val faces = linkedSetOf<CubeFace>()
    var minimumAltitude = Double.POSITIVE_INFINITY
    var maximumAltitude = Double.NEGATIVE_INFINITY
    var energyError = 0.0
    var positionError = 0.0
    var velocityError = 0.0
    for (index in 0..steps) {
        val surface = planet.fromCartesian(state)
        val restored = planet.toCartesian(surface.point, surface.velocity)
        faces.add(surface.point.face)
        minimumAltitude = min(minimumAltitude, surface.point.altitudeMetres)
        maximumAltitude = max(maximumAltitude, surface.point.altitudeMetres)
        energyError = max(energyError, abs((gravity.specificEnergy(state) - initialEnergy) / initialEnergy))
        positionError = max(positionError, (restored.position - state.position).length())
        velocityError = max(velocityError, (restored.velocity - state.velocity).length())
        sample(index * (period / steps), state, surface)
        if (index < steps) state = gravity.step(state, period / steps)
    }
    return FlightMetrics(
        steps,
        period,
        faces,
        (state.position - initial.position).length(),
        (state.velocity - initial.velocity).length(),
        minimumAltitude,
        maximumAltitude,
        energyError,
        positionError,
        velocityError,
    )
}

/** Standalone experiment entry point; test-source executable excluded from the distributable. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Supply an output directory" }
    val output = Path.of(args.single())
    Files.createDirectories(output)
    val report =
        Files.newBufferedWriter(output.resolve("trajectory.csv")).use { writer ->
            writer.appendLine(
                "time_seconds,x_metres,y_metres,z_metres,vx_metres_per_second,vy_metres_per_second,vz_metres_per_second,face,u,v,altitude_metres",
            )
            runReferenceFlight { time, state, surface ->
                val p = state.position
                val v = state.velocity
                writer.appendLine(
                    String.format(
                        Locale.ROOT,
                        "%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%.12g,%.12g,%.12g",
                        time,
                        p.x,
                        p.y,
                        p.z,
                        v.x,
                        v.y,
                        v.z,
                        surface.point.face,
                        surface.point.u,
                        surface.point.v,
                        surface.point.altitudeMetres,
                    ),
                )
            }
        }
    val summary =
        """
        # Finite planet reference flight

        Mathematical experiment only. No Minecraft world, Sable construction, launch control,
        terrain, atmosphere, planet rotation, collision handling or seamless rendering is simulated.
        Radius: 10000 m. Gravitational parameter: 981000000 m^3/s^2. Target apoapsis altitude: 4000 m.
        Velocity Verlet, ${report.steps} equal steps over ${report.durationSeconds} seconds.

        - Faces visited: ${report.visitedFaces.joinToString()}
        - Return position error: ${report.positionReturnErrorMetres} m
        - Return velocity error: ${report.velocityReturnErrorMetresPerSecond} m/s
        - Minimum altitude: ${report.minimumAltitudeMetres} m
        - Maximum altitude: ${report.maximumAltitudeMetres} m
        - Maximum relative specific-energy error: ${report.maximumRelativeEnergyError}
        - Maximum chart round-trip position error: ${report.maximumChartPositionErrorMetres} m
        - Maximum chart round-trip velocity error: ${report.maximumChartVelocityErrorMetresPerSecond} m/s

        Position and velocity stay in one planet-centred inertial frame. Surface coordinates are
        observations of that state, not another integrator. The orbit starts with an idealized
        instantaneous tangential velocity at surface periapsis; this is not a demonstrated ascent.
        """.trimIndent() + "\n"
    Files.writeString(output.resolve("report.md"), summary)
    println(summary)
    println("Trajectory: ${output.resolve("trajectory.csv")}")
}
