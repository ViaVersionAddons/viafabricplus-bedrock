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

package com.viaversion.viafabricplus.bedrock.realms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;

/** REST requests used by the Bedrock Realm Hub. The world list is provided by MinecraftAuth. */
public final class BedrockRealmHubService {

    private static final URI REALMS = URI.create("https://pocket.realms.minecraft.net/");
    private static final URI STORIES = URI.create("https://frontend.realms.minecraft-services.net/api/v1.0/");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final BedrockAuthManager account;
    private final long realmId;

    public BedrockRealmHubService(final BedrockAuthManager account, final long realmId) {
        this.account = account;
        this.realmId = realmId;
    }

    public CompletableFuture<JsonObject> world() {
        return request(REALMS, "GET", "worlds/" + this.realmId, null);
    }

    public CompletableFuture<JsonObject> storySettings() {
        return request(REALMS, "GET", "worlds/" + this.realmId + "/stories/settings", null);
    }

    public CompletableFuture<JsonObject> events() {
        return request(STORIES, "GET", "worlds/" + this.realmId + "/stories", null);
    }

    public CompletableFuture<JsonObject> activity() {
        return request(STORIES, "GET", "worlds/" + this.realmId + "/stories/playeractivity", null);
    }

    public CompletableFuture<JsonObject> saveStorySettings(final JsonObject settings) {
        return request(REALMS, "POST", "worlds/" + this.realmId + "/stories/settings", settings);
    }

    public CompletableFuture<JsonObject> updateStorySetting(final String key, final boolean enabled) {
        if (!key.matches("notifications|autostories|coordinates|optInRequired|timeline|inGameChatMessages")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Realm story setting"));
        }
        final JsonObject body = new JsonObject();
        body.addProperty(key, enabled);
        return this.saveStorySettings(body);
    }

    public CompletableFuture<JsonObject> updateTimelineOptIn(final boolean enabled) {
        final JsonObject body = new JsonObject();
        body.addProperty("playerOptIn", enabled ? "OPT_IN" : "OPT_OUT");
        return this.saveStorySettings(body);
    }

    public CompletableFuture<JsonObject> saveDescription(final String name, final String description) {
        final JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("description", description);
        return request(REALMS, "POST", "worlds/" + this.realmId, body);
    }

    public CompletableFuture<JsonObject> saveWorldOptions(final String name, final String description,
                                                           final JsonObject options) {
        final JsonObject body = new JsonObject();
        final JsonObject text = new JsonObject();
        text.addProperty("name", name);
        text.addProperty("description", description);
        body.add("description", text);
        body.add("options", options);
        return request(REALMS, "POST", "worlds/" + this.realmId + "/configuration", body);
    }

    public CompletableFuture<JsonObject> backups() {
        return request(REALMS, "GET", "worlds/" + this.realmId + "/backups", null);
    }

    public CompletableFuture<JsonObject> restoreBackup(final String backupId) {
        if (backupId.isBlank() || backupId.length() > 256) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Realm backup"));
        }
        return request(REALMS, "PUT", "worlds/" + this.realmId + "/backups?backupId="
            + URLEncoder.encode(backupId, StandardCharsets.UTF_8) + "&clientSupportsRetries", null);
    }

    public CompletableFuture<JsonObject> changeDefaultPermission(final String permission) {
        if (!permission.matches("VISITOR|MEMBER|OPERATOR")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid default Realm permission"));
        }
        final JsonObject body = new JsonObject();
        body.addProperty("permission", permission);
        return request(REALMS, "PUT", "worlds/" + this.realmId + "/defaultPermission", body);
    }

    public CompletableFuture<JsonObject> changePermission(final String xuid, final String permission) {
        if (!xuid.matches("[0-9]+") || !permission.matches("VISITOR|MEMBER|OPERATOR")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Realm player or permission"));
        }
        final JsonObject body = new JsonObject();
        body.addProperty("xuid", xuid);
        body.addProperty("permission", permission);
        return request(REALMS, "PUT", "worlds/" + this.realmId + "/userPermission", body);
    }

    public CompletableFuture<JsonObject> updateInvite(final String xuid, final boolean add) {
        if (!xuid.matches("[0-9]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Realm player"));
        }
        final JsonObject invites = new JsonObject();
        invites.addProperty(xuid, add ? "ADD" : "REMOVE");
        final JsonObject body = new JsonObject();
        body.add("invites", invites);
        return request(REALMS, "PUT", "invites/" + this.realmId + "/invite/update", body);
    }

    public CompletableFuture<JsonObject> activateSlot(final int slot) {
        if (slot < 1 || slot > 3) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Realm slot"));
        }
        return request(REALMS, "PUT", "worlds/" + this.realmId + "/slot/" + slot, null);
    }

    public CompletableFuture<JsonObject> setOpen(final boolean open) {
        return request(REALMS, "PUT", "worlds/" + this.realmId + (open ? "/open" : "/close"), null);
    }

    private CompletableFuture<JsonObject> request(final URI base, final String method, final String path,
                                                  final @Nullable JsonObject body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", this.account.getRealmsXstsToken().getUpToDate().getAuthorizationHeader())
                    .header("Client-Version", ProtocolConstants.BEDROCK_VERSION_NAME)
                    .header("User-Agent", "MCPE/UWP")
                    .header("Accept", "application/json");
                if (body != null) {
                    builder.header("Content-Type", "application/json");
                }
                final HttpRequest request = builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString())).build();
                final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Realm Hub returned HTTP " + response.statusCode());
                }
                if (response.body().isBlank()) {
                    return new JsonObject();
                }
                final JsonElement json = JsonParser.parseString(response.body());
                if (json.isJsonObject()) return json.getAsJsonObject();
                if (json.isJsonPrimitive() || json.isJsonNull()) return new JsonObject();
                throw new IOException("Realm Hub returned an invalid response");
            } catch (Exception exception) {
                throw new IllegalStateException("Could not access the Realm Hub", exception);
            }
        });
    }
}
