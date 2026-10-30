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

package com.viaversion.viafabricplus.bedrock.screen.impl.settings;

import com.viaversion.viafabricplus.bedrock.settings.base.ActionSetting;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class ActionListEntry extends VFPListEntry {

    private static final int VALUE_MARGIN = 2;

    private final ActionSetting setting;

    public ActionListEntry(final ActionSetting setting) {
        this.setting = setting;
    }

    @Override
    public @NonNull Component getNarration() {
        return this.setting.name();
    }

    @Override
    public void mappedMouseClicked() {
        this.setting.run();
    }

    @Override
    public void mappedRender(final GuiGraphicsExtractor context, final int entryWidth, final int entryHeight) {
        final Font font = Minecraft.getInstance().font;

        final Component value = this.setting.value();
        final int offset = font.width(value) + VALUE_MARGIN;
        this.renderScrollableText(context, this.setting.name().copy().withStyle(ChatFormatting.GRAY), offset);
        context.text(font, value, entryWidth - offset, entryHeight / 2 - font.lineHeight / 2, VFPScreen.ACCENT_COLOR);
    }

}
