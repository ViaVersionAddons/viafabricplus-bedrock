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

import java.net.http.HttpTimeoutException;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.extra.realms.exception.RealmsRequestException;
import org.jetbrains.annotations.Nullable;

/** Turns Realms responses into short, useful messages while preserving unknown service errors. */
public final class BedrockRealmsError {

    private static final int TIMELINE_OPT_IN_REQUIRED = RealmsRequestException.ERROR_TIMELINE_OPT_IN_REQUIRED;

    private BedrockRealmsError() {
    }

    public static @Nullable RealmsRequestException request(final Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RealmsRequestException requestException) {
                return requestException;
            }
        }
        return null;
    }

    public static boolean timelineOptInRequired(final Throwable error) {
        final RealmsRequestException request = request(error);
        return request != null && request.getErrorCode() == TIMELINE_OPT_IN_REQUIRED;
    }

    public static boolean subscriptionMissing(final Throwable error) {
        final RealmsRequestException request = request(error);
        return request != null && request.getErrorCode() == 403
            && "No valid subscription".equalsIgnoreCase(request.getErrorMessage());
    }

    public static Component describe(final Throwable error) {
        final RealmsRequestException request = request(error);
        if (request != null) {
            if (subscriptionMissing(error)) {
                return Component.translatable("bedrock_realms.viafabricplus.subscription_required");
            }
            final String known = switch (request.getErrorCode()) {
                case 6001 -> "client_outdated";
                case 6002 -> "terms_required";
                case 6003 -> "download_limited";
                case 6004 -> "upload_limited";
                case 6005 -> "world_locked";
                case 6006 -> "world_outdated";
                case 6007 -> "too_many_realms";
                case 6008 -> "invalid_name";
                case 6009 -> "invalid_description";
                case TIMELINE_OPT_IN_REQUIRED -> "timeline_required";
                default -> null;
            };
            if (known != null) {
                return Component.translatable("bedrock_realms.viafabricplus." + known);
            }
            final String message = shorten(request.getErrorMessage());
            if (!message.isBlank()) {
                return Component.translatable("bedrock_realms.viafabricplus.api_error", message, request.getErrorCode());
            }
            return Component.translatable("bedrock_realms.viafabricplus.api_code", request.getErrorCode());
        }

        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpRequestException requestException) {
                final int status = requestException.getResponse().getStatusCode();
                if (status == 401) {
                    return Component.translatable("bedrock_realms.viafabricplus.sign_in_again");
                }
                if (status == 429) {
                    return Component.translatable("bedrock_realms.viafabricplus.rate_limited");
                }
                if (status >= 500) {
                    return Component.translatable("bedrock_realms.viafabricplus.service_unavailable", status);
                }
                return Component.translatable("bedrock_realms.viafabricplus.http_error", status,
                    shorten(requestException.getMessage()));
            }
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return Component.translatable("bedrock_realms.viafabricplus.timeout");
            }
        }
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        final String message = shorten(root.getMessage());
        return message.isBlank() ? Component.translatable("bedrock_realms.viafabricplus.unknown_error")
            : Component.translatable("bedrock_realms.viafabricplus.other_error", message);
    }

    private static String shorten(final @Nullable String message) {
        if (message == null) {
            return "";
        }
        final String clean = message.replaceAll("\\s+", " ").strip();
        return clean.length() <= 96 ? clean : clean.substring(0, 93) + "…";
    }

}
