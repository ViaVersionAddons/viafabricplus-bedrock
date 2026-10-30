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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.screen.base.VFPPopup;
import com.viaversion.viafabricplus.screen.impl.serverlist.ServerListsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VFPPopup.class, remap = false)
public abstract class MixinVFPPopup {

    @ModifyExpressionValue(method = "init", at = @At(value = "FIELD", target = "Lcom/viaversion/viafabricplus/screen/base/VFPPopup;bodyHeight:I"))
    private int makeRoomForBedrockServerLists(final int bodyHeight) {
        if ((Object) this instanceof ServerListsScreen) {
            return bodyHeight + 2 * (20 + 3); // Two more rows of ServerListsScreen's button height and margin
        } else {
            return bodyHeight;
        }
    }

}
