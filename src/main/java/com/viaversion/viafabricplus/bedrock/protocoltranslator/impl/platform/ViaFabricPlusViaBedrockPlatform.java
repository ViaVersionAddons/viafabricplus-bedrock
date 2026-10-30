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

package com.viaversion.viafabricplus.bedrock.protocoltranslator.impl.platform;

import com.viaversion.viafabricplus.bedrock.protocoltranslator.impl.viabedrock.ViaFabricPlusViaBedrockConfig;
import com.viaversion.viaversion.api.Via;
import java.io.File;
import java.util.logging.Logger;
import net.raphimc.viabedrock.platform.ViaBedrockPlatform;

public final class ViaFabricPlusViaBedrockPlatform implements ViaBedrockPlatform {

    private final Logger logger;

    public ViaFabricPlusViaBedrockPlatform() {
        this.logger = Via.getPlatform().createLogger("ViaBedrock");
        this.init(new ViaFabricPlusViaBedrockConfig(new File(this.getDataFolder(), "viabedrock.yml"), this.logger));
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public File getDataFolder() {
        return Via.getPlatform().getDataFolder();
    }

}
