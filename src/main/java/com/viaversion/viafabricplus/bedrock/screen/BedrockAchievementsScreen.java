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

package com.viaversion.viafabricplus.bedrock.screen;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import com.viaversion.viafabricplus.bedrock.profile.BedrockProfileService;
import com.viaversion.viafabricplus.bedrock.profile.BedrockProfileService.Achievement;
import com.viaversion.viafabricplus.bedrock.visual.BedrockImageCache;
import com.viaversion.viafabricplus.bedrock.visual.BedrockFallbackImages;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** A player's Minecraft Xbox achievements across Bedrock platforms. */
public final class BedrockAchievementsScreen extends VFPScreen {

    private enum Filter {
        ALL, LOCKED, COMPLETED
    }

    private final String xuid;
    private Filter filter = Filter.ALL;
    private @Nullable List<Achievement> achievements;
    private boolean loading;
    private boolean failed;

    public BedrockAchievementsScreen(final String xuid, final String playerName) {
        super(Component.translatable("screen.viafabricplus.bedrock_player_achievements", playerName), true);
        this.xuid = xuid;
    }

    @Override
    protected void init() {
        final int width = Math.min(94, (this.width - 24) / Filter.values().length);
        final int left = (this.width - width * Filter.values().length) / 2;
        for (final Filter item : Filter.values()) {
            final Button button = Button.builder(Component.translatable("bedrock_achievements.viafabricplus."
                    + item.name().toLowerCase()), _ -> {
                        this.filter = item;
                        this.rebuildWidgets();
                    }).pos(left + item.ordinal() * width, 48).size(width, 20).build();
            button.active = this.filter != item;
            this.addRenderableWidget(button);
        }
        this.addRenderableWidget(new AchievementList(this.minecraft, this.width, this.height, 76, FOOTER_HEIGHT,
            this.font.lineHeight * 2 + 12));
        final Button refresh = Button.builder(Component.translatable("bedrock_friends.viafabricplus.refresh"), _ -> this.load()).build();
        refresh.active = !this.loading;
        this.addFooter(refresh);
        super.init();
        if (this.achievements == null && !this.loading && !this.failed) {
            this.load();
        }
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    private void load() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.loading) {
            return;
        }
        this.loading = true;
        this.failed = false;
        BedrockProfileService.achievements(account, this.xuid).thenAcceptAsync(results -> {
            this.loading = false;
            if (ViaFabricPlusBedrock.impl().account().get() == account) {
                this.achievements = results;
                this.rebuildWidgets();
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Failed to load Minecraft achievements", error);
            Minecraft.getInstance().execute(() -> {
                this.loading = false;
                this.failed = true;
                showToast(BedrockXboxError.describe(error));
                this.rebuildWidgets();
            });
            return null;
        });
    }

    private final class AchievementList extends VFPList {

        private AchievementList(final Minecraft minecraft, final int width, final int height, final int top,
                                final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            if (BedrockAchievementsScreen.this.achievements == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockAchievementsScreen.this.failed
                    ? "bedrock_achievements.viafabricplus.failed" : "bedrock_achievements.viafabricplus.loading")));
                return;
            }
            final List<Achievement> shown = BedrockAchievementsScreen.this.achievements.stream()
                .filter(achievement -> switch (BedrockAchievementsScreen.this.filter) {
                    case ALL -> true;
                    case LOCKED -> !achievement.achieved();
                    case COMPLETED -> achievement.achieved();
                })
                .sorted(Comparator.comparing(Achievement::achieved).thenComparing(Achievement::name,
                    String.CASE_INSENSITIVE_ORDER))
                .toList();
            if (shown.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_achievements.viafabricplus.empty")));
            } else {
                shown.forEach(achievement -> this.addEntry(new AchievementEntry(achievement)));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(352, this.width - 20);
        }

    }

    private static final class AchievementEntry extends VFPListEntry {

        private final Achievement achievement;

        private AchievementEntry(final Achievement achievement) {
            this.achievement = achievement;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.achievement.name() + ", " + this.achievement.description());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int width, final int height) {
            final Font font = Minecraft.getInstance().font;
            final String score = this.achievement.gamerscore() == 0 ? ""
                : this.achievement.gamerscore() + " G";
            final int iconSize = height - SLOT_MARGIN * 2;
            if (!BedrockImageCache.drawRemote(graphics, this.achievement.iconUrl(), SLOT_MARGIN,
                SLOT_MARGIN, iconSize, iconSize)) {
                BedrockFallbackImages.drawAchievementIcon(graphics, SLOT_MARGIN, SLOT_MARGIN, iconSize);
            }
            final int textX = SLOT_MARGIN + iconSize + 6;
            final int textWidth = width - textX - SLOT_MARGIN - font.width(score) - 12;
            graphics.text(font, fit(font, this.achievement.name(), textWidth), textX, SLOT_MARGIN + 1, -1);
            graphics.text(font, fit(font, this.achievement.description(), width - textX - SLOT_MARGIN), textX,
                SLOT_MARGIN + font.lineHeight + 4, 0xFFAAAAAA);
            if (!score.isEmpty()) {
                graphics.text(font, score, width - SLOT_MARGIN - font.width(score), SLOT_MARGIN + 1, -1);
            }
        }

        private static String fit(final Font font, final String value, final int available) {
            if (font.width(value) <= available) {
                return value;
            }
            final String ellipsis = "…";
            return font.plainSubstrByWidth(value, Math.max(0, available - font.width(ellipsis))) + ellipsis;
        }

    }

}
