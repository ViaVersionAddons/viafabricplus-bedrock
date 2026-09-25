/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.bedrock.visual;

import com.viaversion.viafabricplus.ViaFabricPlus;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/** Draws the player's Xbox game picture, with a Minecraft head while it loads or is unavailable. */
public final class BedrockPlayerImages {

    private static final Map<String, String> PICTURES = new ConcurrentHashMap<>();
    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

    private BedrockPlayerImages() {
    }

    public static void remember(final String xuid, final String pictureUrl) {
        if (!xuid.isBlank() && !pictureUrl.isBlank()) PICTURES.put(xuid, pictureUrl);
    }

    public static void rememberName(final String xuid, final String name) {
        if (!xuid.isBlank() && !name.isBlank()) NAMES.put(xuid, name);
    }

    public static void draw(final GuiGraphicsExtractor graphics, final String xuid,
                            final int x, final int y, final int size) {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        final String name = NAMES.get(xuid);
        if (connection != null && name != null
            && BedrockProtocolVersion.bedrockLatest.equals(ViaFabricPlus.api().targetVersion())) {
            final PlayerInfo player = connection.getPlayerInfoIgnoreCase(name);
            if (player != null) {
                PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, size);
                return;
            }
        }
        if (BedrockImageCache.drawRemote(graphics, PICTURES.getOrDefault(xuid, ""), x, y, size, size)) return;
        final UUID skinId = UUID.nameUUIDFromBytes(xuid.getBytes(StandardCharsets.UTF_8));
        PlayerFaceExtractor.extractRenderState(graphics, DefaultPlayerSkin.get(skinId), x, y, size);
    }

}
