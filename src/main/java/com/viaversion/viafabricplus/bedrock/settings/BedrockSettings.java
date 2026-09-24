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

package com.viaversion.viafabricplus.bedrock.settings;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;

public final class BedrockSettings {

    private final BooleanSetting replaceDefaultPort;
    private final BooleanSetting experimentalFeatures;

    public BedrockSettings() {
        final SettingGroup group = ViaFabricPlus.api().settings().register("bedrock");
        group.register("account", new ActionSetting(Component.translatable("bedrock_settings.viafabricplus.account"), BedrockSettings::accountName, () -> ViaFabricPlusBedrock.impl().account().login()));
        this.replaceDefaultPort = group.registerBoolean("replace_default_port", true);
        this.experimentalFeatures = group.registerBoolean("experimental_features", true);
    }

    private static Component accountName() {
        final String displayName = ViaFabricPlusBedrock.impl().account().displayName();
        return displayName != null ? Component.nullToEmpty(displayName) : Component.translatable("bedrock_settings.viafabricplus.account.login");
    }

    public String replaceDefaultPort(final String address, final ProtocolVersion version) {
        // The vanilla default port can't simply be replaced because a Bedrock server might be running on it,
        // so only addresses without an explicit port are changed
        if (ViaFabricPlusBedrock.impl().settings().replaceDefaultPort().isActive()
            && Objects.equals(version, BedrockProtocolVersion.bedrockLatest)
            && !address.contains(":")) {
            return address + ":" + ProtocolConstants.BEDROCK_DEFAULT_PORT;
        } else {
            return address;
        }
    }

    public BooleanSetting replaceDefaultPort() {
        return this.replaceDefaultPort;
    }

    public BooleanSetting experimentalFeatures() {
        return this.experimentalFeatures;
    }

}
