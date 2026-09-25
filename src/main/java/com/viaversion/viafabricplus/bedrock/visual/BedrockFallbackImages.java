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

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-derived images used only when content does not provide one. */
public final class BedrockFallbackImages {

    private static final String RESOURCE_ROOT = "/assets/viafabricplus-bedrock/fallback/";

    private BedrockFallbackImages() {
    }

    public static boolean drawRealmPreview(final GuiGraphicsExtractor graphics,
                                           final int x, final int y, final int width, final int height) {
        return BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + "Realms_Default_Thumbnail_3840.jpg",
            x, y, width, height)
            || BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + "realms_default_image.jpg",
                x, y, width, height);
    }

    public static boolean drawAchievementIcon(final GuiGraphicsExtractor graphics,
                                              final int x, final int y, final int size) {
        return BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + "achievements@0.5x.icon.png",
            x, y, size, size);
    }

}
