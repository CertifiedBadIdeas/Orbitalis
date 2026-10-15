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

/** Disposable stand guidance: requests accelerations; never integrates or applies forces. */
final class FlightHoldGuidance {
    record Demand(Vector3 thrustAcceleration, Vector3 angularAcceleration) {}

    static Demand command(CartesianState state, Vector3 target, Vector3 gravity,
                          Vector3 attitudeError, Vector3 angularVelocity) {
        var velocity = limited(target.minus(state.getPosition()).times(0.3), 12.0);
        return new Demand(
            limited(velocity.minus(state.getVelocity()).times(0.8), 4.0).minus(gravity),
            limited(attitudeError.minus(angularVelocity.times(1.6)), 0.8));
    }

    private static Vector3 limited(Vector3 value, double maximum) {
        double length = value.length();
        return length > maximum ? value.times(maximum / length) : value;
    }
}
