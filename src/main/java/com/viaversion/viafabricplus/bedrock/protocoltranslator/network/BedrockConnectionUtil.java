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

package com.viaversion.viafabricplus.bedrock.protocoltranslator.network;

import com.viaversion.viafabricplus.bedrock.injection.access.IServerAddress;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

public final class BedrockConnectionUtil {

    /**
     * Connects to a Bedrock world reachable via NetherNet.
     *
     * @param address the NetherNet address of the world
     */
    public static void connectNetherNet(final SocketAddress address) {
        final ServerAddress serverAddress = ServerAddress.parseString("nethernet.viafabricplus.localhost");
        ((IServerAddress) (Object) serverAddress).viaFabricPlusBedrock$setNetherNetAddress(address);

        final ServerData entry = new ServerData("Bedrock NetherNet " + address, serverAddress.getHost(), ServerData.Type.OTHER);
        ((IServerData) entry).viaFabricPlus$forceVersion(BedrockProtocolVersion.bedrockLatest);

        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

}
