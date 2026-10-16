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

package org.cbi.orbitalis.consumer.flight;

import org.cbi.orbitalis.core.planet.CartesianState;
import org.cbi.orbitalis.core.planet.Vector3;

/** Consumer experiment: inertial acceleration requests, with no world or engine ownership. */
final class OrbitalGuidance {
    static final double RADIUS = 18000;
    static final double MAX_ACCELERATION = 3;

    record Orbit(Vector3 radial, Vector3 tangent, double radius, double radialSpeed,
                 double tangentialSpeed, double planeError) {}

    static Vector3 limited(Vector3 value, double maximum) {
        double length = value.length();
        return length > maximum ? value.times(maximum / length) : value;
    }

    static Orbit observe(CartesianState state, Vector3 centre, Vector3 normal) {
        Vector3 relative = state.getPosition().minus(centre);
        double plane = relative.dot(normal);
        Vector3 inPlane = relative.minus(normal.times(plane));
        double radius = inPlane.length();
        if (!Double.isFinite(radius) || radius < 100) throw new IllegalArgumentException("Outside orbital guidance envelope");
        Vector3 radial = inPlane.times(1 / radius);
        Vector3 tangent = normal.cross(radial);
        return new Orbit(radial, tangent, radius, state.getVelocity().dot(radial),
            state.getVelocity().dot(tangent), plane);
    }

    static Vector3 point(CartesianState state, Vector3 target, double maximumSpeed) {
        Vector3 error = target.minus(state.getPosition());
        // Conservative stopping distance leaves margin for engine fade and discrete sampling.
        double speed = Math.min(maximumSpeed, Math.sqrt(2 * 1.5 * error.length()));
        Vector3 desired = limited(error.times(0.2), speed);
        return limited(desired.minus(state.getVelocity()), MAX_ACCELERATION);
    }

    static Vector3 orbit(CartesianState state, Vector3 centre, Vector3 normal,
                         double radius, double desiredTangentialSpeed) {
        Orbit o = observe(state, centre, normal);
        double radial = Math.clamp((radius - o.radius()) * 0.04 - o.radialSpeed() * 0.8, -2, 2);
        double tangent = Math.clamp((desiredTangentialSpeed - o.tangentialSpeed()) * 0.8, -2, 2);
        double plane = Math.clamp(-o.planeError() * 0.04 - state.getVelocity().dot(normal) * 0.8, -2, 2);
        // Geometric feed-forward keeps the path circular even while braking. At circular
        // speed this equals central gravity, so a settled insertion requires no thrust.
        return limited(o.radial().times(radial).plus(o.tangent().times(tangent)).plus(normal.times(plane)),
            MAX_ACCELERATION).plus(o.radial().times(-o.tangentialSpeed() * o.tangentialSpeed() / o.radius()));
    }

    static double returnSpeed(double remainingArc, double circularSpeed) {
        return Math.copySign(Math.min(circularSpeed, Math.min(Math.sqrt(2 * 1.2 * Math.abs(remainingArc)),
            Math.abs(remainingArc) * 0.3)), remainingArc);
    }

    static double angle(Vector3 radial, Vector3 homeRadial, Vector3 normal) {
        return Math.atan2(normal.dot(homeRadial.cross(radial)), homeRadial.dot(radial));
    }

    static double angleStep(double previous, double current) {
        return Math.atan2(Math.sin(current - previous), Math.cos(current - previous));
    }

}
