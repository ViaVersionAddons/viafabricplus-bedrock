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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IServerAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetAddressParser;
import com.viaversion.viafabricplus.bedrock.settings.BedrockSettings;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.net.SocketAddress;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen {

    @WrapOperation(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(final String input, final Operation<ServerAddress> original, @Local(argsOnly = true) final ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        final ProtocolVersion version;
        if (mixinServerInfo.viaFabricPlus$passedDirectConnectScreen()) {
            version = ViaFabricPlus.api().targetVersion();
        } else {
            version = mixinServerInfo.viaFabricPlus$forcedVersion();
        }
        if (BedrockProtocolVersion.bedrockLatest.equals(version)) {
            final SocketAddress netherNetAddress = NetherNetAddressParser.parse(input);
            if (netherNetAddress != null) {
                final ServerAddress address = original.call("nethernet.viafabricplus.localhost");
                ((IServerAddress) (Object) address).viaFabricPlusBedrock$setNetherNetAddress(netherNetAddress);
                return address;
            }
        }
        return original.call(ViaFabricPlusBedrock.impl().settings().replaceDefaultPort(input, version));
    }

}
