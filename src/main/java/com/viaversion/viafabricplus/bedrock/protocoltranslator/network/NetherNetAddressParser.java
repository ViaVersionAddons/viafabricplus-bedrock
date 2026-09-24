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

import java.net.SocketAddress;
import java.net.URI;
import java.util.Locale;
import org.cloudburstmc.netty.channel.nethernet.config.NetherNetAddress;
import org.jetbrains.annotations.Nullable;

/** Parses the address schemes also accepted by ViaProxy. */
public final class NetherNetAddressParser {

    private NetherNetAddressParser() {
    }

    public static @Nullable SocketAddress parse(final String input) {
        final URI uri;
        try {
            uri = URI.create(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        final String scheme = uri.getScheme();
        if (scheme == null || uri.getRawUserInfo() != null || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
            || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }
        final String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            return null;
        }

        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "nethernet" -> uri.getHost() == null || !validPort(uri) ? null : new NetherNetHttpAddress(uri.getHost(), port(uri, 19132));
            case "nethernet-lan" -> uri.getHost() == null || !validPort(uri) ? null : new NetherNetLanAddress(uri.getHost(), port(uri, 7551));
            case "nethernet-xbox" -> authority.equals(uri.getHost()) ? new NetherNetAddress(authority) : null;
            case "nethernet-xbox-json-rpc" -> authority.equals(uri.getHost()) ? new NetherNetJsonRpcAddress(authority) : null;
            default -> null;
        };
    }

    private static int port(final URI uri, final int defaultPort) {
        return uri.getPort() == -1 ? defaultPort : uri.getPort();
    }

    private static boolean validPort(final URI uri) {
        return uri.getPort() == -1 && uri.getRawAuthority().equals(uri.getHost())
            || uri.getPort() > 0 && uri.getPort() <= 65535;
    }

}
