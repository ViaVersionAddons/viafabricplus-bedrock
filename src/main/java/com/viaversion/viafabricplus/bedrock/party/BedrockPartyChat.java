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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;

/** Text party chat over Minecraft's signaling JSON-RPC connection. */
public final class BedrockPartyChat implements WebSocket.Listener {

    private static final URI SIGNAL = URI.create("wss://signal.franchise.minecraft-services.net/ws/v1.0/messaging/connect");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final BedrockAuthManager account;
    private final String partyId;
    private final ArrayDeque<Message> messages = new ArrayDeque<>();
    private final StringBuilder fragments = new StringBuilder();
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicInteger connectionGeneration = new AtomicInteger();
    private volatile @Nullable WebSocket socket;
    private volatile boolean closed;
    private volatile boolean joined;

    public BedrockPartyChat(final BedrockAuthManager account, final String partyId) {
        this.account = account;
        this.partyId = partyId;
    }

    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.account.getMinecraftSession().getUpToDate().getAuthorizationHeader();
            } catch (Exception exception) {
                throw new IllegalStateException("Could not authenticate party chat", exception);
            }
        }).thenCompose(token -> HTTP.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .header("Authorization", token)
            .header("session-id", UUID.randomUUID().toString())
            .header("request-id", UUID.randomUUID().toString())
            .buildAsync(SIGNAL, this).thenAccept(socket -> this.socket = socket));
    }

    public boolean connected() {
        return this.joined && this.socket != null && !this.closed;
    }

    public synchronized List<Message> messages() {
        return List.copyOf(this.messages);
    }

    public CompletableFuture<Void> send(final String message) {
        final String text = message.strip();
        if (text.isBlank() || text.length() > 256) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Party message must be 1 to 256 characters"));
        }
        if (!this.connected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Party chat is not connected"));
        }
        final JsonObject params = new JsonObject();
        params.addProperty("partyId", this.partyId);
        params.addProperty("message", text);
        // The signaling service echoes the message to the sender.
        return this.sendRpc("PartyChat_SendChat_v1_0", params).thenApply(_ -> null);
    }

    public void close() {
        this.closed = true;
        this.joined = false;
        this.connectionGeneration.incrementAndGet();
        final WebSocket active = this.socket;
        this.socket = null;
        this.failPending(new IllegalStateException("Party chat closed"));
        if (active != null) {
            active.sendClose(WebSocket.NORMAL_CLOSURE, "Leaving party");
        }
    }

    @Override
    public void onOpen(final WebSocket webSocket) {
        this.socket = webSocket;
        webSocket.request(1);
        this.sendRpc("Signaling_TurnAuth_v1_0", new JsonObject()).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().debug("Party TURN request failed", error);
            return null;
        });
        final JsonObject params = new JsonObject();
        params.addProperty("partyId", this.partyId);
        this.sendRpc("PartyChat_JoinParty_v1_0", params).thenAccept(_ -> {
            this.joined = true;
            this.reconnectAttempts.set(0);
            this.schedulePing(this.connectionGeneration.incrementAndGet());
        }).exceptionally(error -> {
            ViaFabricPlusBedrock.impl().logger().error("Could not join Bedrock party chat", error);
            return null;
        });
    }

    @Override
    public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
        synchronized (this.fragments) {
            this.fragments.append(data);
            if (last) {
                final String text = this.fragments.toString();
                this.fragments.setLength(0);
                this.handle(text);
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
        this.joined = false;
        this.socket = null;
        this.connectionGeneration.incrementAndGet();
        this.failPending(new IllegalStateException("Party chat disconnected: " + statusCode));
        this.reconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(final WebSocket webSocket, final Throwable error) {
        this.joined = false;
        this.socket = null;
        this.connectionGeneration.incrementAndGet();
        this.failPending(error);
        ViaFabricPlusBedrock.impl().logger().error("Bedrock party chat connection failed", error);
        this.reconnect();
    }

    private void handle(final String text) {
        try {
            final JsonObject event = JsonParser.parseString(text).getAsJsonObject();
            final String method = string(event, "method");
            if (method.isBlank() && event.has("id")) {
                final CompletableFuture<JsonObject> response = this.pending.remove(string(event, "id"));
                if (response != null) {
                    if (event.has("error")) {
                        response.completeExceptionally(new IllegalStateException("Party chat request rejected: " + event.get("error")));
                    } else {
                        response.complete(event);
                    }
                }
                return;
            }
            if (!method.equals("PartyChat_ReceiveChat_v1_0") && !method.equals("System_Pong_v1_0")) {
                return;
            }
            if (event.has("id")) {
                final JsonObject acknowledgement = new JsonObject();
                acknowledgement.add("id", event.get("id"));
                acknowledgement.addProperty("jsonrpc", "2.0");
                acknowledgement.add("result", com.google.gson.JsonNull.INSTANCE);
                final WebSocket active = this.socket;
                if (active != null) {
                    active.sendText(acknowledgement.toString(), true);
                }
            }
            if (method.equals("PartyChat_ReceiveChat_v1_0")) {
                JsonElement parameters = event.get("params");
                if (parameters != null && parameters.isJsonArray() && !parameters.getAsJsonArray().isEmpty()) {
                    parameters = parameters.getAsJsonArray().get(0);
                }
                if (parameters != null && parameters.isJsonObject()) {
                    final JsonObject chat = parameters.getAsJsonObject();
                    final String sender = string(chat, "Sender");
                    final String content = string(chat, "ScanText");
                    if (!content.isBlank()) {
                        this.add(new Message(sender.isBlank() ? "Player" : sender, content, Instant.now()));
                    }
                }
            }
        } catch (RuntimeException exception) {
            ViaFabricPlusBedrock.impl().logger().debug("Ignored invalid Bedrock party signal", exception);
        }
    }

    private CompletableFuture<JsonObject> sendRpc(final String method, final JsonObject params) {
        final WebSocket active = this.socket;
        if (active == null || this.closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Party chat is not connected"));
        }
        final JsonObject message = new JsonObject();
        message.addProperty("jsonrpc", "2.0");
        message.addProperty("method", method);
        final String id = UUID.randomUUID().toString();
        message.addProperty("id", id);
        message.add("params", params);
        final CompletableFuture<JsonObject> result = new CompletableFuture<>();
        this.pending.put(id, result);
        active.sendText(message.toString(), true).whenComplete((_, error) -> {
            if (error != null && this.pending.remove(id, result)) {
                result.completeExceptionally(error);
            }
        });
        CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS).execute(() -> {
            if (this.pending.remove(id, result)) {
                result.completeExceptionally(new IllegalStateException("Party chat request timed out: " + method));
            }
        });
        return result;
    }

    private void failPending(final Throwable error) {
        this.pending.values().forEach(result -> result.completeExceptionally(error));
        this.pending.clear();
    }

    private void reconnect() {
        if (this.closed || !this.reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        final int attempt = this.reconnectAttempts.incrementAndGet();
        if (attempt > 5) {
            this.reconnectScheduled.set(false);
            ViaFabricPlusBedrock.impl().logger().warn("Bedrock party chat did not reconnect after five attempts");
            return;
        }
        CompletableFuture.delayedExecutor(Math.min(30, attempt * 5L), TimeUnit.SECONDS).execute(() -> {
            this.reconnectScheduled.set(false);
            if (!this.closed) {
                this.connect().exceptionally(error -> {
                    ViaFabricPlusBedrock.impl().logger().warn("Bedrock party chat reconnect failed", error);
                    this.reconnect();
                    return null;
                });
            }
        });
    }

    private void schedulePing(final int generation) {
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
            if (this.connected() && this.connectionGeneration.get() == generation) {
                this.sendRpc("System_Ping_v1_0", new JsonObject());
                this.schedulePing(generation);
            }
        });
    }

    private synchronized void add(final Message message) {
        this.messages.addLast(message);
        while (this.messages.size() > 100) {
            this.messages.removeFirst();
        }
    }

    private static String string(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    public record Message(String sender, String text, Instant time) {
    }

}
