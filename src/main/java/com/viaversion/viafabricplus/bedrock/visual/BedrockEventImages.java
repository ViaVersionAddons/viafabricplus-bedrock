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

import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-provided pictures associated with specific Realm events. */
public final class BedrockEventImages {

    private static final String RESOURCE_ROOT = "/assets/viafabricplus-bedrock/events/";
    private static final Set<String> EVENTS = Set.of(
        "250HostileMobs", "AllMobNameEasterEggs", "CookEverything", "DefeatEnderdragon",
        "DefeatWither", "DiamondEverything", "FirstAbandonedMineshaftFound",
        "FirstAncientCityFound", "FirstBadlandsFound", "FirstConduit",
        "FirstCraftedNetherite", "FirstDiamondFound", "FirstEnchantment",
        "FirstEndPortal", "FirstEnderDragonDefeated", "FirstFullyExploredMap",
        "FirstMushroomFieldFound", "FirstNetherFortressFound", "FirstNetherPortalLit",
        "FirstPeakMountainFound", "FirstPillagerOutpostFound", "FirstPoweredBeacon",
        "FirstWitherDefeated", "FirstWoodlandMansionFound", "NamedMob", "NamedMobDies",
        "NewMember", "PillagerCaptainDefeated", "RealmCreated", "Session"
    );

    private BedrockEventImages() {
    }

    public static boolean hasImage(final String event) {
        return EVENTS.contains(event);
    }

    public static void draw(final GuiGraphicsExtractor graphics, final String event,
                            final int x, final int y, final int width, final int height) {
        if (hasImage(event)) {
            BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + event + ".png", x, y, width, height);
        }
    }

}
