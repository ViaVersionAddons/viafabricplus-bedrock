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
import com.viaversion.viafabricplus.bedrock.profile.BedrockProfileService.Statistic;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** The four Xbox statistics shown by Bedrock profiles. */
public final class BedrockStatsScreen extends VFPScreen {

    private final String xuid;
    private final boolean self;
    private boolean comparing;
    private boolean loading;
    private boolean failed;
    private boolean ownFailed;
    private @Nullable Map<Statistic, String> playerStats;
    private @Nullable Map<Statistic, String> ownStats;

    public BedrockStatsScreen(final String xuid, final String playerName, final boolean self) {
        super(Component.translatable("screen.viafabricplus.bedrock_stats", playerName), true);
        this.xuid = xuid;
        this.self = self;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new StatsList(this.minecraft, this.width, this.height, 62, FOOTER_HEIGHT,
            this.font.lineHeight * 2 + 18));
        final Button refresh = Button.builder(Component.translatable("bedrock_friends.viafabricplus.refresh"), _ -> this.load()).build();
        if (this.self) {
            this.addFooter(refresh);
        } else {
            this.addFooter(Button.builder(Component.translatable(this.comparing
                ? "bedrock_stats.viafabricplus.show_player" : "bedrock_stats.viafabricplus.compare"), _ -> {
                    this.comparing = !this.comparing;
                    if (this.comparing && this.ownStats == null) {
                        this.loadOwn();
                    }
                    this.rebuildWidgets();
                }).build(), refresh);
        }
        super.init();
        if (this.playerStats == null && !this.loading && !this.failed) {
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
        BedrockProfileService.statistics(account, this.xuid).thenAcceptAsync(stats -> {
            this.loading = false;
            if (ViaFabricPlusBedrock.impl().account().get() == account) {
                this.playerStats = stats;
                this.rebuildWidgets();
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Failed to load Minecraft statistics", error);
            Minecraft.getInstance().execute(() -> {
                this.loading = false;
                this.failed = true;
                showToast(BedrockXboxError.describe(error));
                this.rebuildWidgets();
            });
            return null;
        });
    }

    private void loadOwn() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null) {
            return;
        }
        this.ownFailed = false;
        account.getXboxUserProfile().getUpToDateAsync().thenCompose(profile ->
            BedrockProfileService.statistics(account, profile.getId())).thenAcceptAsync(stats -> {
                if (ViaFabricPlusBedrock.impl().account().get() == account) {
                    this.ownStats = stats;
                    if (this.comparing) {
                        this.rebuildWidgets();
                    }
                }
            }, Minecraft.getInstance()).exceptionally(error -> {
                ViaFabricPlusBedrock.impl().logger().error("Failed to load own Minecraft statistics", error);
                Minecraft.getInstance().execute(() -> {
                    this.ownFailed = true;
                    showToast(BedrockXboxError.describe(error));
                    this.rebuildWidgets();
                });
                return null;
            });
    }

    private final class StatsList extends VFPList {

        private StatsList(final Minecraft minecraft, final int width, final int height, final int top,
                          final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            if (BedrockStatsScreen.this.playerStats == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockStatsScreen.this.failed
                    ? "bedrock_stats.viafabricplus.failed" : "bedrock_stats.viafabricplus.loading")));
            } else if (BedrockStatsScreen.this.playerStats.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_stats.viafabricplus.empty")));
            } else if (BedrockStatsScreen.this.comparing && BedrockStatsScreen.this.ownStats == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockStatsScreen.this.ownFailed
                    ? "bedrock_stats.viafabricplus.compare_failed" : "bedrock_stats.viafabricplus.loading")));
            } else {
                for (final Statistic stat : Statistic.values()) {
                    this.addEntry(new StatEntry(stat, BedrockStatsScreen.this.playerStats.get(stat),
                        BedrockStatsScreen.this.comparing ? BedrockStatsScreen.this.ownStats.get(stat) : null));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(352, this.width - 20);
        }

    }

    private final class StatEntry extends VFPListEntry {

        private final Statistic stat;
        private final @Nullable String player;
        private final @Nullable String own;

        private StatEntry(final Statistic stat, final @Nullable String player, final @Nullable String own) {
            this.stat = stat;
            this.player = player;
            this.own = own;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.translatable("bedrock_stats.viafabricplus." + this.stat.name().toLowerCase(),
                this.player == null ? "?" : this.player);
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int width, final int height) {
            final Font font = Minecraft.getInstance().font;
            final int textX = SLOT_MARGIN;
            final String label = Component.translatable("bedrock_stats.viafabricplus." + this.stat.name().toLowerCase()).getString();
            graphics.text(font, label, textX, SLOT_MARGIN + 1, -1);
            final String value = format(this.stat, this.player);
            graphics.text(font, value, textX, SLOT_MARGIN + font.lineHeight + 4, -1);
            if (BedrockStatsScreen.this.comparing) {
                final String comparison = format(this.stat, this.own);
                graphics.text(font, comparison, width - SLOT_MARGIN - font.width(comparison),
                    SLOT_MARGIN + font.lineHeight + 4, 0xFFAAAAAA);
                final double targetNumber = number(this.player);
                final double ownNumber = number(this.own);
                final double max = Math.max(1D, Math.max(targetNumber, ownNumber));
                final int barWidth = width - SLOT_MARGIN * 2;
                final int barY = height - SLOT_MARGIN - 2;
                graphics.fill(SLOT_MARGIN, barY, SLOT_MARGIN + (int) (barWidth * targetNumber / max), barY + 2, ACCENT_COLOR);
                graphics.fill(SLOT_MARGIN, barY + 2, SLOT_MARGIN + (int) (barWidth * ownNumber / max), barY + 4, 0xFFAAAAAA);
            }
        }

    }

    private static double number(final @Nullable String raw) {
        try {
            return raw == null ? 0D : Math.max(0D, Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private static String format(final Statistic stat, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "?";
        }
        final double value = number(raw);
        if (stat == Statistic.MINUTES_PLAYED) {
            final long minutes = (long) value;
            return Component.translatable("bedrock_stats.viafabricplus.time_value", minutes / 1440,
                minutes / 60 % 24, minutes % 60).getString();
        }
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format((long) value);
    }

}
