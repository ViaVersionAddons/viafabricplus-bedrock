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

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.netty.BatchLengthCodec;
import net.raphimc.viabedrock.netty.DisconnectHandler;
import net.raphimc.viabedrock.netty.PacketCodec;
import net.raphimc.viabedrock.netty.raknet.MessageCodec;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class MixinConnection_1 {

    @Final
    @Shadow
    Connection val$connection;

    @Inject(method = "initChannel", at = @At("RETURN"))
    private void injectBedrockIntoPipeline(final Channel channel, final CallbackInfo ci) {
        final IConnection mixinConnection = (IConnection) this.val$connection;
        if (!BedrockProtocolVersion.bedrockLatest.equals(mixinConnection.viaFabricPlus$getTargetVersion())) {
            return;
        }

        final ChannelConfig config = channel.config();
        // RakNet config
        config.setOption(RakChannelOption.RAK_PROTOCOL_VERSION, ProtocolConstants.BEDROCK_RAKNET_PROTOCOL_VERSION);
        config.setOption(RakChannelOption.RAK_COMPATIBILITY_MODE, true);
        config.setOption(RakChannelOption.RAK_CLIENT_INTERNAL_ADDRESSES, 20);
        config.setOption(RakChannelOption.RAK_TIME_BETWEEN_SEND_CONNECTION_ATTEMPTS_MS, 500);
        config.setOption(RakChannelOption.RAK_CONNECT_TIMEOUT, config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS).longValue());
        config.setOption(RakChannelOption.RAK_SESSION_TIMEOUT, 30_000L);
        config.setOption(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong());

        // NetherNet config
        config.setOption(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, config.getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS));
        config.setOption(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1);

        // ViaBedrock, added around the Via handlers ViaFabricPlus put into the pipeline before
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addBefore(HandlerNames.SPLITTER, DisconnectHandler.NAME, new DisconnectHandler());
        pipeline.addBefore(HandlerNames.SPLITTER, MessageCodec.NAME, new MessageCodec());
        pipeline.replace(HandlerNames.SPLITTER, HandlerNames.SPLITTER, new BatchLengthCodec());
        pipeline.remove(HandlerNames.PREPENDER);
        pipeline.addBefore(ViaDecodeHandler.NAME, PacketCodec.NAME, new PacketCodec());
    }

}
