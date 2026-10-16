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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.cbi.orbitalis.core.planet.Vector3;
import org.joml.Quaterniond;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Only landing bookmarks survive a restart. Never persists a running controller or throttle. */
final class LaunchSites extends SavedData {
    record Site(UUID ship, Vector3 position, Quaterniond attitude) {
        Site {
            attitude = new Quaterniond(attitude);
            if (!attitude.isFinite() || !Double.isFinite(position.getX()) ||
                !Double.isFinite(position.getY()) || !Double.isFinite(position.getZ()) ||
                Math.abs(attitude.lengthSquared() - 1) > 1e-6 ||
                Math.abs(position.getX()) > 1800 || Math.abs(position.getZ()) > 1800 ||
                position.getY() < 68 || position.getY() > 320)
                throw new IllegalArgumentException("Launch site outside the diagnostic terrain patch");
        }
        @Override public Quaterniond attitude() { return new Quaterniond(attitude); }
    }
    private final Map<UUID, Site> sites = new HashMap<>();
    static LaunchSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LaunchSites::new, LaunchSites::load, null), "orbitalis_flight_launch_sites");
    }
    Site require(UUID pilot, UUID ship) {
        Site site = sites.get(pilot);
        if (site == null || !site.ship().equals(ship)) throw new IllegalArgumentException("No launch bookmark for this pilot and ship; launch with autopilot orbit first");
        return site;
    }
    void put(UUID pilot, Site site) {
        if (!sites.containsKey(pilot) && sites.size() >= 64) throw new IllegalArgumentException("Test launch bookmark limit reached");
        sites.put(pilot, site); setDirty();
    }
    static LaunchSites load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != 1 || !tag.contains("sites", 9)) throw new IllegalArgumentException("Unsupported launch bookmark data");
        ListTag list = (ListTag) tag.get("sites");
        if (!list.isEmpty() && list.getElementType() != 10) throw new IllegalArgumentException("Invalid launch bookmark list");
        if (list.size() > 64) throw new IllegalArgumentException("Too many launch bookmarks");
        var data = new LaunchSites();
        for (var value : list) {
            var entry = (CompoundTag) value;
            for (var key : new String[]{"x", "y", "z", "qx", "qy", "qz", "qw"}) {
                if (!entry.contains(key, 99) || !Double.isFinite(entry.getDouble(key))) throw new IllegalArgumentException("Invalid launch bookmark vector");
            }
            var site = new Site(entry.getUUID("ship"), new Vector3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
                new Quaterniond(entry.getDouble("qx"), entry.getDouble("qy"), entry.getDouble("qz"), entry.getDouble("qw")));
            if (data.sites.put(entry.getUUID("pilot"), site) != null) throw new IllegalArgumentException("Duplicate launch bookmark");
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        var list = new ListTag();
        sites.forEach((pilot, site) -> {
            var entry = new CompoundTag();
            entry.putUUID("pilot", pilot); entry.putUUID("ship", site.ship());
            entry.putDouble("x", site.position().getX()); entry.putDouble("y", site.position().getY()); entry.putDouble("z", site.position().getZ());
            entry.putDouble("qx", site.attitude.x); entry.putDouble("qy", site.attitude.y);
            entry.putDouble("qz", site.attitude.z); entry.putDouble("qw", site.attitude.w);
            list.add(entry);
        });
        tag.put("sites", list); return tag;
    }
}
