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

package com.viaversion.viafabricplus.bedrock.account;

import com.mojang.blaze3d.Blaze3D;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IConfirmScreen;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viafabricplus.bedrock.party.BedrockPartyService;
import com.viaversion.viafabricplus.bedrock.screen.BedrockRealmsScreen;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.util.JsonSave;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.util.holder.listener.ChangeListener;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;

public final class BedrockAccount {

    private static final Component TITLE = Component.nullToEmpty("Microsoft Bedrock login");

    private BedrockAuthManager account;
    private Thread thread;

    public BedrockAccount(final Path path) {
        JsonSave.load(path, object -> {
            this.account = BedrockAuthManager.fromJson(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME, object);
        }, () -> {
            return this.account == null ? null : BedrockAuthManager.toJson(this.account);
        });
    }

    public @Nullable BedrockAuthManager get() {
        return this.account;
    }

    public @Nullable String displayName() {
        if (this.account != null && this.account.getMinecraftMultiplayerToken().hasValue()) {
            return this.account.getMinecraftMultiplayerToken().getCached().getDisplayName();
        } else {
            return null;
        }
    }

    public void login() {
        this.thread = new Thread(this::performLogin, "ViaFabricPlus Bedrock login");
        this.thread.start();
    }

    private void performLogin() {
        final Minecraft client = Minecraft.getInstance();
        final Screen prevScreen = client.gui.screen();
        try {
            final BedrockAuthManager account = BedrockAuthManager
                .create(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME)
                .login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) deviceCode -> {
                    VFPScreen.setScreen(new ConfirmScreen(copyUrl -> {
                        if (copyUrl) {
                            client.keyboardHandler.setClipboard(deviceCode.getDirectVerificationUri());
                        } else {
                            client.gui.setScreen(prevScreen);
                            this.thread.interrupt();
                        }
                    }, TITLE, Component.translatable("bedrock_account.viafabricplus.notice"), Component.translatable("base.viafabricplus.copy_link"), Component.translatable("base.viafabricplus.cancel")));
                    Blaze3D.openUri(URI.create(deviceCode.getDirectVerificationUri()));
                });
            account.getChangeListeners().add(new ChangeListener() {
                @Override
                public <T> void onChange(final T oldValue, final T newValue) {
                    updateLoginStatus(account, newValue);
                }
            });
            account.getMinecraftMultiplayerToken().refreshIfExpired();
            account.getMinecraftCertificateChain().refreshIfExpired();
            this.account = account;
            BedrockFriendsService.leaveCurrent();
            BedrockPartyService.reset();
            BedrockRealmsScreen.invalidate(); // The realms of the previous account no longer apply

            VFPScreen.setScreen(prevScreen);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                return;
            }

            this.thread.interrupt();
            ViaFabricPlusBedrock.impl().logger().error("Failed to log in to the Bedrock account!", e);
            VFPScreen.setScreen(prevScreen);
            VFPScreen.showToast(Component.translatable("base.viafabricplus.something_went_wrong"));
        }
    }

    private static void updateLoginStatus(final BedrockAuthManager account, final Object value) {
        final String step;
        if (value == account.getMsaToken().getCached()) {
            step = "msatoken";
        } else if (value == account.getXblDeviceToken().getCached()) {
            step = "xbldevicetoken";
        } else if (value == account.getXblUserToken().getCached()) {
            step = "xblusertoken";
        } else if (value == account.getXblTitleToken().getCached()) {
            step = "xbltitletoken";
        } else if (value == account.getBedrockXstsToken().getCached()) {
            step = "bedrockxststoken";
        } else if (value == account.getPlayFabXstsToken().getCached()) {
            step = "playfabxststoken";
        } else if (value == account.getRealmsXstsToken().getCached()) {
            step = "realmsxststoken";
        } else if (value == account.getPlayFabToken().getCached()) {
            step = "playfabtoken";
        } else if (value == account.getMinecraftSession().getCached()) {
            step = "minecraftsession";
        } else if (value == account.getMinecraftMultiplayerToken().getCached()) {
            step = "minecraftmultiplayertoken";
        } else if (value == account.getMinecraftCertificateChain().getCached()) {
            step = "minecraftcertificatechain";
        } else {
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gui.screen() instanceof ConfirmScreen confirmScreen) {
                ((IConfirmScreen) confirmScreen).viaFabricPlusBedrock$updateMessage(Component.translatable("minecraftauth_library.viafabricplus." + step));
            }
        });
    }

}
