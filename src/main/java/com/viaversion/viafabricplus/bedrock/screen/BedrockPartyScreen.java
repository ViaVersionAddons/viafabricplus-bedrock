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
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService.SocialUser;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyChat;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyInvites;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyInvites.Invite;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyService;
import com.viaversion.viafabricplus.bedrock.visual.BedrockPlayerImages;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyService.Member;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyService.Party;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** Party discovery, membership controls, invitations, and text chat. */
public final class BedrockPartyScreen extends VFPScreen {

    private enum View {
        FIND, PARTY, INVITES, INVITE, CHAT
    }

    private static final int TABS_TOP = 48;
    private static final int LIST_TOP = 76;
    private static final int ROW_WIDTH = 352;
    private static final long MEMBERS_REFRESH = TimeUnit.SECONDS.toNanos(5);
    private static final long MEMBERS_RETRY_DELAY = TimeUnit.SECONDS.toNanos(30);
    private static final long DISCOVERY_REFRESH = TimeUnit.SECONDS.toNanos(30);
    private static final long DISCOVERY_RATE_LIMIT_RETRY = TimeUnit.MINUTES.toNanos(2);

    private View view = View.FIND;
    private @Nullable List<Party> joinable;
    private @Nullable List<SocialUser> friends;
    private final Map<String, String> memberNames = new HashMap<>();
    private final Set<String> requestedNames = new HashSet<>();
    private @Nullable String selfXuid;
    private boolean finding;
    private boolean loadingFriends;
    private boolean loadingSelf;
    private boolean refreshing;
    private boolean loadingInvites;
    private boolean invitesFailed;
    private boolean invitesStarted;
    private boolean busy;
    private boolean findFailed;
    private boolean findRateLimited;
    private boolean membersFailed;
    private long lastMembersRefresh;
    private long lastDiscoveryRefresh;
    private int shownMessages;
    private int shownInvites;
    private PartyList list;
    private @Nullable EditBox chatInput;
    private Button primary;
    private Button secondary;
    private Button tertiary;
    private Button refreshButton;
    private @Nullable Button privacyButton;

    public BedrockPartyScreen() {
        super(Component.translatable("screen.viafabricplus.bedrock_party"), true);
    }

    @Override
    protected void init() {
        final int tabWidth = Math.min(80, (this.width - 20) / View.values().length);
        final int left = (this.width - tabWidth * View.values().length) / 2;
        for (final View tab : View.values()) {
            final Button button = Button.builder(Component.translatable("bedrock_party.viafabricplus.tab."
                    + tab.name().toLowerCase()), _ -> this.show(tab))
                .pos(left + tab.ordinal() * tabWidth, TABS_TOP).size(tabWidth, 20).build();
            button.active = tab != this.view;
            this.addRenderableWidget(button);
        }

        if (this.view == View.PARTY) {
            this.privacyButton = this.addRenderableWidget(Button.builder(Component.empty(), _ -> this.changePrivacy())
                .pos((this.width - 160) / 2, LIST_TOP).size(160, 20).build());
        } else {
            this.privacyButton = null;
        }
        final int bottom = this.view == View.CHAT ? FOOTER_HEIGHT + 25 : FOOTER_HEIGHT;
        this.list = this.addRenderableWidget(new PartyList(this.minecraft, this.width, this.height,
            LIST_TOP + (this.view == View.PARTY ? 24 : 0), bottom,
            this.font.lineHeight * 2 + 12));
        if (this.view == View.CHAT) {
            final int fieldWidth = Math.min(350, this.width - 28);
            this.chatInput = this.addRenderableWidget(new SubmitEditBox(this.font, (this.width - fieldWidth) / 2,
                this.height - FOOTER_HEIGHT - 25, fieldWidth, 20,
                Component.translatable("bedrock_party.viafabricplus.message"), this::sendChat));
            this.chatInput.setHint(Component.translatable("bedrock_party.viafabricplus.message"));
            this.chatInput.setMaxLength(256);
        } else {
            this.chatInput = null;
        }
        this.primary = Button.builder(this.label("primary"), _ -> this.primaryAction()).build();
        this.secondary = Button.builder(this.label("secondary"), _ -> this.secondaryAction()).build();
        this.tertiary = Button.builder(this.label("tertiary"), _ -> this.tertiaryAction()).build();
        this.refreshButton = Button.builder(Component.translatable(this.view == View.PARTY
            ? "bedrock_party.viafabricplus.leave" : "bedrock_party.viafabricplus.refresh"), _ -> {
                if (this.view == View.PARTY) {
                    this.leave();
                } else {
                    this.refresh();
                }
            }).build();
        if (this.view == View.FIND || this.view == View.PARTY) {
            this.addFooter(this.primary, this.secondary, this.tertiary, this.refreshButton);
        } else {
            this.addFooter(this.primary, this.secondary, this.refreshButton);
        }
        super.init();
        this.loadSelf();
        if (!this.invitesFailed && !this.invitesStarted) {
            this.loadInvites();
        }
        if (this.view == View.FIND && this.joinable == null && !this.findFailed) {
            this.find();
        }
        if (this.view == View.INVITE && this.friends == null) {
            this.loadFriends();
        }
        if (this.view == View.PARTY && this.lastMembersRefresh == 0
            && BedrockPartyService.current(this.account()) != null) {
            this.refreshMembers();
        }
    }

    @Override
    public void tick() {
        super.tick();
        final Party party = BedrockPartyService.current(this.account());
        final boolean leader = party != null && party.leaderXuid().equals(this.selfXuid);
        final boolean active = party != null;
        this.primary.active = !this.busy && switch (this.view) {
            case FIND -> !active;
            case PARTY -> active;
            case INVITES -> !active && this.selectedInvite() != null;
            case INVITE -> active && this.selectedFriend() != null;
            case CHAT -> active && this.chatInput != null && !this.chatInput.getValue().isBlank()
                && BedrockPartyService.chatConnected(this.account());
        };
        this.secondary.active = !this.busy && switch (this.view) {
            case FIND -> !active;
            case PARTY -> leader && this.selectedMember() != null && !this.selectedMember().xuid().equals(this.selfXuid);
            case INVITES -> this.selectedInvite() != null;
            case INVITE -> true;
            case CHAT -> active;
        };
        this.tertiary.active = !this.busy && switch (this.view) {
            case FIND -> !active && this.selectedParty() != null;
            case PARTY -> leader && this.selectedMember() != null && !this.selectedMember().xuid().equals(this.selfXuid);
            case INVITES, INVITE, CHAT -> false;
        };
        this.refreshButton.active = !this.busy && !this.finding && !this.loadingFriends && !this.refreshing
            && !this.loadingInvites && !(this.view == View.FIND && this.findRateLimited
                && System.nanoTime() - this.lastDiscoveryRefresh < DISCOVERY_REFRESH);
        if (this.privacyButton != null) {
            this.privacyButton.active = active && leader && !this.busy;
            this.privacyButton.setMessage(Component.translatable(party != null && party.open()
                ? "bedrock_party.viafabricplus.make_private" : "bedrock_party.viafabricplus.make_open"));
        }
        if (active && System.nanoTime() - this.lastMembersRefresh > MEMBERS_REFRESH && !this.refreshing) {
            this.refreshMembers();
        }
        if (this.view == View.FIND && System.nanoTime() - this.lastDiscoveryRefresh > DISCOVERY_REFRESH && !this.finding) {
            this.find();
        }
        if (this.view == View.CHAT) {
            final int size = BedrockPartyService.messages(this.account()).size();
            if (size != this.shownMessages) {
                this.shownMessages = size;
                this.rebuildWidgets();
            }
        }
        if (this.view == View.INVITES && this.account() != null) {
            final int count = BedrockPartyInvites.invitations(this.account()).size();
            if (count != this.shownInvites) {
                this.shownInvites = count;
                this.rebuildWidgets();
            }
        }
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    private Component label(final String action) {
        final String key = "bedrock_party.viafabricplus." + this.view.name().toLowerCase() + "." + action;
        return Component.translatable(key);
    }

    private void show(final View tab) {
        this.view = tab;
        this.rebuildWidgets();
    }

    private @Nullable BedrockAuthManager account() {
        return ViaFabricPlusBedrock.impl().account().get();
    }

    private void loadSelf() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.selfXuid != null || this.loadingSelf) {
            return;
        }
        this.loadingSelf = true;
        account.getXboxUserProfile().getUpToDateAsync().thenAcceptAsync(profile -> {
            this.loadingSelf = false;
            if (this.account() == account) {
                this.selfXuid = profile.getId();
                BedrockPlayerImages.remember(profile.getId(),
                    profile.getSettings().getOrDefault("AppDisplayPicRaw", ""));
            }
        }, Minecraft.getInstance()).exceptionally(error -> this.fail("Failed to load party identity", error));
    }

    private void loadInvites() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.loadingInvites) {
            return;
        }
        this.loadingInvites = true;
        BedrockPartyInvites.start(account).thenRunAsync(() -> {
            this.loadingInvites = false;
            this.invitesFailed = false;
            this.invitesStarted = true;
            if (this.view == View.INVITES) {
                this.rebuildWidgets();
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Failed to receive Bedrock party invitations", error);
            Minecraft.getInstance().execute(() -> {
                this.loadingInvites = false;
                this.invitesFailed = true;
                if (this.view == View.INVITES) {
                    showToast(BedrockXboxError.describe(error));
                    this.rebuildWidgets();
                }
            });
            return null;
        });
    }

    private void loadFriends() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.loadingFriends) {
            return;
        }
        this.loadingFriends = true;
        BedrockSocialService.friends(account).thenAcceptAsync(users -> {
            this.loadingFriends = false;
            if (this.account() == account) {
                this.friends = users;
                if (this.view == View.INVITE || this.view == View.PARTY) {
                    this.rebuildWidgets();
                }
            }
        }, Minecraft.getInstance()).exceptionally(error -> this.fail("Failed to load party invitees", error));
    }

    private void find() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.finding) {
            return;
        }
        this.finding = true;
        this.findFailed = false;
        this.findRateLimited = false;
        this.lastDiscoveryRefresh = System.nanoTime();
        BedrockPartyService.findJoinable(account).thenAcceptAsync(parties -> {
            this.finding = false;
            if (this.account() == account) {
                this.joinable = parties;
                if (this.view == View.FIND) {
                    this.rebuildWidgets();
                }
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            this.findFailed = true;
            this.findRateLimited = BedrockXboxError.isRateLimited(error);
            if (this.findRateLimited) {
                this.lastDiscoveryRefresh = System.nanoTime() + DISCOVERY_RATE_LIMIT_RETRY - DISCOVERY_REFRESH;
            }
            return this.fail("Failed to find Bedrock parties", error);
        });
    }

    private void refreshMembers() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.refreshing || BedrockPartyService.current(account) == null) {
            return;
        }
        this.refreshing = true;
        this.lastMembersRefresh = System.nanoTime();
        BedrockPartyService.refresh(account).thenAcceptAsync(party -> {
            this.refreshing = false;
            this.membersFailed = false;
            this.resolveNames(account, party);
            if (this.view == View.PARTY) {
                this.rebuildWidgets();
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to refresh Bedrock party", error);
            Minecraft.getInstance().execute(() -> {
                this.refreshing = false;
                this.lastMembersRefresh = System.nanoTime() + MEMBERS_RETRY_DELAY - MEMBERS_REFRESH;
                if (!this.membersFailed) {
                    showToast(BedrockXboxError.describe(error));
                    this.membersFailed = true;
                }
            });
            return null;
        });
    }

    private void resolveNames(final BedrockAuthManager account, final Party party) {
        final List<String> missing = party.members().stream().map(Member::xuid)
            .filter(xuid -> !xuid.equals(this.selfXuid) && !this.requestedNames.contains(xuid))
            .toList();
        if (missing.isEmpty()) {
            return;
        }
        this.requestedNames.addAll(missing);
        BedrockSocialService.profileNames(account, missing).thenAcceptAsync(names -> {
            if (this.account() == account) {
                this.memberNames.putAll(names);
                if (this.view == View.PARTY) {
                    this.rebuildWidgets();
                }
            }
        }, Minecraft.getInstance()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().warn("Could not resolve party member names", error);
            Minecraft.getInstance().execute(() -> this.requestedNames.removeAll(missing));
            return null;
        });
    }

    private void refresh() {
        switch (this.view) {
            case FIND -> this.find();
            case PARTY -> this.refreshMembers();
            case INVITES -> {
                this.invitesStarted = false;
                this.loadInvites();
            }
            case INVITE -> this.loadFriends();
            case CHAT -> this.rebuildWidgets();
        }
    }

    private void primaryAction() {
        switch (this.view) {
            case FIND -> this.create(false);
            case PARTY -> this.show(View.INVITE);
            case INVITES -> this.acceptInvite();
            case INVITE -> this.invite();
            case CHAT -> this.sendChat();
        }
    }

    private void secondaryAction() {
        switch (this.view) {
            case FIND -> this.create(true);
            case PARTY -> this.promote();
            case INVITES -> this.ignoreInvite();
            case INVITE -> this.show(View.PARTY);
            case CHAT -> this.show(View.PARTY);
        }
    }

    private void tertiaryAction() {
        switch (this.view) {
            case FIND -> this.join();
            case PARTY -> this.remove();
            case INVITES, INVITE, CHAT -> { }
        }
    }

    private void create(final boolean open) {
        final BedrockAuthManager account = this.account();
        if (account != null && !this.busy) {
            this.run(BedrockPartyService.create(account, open), () -> this.show(View.PARTY));
        }
    }

    private void join() {
        final BedrockAuthManager account = this.account();
        final Party selected = this.selectedParty();
        if (account != null && selected != null && !this.busy) {
            this.run(BedrockPartyService.join(account, selected.id()), () -> this.show(View.PARTY));
        }
    }

    private void invite() {
        final BedrockAuthManager account = this.account();
        final SocialUser selected = this.selectedFriend();
        if (account != null && selected != null && !this.busy) {
            this.run(BedrockPartyService.invite(account, selected.xuid()), () ->
                showToast(Component.translatable("bedrock_party.viafabricplus.invited", selected.name())));
        }
    }

    private void acceptInvite() {
        final BedrockAuthManager account = this.account();
        final Invite selected = this.selectedInvite();
        if (account != null && selected != null && BedrockPartyService.current(account) == null && !this.busy) {
            this.run(BedrockPartyService.acceptInvite(account, selected.partyId(), selected.connectionString()), () -> {
                BedrockPartyInvites.dismiss(selected);
                this.show(View.PARTY);
            });
        }
    }

    private void ignoreInvite() {
        final BedrockAuthManager account = this.account();
        final Invite selected = this.selectedInvite();
        if (account != null && selected != null && !this.busy) {
            this.run(BedrockPartyInvites.ignore(account, selected), () -> this.rebuildWidgets());
        }
    }

    private void promote() {
        final BedrockAuthManager account = this.account();
        final Member selected = this.selectedMember();
        if (account != null && selected != null && !this.busy) {
            this.run(BedrockPartyService.promote(account, selected.xuid()), this::refreshMembers);
        }
    }

    private void changePrivacy() {
        final BedrockAuthManager account = this.account();
        final Party party = BedrockPartyService.current(account);
        if (account != null && party != null && party.leaderXuid().equals(this.selfXuid) && !this.busy) {
            this.run(BedrockPartyService.setPrivacy(account, !party.open()), () -> this.show(View.PARTY));
        }
    }

    private void remove() {
        final BedrockAuthManager account = this.account();
        final Member selected = this.selectedMember();
        if (account == null || selected == null || this.busy) {
            return;
        }
        VFPScreen.setScreen(new ConfirmScreen(confirmed -> {
            VFPScreen.setScreen(this);
            if (confirmed) {
                this.run(BedrockPartyService.remove(account, selected.xuid()), this::refreshMembers);
            }
        }, Component.translatable("bedrock_party.viafabricplus.remove_confirm", this.name(selected.xuid())),
            Component.translatable("bedrock_party.viafabricplus.remove_explanation")));
    }

    private void sendChat() {
        final BedrockAuthManager account = this.account();
        if (account == null || this.chatInput == null || this.chatInput.getValue().isBlank() || this.busy
            || BedrockPartyService.current(account) == null || !BedrockPartyService.chatConnected(account)) {
            return;
        }
        final String message = this.chatInput.getValue();
        this.run(BedrockPartyService.sendChat(account, message), () -> {
            if (this.chatInput != null) {
                this.chatInput.setValue("");
            }
        });
    }

    private void leave() {
        final BedrockAuthManager account = this.account();
        if (account != null && BedrockPartyService.current(account) != null && !this.busy) {
            this.run(BedrockPartyService.leave(account), () -> {
                this.lastMembersRefresh = 0;
                this.show(View.FIND);
                this.find();
            });
        }
    }

    private <T> void run(final CompletableFuture<T> task, final Runnable success) {
        this.busy = true;
        task.thenAcceptAsync(_ -> {
            this.busy = false;
            success.run();
        }, Minecraft.getInstance()).exceptionally(error -> this.fail("Bedrock party action failed", error));
    }

    private Void fail(final String operation, final Throwable error) {
        ViaFabricPlusBedrock.impl().logger().error(operation, error);
        Minecraft.getInstance().execute(() -> {
            this.busy = false;
            this.finding = false;
            this.loadingFriends = false;
            this.loadingSelf = false;
            this.refreshing = false;
            showToast(BedrockXboxError.describe(error));
            this.rebuildWidgets();
        });
        return null;
    }

    private @Nullable Party selectedParty() {
        return this.list.getFocused() instanceof PartyEntry entry ? entry.party : null;
    }

    private @Nullable Invite selectedInvite() {
        return this.list.getFocused() instanceof InviteEntry entry ? entry.invite : null;
    }

    private @Nullable Member selectedMember() {
        return this.list.getFocused() instanceof MemberEntry entry ? entry.member : null;
    }

    private @Nullable SocialUser selectedFriend() {
        return this.list.getFocused() instanceof FriendEntry entry ? entry.user : null;
    }

    private String name(final String xuid) {
        if (xuid.equals(this.selfXuid)) {
            final String display = ViaFabricPlusBedrock.impl().account().displayName();
            return display == null ? Component.translatable("bedrock_party.viafabricplus.you").getString() : display;
        }
        if (this.friends != null) {
            for (final SocialUser friend : this.friends) {
                if (friend.xuid().equals(xuid)) {
                    return friend.name();
                }
            }
        }
        final String memberName = this.memberNames.get(xuid);
        if (memberName != null) {
            return memberName;
        }
        return Component.translatable("bedrock_party.viafabricplus.player").getString();
    }

    private final class PartyList extends ActionList {

        private PartyList(final Minecraft minecraft, final int width, final int height, final int top, final int bottom,
                          final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            switch (BedrockPartyScreen.this.view) {
                case FIND -> this.findEntries();
                case PARTY -> this.memberEntries();
                case INVITES -> this.inviteEntries();
                case INVITE -> this.friendEntries();
                case CHAT -> this.chatEntries();
            }
        }

        @Override
        protected boolean activate(final VFPListEntry entry) {
            if (BedrockPartyScreen.this.busy) {
                return false;
            }
            final BedrockAuthManager account = BedrockPartyScreen.this.account();
            final boolean inParty = account != null && BedrockPartyService.current(account) != null;
            if (BedrockPartyScreen.this.view == View.FIND && entry instanceof PartyEntry && !inParty) {
                BedrockPartyScreen.this.join();
            } else if (BedrockPartyScreen.this.view == View.INVITES && entry instanceof InviteEntry && !inParty) {
                BedrockPartyScreen.this.acceptInvite();
            } else if (BedrockPartyScreen.this.view == View.INVITE && entry instanceof FriendEntry && inParty) {
                BedrockPartyScreen.this.invite();
            } else {
                return false;
            }
            return true;
        }

        private void findEntries() {
            if (BedrockPartyScreen.this.joinable == null) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockPartyScreen.this.findRateLimited
                    ? "bedrock_party.viafabricplus.rate_limited" : BedrockPartyScreen.this.findFailed
                        ? "bedrock_party.viafabricplus.load_failed" : "bedrock_party.viafabricplus.loading")));
            } else if (BedrockPartyScreen.this.joinable.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.none")));
            } else {
                BedrockPartyScreen.this.joinable.forEach(party -> this.addEntry(new PartyEntry(party)));
            }
        }

        private void memberEntries() {
            final Party party = BedrockPartyService.current(BedrockPartyScreen.this.account());
            if (party == null) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.not_in_party")));
                return;
            }
            this.addEntry(new VFPTextEntry(Component.translatable(party.open()
                ? "bedrock_party.viafabricplus.open_count" : "bedrock_party.viafabricplus.private_count",
                party.members().size(), party.maxPlayers())));
            party.members().forEach(member -> this.addEntry(new MemberEntry(member, member.xuid().equals(party.leaderXuid()))));
        }

        private void inviteEntries() {
            final BedrockAuthManager account = BedrockPartyScreen.this.account();
            if (account == null) {
                return;
            }
            final List<Invite> pending = BedrockPartyInvites.invitations(account);
            if (pending.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable(BedrockPartyScreen.this.invitesFailed
                    ? "bedrock_party.viafabricplus.invites_failed" : BedrockPartyScreen.this.loadingInvites
                        ? "bedrock_party.viafabricplus.loading" : "bedrock_party.viafabricplus.no_invites")));
            } else {
                pending.forEach(invite -> this.addEntry(new InviteEntry(invite)));
            }
        }

        private void friendEntries() {
            if (BedrockPartyScreen.this.friends == null) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.loading_friends")));
            } else if (BedrockPartyScreen.this.friends.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.no_friends")));
            } else {
                BedrockPartyScreen.this.friends.forEach(friend -> this.addEntry(new FriendEntry(friend)));
            }
        }

        private void chatEntries() {
            if (BedrockPartyService.current(BedrockPartyScreen.this.account()) == null) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.not_in_party")));
            } else {
                final List<BedrockPartyChat.Message> messages = BedrockPartyService.messages(BedrockPartyScreen.this.account());
                if (messages.isEmpty()) {
                    this.addEntry(new VFPTextEntry(Component.translatable("bedrock_party.viafabricplus.no_messages")));
                } else {
                    messages.forEach(message -> this.addEntry(new ChatEntry(message)));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }

    }

    private abstract static class TextRow extends VFPListEntry {

        protected abstract String title();

        protected String xuid() {
            return "";
        }

        protected String detail() {
            return "";
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.title() + (this.detail().isBlank() ? "" : ", " + this.detail()));
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            final String xuid = this.xuid();
            final int portrait = xuid.isBlank() ? 0 : Math.min(23, entryHeight - 5);
            final int textX = SLOT_MARGIN + (portrait == 0 ? 0 : portrait + 5);
            if (portrait > 0) {
                BedrockPlayerImages.draw(graphics, xuid, SLOT_MARGIN, (entryHeight - portrait) / 2, portrait);
            }
            graphics.text(font, fit(font, this.title(), entryWidth - textX - SLOT_MARGIN), textX,
                this.detail().isBlank() ? (entryHeight - font.lineHeight) / 2 : SLOT_MARGIN + 1, -1);
            if (!this.detail().isBlank()) {
                graphics.text(font, fit(font, this.detail(), entryWidth - textX - SLOT_MARGIN), textX,
                    SLOT_MARGIN + font.lineHeight + 4, 0xFFB8B8B8);
            }
        }

    }

    private final class PartyEntry extends TextRow {

        private final Party party;

        private PartyEntry(final Party party) {
            this.party = party;
        }

        @Override
        protected String title() {
            return BedrockPartyScreen.this.name(this.party.leaderXuid());
        }

        @Override
        protected String xuid() {
            return this.party.leaderXuid();
        }

        @Override
        protected String detail() {
            return Component.translatable("bedrock_party.viafabricplus.open_count", this.party.members().size(),
                this.party.maxPlayers()).getString();
        }

    }

    private final class MemberEntry extends TextRow {

        private final Member member;
        private final boolean leader;

        private MemberEntry(final Member member, final boolean leader) {
            this.member = member;
            this.leader = leader;
        }

        @Override
        protected String title() {
            return BedrockPartyScreen.this.name(this.member.xuid());
        }

        @Override
        protected String xuid() {
            return this.member.xuid();
        }

        @Override
        protected String detail() {
            return Component.translatable(this.leader ? "bedrock_party.viafabricplus.leader"
                : "bedrock_party.viafabricplus.member").getString();
        }

    }

    private static final class InviteEntry extends TextRow {

        private final Invite invite;

        private InviteEntry(final Invite invite) {
            this.invite = invite;
        }

        @Override
        protected String title() {
            return Component.translatable("bedrock_party.viafabricplus.invitation").getString();
        }

        @Override
        protected String detail() {
            return Component.translatable("bedrock_party.viafabricplus.invitation_detail").getString();
        }

    }

    private static final class FriendEntry extends TextRow {

        private final SocialUser user;

        private FriendEntry(final SocialUser user) {
            this.user = user;
        }

        @Override
        protected String title() {
            return this.user.name();
        }

        @Override
        protected String xuid() {
            return this.user.xuid();
        }

        @Override
        protected String detail() {
            return Component.translatable(this.user.online() ? "bedrock_friends.viafabricplus.online"
                : "bedrock_friends.viafabricplus.offline").getString();
        }

    }

    private final class ChatEntry extends TextRow {

        private final BedrockPartyChat.Message message;

        private ChatEntry(final BedrockPartyChat.Message message) {
            this.message = message;
        }

        @Override
        protected String title() {
            final String sender = this.message.sender();
            return sender.matches("[0-9]+") ? BedrockPartyScreen.this.name(sender) : sender;
        }

        @Override
        protected String xuid() {
            final String sender = this.message.sender();
            return sender.matches("[0-9]+") ? sender : "";
        }

        @Override
        protected String detail() {
            return this.message.text();
        }

    }

    private static String fit(final Font font, final String value, final int available) {
        if (available <= 0) {
            return "";
        }
        if (font.width(value) <= available) {
            return value;
        }
        final String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, available - font.width(ellipsis))) + ellipsis;
    }

}
