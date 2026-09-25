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

package com.viaversion.viafabricplus.bedrock.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

/** Minecraft achievements and profile statistics available through Xbox Live. */
public final class BedrockProfileService {

    private static final URI ACHIEVEMENTS = URI.create("https://achievements.xboxlive.com/");
    private static final URI USER_STATS = URI.create("https://userstats.xboxlive.com/");
    // Bedrock uses different title IDs across platforms. Xbox accepts a comma-delimited filter here.
    private static final String MINECRAFT_TITLE_IDS = "1944307183,1739947436,1904044383,1810924247,"
        + "1671080157,1828326430,896928775,2047319603,2044456598";
    private static final List<String> MINECRAFT_SERVICE_CONFIG_IDS = List.of(
        "00000000-0000-0000-0000-000073e3c5ef",
        "00000000-0000-0000-0000-000067b57dac",
        "00000000-0000-0000-0000-0000717d695f",
        "00000000-0000-0000-0000-00006bf082d7",
        "00000000-0000-0000-0000-0000639aa8dd",
        "00000000-0000-0000-0000-00006cfa0c1e",
        "4fc10100-5f7a-4470-899b-280835760c07",
        "00000000-0000-0000-0000-00007a079e33",
        "00000000-0000-0000-0000-000079dbee96"
    );
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BedrockProfileService() {
    }

    public static CompletableFuture<List<Achievement>> achievements(final BedrockAuthManager account, final String xuid) {
        if (!xuid.matches("[0-9]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Xbox user ID"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                final List<Achievement> achievements = new ArrayList<>();
                String continuation = "";
                for (int page = 0; page < 10; page++) {
                    final String path = "users/xuid(" + xuid + ")/achievements?titleId=" + MINECRAFT_TITLE_IDS
                        + "&maxItems=100" + (continuation.isBlank() ? "" : "&continuationToken="
                        + URLEncoder.encode(continuation, StandardCharsets.UTF_8));
                    final HttpRequest request = HttpRequest.newBuilder(ACHIEVEMENTS.resolve(path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", account.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader())
                        .header("X-Xbl-Contract-Version", "2")
                        .header("Accept", "application/json")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .GET().build();
                    final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) {
                        throw BedrockXboxError.response("Minecraft achievements", response);
                    }
                    final JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
                    for (final JsonElement element : array(data, "achievements")) {
                        final JsonObject achievement = element.getAsJsonObject();
                        final boolean achieved = "Achieved".equalsIgnoreCase(string(achievement, "progressState"));
                        int gamerscore = 0;
                        for (final JsonElement rewardElement : array(achievement, "rewards")) {
                            final JsonObject reward = rewardElement.getAsJsonObject();
                            if ("Gamerscore".equalsIgnoreCase(string(reward, "type"))) {
                                try {
                                    gamerscore = Integer.parseInt(string(reward, "value"));
                                } catch (NumberFormatException ignored) {
                                    // Some rewards do not provide a numeric score.
                                }
                            }
                        }
                        String iconUrl = "";
                        for (final JsonElement mediaElement : array(achievement, "mediaAssets")) {
                            final JsonObject media = mediaElement.getAsJsonObject();
                            if ("Icon".equalsIgnoreCase(string(media, "type"))) {
                                iconUrl = string(media, "url");
                                break;
                            }
                        }
                        achievements.add(new Achievement(string(achievement, "id"), string(achievement, "name"),
                            achieved ? string(achievement, "description") : string(achievement, "lockedDescription"),
                            achieved, gamerscore, string(object(achievement, "progression"), "timeUnlocked"), iconUrl));
                    }
                    continuation = string(object(data, "pagingInfo"), "continuationToken");
                    if (continuation.isBlank()) {
                        break;
                    }
                }
                return List.copyOf(achievements);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Minecraft achievements", exception);
            }
        });
    }

    /** Four Minecraft profile statistics aggregated across Bedrock platforms. */
    public static CompletableFuture<Map<Statistic, String>> statistics(final BedrockAuthManager account,
                                                                       final String xuid) {
        if (!xuid.matches("[0-9]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Xbox user ID"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                final JsonObject body = new JsonObject();
                final JsonArray users = new JsonArray();
                users.add(xuid);
                body.add("requestedusers", users);
                final JsonArray configurations = new JsonArray();
                for (final String scid : MINECRAFT_SERVICE_CONFIG_IDS) {
                    final JsonObject configuration = new JsonObject();
                    configuration.addProperty("scid", scid);
                    final JsonArray names = new JsonArray();
                    for (final Statistic statistic : Statistic.values()) {
                        names.add(statistic.apiName);
                    }
                    configuration.add("requestedstats", names);
                    configurations.add(configuration);
                }
                body.add("requestedscids", configurations);

                final HttpRequest request = HttpRequest.newBuilder(USER_STATS.resolve("batch?operation=read"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", account.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Xbl-Contract-Version", "1")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                final JsonObject response = send("Minecraft statistics", request);
                if (!response.has("users") || !response.get("users").isJsonArray()) {
                    throw new IOException("Xbox statistics response has no users");
                }
                final Map<Statistic, BigDecimal> totals = new EnumMap<>(Statistic.class);
                for (final JsonElement userElement : array(response, "users")) {
                    final JsonObject user = userElement.getAsJsonObject();
                    if (!xuid.equals(string(user, "xuid"))) {
                        continue;
                    }
                    for (final JsonElement configurationElement : array(user, "scids")) {
                        for (final JsonElement element : array(configurationElement.getAsJsonObject(), "stats")) {
                            final JsonObject stat = element.getAsJsonObject();
                            for (final Statistic known : Statistic.values()) {
                                if (known.apiName.equalsIgnoreCase(string(stat, "statname"))) {
                                    try {
                                        totals.merge(known, new BigDecimal(string(stat, "value")), BigDecimal::add);
                                    } catch (NumberFormatException ignored) {
                                        // Xbox can omit or replace a statistic value on individual platforms.
                                    }
                                }
                            }
                        }
                    }
                }
                final Map<Statistic, String> stats = new EnumMap<>(Statistic.class);
                totals.forEach((statistic, value) -> stats.put(statistic, value.stripTrailingZeros().toPlainString()));
                return Map.copyOf(stats);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Minecraft statistics", exception);
            }
        });
    }

    private static JsonObject send(final String operation, final HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw BedrockXboxError.response(operation, response);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonArray() ? data.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    public record Achievement(String id, String name, String description, boolean achieved, int gamerscore,
                              String unlockedAt, String iconUrl) {
    }

    public enum Statistic {
        MINUTES_PLAYED("MinutesPlayed"), BLOCKS_BROKEN("BlockBrokenTotal"), MOBS_DEFEATED("MobKilled.IsMonster.1"),
        DISTANCE_TRAVELED("DistanceTravelled");

        private final String apiName;

        Statistic(final String apiName) {
            this.apiName = apiName;
        }
    }

}
