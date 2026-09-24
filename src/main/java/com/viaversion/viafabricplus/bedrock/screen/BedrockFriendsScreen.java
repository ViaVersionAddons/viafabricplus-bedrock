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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService.FriendWorld;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService.FriendRequests;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService.SocialUser;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.BedrockConnectionUtil;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** Xbox friends, requests, player search, and joinable Bedrock worlds. */
public final class BedrockFriendsScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_friends");

    private static final int TABS_TOP = 36;
    private static final int DETAILS_TOP = 65;
    private static final int LIST_TOP = 64;
    private static final int SEARCH_LIST_TOP = 88;
    private static final int ROW_WIDTH = 352;
    private static final int SECONDARY_COLOR = 0xFFB8B8B8;
    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toNanos(2);
    private static final Comparator<SocialUser> FRIEND_ORDER = Comparator.comparing(SocialUser::online).reversed()
        .thenComparing(SocialUser::name, String.CASE_INSENSITIVE_ORDER);

    private enum View {
        FRIENDS, REQUESTS, SEARCH, WORLDS
    }

    private View view = View.FRIENDS;
    private @Nullable List<SocialUser> friends;
    private @Nullable FriendRequests requests;
    private @Nullable List<FriendWorld> worlds;
    private @Nullable List<SocialUser> searchResults;
    private String query = "";
    private boolean requested;
    private boolean friendsLoading;
    private boolean requestsLoading;
    private boolean worldsLoading;
    private boolean searching;
    private boolean mutating;
    private boolean joining;
    private boolean friendsError;
    private boolean requestsError;
    private boolean worldsError;
    private boolean searchError;
    private long lastRefresh;
    private String lastErrorToast = "";
    private long lastErrorToastAt;
    private SlotList list;
    private Button joinButton;
    private Button profileButton;
    private Button addButton;
    private Button removeButton;
    private Button refreshButton;

    public BedrockFriendsScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        final int tabWidth = Math.min(88, (this.width - 20) / View.values().length);
        final int tabsLeft = (this.width - tabWidth * View.values().length) / 2;
        for (final View tab : View.values()) {
            final int x = tabsLeft + tab.ordinal() * tabWidth;
            final Button button = Button.builder(Component.translatable("bedrock_friends.viafabricplus.tab." + tab.name().toLowerCase()),
                _ -> this.show(tab)).pos(x, TABS_TOP).size(tabWidth, 20).build();
            button.active = tab != this.view;
            this.addRenderableWidget(button);
        }

        if (this.view == View.SEARCH) {
            final int width = Math.min(230, this.width - 100);
            final int x = (this.width - width - 72) / 2;
            final EditBox field = this.addRenderableWidget(new EditBox(this.font, x, DETAILS_TOP - 4, width, 20,
                Component.translatable("bedrock_friends.viafabricplus.search_hint")));
            field.setHint(Component.translatable("bedrock_friends.viafabricplus.search_hint"));
            field.setValue(this.query);
            field.setResponder(value -> this.query = value.trim());
            this.addRenderableWidget(Button.builder(Component.translatable("bedrock_friends.viafabricplus.find"), _ -> this.search())
                .pos(x + width + 4, DETAILS_TOP - 4).size(68, 20).build());
        }

        this.list = this.addRenderableWidget(new SlotList(this.minecraft, this.width, this.height,
            this.view == View.SEARCH ? SEARCH_LIST_TOP : LIST_TOP, FOOTER_HEIGHT, this.font.lineHeight * 2 + 12));
        this.joinButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.join"), _ -> this.join()).build();
        this.profileButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.profile"), _ -> this.showProfile()).build();
        this.addButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.add"), _ -> this.addFriend()).build();
        this.removeButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.remove"), _ -> this.confirmRemove()).build();
        this.refreshButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.refresh"), _ -> this.refresh()).build();
        this.addFooter(this.joinButton, this.profileButton, this.addButton, this.removeButton, this.refreshButton);
        super.init();

        if (!this.requested) {
            this.requested = true;
            this.refresh();
        }
    }

    @Override
    public void tick() {
        super.tick();
        final SocialUser user = this.selectedUser();
        final boolean busy = this.joining || this.mutating;
        this.joinButton.active = !busy && Minecraft.getInstance().getConnection() == null && this.selectedWorld() != null;
        this.profileButton.active = user != null;
        this.addButton.active = !busy && user != null && !this.isFriend(user) && !this.isOutgoing(user);
        this.removeButton.active = !busy && user != null && (this.isFriend(user) || this.isIncoming(user) || this.isOutgoing(user));
        this.addButton.setMessage(Component.translatable(user != null && this.isIncoming(user)
            ? "bedrock_friends.viafabricplus.accept" : "bedrock_friends.viafabricplus.add"));
        this.removeButton.setMessage(Component.translatable(user != null && this.isIncoming(user)
            ? "bedrock_friends.viafabricplus.decline" : user != null && this.isOutgoing(user)
                ? "bedrock_friends.viafabricplus.cancel" : "bedrock_friends.viafabricplus.remove"));
        this.refreshButton.active = !busy && !this.friendsLoading && !this.requestsLoading && !this.worldsLoading && !this.searching;
        if (System.nanoTime() - this.lastRefresh >= REFRESH_INTERVAL && !busy) {
            this.refresh();
        }
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(2F, 2F);
        graphics.centeredText(this.font, this.title, this.width / 4, 6, ACCENT_COLOR);
        graphics.pose().popMatrix();
    }

    private void show(final View tab) {
        this.view = tab;
        this.rebuildWidgets();
    }

    private void refresh() {
        this.lastRefresh = System.nanoTime();
        if (this.view == View.SEARCH) {
            this.search();
        } else {
            this.loadFriends();
            this.loadRequests();
            this.loadWorlds();
        }
    }

    private void loadFriends() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.friendsLoading) {
            return;
        }
        this.friendsLoading = true;
        this.friendsError = false;
        BedrockSocialService.friends(account)
            .thenAcceptAsync(loaded -> {
                this.friendsLoading = false;
                if (ViaFabricPlusBedrock.impl().account().get() == account) {
                    this.friends = loaded;
                    if (this.view == View.FRIENDS) {
                        this.rebuildWidgets();
                    }
                }
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to load Xbox friends", error, () -> {
                this.friendsLoading = false;
                this.friendsError = true;
            }));
    }

    private void loadRequests() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.requestsLoading) {
            return;
        }
        this.requestsLoading = true;
        this.requestsError = false;
        BedrockSocialService.requests(account)
            .thenAcceptAsync(loaded -> {
                this.requestsLoading = false;
                if (ViaFabricPlusBedrock.impl().account().get() == account) {
                    this.requests = loaded;
                    if (this.view == View.REQUESTS) {
                        this.rebuildWidgets();
                    }
                }
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to load Xbox friend requests", error, () -> {
                this.requestsLoading = false;
                this.requestsError = true;
            }));
    }

    private void loadWorlds() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.worldsLoading) {
            return;
        }
        this.worldsLoading = true;
        this.worldsError = false;
        BedrockFriendsService.worlds(account)
            .thenAcceptAsync(loaded -> {
                this.worldsLoading = false;
                if (ViaFabricPlusBedrock.impl().account().get() == account) {
                    this.worlds = loaded;
                    if (this.view == View.WORLDS || this.view == View.FRIENDS) {
                        this.rebuildWidgets();
                    }
                }
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to load friends' worlds", error, () -> {
                this.worldsLoading = false;
                this.worldsError = true;
            }));
    }

    private void search() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.searching) {
            return;
        }
        if (this.query.isBlank()) {
            this.searchResults = List.of();
            this.rebuildWidgets();
            return;
        }
        this.searching = true;
        this.searchError = false;
        this.searchResults = null;
        final String query = this.query;
        BedrockSocialService.search(account, query)
            .thenAcceptAsync(results -> {
                this.searching = false;
                if (ViaFabricPlusBedrock.impl().account().get() == account && this.query.equals(query)) {
                    this.searchResults = results;
                    if (this.view == View.SEARCH) {
                        this.rebuildWidgets();
                    }
                }
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to search Xbox players", error, () -> {
                this.searching = false;
                this.searchError = true;
            }));
        this.rebuildWidgets();
    }

    private @Nullable SocialUser selectedUser() {
        return this.list.getFocused() instanceof UserEntry entry ? entry.user : null;
    }

    private boolean isFriend(final SocialUser user) {
        return user.friend() || this.friends != null && this.friends.stream().anyMatch(friend -> friend.xuid().equals(user.xuid()));
    }

    private boolean isIncoming(final SocialUser user) {
        return user.incoming() || this.requests != null && this.requests.incoming().stream().anyMatch(request -> request.xuid().equals(user.xuid()));
    }

    private boolean isOutgoing(final SocialUser user) {
        return user.outgoing() || this.requests != null && this.requests.outgoing().stream().anyMatch(request -> request.xuid().equals(user.xuid()));
    }

    private @Nullable FriendWorld selectedWorld() {
        if (this.list.getFocused() instanceof WorldEntry entry) {
            return entry.world;
        }
        final SocialUser user = this.selectedUser();
        if (user != null && this.worlds != null) {
            return this.worlds.stream().filter(world -> world.ownerXuid().equals(user.xuid())).findFirst().orElse(null);
        }
        return null;
    }

    private void join() {
        final FriendWorld world = this.selectedWorld();
        if (world == null || this.joining || Minecraft.getInstance().getConnection() != null) {
            return;
        }
        if (world.protocol() != 0 && world.protocol() != ProtocolConstants.BEDROCK_PROTOCOL_VERSION) {
            showToast(Component.translatable("bedrock_friends.viafabricplus.incompatible"));
            return;
        }
        if (world.maxPlayers() > 0 && world.players() >= world.maxPlayers()) {
            showToast(Component.translatable("bedrock_friends.viafabricplus.full"));
            return;
        }
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null) {
            return;
        }
        this.joining = true;
        BedrockFriendsService.join(account, world)
            .thenAcceptAsync(joined -> BedrockConnectionUtil.connectNetherNet(joined.address()), Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to join a Bedrock friend's world", error, () -> {
                BedrockFriendsService.leaveCurrent();
                this.joining = false;
            }));
    }

    private void addFriend() {
        final SocialUser user = this.selectedUser();
        if (user != null && !this.isFriend(user) && !this.isOutgoing(user)) {
            this.updateFriend(user, true);
        }
    }

    private void showProfile() {
        final SocialUser user = this.selectedUser();
        if (user == null) {
            return;
        }
        final String relationship = this.isFriend(user) ? "profile_friend" : this.isIncoming(user)
            ? "profile_incoming" : this.isOutgoing(user) ? "profile_outgoing" : "profile_not_friend";
        new BedrockFriendProfileScreen(user, Component.translatable("bedrock_friends.viafabricplus." + relationship)).open(this);
    }

    private void confirmRemove() {
        final SocialUser user = this.selectedUser();
        if (user == null) {
            return;
        }
        final String action = this.isIncoming(user) ? "decline_confirm" : this.isOutgoing(user)
            ? "cancel_confirm" : "remove_confirm";
        VFPScreen.setScreen(new ConfirmScreen(confirmed -> {
            VFPScreen.setScreen(this);
            if (confirmed) {
                this.updateFriend(user, false);
            }
        }, Component.translatable("bedrock_friends.viafabricplus." + action, user.name()),
            Component.translatable("bedrock_friends.viafabricplus.confirm_explanation")));
    }

    private void updateFriend(final SocialUser user, final boolean add) {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.mutating) {
            return;
        }
        this.mutating = true;
        final String result = add ? this.isIncoming(user) ? "accepted" : "requested"
            : this.isIncoming(user) ? "declined" : this.isOutgoing(user) ? "canceled" : "removed";
        BedrockSocialService.updateFriend(account, user.xuid(), add)
            .thenRunAsync(() -> {
                this.mutating = false;
                showToast(Component.translatable("bedrock_friends.viafabricplus." + result, user.name()));
                this.loadFriends();
                this.loadRequests();
                if (this.view == View.SEARCH) {
                    this.search();
                }
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to update Xbox friend", error, () -> this.mutating = false));
    }

    private Void fail(final String message, final Throwable error, final Runnable reset) {
        ViaFabricPlusBedrock.impl().logger().error(message, error);
        Minecraft.getInstance().execute(() -> {
            reset.run();
            final Component details = BedrockXboxError.describe(error);
            final String text = details.getString();
            if (!text.equals(this.lastErrorToast) || System.nanoTime() - this.lastErrorToastAt > TimeUnit.SECONDS.toNanos(3)) {
                showToast(details);
                this.lastErrorToast = text;
                this.lastErrorToastAt = System.nanoTime();
            }
            if (this.view != View.SEARCH || this.searchError) {
                this.rebuildWidgets();
            }
        });
        return null;
    }

    private final class SlotList extends VFPList {

        private static double scrollAmount;

        private SlotList(final Minecraft minecraft, final int width, final int height, final int top, final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            switch (BedrockFriendsScreen.this.view) {
                case FRIENDS -> this.addFriends();
                case REQUESTS -> this.addRequests();
                case SEARCH -> this.addSearch();
                case WORLDS -> this.addWorlds();
            }
            this.setScrollAmount(scrollAmount);
        }

        private void addFriends() {
            if (BedrockFriendsScreen.this.friends == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockFriendsScreen.this.friendsError
                    ? "bedrock_friends.viafabricplus.load_failed" : "bedrock_friends.viafabricplus.loading")));
                return;
            }
            final List<SocialUser> friends = BedrockFriendsScreen.this.friends.stream().sorted(FRIEND_ORDER).toList();
            if (friends.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.no_friends")));
                return;
            }
            friends.forEach(user -> this.addEntry(new UserEntry(this, user)));
        }

        private void addRequests() {
            if (BedrockFriendsScreen.this.requests == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockFriendsScreen.this.requestsError
                    ? "bedrock_friends.viafabricplus.load_failed" : "bedrock_friends.viafabricplus.loading")));
                return;
            }
            this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.incoming")));
            if (BedrockFriendsScreen.this.requests.incoming().isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.no_requests")));
            } else {
                BedrockFriendsScreen.this.requests.incoming().forEach(user -> this.addEntry(new UserEntry(this, user)));
            }
            this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.outgoing")));
            BedrockFriendsScreen.this.requests.outgoing().forEach(user -> this.addEntry(new UserEntry(this, user)));
        }

        private void addSearch() {
            if (BedrockFriendsScreen.this.searchResults == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockFriendsScreen.this.searching
                    ? "bedrock_friends.viafabricplus.searching" : BedrockFriendsScreen.this.searchError
                        ? "bedrock_friends.viafabricplus.search_failed" : "bedrock_friends.viafabricplus.search_hint")));
            } else if (BedrockFriendsScreen.this.searchResults.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.no_results")));
            } else {
                BedrockFriendsScreen.this.searchResults.forEach(user -> this.addEntry(new UserEntry(this, user)));
            }
        }

        private void addWorlds() {
            if (BedrockFriendsScreen.this.worlds == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockFriendsScreen.this.worldsError
                    ? "bedrock_friends.viafabricplus.worlds_failed" : "bedrock_friends.viafabricplus.loading_worlds")));
            } else if (BedrockFriendsScreen.this.worlds.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_friends.viafabricplus.empty")));
            } else {
                BedrockFriendsScreen.this.worlds.forEach(world -> this.addEntry(new WorldEntry(this, world)));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }

        @Override
        protected void updateSlotAmount(final double amount) {
            scrollAmount = amount;
        }

        private @Nullable FriendWorld screenWorld(final String xuid) {
            return BedrockFriendsScreen.this.worlds == null ? null : BedrockFriendsScreen.this.worlds.stream()
                .filter(world -> world.ownerXuid().equals(xuid)).findFirst().orElse(null);
        }

        private boolean showingSearch() {
            return BedrockFriendsScreen.this.view == View.SEARCH;
        }

    }

    private static final class UserEntry extends VFPListEntry {

        private final SlotList list;
        private final SocialUser user;

        private UserEntry(final SlotList list, final SocialUser user) {
            this.list = list;
            this.user = user;
        }

        @Override
        public @NonNull Component getNarration() {
            final String status = Component.translatable(this.user.online()
                ? "bedrock_friends.viafabricplus.online" : "bedrock_friends.viafabricplus.offline").getString();
            final String gamertag = this.user.gamertag().equalsIgnoreCase(this.user.name()) ? "" : ", " + this.user.gamertag();
            final String presence = this.user.presence().isBlank() ? "" : ", " + this.user.presence();
            return Component.literal(this.user.name() + gamertag + ", " + status + presence);
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            final FriendWorld world = this.list.screenWorld(this.user.xuid());
            final Component status = Component.translatable(world != null ? "bedrock_friends.viafabricplus.joinable"
                : this.user.online() ? "bedrock_friends.viafabricplus.online" : "bedrock_friends.viafabricplus.offline");
            final int statusWidth = font.width(status);
            final int textWidth = entryWidth - statusWidth - SLOT_MARGIN * 3 - 8;
            final String detail;
            if (world != null) {
                detail = Component.translatable("bedrock_friends.viafabricplus.playing", world.worldName()).getString();
            } else if (this.list.showingSearch() && !this.user.gamertag().equalsIgnoreCase(this.user.name())) {
                detail = this.user.gamertag();
            } else {
                detail = this.user.presence().isBlank() && !this.user.gamertag().equalsIgnoreCase(this.user.name())
                    ? this.user.gamertag() : this.user.presence();
            }
            final int titleY = detail.isBlank() ? (entryHeight - font.lineHeight) / 2 : SLOT_MARGIN + 1;
            if (this.list.getFocused() == this) {
                graphics.fill(0, 0, 2, entryHeight, ACCENT_COLOR);
            }
            graphics.text(font, fit(font, this.user.name(), textWidth), SLOT_MARGIN, titleY,
                this.list.getFocused() == this ? ACCENT_COLOR : -1);
            graphics.text(font, status, entryWidth - statusWidth - SLOT_MARGIN, titleY,
                world != null ? ACCENT_COLOR : this.user.online() ? -1 : SECONDARY_COLOR);
            if (!detail.isBlank()) {
                graphics.text(font, fit(font, detail, entryWidth - SLOT_MARGIN * 2), SLOT_MARGIN,
                    SLOT_MARGIN + font.lineHeight + 4, SECONDARY_COLOR);
            }
        }

    }

    private static final class WorldEntry extends VFPListEntry {

        private final SlotList list;
        private final FriendWorld world;

        private WorldEntry(final SlotList list, final FriendWorld world) {
            this.list = list;
            this.world = world;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.world.worldName() + ", " + this.world.hostName());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            final String players = Component.translatable("bedrock_friends.viafabricplus.players",
                this.world.players(), this.world.maxPlayers()).getString();
            final int playersWidth = font.width(players);
            final int versionWidth = font.width(this.world.version());
            if (this.list.getFocused() == this) {
                graphics.fill(0, 0, 2, entryHeight, ACCENT_COLOR);
            }
            graphics.text(font, fit(font, this.world.worldName(), entryWidth - playersWidth - SLOT_MARGIN * 3 - 8),
                SLOT_MARGIN, SLOT_MARGIN + 1, this.list.getFocused() == this ? ACCENT_COLOR : -1);
            graphics.text(font, players, entryWidth - playersWidth - SLOT_MARGIN, SLOT_MARGIN + 1, -1);
            graphics.text(font, fit(font, this.world.hostName(), entryWidth - versionWidth - SLOT_MARGIN * 3 - 8),
                SLOT_MARGIN, SLOT_MARGIN + font.lineHeight + 4, SECONDARY_COLOR);
            graphics.text(font, this.world.version(), entryWidth - versionWidth - SLOT_MARGIN,
                SLOT_MARGIN + font.lineHeight + 4, SECONDARY_COLOR);
        }

    }

    private static String fit(final Font font, final String value, final int availableWidth) {
        if (availableWidth <= 0) {
            return "";
        }
        if (font.width(value) <= availableWidth) {
            return value;
        }
        final String ellipsis = "…";
        if (font.width(ellipsis) > availableWidth) {
            return "";
        }
        return font.plainSubstrByWidth(value, availableWidth - font.width(ellipsis)) + ellipsis;
    }

}
