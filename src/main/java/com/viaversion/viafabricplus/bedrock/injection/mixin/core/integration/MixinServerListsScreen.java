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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.integration;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.screen.impl.friends.BedrockFriendsScreen;
import com.viaversion.viafabricplus.bedrock.screen.impl.realms.BedrockRealmsScreen;
import com.viaversion.viafabricplus.screen.base.VFPPopup;
import com.viaversion.viafabricplus.screen.impl.serverlist.ServerListsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerListsScreen.class, remap = false)
public abstract class MixinServerListsScreen extends VFPPopup {

    @Shadow
    @Final
    private static int BUTTON_WIDTH;

    @Shadow
    @Final
    private static int BUTTON_HEIGHT;

    @Shadow
    @Final
    private static int BUTTON_MARGIN;

    protected MixinServerListsScreen(final Component title, final int bodyWidth, final int bodyHeight) {
        super(title, bodyWidth, bodyHeight);
    }

    @Inject(method = "initBody", at = @At("TAIL"))
    private void addBedrockServerLists(final ScreenRectangle body, final CallbackInfo ci) {
        final int x = body.left() + (body.width() - BUTTON_WIDTH) / 2;
        final int y = body.top() + BUTTON_MARGIN + 2 * (BUTTON_HEIGHT + BUTTON_MARGIN); // Below ViaFabricPlus' own server lists
        this.viaFabricPlusBedrock$addServerList(BedrockRealmsScreen.TITLE, () -> new BedrockRealmsScreen().open(this.prevScreen), x, y);
        this.viaFabricPlusBedrock$addServerList(BedrockFriendsScreen.TITLE, () -> new BedrockFriendsScreen().open(this.prevScreen), x, y + BUTTON_HEIGHT + BUTTON_MARGIN);
    }

    @Unique
    private void viaFabricPlusBedrock$addServerList(final Component title, final Runnable open, final int x, final int y) {
        final Button.Builder builder = Button.builder(title, _ -> open.run()).pos(x, y).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        final boolean missingAccount = ViaFabricPlusBedrock.impl().account().get() == null; // Only check for presence, later validate
        if (missingAccount) {
            builder.tooltip(Tooltip.create(Component.translatable("bedrock_realms.viafabricplus.warning")));
        }

        final Button button = this.addRenderableWidget(builder.build());
        button.active = !missingAccount;
    }

}
