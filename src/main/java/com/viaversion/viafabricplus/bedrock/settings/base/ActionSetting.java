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

package com.viaversion.viafabricplus.bedrock.settings.base;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.Setting;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public final class ActionSetting implements Setting {

    private final Component name;
    private final Supplier<Component> value;
    private final Runnable action;

    public ActionSetting(final Component name, final Supplier<Component> value, final Runnable action) {
        this.name = name;
        this.value = value;
        this.action = action;
    }

    public Component value() {
        return this.value.get();
    }

    public void run() {
        this.action.run();
    }

    @Override
    public Component name() {
        return this.name;
    }

    @Override
    public void write(final JsonObject object) {
    }

    @Override
    public void read(final JsonObject object) {
    }

}
