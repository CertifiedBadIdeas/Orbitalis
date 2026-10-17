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

package org.cbi.orbitalis.minecraft.client.ship

import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3

/** Optional client-only seam: mixins never reference Sable or load its adapter themselves. */
object ShipViewHooks {
    interface Controller {
        fun setup(
            camera: Camera,
            partialTick: Float,
            fieldOfView: Double,
        )

        fun turn(
            x: Double,
            y: Double,
        ): Boolean
    }

    var controller: Controller? = null

    @JvmStatic fun setup(
        camera: Camera,
        partialTick: Float,
        fieldOfView: Double,
    ) {
        controller?.setup(camera, partialTick, fieldOfView)
    }

    @JvmStatic fun turn(
        x: Double,
        y: Double,
    ): Boolean = controller?.turn(x, y) ?: false
}

interface ShipCameraAccess {
    fun orbitalisSetView(
        position: Vec3,
        yaw: Float,
        pitch: Float,
    )
}
