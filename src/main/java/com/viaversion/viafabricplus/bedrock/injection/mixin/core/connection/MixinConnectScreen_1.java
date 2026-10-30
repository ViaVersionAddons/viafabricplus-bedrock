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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.io.IOException;
import java.security.KeyPair;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.storage.AuthData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class MixinConnectScreen_1 {

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Exception;getMessage()Ljava/lang/String;", remap = false))
    private String handleNullExceptionMessage(final Exception instance, final Operation<String> original) {
        // Vanilla doesn't have these cases, but we do because of NetherNet and other modifications to the Netty pipeline
        return instance.getMessage() == null ? "" : original.call(instance);
    }

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lio/netty/channel/ChannelFuture;syncUninterruptibly()Lio/netty/channel/ChannelFuture;", remap = false, shift = At.Shift.AFTER))
    private void setupBedrockAccount(final CallbackInfo ci, @Local final Connection clientConnection) throws IOException {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }

        final UserConnection connection = ((IConnection) clientConnection).viaFabricPlus$getUserConnection();
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null) {
            ViaFabricPlusBedrock.impl().logger().warn("Could not get Bedrock account. Joining online mode servers will not work!");
            return;
        }

        final MinecraftMultiplayerToken multiplayerToken = account.getMinecraftMultiplayerToken().refresh();
        final KeyPair sessionKeyPair = account.getSessionKeyPair();
        final UUID deviceId = account.getDeviceId();

        connection.put(new AuthData(multiplayerToken.getToken(), sessionKeyPair, deviceId));
    }

}
