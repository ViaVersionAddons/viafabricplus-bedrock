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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.exception.MinecraftServicesRequestException;
import org.jetbrains.annotations.Nullable;

/** Formats Xbox PeopleHub, Social, and multiplayer session errors for the Friends screen. */
public final class BedrockXboxError {

    private BedrockXboxError() {
    }

    public static XboxRequestException response(final String operation, final HttpResponse<String> response) {
        String code = "";
        String message = "";
        try {
            final JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            final JsonObject details = body.has("error") && body.get("error").isJsonObject()
                ? body.getAsJsonObject("error") : body;
            code = first(details, "code", "errorCode", "errorcode", "xErr", "XErr");
            message = first(details, "message", "errorMessage", "errormessage", "errorMsg", "description");
            if (message.isBlank() && details != body) {
                message = first(body, "message", "errorMessage", "errorMsg");
            }
        } catch (RuntimeException ignored) {
            // Some Xbox endpoints send an empty body for an HTTP error.
        }
        return new XboxRequestException(operation, response.statusCode(), code, message);
    }

    public static Component describe(final Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof XboxRequestException request) {
                if (request.status == 401) {
                    return Component.translatable("bedrock_friends.viafabricplus.error.auth");
                }
                if (request.status == 429) {
                    return Component.translatable("bedrock_friends.viafabricplus.error.rate_limit");
                }
                if (request.status >= 500) {
                    return Component.translatable("bedrock_friends.viafabricplus.error.unavailable", request.status);
                }
                if (!request.serviceMessage.isBlank()) {
                    final String code = request.serviceCode.isBlank() ? "HTTP " + request.status : "code " + shorten(request.serviceCode);
                    return Component.translatable("bedrock_friends.viafabricplus.error.service", shorten(request.serviceMessage), code);
                }
                return switch (request.status) {
                    case 403 -> Component.translatable("bedrock_friends.viafabricplus.error.denied");
                    case 404 -> Component.translatable("bedrock_friends.viafabricplus.error.not_found");
                    case 409, 412 -> Component.translatable("bedrock_friends.viafabricplus.error.changed");
                    default -> Component.translatable("bedrock_friends.viafabricplus.error.http", request.status);
                };
            }
            if (cause instanceof HttpRequestException request && request.getResponse() != null) {
                final int status = request.getResponse().getStatusCode();
                return switch (status) {
                    case 401 -> Component.translatable("bedrock_friends.viafabricplus.error.auth");
                    case 429 -> Component.translatable(request instanceof MinecraftServicesRequestException
                        ? "bedrock_friends.viafabricplus.error.token_rate_limit"
                        : "bedrock_friends.viafabricplus.error.rate_limit");
                    default -> status >= 500
                        ? Component.translatable("bedrock_friends.viafabricplus.error.unavailable", status)
                        : Component.translatable("bedrock_friends.viafabricplus.error.http", status);
                };
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return Component.translatable("bedrock_friends.viafabricplus.error.timeout");
            }
        }
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        final String message = shorten(root.getMessage());
        return message.isBlank() ? Component.translatable("bedrock_friends.viafabricplus.error.unknown")
            : Component.translatable("bedrock_friends.viafabricplus.error.other", message);
    }

    public static boolean isRateLimited(final Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof XboxRequestException request && request.status == 429) {
                return true;
            }
            if (cause instanceof HttpRequestException request && request.getResponse() != null
                && request.getResponse().getStatusCode() == 429) {
                return true;
            }
        }
        return false;
    }

    private static String first(final JsonObject object, final String... keys) {
        for (final String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        }
        return "";
    }

    private static String shorten(final @Nullable String message) {
        if (message == null) {
            return "";
        }
        final String clean = message.replaceAll("\\s+", " ").strip();
        return clean.length() <= 96 ? clean : clean.substring(0, 93) + "…";
    }

    public static final class XboxRequestException extends IOException {

        private final int status;
        private final String serviceCode;
        private final String serviceMessage;

        private XboxRequestException(final String operation, final int status, final String serviceCode, final String serviceMessage) {
            super(operation + " failed: HTTP " + status + (serviceCode.isBlank() ? "" : ", code " + serviceCode)
                + (serviceMessage.isBlank() ? "" : ", message " + serviceMessage));
            this.status = status;
            this.serviceCode = serviceCode;
            this.serviceMessage = serviceMessage;
        }

    }

}
