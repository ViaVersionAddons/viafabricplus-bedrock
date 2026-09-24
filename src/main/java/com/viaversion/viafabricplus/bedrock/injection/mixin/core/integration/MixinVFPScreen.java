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
import com.viaversion.viafabricplus.bedrock.screen.BedrockFriendsScreen;
import com.viaversion.viafabricplus.bedrock.screen.BedrockRealmsScreen;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.impl.ViaFabricPlusScreen;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = VFPScreen.class, remap = false)
public abstract class MixinVFPScreen {

    @ModifyVariable(method = "addFooter", at = @At("HEAD"), argsOnly = true)
    private Button[] addBedrockWorldButtons(final Button[] buttons) {
        if (!((Object) this instanceof ViaFabricPlusScreen)) {
            return buttons;
        }

        final Button.Builder builder = Button.builder(BedrockRealmsScreen.TITLE, _ -> new BedrockRealmsScreen().open((Screen) (Object) this));
        final boolean missingAccount = ViaFabricPlusBedrock.impl().account().get() == null; // Only check for presence, later validate
        if (missingAccount) {
            builder.tooltip(Tooltip.create(Component.translatable("bedrock_realms.viafabricplus.warning")));
        }

        final Button realms = builder.build();
        final Button.Builder friendsBuilder = Button.builder(BedrockFriendsScreen.TITLE, _ -> new BedrockFriendsScreen().open((Screen) (Object) this));
        if (missingAccount) {
            friendsBuilder.tooltip(Tooltip.create(Component.translatable("bedrock_realms.viafabricplus.warning")));
        }
        final Button friends = friendsBuilder.build();
        // The realms screen connects to a server, which is not possible while already being connected to one
        realms.active = !missingAccount && Minecraft.getInstance().getConnection() == null;
        friends.active = !missingAccount;

        final Button[] result = Arrays.copyOf(buttons, buttons.length + 2);
        result[buttons.length] = realms;
        result[buttons.length + 1] = friends;
        return result;
    }

}
