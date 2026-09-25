/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.screen;

import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Activates an actionable list row like Minecraft's multiplayer server list. */
abstract class ActionList extends VFPList {

    protected ActionList(final Minecraft minecraft, final int width, final int height, final int top,
                         final int bottom, final int entryHeight) {
        super(minecraft, width, height, top, bottom, entryHeight);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        final VFPListEntry clicked = this.getEntryAtPosition(event.x(), event.y());
        final boolean handled = super.mouseClicked(event, doubleClick);
        if (handled && doubleClick && event.button() == 0 && clicked != null && clicked == this.getFocused()) {
            this.activate(clicked);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        final VFPListEntry selected = this.getFocused();
        if (event.isSelection() && selected != null && this.activate(selected)) {
            return true;
        }
        return super.keyPressed(event);
    }

    protected abstract boolean activate(VFPListEntry entry);

}
