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
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.BedrockConnectionUtil;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetJsonRpcAddress;
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmsError;
import com.viaversion.viafabricplus.bedrock.visual.BedrockPlayerImages;
import com.viaversion.viafabricplus.bedrock.visual.BedrockRealmImages;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import com.viaversion.viafabricplus.util.network.ConnectionUtil;
import org.cloudburstmc.netty.channel.nethernet.config.NetherNetAddress;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.extra.realms.model.RealmsJoinInformation;
import net.raphimc.minecraftauth.extra.realms.model.RealmsServer;
import net.raphimc.minecraftauth.extra.realms.service.impl.BedrockRealmsService;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public final class BedrockRealmsScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_realms");

    private static final int ROW_WIDTH = 360;

    private static @Nullable List<RealmsServer> realmsServers;
    private static @Nullable BedrockRealmsService service;
    private static boolean loading;
    private static Component status = Component.translatable("bedrock_realms.viafabricplus.warning");

    private SlotList list;
    private boolean requested;
    private boolean joining;
    private Button joinButton;
    private Button leaveButton;
    private Button hubButton;
    private Button refreshButton;

    public BedrockRealmsScreen() {
        super(TITLE, true);
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    /**
     * Drops the cached realms so the next opened screen requests them again.
     */
    public static void invalidate() {
        realmsServers = null;
        service = null;
    }

    @Override
    protected void init() {
        if (!this.requested && !loading) {
            invalidate(); // A newly opened screen needs the Realm's current open and subscription state.
        }
        if (realmsServers == null && !this.requested) {
            this.load();
        }

        this.list = this.addRenderableWidget(new SlotList(this, this.minecraft, this.width, this.height, CONTENT_TOP, FOOTER_HEIGHT,
            (this.font.lineHeight + 2) * 3 /* name, version and motd */));
        this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> this.renderRealmDetails(graphics));

        this.joinButton = Button.builder(Component.translatable("bedrock_realms.viafabricplus.join"), _ -> this.join()).build();
        this.joinButton.active = false;

        this.leaveButton = Button.builder(Component.translatable("bedrock_realms.viafabricplus.leave"), _ -> this.leave()).build();
        this.leaveButton.active = false;

        this.hubButton = Button.builder(Component.literal("Realm Hub"), _ -> this.openHub()).build();
        this.hubButton.active = false;

        final Button inviteButton = Button.builder(Component.translatable("bedrock_realms.viafabricplus.invite"), _ ->
            new AcceptInvitationCodeScreen(this::acceptInvite).open(this)).build();
        inviteButton.active = service != null;

        this.refreshButton = Button.builder(Component.translatable("bedrock_realms.viafabricplus.refresh"), _ -> this.refresh()).build();
        this.addFooter(this.joinButton, this.hubButton, this.leaveButton, inviteButton, this.refreshButton);

        super.init();
    }

    @Override
    public void tick() {
        super.tick();

        final boolean selected = this.list.getFocused() instanceof SlotEntry;
        this.joinButton.active = selected && !this.joining;
        this.leaveButton.active = selected && !this.joining;
        this.hubButton.active = selected && !this.joining;
        this.refreshButton.active = !loading && !this.joining;
    }

    // Guarded against the screen being rebuilt while the request is still running, which would send it again
    private void load() {
        if (loading) {
            return;
        }

        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null) {
            status = Component.translatable("bedrock_realms.viafabricplus.warning");
            return;
        }

        loading = true;
        this.requested = true;
        status = Component.translatable("bedrock_realms.viafabricplus.availability_check");

        final BedrockRealmsService realmsService = new BedrockRealmsService(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME, account.getRealmsXstsToken());
        realmsService.isCompatibleAsync().thenAccept(compatible -> {
            if (!compatible) {
                loading = false;
                status = Component.translatable("bedrock_realms.viafabricplus.unavailable");
                Minecraft.getInstance().execute(this::rebuildWidgets);
                return;
            }

            realmsService.getWorldsAsync().thenAccept(worlds -> {
                realmsServers = new ArrayList<>(worlds);
                service = realmsService;
                loading = false;
                Minecraft.getInstance().execute(this::rebuildWidgets);
                final List<String> owners = worlds.stream().map(world -> world.getOwnerUidOr(""))
                    .filter(id -> id.matches("[0-9]+"))
                    .distinct().limit(100).toList();
                BedrockSocialService.profileNames(account, owners).exceptionally(error -> {
                    ViaFabricPlusBedrock.impl().logger().debug("Could not load Realm owner pictures", error);
                    return java.util.Map.of();
                });
            }).exceptionally(throwable -> this.fail("Failed to load the realm worlds", throwable));
        }).exceptionally(throwable -> this.fail("Failed to check the realms availability", throwable));
    }

    private void refresh() {
        if (loading) {
            return;
        }
        invalidate();
        this.requested = false;
        this.load();
        this.rebuildWidgets();
    }

    private void join() {
        final RealmsServer realmsServer = ((SlotEntry) this.list.getFocused()).realmsServer;
        this.join(realmsServer);
    }

    private void openHub() {
        final RealmsServer realm = ((SlotEntry) this.list.getFocused()).realmsServer;
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account != null) {
            new BedrockRealmHubScreen(realm, account).open(this);
        }
    }

    private void renderRealmDetails(final GuiGraphicsExtractor graphics) {
        if (this.width < 780 || !(this.list.getFocused() instanceof SlotEntry selected)) {
            return;
        }
        final RealmsServer realm = selected.realmsServer;
        final int x = this.width / 2 + ROW_WIDTH / 2 + 16;
        final int panelWidth = this.width - x - 12;
        final int top = CONTENT_TOP + 4;
        final int bottom = this.height - FOOTER_HEIGHT - 5;
        final int textX = x + 10;
        final int textWidth = panelWidth - 20;
        int y = top + 10;
        final int availableHeight = bottom - top;
        if (availableHeight >= 270) {
            final int previewHeight = Math.min(110, Math.max(64, availableHeight - 230));
            if (!BedrockRealmImages.drawPreview(graphics, realm.getRawResponse(), textX, y, textWidth, previewHeight)) {
                graphics.centeredText(this.font, Component.translatable("bedrock_realms.viafabricplus.no_preview"),
                    textX + textWidth / 2, y + (previewHeight - this.font.lineHeight) / 2, 0xFFAAAAAA);
            }
            y += previewHeight + 12;
        }
        BedrockPlayerImages.draw(graphics, realm.getOwnerUidOr(""), textX, y, 27);
        y = this.detailLine(graphics, realm.getNameOr("Realm"), textX + 34, y, textWidth - 34, 0xFFFFFFFF, 16);
        y = this.detailLine(graphics, "Owned by " + realm.getOwnerNameOr("Unknown"), textX + 34, y, textWidth - 34,
            0xFFD0D2D5, 15);
        y += 8;
        y = this.detailLine(graphics, realm.getMotdOr("No description"), textX, y, textWidth, 0xFFD0D2D5, 28);
        y += 8;
        y = this.detailLine(graphics, "Status: " + (realm.isExpired() ? "Expired" : realm.getState()),
            textX, y, textWidth, 0xFFFFFFFF, 14);
        y = this.detailLine(graphics, "Players: " + realm.getMaxPlayers() + " maximum", textX, y, textWidth,
            0xFFFFFFFF, 14);
        y = this.detailLine(graphics, "World: " + realm.getWorldType(), textX, y, textWidth,
            0xFFFFFFFF, 14);
        if (!realm.getActiveVersionOr("").isBlank()) {
            y = this.detailLine(graphics, "Version: " + realm.getActiveVersion(), textX, y, textWidth,
                0xFFFFFFFF, 14);
        }
        if (realm.getDaysLeft() >= 0) {
            this.detailLine(graphics, "Subscription: " + realm.getDaysLeft() + " days left", textX, y,
                textWidth, 0xFFFFFFFF, 14);
        }
    }

    private int detailLine(final GuiGraphicsExtractor graphics, final String value, final int x,
                           final int y, final int width, final int color, final int spacing) {
        final String clipped = this.font.width(value) <= width ? value
            : this.font.plainSubstrByWidth(value, Math.max(0, width - this.font.width("…"))) + "…";
        graphics.text(this.font, clipped, x, y, color);
        return y + spacing;
    }

    private void join(final RealmsServer realmsServer) {
        if (this.joining) {
            return;
        }
        if (realmsServer.isExpired()) {
            showToast(Component.translatable("bedrock_realms.viafabricplus.expired"));
            return;
        } else if ("CLOSED".equalsIgnoreCase(realmsServer.getState())) {
            showToast(Component.translatable("bedrock_realms.viafabricplus.closed"));
            return;
        } else if (!realmsServer.isCompatible()) {
            showToast(Component.translatable("bedrock_realms.viafabricplus.incompatible"));
            return;
        }

        this.joining = true;
        service.joinWorldAsync(realmsServer)
            .whenComplete((server, error) -> Minecraft.getInstance().execute(() -> {
                this.joining = false;
                if (error == null) {
                    this.connect(server);
                } else if (BedrockRealmsError.timelineOptInRequired(error)) {
                    final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
                    if (account != null) {
                        new BedrockRealmTimelineScreen(service, realmsServer, () -> this.join(realmsServer)).open(this);
                    } else {
                        this.fail("Bedrock account was removed while joining the realm", error);
                    }
                } else if (BedrockRealmsError.subscriptionMissing(error)) {
                    showToast(BedrockRealmsError.describe(error));
                    this.refresh();
                } else {
                    this.fail("Failed to join the realm", error);
                }
            }));
    }

    private void connect(final RealmsJoinInformation server) {
        final String protocol = server.getNetworkProtocol();
        if (protocol.equalsIgnoreCase(RealmsJoinInformation.PROTOCOL_DEFAULT)) {
            ConnectionUtil.connect(server.getAddress(), BedrockProtocolVersion.bedrockLatest);
        } else if (protocol.equalsIgnoreCase(RealmsJoinInformation.PROTOCOL_NETHERNET)) {
            BedrockConnectionUtil.connectNetherNet(new NetherNetAddress(server.getAddress()));
        } else if (protocol.equalsIgnoreCase(RealmsJoinInformation.PROTOCOL_NETHERNET_JSONRPC)) {
            BedrockConnectionUtil.connectNetherNet(new NetherNetJsonRpcAddress(server.getAddress()));
        } else {
            showToast(Component.translatable("bedrock_realms.viafabricplus.unsupported_protocol", protocol));
        }
    }

    private void leave() {
        final RealmsServer realmsServer = ((SlotEntry) this.list.getFocused()).realmsServer;
        service.leaveInvitedRealmAsync(realmsServer).thenAccept(_ -> {
            realmsServers.remove(realmsServer);
            Minecraft.getInstance().execute(this::rebuildWidgets);
        }).exceptionally(throwable -> this.fail("Failed to leave the realm", throwable));
    }

    private void acceptInvite(final String code) {
        service.acceptInviteAsync(code).thenAccept(realmsServer -> {
            realmsServers.add(realmsServer);
            Minecraft.getInstance().execute(this::rebuildWidgets);
        }).exceptionally(throwable -> this.fail("Failed to accept the invite", throwable));
    }

    private Void fail(final String message, final Throwable throwable) {
        ViaFabricPlusBedrock.impl().logger().error(message, throwable);
        Minecraft.getInstance().execute(() -> {
            loading = false;
            status = BedrockRealmsError.describe(throwable);
            showToast(status);
            this.rebuildWidgets();
        });
        return null;
    }

    public static class SlotList extends ActionList {

        private static double scrollAmount;
        private final BedrockRealmsScreen screen;

        public SlotList(final BedrockRealmsScreen screen, final Minecraft minecraftClient, final int width, final int height,
                        final int top, final int bottom, final int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);
            this.screen = screen;

            if (realmsServers == null) { // The realms are either still loading, unavailable or the request failed
                this.addEntry(new VFPTextEntry(status));
                return;
            }
            if (realmsServers.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_realms.viafabricplus.no_worlds")));
                return;
            }

            realmsServers.forEach(realmsServer -> this.addEntry(new SlotEntry(this, realmsServer)));
            // Needs calling last to have the entries added before setting the scroll amount
            this.setScrollAmount(scrollAmount);
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }

        @Override
        protected boolean activate(final VFPListEntry entry) {
            if (entry instanceof SlotEntry realm && !this.screen.joining) {
                this.screen.join(realm.realmsServer);
                return true;
            }
            return false;
        }

        @Override
        protected void updateSlotAmount(final double amount) {
            scrollAmount = amount;
        }

    }

    public static class SlotEntry extends VFPListEntry {

        private final SlotList slotList;
        private final RealmsServer realmsServer;

        public SlotEntry(final SlotList slotList, final RealmsServer realmsServer) {
            this.slotList = slotList;
            this.realmsServer = realmsServer;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.nullToEmpty(this.realmsServer.getName());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor context, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            final int portrait = Math.min(25, entryHeight - 5);
            final int textX = SLOT_MARGIN + portrait + 5;
            BedrockPlayerImages.draw(context, this.realmsServer.getOwnerUidOr(""), SLOT_MARGIN,
                (entryHeight - portrait) / 2, portrait);

            final StringBuilder name = new StringBuilder();
            final String ownerName = this.realmsServer.getOwnerName();
            if (ownerName != null && !ownerName.isBlank()) {
                name.append(ownerName).append(" - ");
            }
            final String worldName = this.realmsServer.getName();
            if (worldName != null && !worldName.isBlank()) {
                name.append(worldName);
            }
            final String state = this.realmsServer.isExpired() ? Component.translatable("bedrock_realms.viafabricplus.expired_label").getString()
                : "CLOSED".equalsIgnoreCase(this.realmsServer.getState()) ? Component.translatable("bedrock_realms.viafabricplus.closed_label").getString()
                    : this.realmsServer.getState();
            name.append(" (").append(state).append(")");

            final String version = this.version();
            final int availableWidth = entryWidth - font.width(version) - textX - SLOT_MARGIN - 8;
            context.text(font, fit(font, name.toString(), availableWidth), textX, SLOT_MARGIN,
                this.slotList.getFocused() == this ? ACCENT_COLOR : -1);
            context.text(font, version, entryWidth - font.width(version) - SLOT_MARGIN, SLOT_MARGIN, -1);

            final String motd = this.realmsServer.getMotd();
            if (motd != null && !motd.isBlank()) {
                context.text(font, fit(font, motd, entryWidth - textX - SLOT_MARGIN), textX,
                    entryHeight - font.lineHeight - SLOT_MARGIN, 0xFFD0D2D5);
            }
        }

        private String version() {
            final String activeVersion = this.realmsServer.getActiveVersion();
            if (activeVersion != null && !activeVersion.isBlank()) {
                return this.realmsServer.getWorldType() + " - " + activeVersion;
            } else {
                return this.realmsServer.getWorldType();
            }
        }

        private static String fit(final Font font, final String value, final int width) {
            if (width <= 0) {
                return "";
            }
            if (font.width(value) <= width) {
                return value;
            }
            final String ellipsis = "…";
            return font.plainSubstrByWidth(value, Math.max(0, width - font.width(ellipsis))) + ellipsis;
        }

    }

}
