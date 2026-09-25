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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws images supplied by a Realm, or the client's default Realm preview. */
public final class BedrockRealmImages {

    private BedrockRealmImages() {
    }

    public static boolean drawPreview(final GuiGraphicsExtractor graphics, final JsonObject realm,
                                      final int x, final int y, final int width, final int height) {
        final String image = image(realm);
        if (!image.isBlank()) {
            final boolean drawn = image.startsWith("https://") || image.startsWith("http://")
                ? BedrockImageCache.drawRemote(graphics, image, x, y, width, height)
                : BedrockImageCache.drawEncoded(graphics, realmId(realm), image, x, y, width, height);
            if (drawn) return true;
        }
        return BedrockFallbackImages.drawRealmPreview(graphics, x, y, width, height);
    }

    private static String image(final JsonObject realm) {
        final String minigame = string(realm, "minigameImage");
        if (!minigame.isBlank()) return minigame;
        final int activeSlot = number(realm, "activeSlot");
        if (!realm.has("slots") || !realm.get("slots").isJsonArray()) return "";
        for (final JsonElement element : realm.getAsJsonArray("slots")) {
            if (!element.isJsonObject()) continue;
            final JsonObject slot = element.getAsJsonObject();
            if (number(slot, "slotId") != activeSlot) continue;
            try {
                final JsonElement options = JsonParser.parseString(string(slot, "options"));
                if (options.isJsonObject()) return string(options.getAsJsonObject(), "worldTemplateImage");
            } catch (RuntimeException ignored) {
                return "";
            }
        }
        return "";
    }

    private static String realmId(final JsonObject realm) {
        return realm.has("id") && realm.get("id").isJsonPrimitive() ? realm.get("id").getAsString() : "realm";
    }

    private static String string(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int number(final JsonObject object, final String key) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

}
