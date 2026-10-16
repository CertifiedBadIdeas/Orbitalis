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

package org.cbi.orbitalis.minecraft.lab

import net.minecraft.core.Direction
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/** Chart changes preserve the heading transported by the shortest turn between adjacent normals. */
object CubePlayerOrientation {
    fun yawQuarterTurns(
        from: Direction,
        to: Direction,
    ): Int {
        if (from.axis == to.axis) return 0 // Opposite faces are a teleport, not an edge crossing.
        return (yawShift(CubePlayerPhysics.rotation(from), CubePlayerPhysics.rotation(to)) / (Math.PI / 2)).roundToInt()
    }

    fun yawShift(
        from: Quaternionf,
        to: Quaternionf,
    ): Double {
        val oldUp = from.transform(Vector3f(0f, 1f, 0f)).normalize()
        val newUp = to.transform(Vector3f(0f, 1f, 0f)).normalize()
        val turn =
            if (oldUp.dot(newUp) < -0.999999f) {
                Quaternionf().rotationAxis(Math.PI.toFloat(), from.transform(Vector3f(1f, 0f, 0f)).normalize())
            } else {
                Quaternionf().rotationTo(oldUp, newUp)
            }
        val forward = turn.transform(from.transform(Vector3f(0f, 0f, 1f)))
        Quaternionf(to).conjugate().transform(forward)
        return atan2(-forward.x.toDouble(), forward.z.toDouble())
    }

    /** Undo the local yaw rebase before easing, keeping the rendered world heading continuous. */
    fun ease(
        visual: Quaternionf,
        target: Quaternionf,
        rebaseRadians: Double,
        elapsed: Double,
    ): Quaternionf {
        visual.rotateY(rebaseRadians.toFloat())
        val angle = 2.0 * acos(abs(visual.dot(target)).coerceIn(0f, 1f).toDouble())
        if (angle > 1e-6) visual.slerp(target, min(1.0, Math.PI * elapsed.coerceIn(0.0, 0.1) / angle).toFloat())
        return visual
    }
}
