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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmHubService;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Edits the public Realm name and description without changing its world options. */
public final class BedrockRealmInfoScreen extends VFPScreen {

    private final BedrockRealmHubService service;
    private final String initialName;
    private final String initialDescription;
    private final Runnable saved;
    private EditBox name;
    private EditBox description;
    private Button saveButton;
    private boolean saving;

    public BedrockRealmInfoScreen(final BedrockRealmHubService service, final String name,
                                  final String description, final Runnable saved) {
        super(Component.literal("Edit Realm info"), true);
        this.service = service;
        this.initialName = name;
        this.initialDescription = description;
        this.saved = saved;
    }

    @Override
    protected void init() {
        super.init();
        final int fieldWidth = Math.min(340, this.width - 40);
        final int left = (this.width - fieldWidth) / 2;
        this.name = this.addRenderableWidget(new SubmitEditBox(this.font, left, this.height / 2 - 32,
            fieldWidth, 20, Component.literal("Realm name"), this::save));
        this.name.setMaxLength(32);
        this.name.setValue(this.initialName);
        this.description = this.addRenderableWidget(new SubmitEditBox(this.font, left, this.height / 2 + 15,
            fieldWidth, 20, Component.literal("Realm description"), this::save));
        this.description.setMaxLength(255);
        this.description.setValue(this.initialDescription);
        this.saveButton = Button.builder(Component.literal("Save"), _ -> this.save()).build();
        this.addFooter(this.saveButton, Button.builder(Component.literal("Cancel"), _ -> this.onClose()).build());
    }

    @Override
    public void tick() {
        super.tick();
        this.saveButton.active = !this.saving && !this.name.getValue().isBlank();
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX,
                                   final int mouseY, final float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderScreenTitle(graphics);
        final int left = this.name.getX();
        graphics.text(this.font, "Realm name", left, this.name.getY() - 12, -1);
        graphics.text(this.font, "Description", left, this.description.getY() - 12, -1);
    }

    private void save() {
        if (this.saving || this.name.getValue().isBlank()) return;
        this.saving = true;
        this.service.saveDescription(this.name.getValue().strip(), this.description.getValue().strip())
            .whenComplete((_, error) -> Minecraft.getInstance().execute(() -> {
                this.saving = false;
                if (error != null) {
                    ViaFabricPlusBedrock.impl().logger().error("Failed to save Realm info", error);
                    showToast(Component.literal("Could not save Realm info. Try again."));
                } else {
                    this.saved.run();
                    this.onClose();
                }
            }));
    }
}
