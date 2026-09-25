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

import com.mojang.blaze3d.Blaze3D;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService.SocialUser;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import com.viaversion.viafabricplus.bedrock.visual.BedrockPlayerImages;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jspecify.annotations.NonNull;

/** Xbox identity and relationship actions for a Bedrock player. */
public final class BedrockFriendProfileScreen extends VFPScreen {

    private final SocialUser user;
    private final Component initialRelationship;
    private final boolean self;
    private final boolean initiallyFriend;
    private boolean friend;
    private boolean incoming;
    private boolean outgoing;
    private boolean favorite;
    private boolean busy;
    private Button relationshipButton;
    private Button favoriteButton;

    public BedrockFriendProfileScreen(final SocialUser user, final Component relationship, final boolean self,
                                      final boolean friend) {
        super(Component.literal(user.name()), true);
        this.user = user;
        this.initialRelationship = relationship;
        this.self = self;
        this.initiallyFriend = friend;
        this.friend = friend;
        this.incoming = user.incoming();
        this.outgoing = user.outgoing();
        this.favorite = user.favorite();
    }

    @Override
    protected void init() {
        if (this.self) {
            this.addFooter(
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.achievements"), _ ->
                    new BedrockAchievementsScreen(this.user.xuid(), this.user.name()).open(this)).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.stats"), _ ->
                    new BedrockStatsScreen(this.user.xuid(), this.user.name(), true).open(this)).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.gallery"), _ ->
                    new BedrockScreenshotGalleryScreen().open(this)).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.manage_account"), _ ->
                    Blaze3D.openUri(URI.create("https://account.xbox.com/"))).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.privacy"), _ ->
                    Blaze3D.openUri(URI.create("https://account.xbox.com/Settings"))).build()
            );
        } else {
            this.relationshipButton = Button.builder(Component.empty(), _ -> this.changeFriend()).build();
            this.favoriteButton = Button.builder(Component.empty(), _ -> this.changeFavorite()).build();
            this.addFooter(this.relationshipButton, this.favoriteButton,
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.achievements"), _ ->
                    new BedrockAchievementsScreen(this.user.xuid(), this.user.name()).open(this)).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.stats"), _ ->
                    new BedrockStatsScreen(this.user.xuid(), this.user.name(), false).open(this)).build(),
                Button.builder(Component.translatable("bedrock_profile.viafabricplus.xbox_profile"), _ ->
                    this.openXboxProfile()).build());
        }
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.self) {
            this.relationshipButton.active = !this.busy && !this.outgoing;
            this.favoriteButton.active = !this.busy;
            this.relationshipButton.setMessage(Component.translatable(this.outgoing
                ? "bedrock_profile.viafabricplus.pending" : this.friend
                    ? "bedrock_friends.viafabricplus.remove" : this.incoming
                        ? "bedrock_profile.viafabricplus.accept" : "bedrock_friends.viafabricplus.add"));
            this.favoriteButton.setMessage(Component.translatable(this.favorite
                ? "bedrock_profile.viafabricplus.remove_favorite" : "bedrock_profile.viafabricplus.favorite"));
        }
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
                                   final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        final int center = this.width / 2;
        BedrockPlayerImages.draw(graphics, this.user.xuid(), center - 16, 52, 32);
        int y = 91;
        final String gamertag = this.user.gamertag().isBlank() ? this.user.name() : this.user.gamertag();
        graphics.centeredText(this.font, this.fit(gamertag), center, y, ACCENT_COLOR);
        y += 18;
        final Component presence = this.user.presence().isBlank()
            ? Component.translatable(this.user.online() ? "bedrock_friends.viafabricplus.online"
                : "bedrock_friends.viafabricplus.offline") : Component.literal(this.user.presence());
        graphics.centeredText(this.font, this.fit(presence.getString()), center, y, -1);
        y += 18;
        final Component relationship = this.friend ? Component.translatable("bedrock_friends.viafabricplus.profile_friend")
            : this.outgoing ? Component.translatable("bedrock_friends.viafabricplus.profile_outgoing")
                : this.incoming ? Component.translatable("bedrock_friends.viafabricplus.profile_incoming")
                    : this.initiallyFriend ? Component.translatable("bedrock_friends.viafabricplus.profile_not_friend")
                        : this.initialRelationship;
        graphics.centeredText(this.font, this.fit(relationship.getString()), center, y, 0xFFB8B8B8);
        y += 22;
        if (!this.user.gamerScore().isBlank()) {
            graphics.centeredText(this.font, Component.translatable("bedrock_friends.viafabricplus.profile_score",
                this.user.gamerScore()), center, y, -1);
            y += 18;
        }
        if (this.user.friendCount() >= 0) {
            graphics.centeredText(this.font, Component.translatable("bedrock_friends.viafabricplus.profile_friends",
                this.user.friendCount()), center, y, -1);
        }
    }

    private String fit(final String value) {
        final int available = Math.max(40, this.width - 32);
        if (this.font.width(value) <= available) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, available - this.font.width("…")) + "…";
    }

    private void changeFriend() {
        if (this.friend) {
            VFPScreen.setScreen(new ConfirmScreen(confirmed -> {
                VFPScreen.setScreen(this);
                if (confirmed) {
                    this.updateFriend(false);
                }
            }, Component.translatable("bedrock_friends.viafabricplus.remove_confirm", this.user.name()),
                Component.translatable("bedrock_friends.viafabricplus.confirm_explanation")));
        } else {
            this.updateFriend(true);
        }
    }

    private void updateFriend(final boolean add) {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account != null && !this.busy) {
            this.mutate(BedrockSocialService.updateFriend(account, this.user.xuid(), add), () -> {
                final boolean accepted = add && this.incoming;
                this.friend = add && this.incoming;
                this.outgoing = add && !this.incoming;
                this.incoming = false;
                showToast(Component.translatable(accepted ? "bedrock_friends.viafabricplus.accepted"
                    : add ? "bedrock_friends.viafabricplus.requested" : "bedrock_friends.viafabricplus.removed",
                    this.user.name()));
            });
        }
    }

    private void changeFavorite() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account != null && !this.busy) {
            final boolean add = !this.favorite;
            this.mutate(BedrockSocialService.updateFavorite(account, this.user.xuid(), add), () -> {
                this.favorite = add;
                showToast(Component.translatable(add ? "bedrock_profile.viafabricplus.favorited"
                    : "bedrock_profile.viafabricplus.unfavorited", this.user.name()));
            });
        }
    }

    private void mutate(final CompletableFuture<Void> action, final Runnable success) {
        this.busy = true;
        action.thenRunAsync(() -> {
            this.busy = false;
            success.run();
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Xbox profile action failed", error);
            Minecraft.getInstance().execute(() -> {
                this.busy = false;
                showToast(BedrockXboxError.describe(error));
            });
            return null;
        });
    }

    private void openXboxProfile() {
        final String gamertag = this.user.gamertag();
        if (!gamertag.isBlank()) {
            Blaze3D.openUri(URI.create("https://www.xbox.com/en-US/play/user/"
                + URLEncoder.encode(gamertag, StandardCharsets.UTF_8)));
        }
    }

}
