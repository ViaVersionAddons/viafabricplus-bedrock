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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockSocialService;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmHubService;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmsError;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRelativeTime;
import com.viaversion.viafabricplus.bedrock.visual.BedrockEventImages;
import com.viaversion.viafabricplus.bedrock.visual.BedrockPlayerImages;
import com.viaversion.viafabricplus.bedrock.visual.BedrockRealmImages;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.extra.realms.model.RealmsServer;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** Community, Timeline, World, and Settings for one Bedrock Realm. */
public final class BedrockRealmHubScreen extends VFPScreen {

    private static final int TAB_Y = 48;
    private static final int SUBTAB_Y = 71;
    private static final int LIST_Y = 95;
    private static final int ROW_WIDTH = 620;
    private static final int SECONDARY = 0xFFC9C9C9;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d");
    private static final DateTimeFormatter BACKUP_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    private enum Tab { COMMUNITY, TIMELINE, WORLD, SETTINGS }

    private enum Community { STORIES, MEMBERS }

    private enum World { OVERVIEW, BACKUPS }

    private enum Settings { HUB, SERVER }

    private final RealmsServer realm;
    private final BedrockAuthManager account;
    private final BedrockRealmHubService service;
    private String defaultPermission;
    private final Map<String, String> names = new HashMap<>();
    private final Set<String> pendingNames = new HashSet<>();
    private final Set<String> resolvedProfiles = new HashSet<>();
    private Tab tab = Tab.COMMUNITY;
    private Community community = Community.STORIES;
    private World worldTab = World.OVERVIEW;
    private Settings settingsTab = Settings.HUB;
    private @Nullable JsonObject world;
    private @Nullable JsonObject storySettings;
    private @Nullable JsonObject events;
    private @Nullable JsonObject activity;
    private @Nullable JsonObject backups;
    private boolean worldLoading;
    private boolean storiesLoading;
    private boolean activityLoading;
    private boolean backupsLoading;
    private boolean settingsLoading;
    private boolean busy;
    private boolean showOptedOut;
    private int weekOffset;
    private String memberQuery = "";
    private HubList list;
    private Button primaryButton;
    private Button secondaryButton;
    private Button removeButton;
    private Button refreshButton;

    public BedrockRealmHubScreen(final RealmsServer realm, final BedrockAuthManager account) {
        super(Component.literal(realm.getNameOr("Realm") + " Hub"), true);
        this.realm = realm;
        this.account = account;
        this.service = new BedrockRealmHubService(account, realm.getId());
        this.defaultPermission = string(realm.getRawResponse(), "defaultPermission");
    }

    @Override
    protected void init() {
        final int tabWidth = Math.min(128, (this.width - 20) / 4);
        final int tabX = (this.width - tabWidth * 4) / 2;
        for (final Tab choice : Tab.values()) {
            final Button button = Button.builder(Component.literal(label(choice)), _ -> {
                this.tab = choice;
                this.rebuildWidgets();
                this.loadCurrent();
            }).pos(tabX + choice.ordinal() * tabWidth, TAB_Y).size(tabWidth, 20).build();
            button.active = choice != this.tab;
            this.addRenderableWidget(button);
        }
        if (this.tab == Tab.COMMUNITY) {
            this.addSubtabs(Community.values().length, choice -> label(Community.values()[choice]), choice -> {
                this.community = Community.values()[choice];
                this.rebuildWidgets();
            }, this.community.ordinal());
        } else if (this.tab == Tab.SETTINGS) {
            this.addSubtabs(Settings.values().length, choice -> label(Settings.values()[choice]), choice -> {
                this.settingsTab = Settings.values()[choice];
                this.rebuildWidgets();
            }, this.settingsTab.ordinal());
        } else if (this.tab == Tab.WORLD) {
            this.addSubtabs(World.values().length, choice -> label(World.values()[choice]), choice -> {
                this.worldTab = World.values()[choice];
                this.rebuildWidgets();
            }, this.worldTab.ordinal());
        } else if (this.tab == Tab.TIMELINE) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), _ -> {
                this.weekOffset = Math.min(4, this.weekOffset + 1);
                this.rebuildWidgets();
            }).pos(this.width / 2 - 128, SUBTAB_Y).size(28, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Week of " + this.firstDay()), _ -> {})
                .pos(this.width / 2 - 96, SUBTAB_Y).size(192, 20).build()).active = false;
            final Button next = Button.builder(Component.literal(">"), _ -> {
                this.weekOffset = Math.max(0, this.weekOffset - 1);
                this.rebuildWidgets();
            }).pos(this.width / 2 + 100, SUBTAB_Y).size(28, 20).build();
            next.active = this.weekOffset > 0;
            this.addRenderableWidget(next);
        }
        if (this.tab == Tab.COMMUNITY && this.community == Community.MEMBERS) {
            final EditBox search = this.addRenderableWidget(new EditBox(this.font, this.width / 2 + 140,
                SUBTAB_Y, Math.min(150, this.width / 2 - 150), 20, Component.literal("Search members")));
            search.setHint(Component.literal("Search members"));
            search.setValue(this.memberQuery);
            search.setResponder(value -> {
                this.memberQuery = value.trim();
                this.populate();
            });
        }

        if (this.tab == Tab.WORLD && this.width >= 960) {
            this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> this.renderRealmPreview(graphics));
        }
        this.list = this.addRenderableWidget(new HubList(this.minecraft, this.width, this.height,
            LIST_Y, FOOTER_HEIGHT, this.tab == Tab.TIMELINE ? 30
                : this.tab == Tab.COMMUNITY && this.community == Community.STORIES ? 48 : 37));
        this.primaryButton = Button.builder(Component.literal(""), _ -> this.primary()).build();
        this.secondaryButton = Button.builder(Component.literal(""), _ -> this.secondary()).build();
        this.removeButton = Button.builder(Component.literal("Remove member"), _ -> this.removeMember()).build();
        this.refreshButton = Button.builder(Component.literal("Refresh"), _ -> this.refresh()).build();
        this.addFooter(this.primaryButton, this.secondaryButton, this.removeButton, this.refreshButton);
        this.populate();
        super.init();
        this.loadCurrent();
    }

    @Override
    public void tick() {
        super.tick();
        final HubEntry selected = this.selected();
        final boolean owner = this.isOwner();
        this.primaryButton.visible = this.tab == Tab.TIMELINE || this.tab == Tab.WORLD
            || this.tab == Tab.SETTINGS || this.tab == Tab.COMMUNITY && this.community == Community.MEMBERS;
        this.removeButton.visible = this.tab == Tab.COMMUNITY && this.community == Community.MEMBERS;
        this.primaryButton.setMessage(Component.literal(switch (this.tab) {
            case COMMUNITY -> "Change permission";
            case TIMELINE -> "Opt in or out";
            case WORLD -> this.worldTab == World.BACKUPS ? "Restore backup"
                : selected != null && selected.kind.equals("option") ? "Change world option" : "Activate slot";
            case SETTINGS -> this.settingsTab == Settings.SERVER
                ? selected != null && selected.kind.equals("default-permission") ? "Change default permission" : "Edit Realm info"
                : "Change setting";
        }));
        this.secondaryButton.setMessage(Component.literal(switch (this.tab) {
            case COMMUNITY -> "Invite player";
            case TIMELINE -> this.showOptedOut ? "Show activity" : "Opted-out members";
            case WORLD, SETTINGS -> this.world != null && "OPEN".equals(string(this.world, "state"))
                ? "Close Realm" : "Open Realm";
        }));
        this.primaryButton.active = this.primaryActionAvailable(selected);
        this.secondaryButton.active = !this.busy && this.world != null
            && (this.tab == Tab.TIMELINE || owner && (this.tab == Tab.COMMUNITY
                || this.tab == Tab.WORLD && this.worldTab == World.OVERVIEW));
        this.secondaryButton.visible = this.tab == Tab.COMMUNITY && this.community == Community.MEMBERS
            || this.tab == Tab.WORLD && this.worldTab == World.OVERVIEW || this.tab == Tab.TIMELINE;
        this.removeButton.active = !this.busy && owner && selected != null && selected.kind.equals("member")
            && !selected.id.equals(this.realm.getOwnerUid());
        this.refreshButton.active = !this.busy && !this.worldLoading && !this.storiesLoading
            && !this.activityLoading && !this.settingsLoading && !this.backupsLoading;
    }

    private boolean primaryActionAvailable(final @Nullable HubEntry selected) {
        final boolean owner = this.isOwner();
        return !this.busy && switch (this.tab) {
            case COMMUNITY -> owner && selected != null && selected.kind.equals("member")
                && !selected.id.equals(this.realm.getOwnerUid());
            case TIMELINE -> this.storySettings != null;
            case WORLD -> owner && selected != null && (this.worldTab == World.BACKUPS
                ? selected.kind.equals("backup") : selected.kind.equals("option")
                || selected.kind.equals("slot") && number(this.world, "activeSlot") != Integer.parseInt(selected.id));
            case SETTINGS -> selected != null && (this.settingsTab == Settings.SERVER
                ? owner && (selected.kind.equals("realm-info") || selected.kind.equals("default-permission"))
                : this.storySettings != null && selected.kind.equals("setting")
                    && (selected.id.equals("notifications") || owner));
        };
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    private void renderRealmPreview(final GuiGraphicsExtractor graphics) {
        final int x = (this.width - ROW_WIDTH) / 2 - 172;
        final int y = LIST_Y + 4;
        if (!BedrockRealmImages.drawPreview(graphics, this.world == null ? this.realm.getRawResponse() : this.world,
            x, y, 154, 86)) {
            graphics.centeredText(this.font, Component.translatable("bedrock_realms.viafabricplus.no_preview"),
                x + 77, y + 38, SECONDARY);
        }
        graphics.text(this.font, this.realm.getNameOr("Realm"), x, y + 88, -1);
    }

    private void addSubtabs(final int count, final java.util.function.IntFunction<String> label,
                            final java.util.function.IntConsumer action, final int selected) {
        final int width = 105;
        final int left = (this.width - count * width) / 2;
        for (int index = 0; index < count; index++) {
            final int value = index;
            final Button button = Button.builder(Component.literal(label.apply(index)), _ -> action.accept(value))
                .pos(left + index * width, SUBTAB_Y).size(width, 20).build();
            button.active = index != selected;
            this.addRenderableWidget(button);
        }
    }

    private static String label(final Tab tab) {
        return switch (tab) {
            case COMMUNITY -> "Community";
            case TIMELINE -> "Timeline";
            case WORLD -> "World";
            case SETTINGS -> "Settings";
        };
    }

    private static String label(final Community tab) {
        return tab == Community.STORIES ? "Realm events" : "Members";
    }

    private static String label(final Settings tab) {
        return tab == Settings.HUB ? "Hub" : "Server";
    }

    private static String label(final World tab) {
        return tab == World.OVERVIEW ? "Overview" : "Backups";
    }

    private void loadCurrent() {
        if (this.world == null && !this.worldLoading) {
            this.worldLoading = true;
            this.service.world().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                this.worldLoading = false;
                if (error == null) {
                    this.world = result;
                    this.resolveNames();
                } else {
                    this.report(error);
                }
                this.populate();
            }));
        }
        if (this.tab == Tab.COMMUNITY && this.events == null && !this.storiesLoading) {
            this.storiesLoading = true;
            this.service.events().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                this.storiesLoading = false;
                if (error == null) {
                    this.events = result;
                    this.resolveNames();
                } else {
                    this.report(error);
                }
                this.populate();
            }));
        }
        if (this.tab == Tab.TIMELINE && this.activity == null && !this.activityLoading) {
            this.activityLoading = true;
            this.service.activity().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                this.activityLoading = false;
                if (error == null) {
                    this.activity = result;
                    this.resolveNames();
                } else {
                    this.report(error);
                }
                this.populate();
            }));
        }
        if ((this.tab == Tab.SETTINGS || this.tab == Tab.TIMELINE) && this.storySettings == null && !this.settingsLoading) {
            this.settingsLoading = true;
            this.service.storySettings().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                this.settingsLoading = false;
                if (error == null) {
                    this.storySettings = result;
                } else {
                    this.report(error);
                }
                this.populate();
            }));
        }
        if (this.tab == Tab.WORLD && this.worldTab == World.BACKUPS && this.backups == null
            && !this.backupsLoading && this.isOwner()) {
            this.backupsLoading = true;
            this.service.backups().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                this.backupsLoading = false;
                if (error == null) this.backups = result;
                else this.report(error);
                this.populate();
            }));
        }
    }

    private void refresh() {
        this.world = null;
        if (this.tab == Tab.COMMUNITY) this.events = null;
        if (this.tab == Tab.TIMELINE) this.activity = null;
        if (this.tab == Tab.WORLD && this.worldTab == World.BACKUPS) this.backups = null;
        if (this.tab == Tab.SETTINGS || this.tab == Tab.TIMELINE) this.storySettings = null;
        this.populate();
        this.loadCurrent();
    }

    private void resolveNames() {
        final Set<String> ids = new HashSet<>();
        if (this.world != null) {
            for (final JsonElement member : array(this.world, "players")) {
                final JsonObject player = member.getAsJsonObject();
                final String id = string(player, "uuid");
                final String name = string(player, "name");
                if (!name.isBlank()) this.names.put(id, name);
                if (id.matches("[0-9]+") && !this.resolvedProfiles.contains(id)
                    && !this.pendingNames.contains(id)) ids.add(id);
            }
        }
        if (this.activity != null && object(this.activity, "result") != null) {
            final JsonObject activities = object(object(this.activity, "result"), "activity");
            if (activities != null) {
                for (final String id : activities.keySet()) {
                    if (id.matches("[0-9]+") && !this.resolvedProfiles.contains(id)
                        && !this.pendingNames.contains(id)) ids.add(id);
                }
            }
        }
        if (this.events != null) {
            for (final JsonElement element : array(this.events, "result")) {
                for (final JsonElement player : array(element.getAsJsonObject(), "players")) {
                    final String id = player.getAsString();
                    if (id.matches("[0-9]+") && !this.resolvedProfiles.contains(id)
                        && !this.pendingNames.contains(id)) ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) return;
        this.pendingNames.addAll(ids);
        final List<String> lookup = new ArrayList<>(ids);
        CompletableFuture<Void> request = CompletableFuture.completedFuture(null);
        for (int offset = 0; offset < lookup.size(); offset += 100) {
            final List<String> batch = List.copyOf(lookup.subList(offset, Math.min(lookup.size(), offset + 100)));
            request = request.thenCompose(_ -> BedrockSocialService.profileNames(this.account, batch)
                .handle((found, error) -> {
                    Minecraft.getInstance().execute(() -> {
                        this.pendingNames.removeAll(batch);
                        if (error == null) {
                            this.resolvedProfiles.addAll(batch);
                            this.names.putAll(found);
                            this.populate();
                        }
                    });
                    return null;
                }));
        }
    }

    private void populate() {
        if (this.list == null) return;
        this.list.reset();
        switch (this.tab) {
            case COMMUNITY -> {
                if (this.community == Community.STORIES) this.populateStories();
                else this.populateMembers();
            }
            case TIMELINE -> this.populateTimeline();
            case WORLD -> {
                if (this.worldTab == World.BACKUPS) this.populateBackups();
                else this.populateWorld();
            }
            case SETTINGS -> this.populateSettings();
        }
    }

    private void populateStories() {
        if (this.events == null) {
            this.message(this.storiesLoading ? "Loading stories..." : "Stories are unavailable. Select Refresh to retry.");
            return;
        }
        final JsonArray stories = array(this.events, "result");
        if (stories.isEmpty()) this.message("No Realm events yet.");
        for (int index = 0; index < stories.size(); index++) {
            final JsonObject story = stories.get(index).getAsJsonObject();
            final String event = string(story, "eventName").replaceAll("(?<=[a-z])(?=[A-Z])", " ");
            final List<String> players = new ArrayList<>();
            for (final JsonElement id : array(story, "players")) players.add(this.name(id.getAsString()));
            this.list.append(new HubEntry("story", string(story, "id"), event,
                String.join(", ", players), string(story, "timestamp")));
        }
    }

    private void populateMembers() {
        if (this.world == null) {
            this.message(this.worldLoading ? "Loading members..." : "Members are unavailable. Select Refresh to retry.");
            return;
        }
        final List<JsonObject> players = new ArrayList<>();
        for (final JsonElement member : array(this.world, "players")) players.add(member.getAsJsonObject());
        players.sort(Comparator.comparing((JsonObject player) -> !"Owner".equalsIgnoreCase(string(player, "role")))
            .thenComparing(player -> !bool(player, "online"))
            .thenComparing(player -> this.name(string(player, "uuid")), String.CASE_INSENSITIVE_ORDER));
        int displayed = 0;
        for (final JsonObject player : players) {
            final String id = string(player, "uuid");
            final String name = this.name(id);
            if (!name.toLowerCase(Locale.ROOT).contains(this.memberQuery.toLowerCase(Locale.ROOT))) continue;
            final String state = bool(player, "online") ? "Online" : bool(player, "accepted") ? "Member" : "Invited";
            this.list.append(new HubEntry("member", id, name,
                string(player, "role") + " · " + string(player, "permission"),
                state + (player.has("stories") ? bool(player, "stories") ? " · Timeline on" : " · Timeline off" : "")));
            displayed++;
        }
        if (displayed == 0) this.message("No members match the search.");
    }

    private void populateTimeline() {
        if (this.showOptedOut) {
            this.populateOptedOut();
            return;
        }
        if (this.storySettings != null && !"OPT_IN".equals(string(this.storySettings, "playerOptIn"))) {
            this.message("Opt in to see Realm activity. Your play sessions will then appear here.");
            return;
        }
        if (this.activity == null) {
            this.message(this.activityLoading ? "Loading activity..." : "Timeline is unavailable. Select Refresh to retry.");
            return;
        }
        final JsonObject result = object(this.activity, "result");
        final JsonObject sessions = object(result, "activity");
        if (sessions == null || sessions.isEmpty()) {
            this.message("No activity in this Realm yet.");
            return;
        }
        this.list.append(new HubEntry("timeline-header", "", "Player", "", ""));
        sessions.entrySet().stream().filter(entry -> entry.getValue().isJsonArray() && !entry.getValue().getAsJsonArray().isEmpty())
            .sorted(Comparator.comparing(entry -> this.name(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> this.list.append(new HubEntry("timeline", entry.getKey(), this.name(entry.getKey()), "", "")));
    }

    private void populateOptedOut() {
        if (this.world == null) {
            this.message(this.worldLoading ? "Loading members..." : "Members are unavailable. Select Refresh to retry.");
            return;
        }
        int count = 0;
        for (final JsonElement element : array(this.world, "players")) {
            final JsonObject player = element.getAsJsonObject();
            if (!player.has("stories") || bool(player, "stories") || !bool(player, "accepted")) continue;
            this.list.append(new HubEntry("summary", string(player, "uuid"),
                this.name(string(player, "uuid")), string(player, "role"), "Opted out"));
            count++;
        }
        if (count == 0) this.message("No opted-out members are listed for this Realm.");
    }

    private void populateWorld() {
        if (this.world == null) {
            this.message(this.worldLoading ? "Loading world..." : "World details are unavailable. Select Refresh to retry.");
            return;
        }
        final int active = number(this.world, "activeSlot");
        this.list.append(new HubEntry("summary", "", string(this.world, "name"),
            string(this.world, "motd"), string(this.world, "state") + " · " + number(this.world, "maxPlayers") + " players"));
        for (final JsonElement element : array(this.world, "slots")) {
            final JsonObject slot = element.getAsJsonObject();
            final int id = number(slot, "slotId");
            final JsonObject options = slotOptions(slot);
            if (options == null) continue;
            final String name = string(options, "slotName").isBlank() ? "World " + id : string(options, "slotName");
            final String mode = bool(options, "hardcore") ? "Hardcore" : number(options, "gameMode") == 1 ? "Creative" : "Survival";
            this.list.append(new HubEntry("slot", String.valueOf(id), name,
                mode + " · " + string(options, "version"), id == active ? "Active world" : "Slot " + id));
            if (id == active) {
                this.worldOption("gameMode", "Game mode", number(options, "gameMode") == 1 ? "Creative" : "Survival");
                this.worldOption("difficulty", "Difficulty", difficulty(number(options, "difficulty")));
                this.worldOption("pvp", "Player versus player", onOff(options, "pvp"));
                this.worldOption("cheatsAllowed", "Cheats", onOff(options, "cheatsAllowed"));
                this.worldOption("spawnMonsters", "Spawn monsters", onOff(options, "spawnMonsters"));
                this.worldOption("spawnAnimals", "Spawn animals", onOff(options, "spawnAnimals"));
            }
        }
    }

    private void populateBackups() {
        if (!this.isOwner()) {
            this.message("Only the Realm owner can view or restore backups.");
            return;
        }
        if (this.backups == null) {
            this.message(this.backupsLoading ? "Loading backups..." : "Backups are unavailable. Select Refresh to retry.");
            return;
        }
        final JsonArray available = array(this.backups, "backups");
        if (available.isEmpty()) {
            this.message("No backups are available for this Realm.");
            return;
        }
        for (int index = 0; index < available.size(); index++) {
            final JsonObject backup = available.get(index).getAsJsonObject();
            final String id = string(backup, "backupId");
            if (id.isBlank()) continue;
            final long timestamp = backup.has("lastModifiedDate") && backup.get("lastModifiedDate").isJsonPrimitive()
                ? backup.get("lastModifiedDate").getAsLong() : 0L;
            final String date = timestamp > 0 ? BACKUP_DATE.format(Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())) : "Date unavailable";
            final long size = backup.has("size") && backup.get("size").isJsonPrimitive()
                ? backup.get("size").getAsLong() : 0L;
            this.list.append(new HubEntry("backup", id, "Backup " + (index + 1), date,
                size > 0 ? size / (1024 * 1024) + " MB" : ""));
        }
    }

    private void populateSettings() {
        if (this.settingsTab == Settings.HUB && this.storySettings == null) {
            this.message(this.settingsLoading ? "Loading settings..." : "Settings are unavailable. Select Refresh to retry.");
            return;
        }
        if (this.settingsTab == Settings.HUB) {
            this.setting("notifications", "Badge notifications", "Show unread stories");
            this.setting("autostories", "Realm Events", "Post gameplay accomplishments");
            this.setting("coordinates", "Realm Event coordinates", "Show coordinates in events");
            this.setting("optInRequired", "Require Timeline opt in", "Members must opt in before playing");
            this.setting("timeline", "Timeline", "Let members view activity");
            if (this.storySettings.has("inGameChatMessages")) {
                this.setting("inGameChatMessages", "In-game chat messages", "Show chat in Realm Stories");
            }
        } else if (this.world != null) {
            this.list.append(new HubEntry("realm-info", "", "Realm name", string(this.world, "name"), "Edit"));
            this.list.append(new HubEntry("realm-info", "", "Description", string(this.world, "motd"), "Edit"));
            this.list.append(new HubEntry("summary", "", "Player limit",
                number(this.world, "maxPlayers") + " players", ""));
            this.list.append(new HubEntry("default-permission", "", "Default player permission",
                "Applies to new members", this.defaultPermission));
            final JsonObject activeOptions = this.activeOptions();
            if (activeOptions != null) {
                if (activeOptions.has("renderDistance")) this.list.append(new HubEntry("summary", "",
                    "Render distance limit", number(activeOptions, "renderDistance") + " chunks", ""));
                if (activeOptions.has("simDistance")) this.list.append(new HubEntry("summary", "",
                    "Simulation distance", number(activeOptions, "simDistance") + " chunks", ""));
            }
            this.list.append(new HubEntry("summary", "", "Realm state", string(this.world, "state"), ""));
            this.list.append(new HubEntry("summary", "", "Subscription",
                this.realm.getDaysLeft() + " days remaining", ""));
        } else {
            this.message(this.worldLoading ? "Loading server settings..." : "Server settings are unavailable.");
        }
    }

    private void worldOption(final String key, final String label, final String value) {
        this.list.append(new HubEntry("option", key, label, "Active world", value));
    }

    private static String onOff(final JsonObject options, final String key) {
        return bool(options, key) ? "On" : "Off";
    }

    private static String difficulty(final int value) {
        return switch (value) {
            case 0 -> "Peaceful";
            case 1 -> "Easy";
            case 2 -> "Normal";
            case 3 -> "Hard";
            default -> "Unknown";
        };
    }

    private void setting(final String key, final String label, final String description) {
        this.list.append(new HubEntry("setting", key, label, description,
            bool(this.storySettings, key) ? "On" : "Off"));
    }

    private void message(final String text) {
        this.list.append(new VFPTextEntry(Component.literal(text)));
    }

    private void primary() {
        final HubEntry selected = this.selected();
        switch (this.tab) {
            case COMMUNITY -> {
                if (selected != null) this.changePermission(selected.id);
            }
            case TIMELINE -> this.changeOptIn();
            case WORLD -> {
                if (selected != null) {
                    if (selected.kind.equals("backup")) this.restoreBackup(selected.id);
                    else if (selected.kind.equals("option")) this.changeWorldOption(selected.id);
                    else this.activateSlot(selected.id);
                }
            }
            case SETTINGS -> {
                if (selected != null) {
                    if (this.settingsTab == Settings.SERVER) {
                        if (selected.kind.equals("default-permission")) this.changeDefaultPermission();
                        else this.editRealmInfo();
                    }
                    else this.toggleSetting(selected.id);
                }
            }
        }
    }

    private void secondary() {
        if (this.tab == Tab.COMMUNITY) {
            new BedrockRealmInviteScreen(this.account, this.service, () -> {
                this.world = null;
                this.loadCurrent();
            }).open(this);
            return;
        }
        if (this.tab == Tab.TIMELINE) {
            this.showOptedOut = !this.showOptedOut;
            this.populate();
            return;
        }
        if (this.world == null) return;
        final boolean open = !"OPEN".equals(string(this.world, "state"));
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.setOpen(open), () -> this.world = null);
        }, Component.literal(open ? "Open Realm?" : "Close Realm?"),
            Component.literal(open ? "Members can join again." : "Members will be unable to join until you reopen it.")));
    }

    private void removeMember() {
        final HubEntry selected = this.selected();
        if (selected == null || !selected.kind.equals("member")) return;
        final String id = selected.id;
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.updateInvite(id, false), () -> this.world = null);
        }, Component.literal("Remove " + this.name(id) + "?"),
            Component.literal("This player will lose access to the Realm.")));
    }

    private void changePermission(final String id) {
        final JsonObject player = this.player(id);
        if (player == null) return;
        final String current = string(player, "permission");
        final String next = switch (current) {
            case "VISITOR" -> "MEMBER";
            case "MEMBER" -> "OPERATOR";
            default -> "VISITOR";
        };
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.changePermission(id, next), () -> this.world = null);
        }, Component.literal("Change player permission?"),
            Component.literal(this.name(id) + ": " + current + " to " + next)));
    }

    private void activateSlot(final String id) {
        final int slot = Integer.parseInt(id);
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.activateSlot(slot), () -> this.world = null);
        }, Component.literal("Activate world slot " + slot + "?"),
            Component.literal("The Realm will switch to this world.")));
    }

    private void restoreBackup(final String id) {
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.restoreBackup(id), () -> {
                this.backups = null;
                this.world = null;
            });
        }, Component.literal("Restore this backup?"),
            Component.literal("The active Realm world will be replaced with this backup.")));
    }

    private void changeWorldOption(final String key) {
        final JsonObject options = this.activeOptions();
        if (options == null || !options.has(key)) return;
        final JsonObject updated = options.deepCopy();
        if (key.equals("gameMode")) updated.addProperty(key, number(options, key) == 1 ? 0 : 1);
        else if (key.equals("difficulty")) updated.addProperty(key, (number(options, key) + 1) % 4);
        else updated.addProperty(key, !bool(options, key));
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.saveWorldOptions(string(this.world, "name"),
                string(this.world, "motd"), updated), () -> this.world = null);
        }, Component.literal("Change world option?"),
            Component.literal("The active Realm world may restart to apply this change.")));
    }

    private void editRealmInfo() {
        if (this.world == null) return;
        new BedrockRealmInfoScreen(this.service, string(this.world, "name"), string(this.world, "motd"),
            () -> {
                this.world = null;
                this.loadCurrent();
            }).open(this);
    }

    private void changeOptIn() {
        if (this.storySettings == null) return;
        final boolean optIn = !"OPT_IN".equals(string(this.storySettings, "playerOptIn"));
        this.mutate(this.service.updateTimelineOptIn(optIn), () -> this.storySettings = null);
    }

    private void toggleSetting(final String key) {
        if (this.storySettings == null || !this.storySettings.has(key)) return;
        this.mutate(this.service.updateStorySetting(key, !bool(this.storySettings, key)), () -> this.storySettings = null);
    }

    private void changeDefaultPermission() {
        if (this.world == null) return;
        final String current = this.defaultPermission;
        final String next = switch (current) {
            case "VISITOR" -> "MEMBER";
            case "MEMBER" -> "OPERATOR";
            default -> "VISITOR";
        };
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            this.minecraft.gui.setScreen(this);
            if (confirmed) this.mutate(this.service.changeDefaultPermission(next), () -> {
                this.defaultPermission = next;
            });
        }, Component.literal("Change default player permission?"),
            Component.literal("New members will join as " + next.toLowerCase(Locale.ROOT) + ".")));
    }

    private void mutate(final java.util.concurrent.CompletableFuture<JsonObject> operation, final Runnable invalidate) {
        this.busy = true;
        operation.whenComplete((_, error) -> Minecraft.getInstance().execute(() -> {
            this.busy = false;
            if (error != null) this.report(error);
            else {
                invalidate.run();
                this.loadCurrent();
            }
            this.populate();
        }));
    }

    private @Nullable JsonObject player(final String id) {
        if (this.world == null) return null;
        for (final JsonElement element : array(this.world, "players")) {
            final JsonObject player = element.getAsJsonObject();
            if (id.equals(string(player, "uuid"))) return player;
        }
        return null;
    }

    private @Nullable JsonObject activeOptions() {
        if (this.world == null) return null;
        final int active = number(this.world, "activeSlot");
        for (final JsonElement element : array(this.world, "slots")) {
            final JsonObject slot = element.getAsJsonObject();
            if (number(slot, "slotId") == active) return slotOptions(slot);
        }
        return null;
    }

    private static @Nullable JsonObject slotOptions(final JsonObject slot) {
        try {
            final JsonElement parsed = JsonParser.parseString(string(slot, "options"));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private @Nullable HubEntry selected() {
        return this.list.getFocused() instanceof HubEntry entry ? entry : null;
    }

    private boolean isOwner() {
        final var profile = this.account.getXboxUserProfile().getCached();
        return this.realm.getOwnerUid() != null
            && profile != null && this.realm.getOwnerUid().equals(profile.getId());
    }

    private String name(final String id) {
        return this.names.getOrDefault(id, id.matches("[0-9]+")
            ? "Xbox player …" + id.substring(Math.max(0, id.length() - 6)) : id);
    }

    private LocalDate firstDay() {
        return LocalDate.now().minusDays(6L + this.weekOffset * 7L);
    }

    private void report(final Throwable error) {
        ViaFabricPlusBedrock.impl().logger().error("Realm Hub request failed", error);
        showToast(BedrockRealmsError.describe(error));
    }

    private static @Nullable JsonObject object(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray array(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && !parent.get(key).isJsonNull() ? parent.get(key).getAsString() : "";
    }

    private static int number(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsInt() : 0;
    }

    private static boolean bool(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonPrimitive() && parent.get(key).getAsBoolean();
    }

    private final class HubList extends ActionList {
        private HubList(final Minecraft minecraft, final int width, final int height, final int top,
                        final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
        }

        private void reset() {
            this.clearEntries();
        }

        private void append(final VFPListEntry entry) {
            this.addEntry(entry);
        }

        @Override
        protected boolean activate(final VFPListEntry entry) {
            if (entry instanceof HubEntry selected && BedrockRealmHubScreen.this.tab != Tab.TIMELINE
                && BedrockRealmHubScreen.this.primaryActionAvailable(selected)) {
                BedrockRealmHubScreen.this.primary();
                return true;
            }
            return false;
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }
    }

    private final class HubEntry extends VFPListEntry {
        private final String kind;
        private final String id;
        private final String title;
        private final String description;
        private final String detail;
        private final @Nullable Instant eventTimestamp;

        private HubEntry(final String kind, final String id, final String title,
                         final String description, final String detail) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.description = description;
            this.detail = detail;
            this.eventTimestamp = kind.equals("story") ? BedrockRelativeTime.parse(detail) : null;
        }

        private String displayDetail() {
            return this.kind.equals("story")
                ? BedrockRelativeTime.format(this.eventTimestamp, Instant.now(), ZoneId.systemDefault())
                : this.detail;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.title + " " + this.description + " " + this.displayDetail());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int width, final int height) {
            final Font font = Minecraft.getInstance().font;
            final int color = BedrockRealmHubScreen.this.list.getFocused() == this ? ACCENT_COLOR : -1;
            if (this.kind.startsWith("timeline")) {
                this.renderTimeline(graphics, width, height, font, color);
                return;
            }
            if (this.kind.equals("story")) {
                final String event = this.title.replace(" ", "");
                final boolean hasImage = BedrockEventImages.hasImage(event);
                final int textX = hasImage ? 67 : 7;
                if (hasImage) BedrockEventImages.draw(graphics, event, 5, (height - 32) / 2, 56, 32);
                graphics.text(font, clip(font, this.title, width - textX - 7), textX, 4, color);
                graphics.text(font, clip(font, this.displayDetail(), width - textX - 7), textX,
                    7 + font.lineHeight, SECONDARY);
                if (!this.description.isBlank()) {
                    graphics.text(font, clip(font, this.description, width - textX - 7), textX,
                        10 + font.lineHeight * 2, SECONDARY);
                }
                return;
            }
            final boolean portrait = this.kind.equals("member")
                || this.kind.equals("summary") && this.id.matches("[0-9]+");
            final int textX = portrait ? 42 : 7;
            if (portrait) {
                BedrockPlayerImages.draw(graphics, this.id, 5, 3, 30);
            }
            graphics.text(font, clip(font, this.title, width - textX - 125), textX, 4, color);
            final String displayDetail = this.displayDetail();
            if (!displayDetail.isBlank()) graphics.text(font, clip(font, displayDetail, 120), width - 125, 4, SECONDARY);
            if (!this.description.isBlank()) graphics.text(font, clip(font, this.description, width - textX - 8), textX,
                5 + font.lineHeight, SECONDARY);
        }

        private void renderTimeline(final GuiGraphicsExtractor graphics, final int width, final int height,
                                    final Font font, final int color) {
            final int labelWidth = Math.min(140, width / 3);
            final int labelX = this.kind.equals("timeline-header") ? 7 : 33;
            if (!this.kind.equals("timeline-header")) {
                BedrockPlayerImages.draw(graphics, this.id, 5, 3, 24);
            }
            graphics.text(font, clip(font, this.title, labelWidth - labelX - 3), labelX, 9, color);
            final int dayWidth = (width - labelWidth) / 7;
            for (int day = 0; day < 7; day++) {
                final LocalDate date = BedrockRealmHubScreen.this.firstDay().plusDays(day);
                final int x = labelWidth + day * dayWidth;
                graphics.fill(x, 0, x + 1, height - 2, 0xFF1D1E20);
                if (this.kind.equals("timeline-header")) {
                    graphics.text(font, DAY.format(date), x + 4, 9, SECONDARY);
                    continue;
                }
                final JsonObject sessions = object(object(BedrockRealmHubScreen.this.activity, "result"), "activity");
                if (sessions == null || !sessions.has(this.id)) continue;
                final long start = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                final long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                for (final JsonElement element : sessions.getAsJsonArray(this.id)) {
                    final JsonObject session = element.getAsJsonObject();
                    final long begin = Math.max(start, session.get("s").getAsLong());
                    final long finish = Math.min(end, session.get("e").getAsLong());
                    if (finish <= begin) continue;
                    final int barX = x + 3 + (int) ((begin - start) * Math.max(1, dayWidth - 6) / (end - start));
                    final int barEnd = x + 3 + (int) ((finish - start) * Math.max(1, dayWidth - 6) / (end - start));
                    graphics.fill(barX, 5, Math.max(barX + 2, barEnd), height - 7, ACCENT_COLOR);
                }
            }
        }

        private static String clip(final Font font, final String value, final int width) {
            return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - 8)) + "…";
        }
    }
}
