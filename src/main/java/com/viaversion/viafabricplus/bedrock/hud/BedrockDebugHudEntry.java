/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.hud;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.injection.access.IChunkTracker;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public final class BedrockDebugHudEntry implements DebugScreenEntry {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "viafabricplus-bedrock");

    @Override
    public void display(final @NonNull DebugScreenDisplayer lines, @Nullable final Level world, @Nullable final LevelChunk clientChunk, @Nullable final LevelChunk chunk) {
        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection == null) {
            return;
        }

        final List<String> information = new ArrayList<>();

        final ChunkTracker chunkTracker = connection.get(ChunkTracker.class);
        if (chunkTracker != null) {
            final IChunkTracker mixinChunkTracker = (IChunkTracker) chunkTracker;
            information.add("Chunk Tracker: R: " + mixinChunkTracker.viaFabricPlusBedrock$getSubChunkRequests()
                + ", P: " + mixinChunkTracker.viaFabricPlusBedrock$getPendingSubChunks()
                + ", C: " + mixinChunkTracker.viaFabricPlusBedrock$getChunks());
        }

        if (!information.isEmpty()) {
            lines.addToGroup(ID, information);
        }
    }

    @Override
    public boolean isAllowed(final boolean reducedDebugInfo) {
        return true;
    }

}
