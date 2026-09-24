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

package com.viaversion.viafabricplus.bedrock;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusEntrypoint;
import com.viaversion.viafabricplus.bedrock.account.BedrockAccount;
import com.viaversion.viafabricplus.bedrock.friends.FriendWorldSkinProvider;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.platform.ViaFabricPlusNettyPipelineProvider;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.platform.ViaFabricPlusViaBedrockPlatform;
import com.viaversion.viafabricplus.bedrock.settings.BedrockSettings;
import com.viaversion.viaversion.api.Via;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;
import net.raphimc.viabedrock.protocol.provider.SkinProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ViaFabricPlusBedrock implements ViaFabricPlusEntrypoint {

    private static ViaFabricPlusBedrock INSTANCE;

    private final Logger logger = LogManager.getLogger("ViaFabricPlus Bedrock");

    private BedrockSettings settings;
    private BedrockAccount account;

    public ViaFabricPlusBedrock() {
        INSTANCE = this;
    }

    @Override
    public void onPreSettingsLoading() {
        this.settings = new BedrockSettings();
        this.account = new BedrockAccount(ViaFabricPlus.api().path().resolve("bedrock.json"));
    }

    @Override
    public void onPostProtocolTranslationLoading() {
        new ViaFabricPlusViaBedrockPlatform();
        Via.getManager().getProviders().use(NettyPipelineProvider.class, new ViaFabricPlusNettyPipelineProvider());
        Via.getManager().getProviders().use(SkinProvider.class, new FriendWorldSkinProvider());
    }

    public static ViaFabricPlusBedrock impl() {
        return INSTANCE;
    }

    public Logger logger() {
        return this.logger;
    }

    public BedrockSettings settings() {
        return this.settings;
    }

    public BedrockAccount account() {
        return this.account;
    }

}
