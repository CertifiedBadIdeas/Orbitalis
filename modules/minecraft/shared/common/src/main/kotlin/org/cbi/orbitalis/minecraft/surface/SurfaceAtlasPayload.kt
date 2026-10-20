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

package org.cbi.orbitalis.minecraft.surface

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import java.util.UUID

/** Clientbound presentation only. Session resets prevent data crossing multiplayer/save boundaries. */
data class SurfaceAtlasPayload(
    val session: UUID,
    val reset: Boolean,
    val revision: Long,
    val samples: List<SurfaceAtlas.Sample>,
) : CustomPacketPayload {
    init {
        require(revision >= 0 && samples.size <= 256)
    }

    override fun type() = TYPE

    companion object {
        const val VERSION = 2
        val TYPE = CustomPacketPayload.Type<SurfaceAtlasPayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "surface_atlas"))
        val CODEC: StreamCodec<FriendlyByteBuf, SurfaceAtlasPayload> =
            object : StreamCodec<FriendlyByteBuf, SurfaceAtlasPayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    payload: SurfaceAtlasPayload,
                ) {
                    buffer.writeVarInt(VERSION)
                    buffer.writeUUID(payload.session)
                    buffer.writeBoolean(payload.reset)
                    buffer.writeLong(payload.revision)
                    buffer.writeVarInt(payload.samples.size)
                    for (sample in payload.samples) {
                        buffer.writeShort(sample.x)
                        buffer.writeShort(sample.z)
                        buffer.writeShort(sample.height)
                        buffer.writeInt(sample.color)
                        buffer.writeByte(sample.waterCoverage)
                    }
                }

                override fun decode(buffer: FriendlyByteBuf): SurfaceAtlasPayload {
                    require(buffer.readVarInt() == VERSION) { "Unsupported surface atlas payload version" }
                    val session = buffer.readUUID()
                    val reset = buffer.readBoolean()
                    val revision = buffer.readLong()
                    val count = buffer.readVarInt()
                    require(count in 0..256)
                    return SurfaceAtlasPayload(
                        session,
                        reset,
                        revision,
                        List(count) {
                            SurfaceAtlas.Sample(
                                buffer.readShort().toInt(),
                                buffer.readShort().toInt(),
                                buffer.readShort().toInt(),
                                buffer.readInt(),
                                buffer.readUnsignedByte().toInt(),
                            )
                        },
                    )
                }
            }
    }
}

/** Installed by client setup only; common/dedicated-server registration loads no client classes. */
object SurfaceAtlasReceiver {
    var accept: (SurfaceAtlasPayload) -> Unit = {}
}
