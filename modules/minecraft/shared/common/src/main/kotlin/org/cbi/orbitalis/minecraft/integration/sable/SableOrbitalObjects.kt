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

package org.cbi.orbitalis.minecraft.integration.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.SubLevelHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.core.orbit.OrbitalEvolution
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData.Record
import org.cbi.orbitalis.minecraft.space.SpaceWorlds
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.math.abs

/** Server-thread ownership coordinator; native storage owns every block, entity and plot address. */
object SableOrbitalObjects {
    private val wakeTicket =
        SubLevelLoadingTicketType.create(
            net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("orbitalis", "orbital_wake"),
            com.mojang.serialization.Codec
                .unit(Unit.INSTANCE),
        )
    private val services = IdentityHashMap<ServerLevel, Service>()
    var mayAdvance: (MinecraftServer) -> Boolean = { true }

    private fun service(level: ServerLevel): Service? {
        if (!SpaceWorlds.objects(level)) return null
        return services.getOrPut(level) { Service(level) }.also {
            if (level.dimension() != FlatPlanetSpace.SPACE) {
                org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation.data(level.server)?.let { clock ->
                    it.data.synchronizeClock(clock.clock)
                }
            }
        }
    }

    fun data(level: ServerLevel): OrbitalObjectData = checkNotNull(service(level)).data

    /** Called only by native save paths, never by transfer snapshot serialization. */
    fun prepareNativeSave(body: ServerSubLevel) {
        val service = service(body.level) ?: return
        if (!mayAdvance(body.level.server) || !service.data.readable) return
        if (SubLevelHelper.getLoadingDependencyChain(body).size != 1) {
            optOut(body, service.data)
            return
        }
        if (body.uniqueId !in service.data.records && service.data.records.size >= OrbitalObjectData.MAX_OBJECTS) return
        val mass = body.massTracker
        if (mass.mass <= 0.0 || mass.centerOfMass == null) return
        val native = SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body)
        val position = body.logicalPose().transformPosition(mass.centerOfMass, Vector3d())
        val velocity = native.getLinearVelocity(Vector3d())
        val angularVelocity = native.getAngularVelocity(Vector3d())
        val bounds = body.boundingBox()
        val radius = Vector3d(bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ()).length() * 0.5
        if (!position.isFinite || !velocity.isFinite || !angularVelocity.isFinite ||
            position.length() > 1e8 || velocity.length() > 10000.0 || angularVelocity.length() > 100.0 || radius > 2048.0
        ) {
            optOut(body, service.data)
            return
        }
        val user = body.userDataTag?.copy() ?: net.minecraft.nbt.CompoundTag()
        if (user.contains(OrbitalObjectData.KEY)) {
            try {
                OrbitalObjectData.read(user.getCompound(OrbitalObjectData.KEY))
            } catch (_: IllegalArgumentException) {
                return
            } // Preserve unknown/future payloads.
        }
        val record =
            Record(
                body.uniqueId,
                UUID.randomUUID(),
                service.data.clock,
                service.data.clock,
                CelestialFlight
                    .sample(
                        body.level.server,
                    ).rootState(CartesianState(vector(position), vector(velocity)), body.level.dimension()),
                radius,
                body.lastSerializationPointer?.let(::pointer),
            )
        val tag = OrbitalObjectData.write(record)
        tag.put("Angular", SableNBTUtils.writeVector3d(angularVelocity))
        user.put(OrbitalObjectData.KEY, tag)
        body.userDataTag = user
        service.data.put(record)
    }

    /** Native dependency chains are outside this first independent ballistic-body contract. */
    fun prepareNativeUnload(body: ServerSubLevel) {
        SubLevelHelper.getLoadingDependencyChain(body).forEach(::prepareNativeSave)
    }

    private fun optOut(
        body: ServerSubLevel,
        data: OrbitalObjectData,
    ) {
        val user = body.userDataTag ?: return
        if (!user.contains(OrbitalObjectData.KEY)) return
        val owned = runCatching { OrbitalObjectData.read(user.getCompound(OrbitalObjectData.KEY)) }.getOrNull()
        if (owned?.id != body.uniqueId) return
        body.userDataTag = user.copy().also { it.remove(OrbitalObjectData.KEY) }
        data.remove(body.uniqueId)
    }

    /** Sable invokes saveAll immediately before vanilla saveLevelData: persist its current pointers. */
    fun checkpoint(level: ServerLevel) {
        val service = service(level) ?: return
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        for (record in service.data.records.values
            .toList()) {
            val ref =
                service.holdingRefs[record.id]?.pointer()
                    ?: container.holdingChunkMap.getHoldingSubLevel(record.id)?.pointer()
                    ?: (container.getSubLevel(record.id) as? ServerSubLevel)?.lastSerializationPointer
            if (ref != null) {
                if (pointer(ref) != record.pointer) service.data.put(record.copy(pointer = pointer(ref)))
                service.holdingRefs.remove(record.id)
            }
        }
    }

    /** Returns false to retain native holding ownership while a long catch-up is still pending. */
    fun beforeNativeLoad(
        level: ServerLevel,
        holding: HoldingSubLevel,
    ): Boolean {
        val service = service(level) ?: return true
        val user = holding.data().fullTag().getCompound("user_data")
        if (!user.contains(OrbitalObjectData.KEY)) return true // Existing native saves migrate on their next save.
        if (!service.data.readable) return true
        val saved =
            try {
                OrbitalObjectData.read(user.getCompound(OrbitalObjectData.KEY))
            } catch (
                _: IllegalArgumentException,
            ) {
                return true
            }
        if (saved.id != holding.data().uuid()) return true
        val angular = SableNBTUtils.readVector3d(user.getCompound(OrbitalObjectData.KEY).getCompound("Angular"))
        if (!angular.isFinite || angular.length() > 100.0) return true
        var current = service.data.records[saved.id]?.takeIf { it.anchor == saved.anchor } ?: saved
        if (current.tick > service.data.clock) return true
        val ticks = service.data.clock - current.tick
        if (ticks > OrbitalEvolution.MAX_TICKS) {
            service.data.put(current.copy(pointer = holding.pointer()?.let(::pointer)))
            return false
        }
        if (ticks > 0) {
            current =
                current.copy(
                    tick = service.data.clock,
                    state =
                        OrbitalEvolution.advanceTimed(current.state, current.tick, ticks) { p, tick ->
                            val sample = CelestialFlight.sample(level.server)
                            sample.dynamics.acceleration(p, (tick - sample.frames.tick) / 20.0)
                        },
                )
        }
        val pose = SableNBTUtils.readPose3d(holding.data().fullTag().getCompound("pose"))
        val oldPosition = Vector3d(pose.position())
        val nativeState = CelestialFlight.sample(level.server).nativeState(current.state, level.dimension())
        if (nativeState.position.length() > 2e6) {
            // Never reconstruct a native body at astronomical coordinates. Keep durable holding
            // ownership until a bounded frame migration is requested by its authoritative owner.
            service.data.put(current.copy(pointer = holding.pointer()?.let(::pointer)))
            if (service.blockedNative.add(current.id)) {
                Sable.LOGGER.warn(
                    "Unloaded ship {} requires cold inter-sector migration from {}; native payload retained without unsafe reconstruction",
                    current.id,
                    level.dimension().location(),
                )
            }
            return false
        }
        pose.position().set(joml(nativeState.position))
        if (angular.length() > 0.0) {
            val rotation =
                Quaterniond().rotationAxis(
                    angular.length() * (current.tick - saved.snapshotTick) * 0.05,
                    angular.x / angular.length(),
                    angular.y / angular.length(),
                    angular.z / angular.length(),
                )
            pose.orientation().premul(rotation).normalize()
        }
        holding.data().fullTag().put("pose", SableNBTUtils.writePose3d(pose))
        holding.data().pose().set(pose)
        val offset = Vector3d(pose.position()).sub(oldPosition)
        holding.data().bounds().move(offset.x, offset.y, offset.z)
        holding.data().fullTag().put("world_bounds", SableNBTUtils.writeBoundingBox(holding.data().bounds()))
        service.restoring[saved.id] = current to angular
        service.data.put(current)
        return true
    }

    /** Apply exact motion before the first native physics tick, bypassing native load damping only here. */
    fun afterNativeLoad(
        level: ServerLevel,
        holding: HoldingSubLevel,
    ) {
        val service = services[level] ?: return
        service.holdingRefs.remove(holding.data().uuid())
        val (record, angular) = service.restoring.remove(holding.data().uuid()) ?: return
        val body = SubLevelContainer.getContainer(level)?.getSubLevel(record.id) as? ServerSubLevel ?: return
        val physics = SubLevelPhysicsSystem.require(level)
        body.updateMergedMassData(1.0f)
        physics.pipeline.onStatsChanged(body)
        physics.pipeline.prePhysicsTicks()
        physics.pipeline.resetVelocity(body)
        physics
            .getPhysicsHandle(
                body,
            ).addLinearAndAngularVelocity(
                joml(CelestialFlight.sample(level.server).nativeState(record.state, level.dimension()).velocity),
                angular,
            )
        physics.pipeline.prePhysicsTicks()
        body.updateLastPose()
    }

    fun requestNativeLoad(
        level: ServerLevel,
        id: UUID,
    ) {
        val service = service(level) ?: return
        val map = checkNotNull(SubLevelContainer.getContainer(level)).holdingChunkMap
        val holding = map.getHoldingSubLevel(id)
        if (holding != null) {
            val position = holding.data().pose().position()
            val owner =
                holding.data().originLoadedChunk
                    ?: ChunkPos(
                        net.minecraft.core.BlockPos
                            .containing(position.x, position.y, position.z),
                    )
            (map as SableHoldingMapAccess).orbitalisTakeAndLoad(owner, id)
        } else {
            service.data.records[id]
                ?.pointer
                ?.let { map.snatchAndLoad(pointer(it), id) }
        }
    }

    fun tick(level: ServerLevel) {
        val service = service(level) ?: return
        if (!mayAdvance(level.server) || SubLevelPhysicsSystem.require(level).paused) return
        service.tick()
    }

    fun close(server: MinecraftServer) {
        services.keys
            .filter { it.server === server }
            .toList()
            .forEach { services.remove(it)?.close() }
    }

    private fun pointer(value: GlobalSavedSubLevelPointer) =
        OrbitalObjectData.Pointer(value.chunkPos().x, value.chunkPos().z, value.storageIndex(), value.subLevelIndex())

    private fun pointer(value: OrbitalObjectData.Pointer) =
        GlobalSavedSubLevelPointer(ChunkPos(value.x, value.z), value.storage, value.index)

    private fun vector(v: org.joml.Vector3dc) = Vector3(v.x(), v.y(), v.z())

    private fun joml(v: Vector3) = Vector3d(v.x, v.y, v.z)

    private class Service(
        val level: ServerLevel,
    ) : AutoCloseable {
        val data = level.dataStorage.computeIfAbsent(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)
        val restoring = mutableMapOf<UUID, Pair<Record, Vector3d>>()
        val blockedNative = mutableSetOf<UUID>()

        // Retain a native holding reference only until its pointer has been checkpointed.
        val holdingRefs = mutableMapOf<UUID, HoldingSubLevel>()
        private val worker =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "orbitalis-unloaded-orbits").apply { isDaemon = true }
            }

        private data class Propagation(
            val record: Record,
            val error: String? = null,
        )

        private var pending: CompletableFuture<List<Propagation>>? = null
        private val quarantined = mutableMapOf<UUID, UUID>()
        private val leases = mutableMapOf<UUID, Long>()
        private val container = checkNotNull(SubLevelContainer.getContainer(level))
        private var warned = false

        init {
            container.addObserver(
                object : SubLevelObserver {
                    override fun onSubLevelRemoved(
                        body: SubLevel,
                        reason: SubLevelRemovalReason,
                    ) {
                        if (reason == SubLevelRemovalReason.REMOVED) {
                            data.remove(body.uniqueId)
                            holdingRefs.remove(body.uniqueId)
                        } else if (body.uniqueId in data.records) {
                            container.holdingChunkMap.getHoldingSubLevel(body.uniqueId)?.let { holdingRefs[body.uniqueId] = it }
                        }
                    }
                },
            )
        }

        fun tick() {
            if (!data.readable) {
                if (!warned) {
                    Sable.LOGGER.warn("Orbital object index is unknown or malformed; preserving it without background simulation")
                    warned =
                        true
                }
                return
            }
            // Recover only our transient tickets after a previous process stopped unexpectedly.
            for ((body, tickets) in container.collectForceLoadTickets()) {
                if (body.uniqueId !in leases && tickets.any { it.type() == wakeTicket }) {
                    container.removeForceLoadTicket(body, wakeTicket, Unit.INSTANCE)
                }
            }
            pending?.takeIf { it.isDone }?.let { completed ->
                pending = null
                try {
                    for (result in completed.join()) {
                        val next = result.record
                        val old = data.records[next.id] ?: continue
                        if (old.anchor != next.anchor || container.getSubLevel(next.id) != null) continue
                        if (result.error != null) {
                            quarantined[next.id] = next.anchor
                            Sable.LOGGER.warn("Orbital evolution stopped for {}: {}; native payload retained", next.id, result.error)
                        } else if (old.tick < next.tick) {
                            // Native storage may have checkpointed a new pointer while this worker ran.
                            data.put(old.copy(tick = next.tick, state = next.state))
                        }
                    }
                } catch (failure: java.util.concurrent.CompletionException) {
                    Sable.LOGGER.error("Background orbital evolution failed; native payloads retained", failure.cause)
                }
            }
            for ((id, until) in leases.toMap()) {
                val body = container.getSubLevel(id) as? ServerSubLevel
                if (body == null || data.clock >= until) {
                    if (body != null) container.removeForceLoadTicket(body, wakeTicket, Unit.INSTANCE)
                    leases.remove(id)
                }
            }
            if (data.clock % 20L != 0L) return
            val sample = CelestialFlight.sample(level.server)
            for (record in data.records.values.toList()) {
                if (container.getSubLevel(record.id) != null || data.clock - record.tick > 40) continue
                val nativePosition = sample.nativePoint(record.state.position, level.dimension())
                val nearbyPlayer =
                    level.players().any { player ->
                        nativePosition.minus(Vector3(player.x, player.y, player.z)).length() <
                            512.0 + record.radius + record.state.velocity.length() * 2
                    }
                val nearbyShip =
                    container.allSubLevels.any { body ->
                        nativePosition.minus(vector(body.logicalPose().position())).length() < 512.0 + record.radius
                    }
                val p = sample.nativePoint(record.state.position, net.minecraft.world.level.Level.OVERWORLD)
                val approachingSurface =
                    sample.frames.profile.geometry.earthEnvelope
                        .distance(p) <
                        sample.frames.profile.geometry.earthEnvelope.ascentDistance + record.radius + 256
                val moonLocal =
                    sample.frames.profile
                        .lunarSurfaceFrame(data.clock)
                        .fromParent(record.state)
                val approachingMoon =
                    sample.frames.profile.geometry.moonEnvelope.let { envelope ->
                        envelope.distance(moonLocal.position) < envelope.ascentDistance + record.radius + 256.0
                    }
                val approachingInner =
                    sample.frames.profile.innerPlanets.any { planet ->
                        val local =
                            sample.frames.profile
                                .innerPlanetSurfaceFrame(planet.id, data.clock)
                                .fromParent(record.state)
                        planet.envelope.distance(local.position) < planet.envelope.ascentDistance + record.radius + 256.0
                    }
                val migrating =
                    sample.frames.orbitalDestination(record.state, CelestialFlight.chart(level.dimension())) !=
                        CelestialFlight.chart(level.dimension())
                if (!nearbyPlayer && !nearbyShip && !approachingSurface && !approachingMoon && !approachingInner && !migrating) continue
                requestNativeLoad(level, record.id)
                (container.getSubLevel(record.id) as? ServerSubLevel)?.let { body ->
                    // Only add/remove our ticket if no other owner already holds this native key.
                    if (container.addForceLoadTicket(body, wakeTicket, Unit.INSTANCE)) {
                        leases[record.id] = data.clock + 80
                    }
                }
            }
            if (pending == null) {
                val target = data.clock
                val inputs =
                    data.records.values
                        .filter { container.getSubLevel(it.id) == null && it.tick < target && quarantined[it.id] != it.anchor }
                        .sortedBy { it.tick }
                        .take(64)
                if (inputs.isNotEmpty()) {
                    pending =
                        CompletableFuture.supplyAsync({
                            inputs.map { record ->
                                val ticks = minOf(target - record.tick, OrbitalEvolution.MAX_TICKS)
                                try {
                                    Propagation(
                                        record.copy(
                                            tick = record.tick + ticks,
                                            state =
                                                OrbitalEvolution.advanceTimed(record.state, record.tick, ticks) { p, tick ->
                                                    sample.dynamics.acceleration(p, (tick - sample.frames.tick) / 20.0)
                                                },
                                        ),
                                    )
                                } catch (failure: IllegalArgumentException) {
                                    Propagation(record, failure.message ?: "Unsupported orbital state")
                                }
                            }
                        }, worker)
                }
            }
        }

        override fun close() {
            pending?.cancel(true)
            worker.shutdownNow()
            for (id in leases.keys) {
                (container.getSubLevel(id) as? ServerSubLevel)?.let {
                    container.removeForceLoadTicket(it, wakeTicket, Unit.INSTANCE)
                }
            }
            leases.clear()
        }
    }
}
