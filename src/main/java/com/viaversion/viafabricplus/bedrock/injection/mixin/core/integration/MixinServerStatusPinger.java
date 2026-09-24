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
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetAddressParser;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetHttpAddress;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import java.net.URI;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger {

    private static final HttpClient NETHERNET_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Inject(method = "pingServer", at = @At("HEAD"), cancellable = true)
    private void pingNetherNet(final ServerData data, final Runnable onPersistentDataChange, final Runnable onPongResponse,
                               final EventLoopGroupHolder eventLoopGroupHolder, final CallbackInfo ci) {
        if (!BedrockProtocolVersion.bedrockLatest.equals(((IServerData) data).viaFabricPlus$forcedVersion())) {
            return;
        }
        final SocketAddress address = NetherNetAddressParser.parse(data.ip);
        if (address == null) {
            return;
        }
        ci.cancel();
        data.playerList = List.of();
        if (!(address instanceof NetherNetHttpAddress httpAddress)) {
            data.motd = Component.translatable("bedrock_nethernet.viafabricplus.status_unavailable");
            data.status = Component.empty();
            data.setState(ServerData.State.SUCCESSFUL);
            onPongResponse.run();
            return;
        }

        data.motd = Component.translatable("multiplayer.status.pinging");
        data.setState(ServerData.State.PINGING);
        final long start = System.nanoTime();
        final URI uri;
        try {
            uri = new URI("http", null, httpAddress.host(), httpAddress.port(), "/v1/join", null, null);
        } catch (Exception exception) {
            data.setState(ServerData.State.UNREACHABLE);
            return;
        }
        final HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        NETHERNET_HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenCompleteAsync((response, error) -> {
                if (error != null || response.statusCode() / 100 != 2) {
                    data.motd = Component.translatable("multiplayer.status.cannot_connect");
                    data.status = Component.empty();
                    data.setState(ServerData.State.UNREACHABLE);
                    onPongResponse.run();
                    return;
                }
                try {
                    final JsonObject info = JsonParser.parseString(response.body()).getAsJsonObject();
                    final int protocol = info.get("protocol").getAsInt();
                    final int players = info.get("players").getAsInt();
                    final int maxPlayers = info.get("maxPlayers").getAsInt();
                    data.motd = Component.literal(info.get("name").getAsString());
                    data.version = Component.literal(info.get("version").getAsString());
                    data.protocol = BedrockProtocolVersion.bedrockLatest.getVersion();
                    data.players = new ServerStatus.Players(maxPlayers, players, List.of());
                    data.status = ServerStatusPinger.formatPlayerCount(players, maxPlayers);
                    data.ping = (System.nanoTime() - start) / 1_000_000L;
                    data.setState(protocol == ProtocolConstants.BEDROCK_PROTOCOL_VERSION
                        ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
                } catch (Exception exception) {
                    data.motd = Component.translatable("multiplayer.status.cannot_connect");
                    data.status = Component.empty();
                    data.setState(ServerData.State.UNREACHABLE);
                }
                onPongResponse.run();
            }, Minecraft.getInstance());
    }

    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(final String input, final Operation<ServerAddress> original, @Local(argsOnly = true) final ServerData data) {
        // Replaces the port when pinging a server with a forced version
        return original.call(ViaFabricPlusBedrock.impl().settings().replaceDefaultPort(input, ((IServerData) data).viaFabricPlus$forcedVersion()));
    }

}
