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

package org.cbi.orbitalis.neoforge

import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.neoforge.integration.SableLabIntegration

/** Loader entry point. Gameplay belongs in shared/common or the independent core. */
@Mod(Orbitalis.MOD_ID)
class OrbitalisMod {
    init {
        if (ModList.get().isLoaded("sable")) SableLabIntegration.install()
    }
}
