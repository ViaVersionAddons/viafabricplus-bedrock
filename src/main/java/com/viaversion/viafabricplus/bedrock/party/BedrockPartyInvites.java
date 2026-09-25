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

package com.viaversion.viafabricplus.bedrock.party;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.messagepack.MessagePackHubProtocol;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.playfab.model.PlayFabEntityToken;
import org.jetbrains.annotations.Nullable;

/** Receives invitation notifications from the PlayFab lobby PubSub hub. */
public final class BedrockPartyInvites {

    private static final URI SUBSCRIBE = URI.create("https://20ca2.playfabapi.com/Lobby/SubscribeToLobbyResource");
    private static final URI HUB = URI.create("https://20ca2.playfabapi.com/pubsub");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final List<Invite> INVITES = new CopyOnWriteArrayList<>();

    private static volatile @Nullable BedrockAuthManager account;
    private static volatile @Nullable HubConnection connection;
    private static volatile @Nullable String inviteTopic;
    private static volatile @Nullable CompletableFuture<Void> starting;

    private BedrockPartyInvites() {
    }

    public static List<Invite> invitations(final BedrockAuthManager selectedAccount) {
        return account == selectedAccount ? List.copyOf(INVITES) : List.of();
    }

    public static synchronized CompletableFuture<Void> start(final BedrockAuthManager selectedAccount) {
        if (account == selectedAccount && starting != null && !starting.isCompletedExceptionally()
            && (!starting.isDone() || connection != null)) {
            return starting;
        }
        reset();
        account = selectedAccount;
        starting = CompletableFuture.runAsync(() -> connect(selectedAccount));
        return starting;
    }

    private static void connect(final BedrockAuthManager selectedAccount) {
        HubConnection hub = null;
        try {
            final PlayFabEntityToken token = selectedAccount.getPlayFabToken().getUpToDate().getEntityToken();
            hub = HubConnectionBuilder.create(HUB.toString())
                .withHeader("X-EntityToken", token.getToken())
                .withHeader("X-PlayFabSDK", "PlayFabMultiplayerSDK.WinGameCore-1.8.0")
                .withHubProtocol(new MessagePackHubProtocol())
                .withKeepAliveInterval(5_000)
                .build();
            final HubConnection connectedHub = hub;
            hub.on("ReceiveMessage", message -> receive(selectedAccount, message), PubSubMessage.class);
            hub.onClosed(error -> {
                if (account == selectedAccount && connection == connectedHub) {
                    connection = null;
                    if (error != null) {
                        ViaFabricPlusBedrock.impl().logger().warn("Bedrock party invitation stream closed", error);
                    }
                    CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
                        if (account == selectedAccount && connection == null) {
                            start(selectedAccount).exceptionally(reconnectError -> {
                                ViaFabricPlusBedrock.impl().logger().warn("Could not reconnect party invitations", reconnectError);
                                return null;
                            });
                        }
                    });
                }
            });
            hub.start().blockingAwait();
            final String traceId = UUID.randomUUID().toString().replace("-", "");
            final String parentId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            final Map<String, String> request = Map.of("traceParent", "00-" + traceId + "-" + parentId + "-01");
            final Map<?, ?> response = hub.invoke(Map.class, "StartOrRecoverSession", request).blockingGet();
            final Object handle = response.get("newConnectionHandle");
            if (!(handle instanceof String value) || value.isBlank()) {
                throw new IOException("PlayFab returned no invitation connection handle");
            }
            final String topic = subscribe(token, value);
            if (account == selectedAccount) {
                inviteTopic = topic;
                connection = hub;
            } else {
                hub.close();
            }
        } catch (Exception exception) {
            if (hub != null) {
                hub.close();
            }
            throw new IllegalStateException("Could not receive Bedrock party invitations", exception);
        }
    }

    private static String subscribe(final PlayFabEntityToken token, final String handle) throws IOException, InterruptedException {
        final JsonObject entity = new JsonObject();
        entity.addProperty("Id", token.getEntityId());
        entity.addProperty("Type", token.getEntityType());
        final JsonObject body = new JsonObject();
        body.add("EntityKey", entity);
        body.addProperty("PubSubConnectionHandle", handle);
        body.addProperty("ResourceId", "@me");
        body.addProperty("SubscriptionVersion", 1);
        body.addProperty("Type", "LobbyInvite");
        final HttpRequest request = HttpRequest.newBuilder(SUBSCRIBE)
            .timeout(Duration.ofSeconds(15))
            .header("X-EntityToken", token.getToken())
            .header("X-PlayFabSDK", "PlayFabMultiplayerSDK.WinGameCore-1.8.0")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw BedrockXboxError.response("Bedrock party invitation subscription", response);
        }
        final JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        final JsonObject result = data.has("data") && data.get("data").isJsonObject() ? data.getAsJsonObject("data") : data;
        final String topic = string(result, "Topic");
        if (topic.isBlank()) {
            throw new IOException("PlayFab returned no invitation topic");
        }
        return topic;
    }

    private static void receive(final BedrockAuthManager selectedAccount, final PubSubMessage message) {
        if (account != selectedAccount || message == null || inviteTopic == null
            || !Objects.equals(message.topic, inviteTopic)) {
            return;
        }
        try {
            final byte[] payload = message.payloadBytes();
            final JsonObject invitation = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            final String lobbyId = string(invitation, "lobbyId");
            final String connectionString = string(invitation, "connectionString");
            if (lobbyId.isBlank() || connectionString.isBlank()) {
                return;
            }
            INVITES.removeIf(existing -> existing.partyId().equals(lobbyId));
            INVITES.add(new Invite(lobbyId, connectionString));
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                com.viaversion.viafabricplus.screen.base.VFPScreen.showToast(
                    net.minecraft.network.chat.Component.translatable("bedrock_party.viafabricplus.new_invite")));
        } catch (RuntimeException exception) {
            ViaFabricPlusBedrock.impl().logger().warn("Could not read Bedrock party invitation", exception);
        }
    }

    public static void dismiss(final Invite invite) {
        INVITES.remove(invite);
    }

    public static CompletableFuture<Void> ignore(final BedrockAuthManager selectedAccount, final Invite invite) {
        return BedrockPartyService.ignoreInvite(selectedAccount, invite.partyId()).thenRun(() -> dismiss(invite));
    }

    public static synchronized void reset() {
        final HubConnection old = connection;
        connection = null;
        inviteTopic = null;
        starting = null;
        account = null;
        INVITES.clear();
        if (old != null) {
            old.close();
        }
    }

    private static String string(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    public record Invite(String partyId, String connectionString) {
    }

    /** MessagePack maps these public fields from the SignalR payload. */
    public static final class PubSubMessage {
        public String topic;
        public Object payload;

        public byte[] payloadBytes() {
            if (this.payload instanceof byte[] bytes) {
                return bytes;
            }
            if (this.payload instanceof String base64) {
                return Base64.getDecoder().decode(base64);
            }
            if (this.payload instanceof List<?> numbers) {
                final byte[] bytes = new byte[numbers.size()];
                for (int index = 0; index < numbers.size(); index++) {
                    bytes[index] = ((Number) numbers.get(index)).byteValue();
                }
                return bytes;
            }
            return new byte[0];
        }
    }

}
