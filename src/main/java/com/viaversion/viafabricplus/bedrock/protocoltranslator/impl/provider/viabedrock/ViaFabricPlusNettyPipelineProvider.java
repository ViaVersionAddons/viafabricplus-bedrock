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

package com.viaversion.viafabricplus.bedrock.protocoltranslator.impl.provider.viabedrock;

import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.Channel;
import javax.crypto.SecretKey;
import net.minecraft.network.HandlerNames;
import net.raphimc.viabedrock.netty.CompressionCodec;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PacketCompressionAlgorithm;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;

public final class ViaFabricPlusNettyPipelineProvider extends NettyPipelineProvider {

    @Override
    public void enableCompression(final UserConnection user, final PacketCompressionAlgorithm preferredCompressionAlgorithm, final int threshold) {
        final Channel channel = user.getChannel();
        if (!channel.pipeline().names().contains(HandlerNames.COMPRESS)) {
            channel.pipeline().addBefore(HandlerNames.SPLITTER, HandlerNames.COMPRESS, new CompressionCodec(preferredCompressionAlgorithm, threshold));
        } else {
            channel.pipeline().replace(HandlerNames.COMPRESS, HandlerNames.COMPRESS, new CompressionCodec(preferredCompressionAlgorithm, threshold));
        }
    }

    @Override
    public void enableEncryption(final UserConnection user, final SecretKey key) {
        // Not needed for NetherNet
    }

}
