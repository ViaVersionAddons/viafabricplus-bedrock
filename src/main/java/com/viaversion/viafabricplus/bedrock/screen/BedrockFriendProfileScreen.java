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

import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService.SocialUser;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** A compact Xbox profile using the details already returned by PeopleHub. */
public final class BedrockFriendProfileScreen extends VFPScreen {

    private final SocialUser user;
    private final Component relationship;

    public BedrockFriendProfileScreen(final SocialUser user, final Component relationship) {
        super(Component.literal(user.name()), true);
        this.user = user;
        this.relationship = relationship;
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.renderScreenTitle(graphics);

        int y = 91;
        graphics.centeredText(this.font, this.user.gamertag().isBlank() ? this.user.name() : this.user.gamertag(),
            this.width / 2, y, ACCENT_COLOR);
        y += 20;
        final Component presence = this.user.presence().isBlank()
            ? Component.translatable(this.user.online() ? "bedrock_friends.viafabricplus.online" : "bedrock_friends.viafabricplus.offline")
            : Component.literal(this.user.presence());
        graphics.centeredText(this.font, presence, this.width / 2, y, -1);
        y += 20;
        graphics.centeredText(this.font, this.relationship, this.width / 2, y, -1);
        y += 20;
        if (!this.user.gamerScore().isBlank()) {
            graphics.centeredText(this.font, Component.translatable("bedrock_friends.viafabricplus.profile_score", this.user.gamerScore()),
                this.width / 2, y, -1);
            y += 20;
        }
        if (this.user.friendCount() >= 0) {
            graphics.centeredText(this.font, Component.translatable("bedrock_friends.viafabricplus.profile_friends", this.user.friendCount()),
                this.width / 2, y, -1);
        }
    }

}
