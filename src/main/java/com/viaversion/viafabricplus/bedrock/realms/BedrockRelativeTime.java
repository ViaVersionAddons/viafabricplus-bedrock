/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.realms;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.jetbrains.annotations.Nullable;

/** Formats Realm event timestamps in the compact style used by Bedrock. */
public final class BedrockRelativeTime {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DATE_WITH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private BedrockRelativeTime() {
    }

    public static @Nullable Instant parse(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    public static String format(final @Nullable Instant timestamp, final Instant now, final ZoneId zone) {
        if (timestamp == null) {
            return "Date unavailable";
        }
        final long seconds = Math.max(0, Duration.between(timestamp, now).getSeconds());
        if (seconds < 60) {
            return "Just now";
        }
        if (seconds < 3_600) {
            return seconds / 60 + "m ago";
        }
        if (seconds < 86_400) {
            return seconds / 3_600 + "h ago";
        }
        if (seconds < 604_800) {
            return seconds / 86_400 + "d ago";
        }
        final var date = timestamp.atZone(zone);
        return (date.getYear() == now.atZone(zone).getYear() ? DATE : DATE_WITH_YEAR).format(date);
    }

}
