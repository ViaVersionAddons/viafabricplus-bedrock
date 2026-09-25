/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.screen;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmHubService;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jspecify.annotations.NonNull;

/** Finds one exact Xbox gamertag before sending a Realm invite. */
public final class BedrockRealmInviteScreen extends VFPScreen {

    private final BedrockAuthManager account;
    private final BedrockRealmHubService service;
    private final Runnable invited;
    private EditBox gamertag;
    private Button inviteButton;
    private boolean busy;

    public BedrockRealmInviteScreen(final BedrockAuthManager account, final BedrockRealmHubService service,
                                    final Runnable invited) {
        super(Component.literal("Invite to Realm"), true);
        this.account = account;
        this.service = service;
        this.invited = invited;
    }

    @Override
    protected void init() {
        super.init();
        final int fieldWidth = Math.min(300, this.width - 40);
        this.gamertag = this.addRenderableWidget(new SubmitEditBox(this.font, (this.width - fieldWidth) / 2,
            this.height / 2 - 10, fieldWidth, 20, Component.literal("Xbox gamertag"), this::find));
        this.gamertag.setMaxLength(32);
        this.gamertag.setHint(Component.literal("Exact Xbox gamertag"));
        this.inviteButton = Button.builder(Component.literal("Find and invite"), _ -> this.find()).build();
        this.addFooter(this.inviteButton, Button.builder(Component.literal("Cancel"), _ -> this.onClose()).build());
    }

    @Override
    public void tick() {
        super.tick();
        this.inviteButton.active = !this.busy && !this.gamertag.getValue().isBlank();
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX,
                                   final int mouseY, final float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderScreenTitle(graphics);
        graphics.centeredText(this.font, "Enter the player's exact Xbox gamertag", this.width / 2,
            this.gamertag.getY() - 18, -1);
    }

    private void find() {
        if (this.busy || this.gamertag.getValue().isBlank()) return;
        final String query = this.gamertag.getValue().strip();
        this.busy = true;
        BedrockSocialService.search(this.account, query).whenComplete((results, error) ->
            Minecraft.getInstance().execute(() -> {
                this.busy = false;
                if (error != null) {
                    this.failure("Could not find Xbox players", error);
                    return;
                }
                final var match = results.stream().filter(user -> user.gamertag().equalsIgnoreCase(query))
                    .findFirst();
                if (match.isEmpty()) {
                    showToast(Component.literal("No exact Xbox gamertag found."));
                    return;
                }
                final var player = match.get();
                this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                    this.minecraft.gui.setScreen(this);
                    if (confirmed) this.invite(player.xuid());
                }, Component.literal("Invite " + player.gamertag() + "?"),
                    Component.literal("They will be able to join this Realm.")));
            }));
    }

    private void invite(final String xuid) {
        this.busy = true;
        this.service.updateInvite(xuid, true).whenComplete((_, error) ->
            Minecraft.getInstance().execute(() -> {
                this.busy = false;
                if (error != null) this.failure("Could not invite this player", error);
                else {
                    this.invited.run();
                    this.onClose();
                }
            }));
    }

    private void failure(final String message, final Throwable error) {
        ViaFabricPlusBedrock.impl().logger().error(message, error);
        showToast(Component.literal(message + ". Try again."));
    }
}
