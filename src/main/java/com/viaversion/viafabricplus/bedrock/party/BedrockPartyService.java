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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.util.holder.Holder;
import net.raphimc.minecraftauth.xbl.model.XblTitleToken;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import net.raphimc.minecraftauth.xbl.request.XblXstsAuthorizeRequest;
import org.jetbrains.annotations.Nullable;

/** Minecraft Bedrock party REST operations. Chat uses the separate signaling connection. */
public final class BedrockPartyService {

    private static final URI MULTIPLAYER = URI.create("https://secondary.multiplayer.minecraft-services.net/api/v1.0/");
    private static final URI PLAYFAB = URI.create("https://20ca2.playfabapi.com/Lobby/GetLobby");
    private static final URI PLAYFAB_UPDATE = URI.create("https://20ca2.playfabapi.com/Lobby/UpdateLobby");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static volatile @Nullable Party current;
    private static volatile @Nullable BedrockAuthManager currentAccount;
    private static volatile @Nullable BedrockPartyChat chat;
    private static volatile @Nullable BedrockAuthManager searchTokenAccount;
    private static volatile @Nullable Holder<XblXstsToken> searchToken;

    private BedrockPartyService() {
    }

    public static @Nullable Party current(final BedrockAuthManager account) {
        return currentAccount == account ? current : null;
    }

    public static List<BedrockPartyChat.Message> messages(final BedrockAuthManager account) {
        final BedrockPartyChat active = currentAccount == account ? chat : null;
        return active == null ? List.of() : active.messages();
    }

    public static boolean chatConnected(final BedrockAuthManager account) {
        final BedrockPartyChat active = currentAccount == account ? chat : null;
        return active != null && active.connected();
    }

    public static CompletableFuture<List<Party>> findJoinable(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                body.addProperty("xboxToken", searchToken(account).getAuthorizationHeader());
                body.addProperty("maxResults", 50);
                body.addProperty("includeFullParties", false);
                final JsonObject response = partyRequest(account, "party/findJoinable", body);
                final JsonArray results = array(response, "result");
                final List<Party> parties = new ArrayList<>();
                for (final JsonElement element : results) {
                    if (element.isJsonObject()) {
                        parties.add(parseParty(element.getAsJsonObject()));
                    }
                }
                return List.copyOf(parties);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not find Bedrock parties", exception);
            }
        });
    }

    public static CompletableFuture<Party> create(final BedrockAuthManager account, final boolean open) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                body.add("memberData", memberData(account));
                body.addProperty("privacy", open ? "open" : "closed");
                body.addProperty("restrictInvitesToLeader", false);
                return enter(account, result(partyRequest(account, "party/create", body)));
            } catch (Exception exception) {
                throw new IllegalStateException("Could not create Bedrock party", exception);
            }
        });
    }

    public static CompletableFuture<Party> join(final BedrockAuthManager account, final String partyId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                body.addProperty("xboxToken", account.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader());
                body.add("memberData", memberData(account));
                return enter(account, result(partyRequest(account, "party/" + partyId(partyId) + "/join", body)));
            } catch (Exception exception) {
                throw new IllegalStateException("Could not join Bedrock party", exception);
            }
        });
    }

    public static CompletableFuture<Party> acceptInvite(final BedrockAuthManager account, final String partyId,
                                                         final String connectionString) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                body.addProperty("connectionString", connectionString);
                body.add("memberData", memberData(account));
                return enter(account, result(partyRequest(account, "party/" + partyId(partyId) + "/invite/accept", body)));
            } catch (Exception exception) {
                throw new IllegalStateException("Could not accept Bedrock party invitation", exception);
            }
        });
    }

    public static CompletableFuture<Void> ignoreInvite(final BedrockAuthManager account, final String partyId) {
        return CompletableFuture.runAsync(() -> {
            try {
                partyRequest(account, "party/" + partyId(partyId) + "/invite/ignore", null);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not ignore Bedrock party invitation", exception);
            }
        });
    }

    public static CompletableFuture<Void> invite(final BedrockAuthManager account, final String xuid) {
        return mutate(account, "invite", xuid, false);
    }

    public static CompletableFuture<Void> promote(final BedrockAuthManager account, final String xuid) {
        return mutate(account, "setLeader", xuid, false);
    }

    public static CompletableFuture<Void> remove(final BedrockAuthManager account, final String xuid) {
        return mutate(account, "remove", xuid, false);
    }

    private static CompletableFuture<Void> mutate(final BedrockAuthManager account, final String operation, final String xuid,
                                                  final boolean preventRejoin) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!xuid.matches("[0-9]+")) {
                    throw new IOException("Invalid Xbox user ID");
                }
                final Party party = requireCurrent(account);
                final JsonObject body = new JsonObject();
                body.addProperty("playerId", xuid);
                if (operation.equals("invite")) {
                    body.addProperty("xboxToken", account.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader());
                } else if (operation.equals("remove")) {
                    body.addProperty("preventRejoin", preventRejoin);
                }
                partyRequest(account, "party/" + partyId(party.id()) + "/" + operation, body);
                if (operation.equals("setLeader")) {
                    current = new Party(party.id(), party.connectionString(), party.open(), party.maxPlayers(), xuid, party.members());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not " + operation + " Bedrock party member", exception);
            }
        });
    }

    public static CompletableFuture<Void> leave(final BedrockAuthManager account) {
        return CompletableFuture.runAsync(() -> {
            try {
                final Party party = requireCurrent(account);
                partyRequest(account, "party/" + partyId(party.id()) + "/leave", null);
                if (currentAccount == account && current != null && current.id().equals(party.id())) {
                    final BedrockPartyChat oldChat = chat;
                    chat = null;
                    current = null;
                    currentAccount = null;
                    if (oldChat != null) {
                        oldChat.close();
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not leave Bedrock party", exception);
            }
        });
    }

    public static CompletableFuture<Party> refresh(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final Party previous = requireCurrent(account);
                final Party updated = readLobby(account, previous);
                if (currentAccount == account && current != null && current.id().equals(previous.id())) {
                    current = updated;
                }
                return updated;
            } catch (Exception exception) {
                throw new IllegalStateException("Could not refresh Bedrock party", exception);
            }
        });
    }

    public static CompletableFuture<Party> setPrivacy(final BedrockAuthManager account, final boolean open) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final Party party = requireCurrent(account);
                final JsonObject body = new JsonObject();
                body.addProperty("LobbyId", party.id());
                body.addProperty("AccessPolicy", open ? "Public" : "Private");
                final HttpRequest request = playFabRequest(account, PLAYFAB_UPDATE, body);
                send("Bedrock party privacy", request);
                final Party updated = readLobby(account, party);
                if (currentAccount == account && current != null && current.id().equals(party.id())) {
                    current = updated;
                }
                return updated;
            } catch (Exception exception) {
                throw new IllegalStateException("Could not change Bedrock party privacy", exception);
            }
        });
    }

    private static Party readLobby(final BedrockAuthManager account, final Party previous) throws Exception {
        final JsonObject body = new JsonObject();
        body.addProperty("LobbyId", previous.id());
        final HttpRequest request = playFabRequest(account, PLAYFAB, body);
        final JsonObject lobby = object(object(send("Bedrock party members", request), "data"), "Lobby");
        if (lobby.isEmpty()) {
            throw new IOException("PlayFab returned no lobby details");
        }
        final String ownerEntityId = string(object(lobby, "Owner"), "Id");
        final List<Member> members = new ArrayList<>();
        String leaderXuid = previous.leaderXuid();
        for (final JsonElement element : array(lobby, "Members")) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject member = element.getAsJsonObject();
            final String xuid = string(object(member, "MemberData"), "Xuid");
            if (!xuid.isBlank()) {
                members.add(new Member(xuid, ""));
                if (ownerEntityId.equals(string(object(member, "MemberEntity"), "Id"))) {
                    leaderXuid = xuid;
                }
            }
        }
        final String access = string(lobby, "AccessPolicy");
        final boolean open = access.isBlank() ? previous.open() : "Public".equalsIgnoreCase(access);
        return new Party(previous.id(), previous.connectionString(), open,
            number(lobby, "MaxPlayers", previous.maxPlayers()), leaderXuid, List.copyOf(members));
    }

    private static HttpRequest playFabRequest(final BedrockAuthManager account, final URI endpoint, final JsonObject body)
        throws IOException {
        return HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(15))
            .header("X-EntityToken", account.getPlayFabToken().getUpToDate().getEntityToken().getToken())
            .header("X-PlayFabSDK", "PlayFabMultiplayerSDK.WinGameCore-1.8.0")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    public static CompletableFuture<Void> sendChat(final BedrockAuthManager account, final String message) {
        final BedrockPartyChat active = currentAccount == account ? chat : null;
        return active == null ? CompletableFuture.failedFuture(new IllegalStateException("Party chat is not connected"))
            : active.send(message);
    }

    public static void reset() {
        BedrockPartyInvites.reset();
        final BedrockPartyChat oldChat = chat;
        chat = null;
        current = null;
        currentAccount = null;
        if (oldChat != null) {
            oldChat.close();
        }
    }

    private static Party enter(final BedrockAuthManager account, final JsonObject data) throws IOException {
        final Party party = parseParty(data);
        if (party.id().isBlank()) {
            throw new IOException("Party service did not return a party ID");
        }
        final BedrockPartyChat oldChat = chat;
        if (oldChat != null) {
            oldChat.close();
        }
        current = party;
        currentAccount = account;
        chat = new BedrockPartyChat(account, party.id());
        chat.connect().exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Failed to connect Bedrock party chat", error);
            return null;
        });
        try {
            final Party loaded = readLobby(account, party);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            ViaFabricPlusBedrock.impl().logger().warn("Party joined, but member details are unavailable", exception);
            return party;
        }
    }

    private static Party requireCurrent(final BedrockAuthManager account) {
        final Party party = current(account);
        if (party == null) {
            throw new IllegalStateException("No active Bedrock party");
        }
        return party;
    }

    private static JsonObject memberData(final BedrockAuthManager account) {
        final JsonObject data = new JsonObject();
        data.addProperty("clientVersion", account.getGameVersion());
        return data;
    }

    private static String partyId(final String value) throws IOException {
        if (!value.matches("[A-Za-z0-9.-]{1,100}")) {
            throw new IOException("Invalid party ID");
        }
        return value;
    }

    private static XblXstsToken searchToken(final BedrockAuthManager account) throws IOException {
        Holder<XblXstsToken> holder = searchToken;
        if (searchTokenAccount != account || holder == null) {
            synchronized (BedrockPartyService.class) {
                if (searchTokenAccount != account || searchToken == null) {
                    searchToken = new Holder<>(() -> {
                        final XblTitleToken title = account.getMsaApplicationConfig().isTitleClientId()
                            ? account.getXblTitleToken().getUpToDate() : null;
                        return account.getHttpClient().executeAndHandle(new XblXstsAuthorizeRequest(
                            account.getXblDeviceToken().getUpToDate(), account.getXblUserToken().getUpToDate(),
                            title, "http://playfab.xboxlive.com/"));
                    });
                    searchTokenAccount = account;
                }
                holder = searchToken;
            }
        }
        return holder.getUpToDate();
    }

    private static JsonObject partyRequest(final BedrockAuthManager account, final String path, final @Nullable JsonObject body)
        throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(MULTIPLAYER.resolve(path))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", account.getMinecraftSession().getUpToDate().getAuthorizationHeader())
            .header("Accept", "application/json")
            .header("session-id", UUID.randomUUID().toString());
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        return send("Bedrock party request", builder.build());
    }

    private static JsonObject send(final String operation, final HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw BedrockXboxError.response(operation, response);
        }
        if (response.body() == null || response.body().isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException(operation + " returned invalid JSON", exception);
        }
    }

    private static Party parseParty(final JsonObject data) {
        final JsonObject leader = object(data, "leader");
        final List<Member> members = new ArrayList<>();
        for (final JsonElement element : array(data, "members")) {
            if (element.isJsonObject()) {
                final JsonObject member = element.getAsJsonObject();
                final String xuid = string(member, "xuid");
                if (!xuid.isBlank()) {
                    members.add(new Member(xuid, ""));
                }
            }
        }
        return new Party(string(data, "id"), string(data, "connectionString"),
            "Open".equalsIgnoreCase(string(data, "privacy")), number(data, "maxPlayers", 15),
            string(leader, "xuid"), List.copyOf(members));
    }

    private static JsonObject result(final JsonObject data) {
        return object(data, "result");
    }

    private static JsonObject object(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonArray() ? data.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    private static int number(final JsonObject data, final String key, final int fallback) {
        return data.has(key) && data.get(key).isJsonPrimitive() && data.get(key).getAsJsonPrimitive().isNumber()
            ? data.get(key).getAsInt() : fallback;
    }

    public record Member(String xuid, String name) {
    }

    public record Party(String id, String connectionString, boolean open, int maxPlayers, String leaderXuid,
                        List<Member> members) {
    }

}
