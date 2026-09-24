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

package com.viaversion.viafabricplus.bedrock.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetJsonRpcAddress;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import org.cloudburstmc.netty.channel.nethernet.config.NetherNetAddress;
import org.jetbrains.annotations.Nullable;

/** Xbox Live activity discovery and membership for player hosted Bedrock worlds. */
public final class BedrockFriendsService {

    private static final URI DIRECTORY = URI.create("https://sessiondirectory.xboxlive.com/");
    private static final URI RTA = URI.create("wss://rta.xboxlive.com/connect");
    private static final String SCID = "4fc10100-5f7a-4470-899b-280835760c07";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final AtomicReference<JoinedWorld> CURRENT_WORLD = new AtomicReference<>();

    private BedrockFriendsService() {
    }

    public static CompletableFuture<List<FriendWorld>> worlds(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final XblXstsToken token = account.getXboxLiveXstsToken().refresh();
                final String xuid = account.getXboxUserProfile().refresh().getId();
                final JsonObject owners = new JsonObject();
                final JsonObject people = new JsonObject();
                people.addProperty("moniker", "people");
                people.addProperty("monikerXuid", xuid);
                owners.add("people", people);
                final JsonObject query = new JsonObject();
                query.addProperty("type", "activity");
                query.addProperty("scid", SCID);
                query.add("owners", owners);

                final JsonObject response = request(DIRECTORY.resolve("handles/query?include=relatedInfo,customProperties"), "POST", query, token);
                final List<FriendWorld> worlds = new ArrayList<>();
                for (final JsonElement element : array(response, "results")) {
                    final JsonObject activity = element.getAsJsonObject();
                    final JsonObject relatedInfo = object(activity, "relatedInfo");
                    if (relatedInfo != null && bool(relatedInfo, "closed")) {
                        continue;
                    }
                    final JsonObject properties = object(activity, "customProperties");
                    if (properties == null || number(properties, "RealmId") != 0) {
                        continue;
                    }
                    final SocketAddress address = connection(properties);
                    if (address == null) {
                        continue;
                    }
                    worlds.add(new FriendWorld(string(activity, "id"), string(activity, "ownerXuid"), string(properties, "hostName"),
                        string(properties, "worldName"), string(properties, "version"),
                        number(properties, "MemberCount"), number(properties, "MaxMemberCount"),
                        number(properties, "protocol"), address));
                }
                return List.copyOf(worlds);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Bedrock friends' worlds", exception);
            }
        });
    }

    public static CompletableFuture<JoinedWorld> join(final BedrockAuthManager account, final FriendWorld world) {
        return CompletableFuture.supplyAsync(() -> {
            RtaSubscription subscription = null;
            JoinedWorld joined = null;
            try {
                final XblXstsToken token = account.getXboxLiveXstsToken().refresh();
                final String xuid = account.getXboxUserProfile().refresh().getId();
                subscription = RtaSubscription.open(token);

                final JsonObject systemConstants = new JsonObject();
                systemConstants.addProperty("initialize", true);
                systemConstants.addProperty("xuid", xuid);
                final JsonObject constants = new JsonObject();
                constants.add("system", systemConstants);
                final JsonObject subscriptionInfo = new JsonObject();
                subscriptionInfo.addProperty("id", UUID.randomUUID().toString().toUpperCase());
                final JsonArray changes = new JsonArray();
                changes.add("everything");
                subscriptionInfo.add("changeTypes", changes);
                final JsonObject systemProperties = new JsonObject();
                systemProperties.addProperty("active", true);
                systemProperties.addProperty("connection", subscription.connectionId());
                systemProperties.add("subscription", subscriptionInfo);
                final JsonObject properties = new JsonObject();
                properties.add("system", systemProperties);
                final JsonObject me = new JsonObject();
                me.add("constants", constants);
                me.add("properties", properties);
                final JsonObject members = new JsonObject();
                members.add("me", me);
                final JsonObject body = new JsonObject();
                body.add("members", members);

                final URI handle = DIRECTORY.resolve("handles/" + UUID.fromString(world.handleId()) + "/session");
                final HttpResponse<String> response = updateSession(handle, body, token);
                if (response.statusCode() != 200) {
                    throw BedrockXboxError.response("Xbox session join", response);
                }
                final URI sessionUri = sessionUri(response);
                joined = new JoinedWorld(sessionUri, account, subscription);
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < deadline) {
                    final JsonObject session = request(sessionUri, "GET", null, token);
                    final JsonObject worldData = object(object(session, "properties"), "custom");
                    final JsonObject nonces = object(worldData, "nonces");
                    final String nonce = string(nonces, xuid);
                    final SocketAddress updatedAddress = connection(worldData);
                    final SocketAddress address = updatedAddress != null ? updatedAddress : world.address();
                    if (!nonce.isBlank() && address != null) {
                        joined.ready(address, nonce);
                        final JoinedWorld old = CURRENT_WORLD.getAndSet(joined);
                        if (old != null) {
                            CompletableFuture.runAsync(old::close);
                        }
                        return joined;
                    }
                    Thread.sleep(500);
                }
                throw new IOException("The world host did not authorize the session in time");
            } catch (Exception exception) {
                if (joined != null) {
                    joined.close();
                } else if (subscription != null) {
                    subscription.close();
                }
                throw new IllegalStateException("Could not join the friend's world", exception);
            }
        });
    }

    public static @Nullable String nonceFor(final SocketAddress remote) {
        final JoinedWorld world = CURRENT_WORLD.get();
        return world != null && sameAddress(world.address(), remote) ? world.nonce() : null;
    }

    public static void leaveIfCurrent(final SocketAddress remote) {
        final JoinedWorld world = CURRENT_WORLD.get();
        if (world != null && sameAddress(world.address(), remote) && CURRENT_WORLD.compareAndSet(world, null)) {
            CompletableFuture.runAsync(world::close);
        }
    }

    private static boolean sameAddress(final SocketAddress expected, final SocketAddress actual) {
        return expected instanceof NetherNetAddress a && actual instanceof NetherNetAddress b
            && expected.getClass() == actual.getClass() && a.getNetworkId().equals(b.getNetworkId());
    }

    public static void leaveCurrent() {
        final JoinedWorld world = CURRENT_WORLD.getAndSet(null);
        if (world != null) {
            CompletableFuture.runAsync(world::close);
        }
    }

    private static @Nullable SocketAddress connection(final @Nullable JsonObject properties) {
        for (final JsonElement element : array(properties, "SupportedConnections")) {
            final JsonObject candidate = element.getAsJsonObject();
            final int type = number(candidate, "ConnectionType");
            final String id = string(candidate, "NetherNetId");
            if (type == 7 && !id.isBlank() && !string(candidate, "PmsgId").isBlank()) {
                return new NetherNetJsonRpcAddress(string(candidate, "PmsgId"));
            } else if (type == 3 && !id.isBlank()) {
                return new NetherNetAddress(id);
            }
        }
        return null;
    }

    private static URI sessionUri(final HttpResponse<String> response) throws IOException {
        final String location = response.headers().firstValue("Content-Location").orElseThrow(() -> new IOException("Xbox session location is missing"));
        final URI uri = DIRECTORY.resolve(location);
        if (!"https".equals(uri.getScheme()) || !DIRECTORY.getHost().equals(uri.getHost())) {
            throw new IOException("Xbox returned an unexpected session location");
        }
        return uri;
    }

    private static JsonObject request(final URI uri, final String method, final @Nullable JsonObject body, final XblXstsToken token) throws IOException, InterruptedException {
        final HttpResponse<String> response = send(uri, method, body, token, null);
        if (response.statusCode() / 100 != 2) {
            throw BedrockXboxError.response("Xbox session request", response);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpResponse<String> send(final URI uri, final String method, final @Nullable JsonObject body, final XblXstsToken token, final @Nullable String ifMatch) throws IOException, InterruptedException {
        final HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", token.getAuthorizationHeader())
            .header("x-xbl-contract-version", "107")
            .header("Accept", "application/json");
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return HTTP.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> updateSession(final URI uri, final JsonObject body, final XblXstsToken token) throws IOException, InterruptedException {
        String match = "*";
        for (int attempt = 0; attempt < 3; attempt++) {
            final HttpResponse<String> response = send(uri, "PUT", body, token, match);
            if (response.statusCode() != 412) {
                return response;
            }
            match = response.headers().firstValue("ETag").orElse("*");
        }
        throw new IOException("Xbox session changed during the update");
    }

    private static @Nullable JsonObject object(final @Nullable JsonObject parent, final String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonArray array(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : "";
    }

    private static int number(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonPrimitive() && parent.get(key).getAsJsonPrimitive().isNumber()
            ? parent.get(key).getAsInt() : 0;
    }

    private static boolean bool(final @Nullable JsonObject parent, final String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonPrimitive() && parent.get(key).getAsBoolean();
    }

    public record FriendWorld(String handleId, String ownerXuid, String hostName, String worldName, String version,
                              int players, int maxPlayers, int protocol, SocketAddress address) {
    }

    public static final class JoinedWorld implements AutoCloseable {
        private final URI sessionUri;
        private final BedrockAuthManager account;
        private final RtaSubscription subscription;
        private volatile SocketAddress address;
        private volatile String nonce;

        private JoinedWorld(final URI sessionUri, final BedrockAuthManager account, final RtaSubscription subscription) {
            this.sessionUri = sessionUri;
            this.account = account;
            this.subscription = subscription;
        }

        private void ready(final SocketAddress address, final String nonce) {
            this.address = address;
            this.nonce = nonce;
        }

        public SocketAddress address() {
            return this.address;
        }

        public String nonce() {
            return this.nonce;
        }

        @Override
        public void close() {
            try {
                final JsonObject members = new JsonObject();
                members.add("me", null);
                final JsonObject body = new JsonObject();
                body.add("members", members);
                updateSession(this.sessionUri, body, this.account.getXboxLiveXstsToken().refresh());
            } catch (Exception ignored) {
                // The session directory expires abandoned memberships when offline.
            } finally {
                this.subscription.close();
            }
        }
    }

    private static final class RtaSubscription implements WebSocket.Listener, AutoCloseable {
        private final CompletableFuture<String> connectionId = new CompletableFuture<>();
        private final StringBuilder message = new StringBuilder();
        private WebSocket socket;

        private static RtaSubscription open(final XblXstsToken token) throws Exception {
            final RtaSubscription subscription = new RtaSubscription();
            try {
                subscription.socket = HTTP.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .header("Authorization", token.getAuthorizationHeader())
                    .subprotocols("rta.xboxlive.com.V2")
                    .buildAsync(RTA, subscription).get(10, TimeUnit.SECONDS);
                subscription.socket.sendText("[1,1,\"https://sessiondirectory.xboxlive.com/connections/\"]", true).get(10, TimeUnit.SECONDS);
                if (subscription.connectionId.get(10, TimeUnit.SECONDS).isBlank()) {
                    throw new IOException("Xbox activity subscription returned no connection ID");
                }
                return subscription;
            } catch (Exception exception) {
                subscription.close();
                throw exception;
            }
        }

        private String connectionId() throws Exception {
            return this.connectionId.get(10, TimeUnit.SECONDS);
        }

        @Override
        public void onOpen(final WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
            this.message.append(data);
            if (last) {
                try {
                    final JsonArray response = JsonParser.parseString(this.message.toString()).getAsJsonArray();
                    if (response.size() >= 5 && response.get(0).getAsInt() == 1 && response.get(1).getAsInt() == 1) {
                        if (response.get(2).getAsInt() != 0) {
                            this.connectionId.completeExceptionally(new IOException("Xbox activity subscription failed: " + response.get(2)));
                        } else {
                            this.connectionId.complete(string(response.get(4).getAsJsonObject(), "ConnectionId"));
                        }
                    }
                } catch (Exception exception) {
                    this.connectionId.completeExceptionally(exception);
                }
                this.message.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(final WebSocket webSocket, final Throwable error) {
            this.connectionId.completeExceptionally(error);
        }

        @Override
        public void close() {
            if (this.socket != null) {
                this.socket.sendClose(WebSocket.NORMAL_CLOSURE, "");
            }
        }
    }

}
