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
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmsError;
import com.viaversion.viafabricplus.bedrock.visual.BedrockImageCache;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.raphimc.minecraftauth.extra.realms.model.RealmsServer;
import net.raphimc.minecraftauth.extra.realms.service.impl.BedrockRealmsService;
import org.jetbrains.annotations.Nullable;

/** Explains Realm Timeline sharing before a member opts in. */
public final class BedrockRealmTimelineScreen extends VFPScreen {

    private static final String TIMELINE_IMAGE = "/assets/viafabricplus-bedrock/content/timeline-opt-in.png";

    private final BedrockRealmsService service;
    private final RealmsServer realm;
    private final Runnable join;
    private @Nullable Component status;
    private boolean saving;
    private boolean failed;
    private boolean closed;
    private Button actionButton;

    public BedrockRealmTimelineScreen(final BedrockRealmsService service, final RealmsServer realm, final Runnable join) {
        super(Component.translatable("bedrock_realms.viafabricplus.timeline.title"), true);
        this.service = service;
        this.realm = realm;
        this.join = join;
    }

    @Override
    protected void init() {
        this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> this.renderExplanation(graphics));
        this.actionButton = Button.builder(Component.translatable("bedrock_realms.viafabricplus.timeline.opt_in"),
            _ -> this.optIn()).build();
        this.addFooter(this.actionButton, Button.builder(
            Component.translatable("bedrock_realms.viafabricplus.timeline.back"), _ -> this.onClose()).build());
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        this.actionButton.active = !this.saving;
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    @Override
    public void onClose() {
        this.closed = true;
        super.onClose();
    }

    private void renderExplanation(final GuiGraphicsExtractor graphics) {
        final int textWidth = Math.min(420, this.width - 32);
        final int left = (this.width - textWidth) / 2;
        graphics.centeredText(this.font, Component.translatable("bedrock_realms.viafabricplus.timeline.requirement"),
            this.width / 2, 60, -1);

        final String explanationKey = this.height < 170
            ? "bedrock_realms.viafabricplus.timeline.explanation_tiny"
            : this.height < 240
                ? "bedrock_realms.viafabricplus.timeline.explanation_short"
                : "bedrock_realms.viafabricplus.timeline.explanation";
        final Component body = this.status != null && (this.failed || this.saving)
            ? this.status : Component.translatable(explanationKey);
        int y = 82;
        for (final FormattedCharSequence line : this.font.split(body, textWidth)) {
            if (y + this.font.lineHeight >= this.height - FOOTER_HEIGHT) {
                break;
            }
            graphics.text(this.font, line, left, y, this.failed ? 0xFFFF5555 : -1);
            y += this.font.lineHeight + 3;
        }
        final int imageTop = y + 12;
        final int maxHeight = this.height - FOOTER_HEIGHT - imageTop - 8;
        if (maxHeight >= 90) {
            final int imageWidth = Math.min(textWidth, Math.min(420, maxHeight * 16 / 9));
            final int imageHeight = imageWidth * 9 / 16;
            BedrockImageCache.drawBundled(graphics, TIMELINE_IMAGE, (this.width - imageWidth) / 2,
                imageTop, imageWidth, imageHeight);
        }
    }

    private void optIn() {
        if (this.saving) {
            return;
        }
        this.saving = true;
        this.failed = false;
        this.status = Component.translatable("bedrock_realms.viafabricplus.timeline.saving");
        this.service.updateWorldStorySettingsAsync(this.realm, null, true).whenComplete((_, error) ->
            Minecraft.getInstance().execute(() -> {
                this.saving = false;
                if (error != null) {
                    this.failed = true;
                    ViaFabricPlusBedrock.impl().logger().error("Failed to opt in to Realm Timeline", error);
                    this.status = BedrockRealmsError.describe(error);
                    showToast(this.status);
                } else if (!this.closed) {
                    this.onClose();
                    this.join.run();
                }
            }));
    }

}
