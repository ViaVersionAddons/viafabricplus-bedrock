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
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmTimelineService;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmsError;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.extra.realms.model.RealmsServer;
import org.jspecify.annotations.NonNull;

/** Explains the Realm's Timeline requirement before changing the member's own consent. */
public final class BedrockRealmTimelineScreen extends VFPScreen {

    private final BedrockAuthManager account;
    private final RealmsServer realm;
    private final Runnable join;
    private Component status = Component.translatable("bedrock_realms.viafabricplus.timeline.loading");
    private boolean requested;
    private boolean checking;
    private boolean optedIn;
    private boolean failed;
    private boolean saving;
    private Button actionButton;

    public BedrockRealmTimelineScreen(final BedrockAuthManager account, final RealmsServer realm, final Runnable join) {
        super(Component.translatable("bedrock_realms.viafabricplus.timeline.title"), true);
        this.account = account;
        this.realm = realm;
        this.join = join;
    }

    @Override
    protected void init() {
        this.actionButton = Button.builder(Component.empty(), _ -> this.act()).build();
        this.addFooter(this.actionButton, Button.builder(Component.translatable("bedrock_realms.viafabricplus.timeline.back"), _ -> this.onClose()).build());
        super.init();
        if (!this.requested) {
            this.requested = true;
            this.check();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.actionButton.active = !this.checking && !this.saving;
        this.actionButton.setMessage(Component.translatable(this.failed ? "bedrock_realms.viafabricplus.timeline.retry"
            : this.optedIn ? "bedrock_realms.viafabricplus.timeline.join"
                : "bedrock_realms.viafabricplus.timeline.opt_in"));
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.renderScreenTitle(graphics);

        final int textWidth = Math.min(320, this.width - 32);
        final String name = this.realm.getNameOr("Realm");
        final String shownName = this.font.width(name) <= textWidth ? name
            : this.font.plainSubstrByWidth(name, textWidth - this.font.width("…")) + "…";
        final List<FormattedCharSequence> explanation = this.font.split(Component.translatable("bedrock_realms.viafabricplus.timeline.explanation"), textWidth);
        final List<FormattedCharSequence> state = this.font.split(this.status, textWidth);
        final int contentHeight = 22 + (explanation.size() + state.size()) * (this.font.lineHeight + 3) + 9;
        int y = Math.max(38, (this.height - FOOTER_HEIGHT - contentHeight) / 2);
        graphics.centeredText(this.font, shownName, this.width / 2, y, ACCENT_COLOR);
        y += 22;
        for (final FormattedCharSequence line : explanation) {
            graphics.centeredText(this.font, line, this.width / 2, y, -1);
            y += this.font.lineHeight + 3;
        }
        y += 9;
        for (final FormattedCharSequence line : state) {
            graphics.centeredText(this.font, line, this.width / 2, y, this.failed ? 0xFFFF7777 : 0xFFB8B8B8);
            y += this.font.lineHeight + 3;
        }
    }

    private void act() {
        if (this.failed) {
            this.check();
        } else if (this.optedIn) {
            this.onClose();
            this.join.run();
        } else {
            this.optIn();
        }
    }

    private void check() {
        this.checking = true;
        this.failed = false;
        this.status = Component.translatable("bedrock_realms.viafabricplus.timeline.loading");
        BedrockRealmTimelineService.isOptedIn(this.account, this.realm.getId()).whenComplete((optedIn, error) ->
            Minecraft.getInstance().execute(() -> {
                this.checking = false;
                if (error != null) {
                    ViaFabricPlusBedrock.impl().logger().error("Failed to load Realm Timeline consent", error);
                    this.failed = true;
                    this.status = BedrockRealmsError.describe(error);
                    showToast(this.status);
                } else {
                    this.optedIn = optedIn;
                    this.status = Component.translatable(optedIn ? "bedrock_realms.viafabricplus.timeline.already_in"
                        : "bedrock_realms.viafabricplus.timeline.choice");
                }
            }));
    }

    private void optIn() {
        this.saving = true;
        this.status = Component.translatable("bedrock_realms.viafabricplus.timeline.saving");
        BedrockRealmTimelineService.optIn(this.account, this.realm.getId()).whenComplete((_, error) ->
            Minecraft.getInstance().execute(() -> {
                this.saving = false;
                if (error != null) {
                    ViaFabricPlusBedrock.impl().logger().error("Failed to opt in to Realm Timeline", error);
                    this.status = BedrockRealmsError.describe(error);
                    showToast(this.status);
                } else {
                    this.optedIn = true;
                    this.onClose();
                    this.join.run();
                }
            }));
    }

}
