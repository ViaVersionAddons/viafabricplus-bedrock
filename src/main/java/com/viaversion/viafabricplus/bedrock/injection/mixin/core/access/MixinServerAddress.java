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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.access;

import com.viaversion.viafabricplus.bedrock.injection.access.IServerAddress;
import java.net.SocketAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetAddressParser;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public abstract class MixinServerAddress implements IServerAddress {

    @Inject(method = "isValidAddress", at = @At("HEAD"), cancellable = true)
    private static void allowNetherNetAddress(final String input, final CallbackInfoReturnable<Boolean> cir) {
        if (NetherNetAddressParser.parse(input) != null) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private SocketAddress viaFabricPlusBedrock$netherNetAddress;

    @Override
    public SocketAddress viaFabricPlusBedrock$getNetherNetAddress() {
        return this.viaFabricPlusBedrock$netherNetAddress;
    }

    @Override
    public void viaFabricPlusBedrock$setNetherNetAddress(final SocketAddress address) {
        this.viaFabricPlusBedrock$netherNetAddress = address;
    }

}
