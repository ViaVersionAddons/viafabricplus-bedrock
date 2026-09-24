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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;

/** Xbox Live friends, player search, and mutual friend requests. */
public final class BedrockSocialService {

    private static final URI PEOPLE = URI.create("https://peoplehub.xboxlive.com/users/me/people/");
    private static final URI SOCIAL = URI.create("https://social.xboxlive.com/users/me/people/friends/v2/");
    private static final String DECORATIONS = "/decoration/bio,detail,multiplayerSummary,preferredColor,presenceDetail";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BedrockSocialService() {
    }

    public static CompletableFuture<List<SocialUser>> friends(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return users(PEOPLE.resolve("friends" + DECORATIONS), account.getXboxLiveXstsToken().refresh());
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Xbox friends", exception);
            }
        });
    }

    public static CompletableFuture<FriendRequests> requests(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final XblXstsToken token = account.getXboxLiveXstsToken().refresh();
                return new FriendRequests(
                    users(PEOPLE.resolve("friendRequests(received)" + DECORATIONS), token),
                    users(PEOPLE.resolve("friendRequests(sent)" + DECORATIONS), token)
                );
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Xbox friend requests", exception);
            }
        });
    }

    public static CompletableFuture<List<SocialUser>> search(final BedrockAuthManager account, final String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                final URI uri = PEOPLE.resolve("search/decoration/detail,preferredColor?q=" + encoded);
                return users(uri, account.getXboxLiveXstsToken().refresh());
            } catch (Exception exception) {
                throw new IllegalStateException("Could not search Xbox players", exception);
            }
        });
    }

    public static CompletableFuture<Void> updateFriend(final BedrockAuthManager account, final String xuid, final boolean add) {
        if (!xuid.matches("[0-9]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Xbox user ID"));
        }
        return CompletableFuture.runAsync(() -> {
            try {
                final URI uri = SOCIAL.resolve("xuid(" + xuid + ")" + (add ? "" : "?deleteRelationships=friends"));
                final HttpRequest request = HttpRequest.newBuilder(uri)
                    // Xbox requires Content-Length: 0 here; Java's HTTP/2 request omits it and receives 411.
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", account.getXboxLiveXstsToken().refresh().getAuthorizationHeader())
                    .header("X-Xbl-Contract-Version", "3")
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .method(add ? "PUT" : "DELETE", HttpRequest.BodyPublishers.noBody())
                    .build();
                final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw BedrockXboxError.response("Xbox friend update", response);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not update Xbox friend", exception);
            }
        });
    }

    private static List<SocialUser> users(final URI uri, final XblXstsToken token) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", token.getAuthorizationHeader())
            .header("X-Xbl-Contract-Version", "7")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET().build();
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw BedrockXboxError.response("Xbox people request", response);
        }
        final JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        final List<SocialUser> result = new ArrayList<>();
        if (!data.has("people") || !data.get("people").isJsonArray()) {
            throw new IOException("Xbox people response has no player list");
        }
        for (final JsonElement element : data.getAsJsonArray("people")) {
            final JsonObject user = element.getAsJsonObject();
            final String xuid = string(user, "xuid");
            if (xuid.isBlank()) {
                continue;
            }
            final String gamertag = string(user, "uniqueModernGamertag").isBlank()
                ? string(user, "gamertag") : string(user, "uniqueModernGamertag");
            final JsonObject detail = user.has("detail") && user.get("detail").isJsonObject()
                ? user.getAsJsonObject("detail") : new JsonObject();
            result.add(new SocialUser(xuid, gamertag, string(user, "displayName"),
                "Online".equalsIgnoreCase(string(user, "presenceState")), string(user, "presenceText"),
                string(user, "gamerScore"), number(detail, "friendCount"), bool(user, "isFriend") || bool(detail, "friend"),
                bool(user, "isFriendRequestReceived") || bool(detail, "isFriendRequestReceived"),
                bool(user, "isFriendRequestSent") || bool(detail, "isFriendRequestSent")));
        }
        return List.copyOf(result);
    }

    private static String string(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static boolean bool(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static int number(final JsonObject object, final String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()
            ? object.get(key).getAsInt() : -1;
    }

    public record FriendRequests(List<SocialUser> incoming, List<SocialUser> outgoing) {
    }

    public record SocialUser(String xuid, String gamertag, String displayName, boolean online, String presence,
                             String gamerScore, int friendCount, boolean friend, boolean incoming, boolean outgoing) {

        public String name() {
            return !this.displayName.isBlank() ? this.displayName : !this.gamertag.isBlank() ? this.gamertag : this.xuid;
        }

    }

}
