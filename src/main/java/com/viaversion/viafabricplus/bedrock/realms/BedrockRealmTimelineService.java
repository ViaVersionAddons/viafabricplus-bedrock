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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;

/** The member's own Realms Timeline consent, separate from the owner's Realm settings. */
public final class BedrockRealmTimelineService {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BedrockRealmTimelineService() {
    }

    public static CompletableFuture<Boolean> isOptedIn(final BedrockAuthManager account, final long realmId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return isOptedInNow(account, realmId);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Realm Timeline consent", exception);
            }
        });
    }

    public static CompletableFuture<Void> optIn(final BedrockAuthManager account, final long realmId) {
        return CompletableFuture.runAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                body.addProperty("playerOptIn", "OPT_IN");
                final HttpRequest request = request(account, realmId)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
                final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw responseError("Realm Timeline opt-in", response);
                }
                if (!isOptedInNow(account, realmId)) {
                    throw new IOException("Realm Timeline opt-in was not saved");
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not opt in to the Realm Timeline", exception);
            }
        });
    }

    private static boolean isOptedInNow(final BedrockAuthManager account, final long realmId) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP.send(request(account, realmId).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw responseError("Realm Timeline settings", response);
        }
        final JsonObject settings = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!settings.has("playerOptIn") || !settings.get("playerOptIn").isJsonPrimitive()) {
            throw new IOException("Realm Timeline settings have no player opt-in state");
        }
        return "OPT_IN".equals(settings.get("playerOptIn").getAsString());
    }

    private static IOException responseError(final String operation, final HttpResponse<String> response) {
        try {
            final JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("errorCode") && body.has("errorMsg")) {
                return new IOException(operation + " failed: " + body.get("errorMsg").getAsString()
                    + " (code " + body.get("errorCode").getAsInt() + ")");
            }
        } catch (RuntimeException ignored) {
            // Realms does not return JSON for every HTTP error.
        }
        return new IOException(operation + " failed: HTTP " + response.statusCode());
    }

    private static HttpRequest.Builder request(final BedrockAuthManager account, final long realmId) throws IOException {
        if (realmId <= 0) {
            throw new IOException("Invalid Realm ID");
        }
        return HttpRequest.newBuilder(URI.create("https://pocket.realms.minecraft.net/worlds/" + realmId + "/stories/settings"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", account.getRealmsXstsToken().refresh().getAuthorizationHeader())
            .header("Client-Version", ProtocolConstants.BEDROCK_VERSION_NAME)
            .header("Accept", "application/json");
    }

}
