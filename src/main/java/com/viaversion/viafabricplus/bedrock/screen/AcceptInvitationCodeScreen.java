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

package com.viaversion.viafabricplus.bedrock.screen;

import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class AcceptInvitationCodeScreen extends VFPScreen {

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;

    private final Consumer<String> codeHandler;
    private EditBox codeField;

    public AcceptInvitationCodeScreen(final Consumer<String> codeHandler) {
        super(Component.translatable("screen.viafabricplus.accept_invite"), true);

        this.codeHandler = codeHandler;
    }

    @Override
    protected void init() {
        super.init();

        this.codeField = this.addRenderableWidget(new SubmitEditBox(this.font, (this.width - FIELD_WIDTH) / 2,
            this.height / 2 - FIELD_HEIGHT, FIELD_WIDTH, FIELD_HEIGHT, Component.empty(), this::acceptCode));
        this.codeField.setHint(Component.translatable("base.viafabricplus.code"));

        this.addFooter(Button.builder(Component.translatable("base.viafabricplus.accept"), _ -> this.acceptCode()).build());
    }

    private void acceptCode() {
        this.codeHandler.accept(this.codeField.getValue());
        this.onClose();
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.renderScreenTitle(graphics);
    }

}
