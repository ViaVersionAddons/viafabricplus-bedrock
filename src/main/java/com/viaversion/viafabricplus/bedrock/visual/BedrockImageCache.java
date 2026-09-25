/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.bedrock.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Loads Xbox and Realm imagery without blocking the game thread. */
public final class BedrockImageCache {

    private static final int MAX_IMAGES = 128;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<String, Image> IMAGES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private BedrockImageCache() {
    }

    public static boolean drawRemote(final GuiGraphicsExtractor graphics, final String url, final int x,
                                     final int y, final int width, final int height) {
        if (url.isBlank()) return false;
        final URI uri;
        try {
            final URI parsed = URI.create(url);
            uri = URI.create(parsed.toString().replaceFirst("^http:", "https:"));
        } catch (Exception exception) {
            return false;
        }
        final String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
            || host == null || !trustedImageHost(host)) return false;
        return draw(graphics, uri.toString(), () -> {
            final HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            final HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("Image returned HTTP " + response.statusCode());
            return response.body();
        }, 2048, x, y, width, height);
    }

    public static boolean drawEncoded(final GuiGraphicsExtractor graphics, final String key, final String encoded,
                                      final int x, final int y, final int width, final int height) {
        if (encoded.isBlank() || encoded.length() > MAX_BYTES * 4 / 3 + 128) return false;
        final int separator = encoded.indexOf(',');
        final String data = encoded.startsWith("data:image/") && separator > 0
            ? encoded.substring(separator + 1) : encoded;
        return draw(graphics, "encoded:" + key + ':' + encoded.length() + ':' + encoded.hashCode(),
            () -> Base64.getDecoder().decode(data), 2048, x, y, width, height);
    }

    private static boolean trustedImageHost(final String host) {
        return host.equals("xboxlive.com") || host.endsWith(".xboxlive.com")
            || host.equals("xbox.com") || host.endsWith(".xbox.com")
            || host.equals("minecraft.net") || host.endsWith(".minecraft.net")
            || host.equals("minecraft-services.net") || host.endsWith(".minecraft-services.net");
    }

    public static boolean drawBundled(final GuiGraphicsExtractor graphics, final String resource,
                                      final int x, final int y, final int width, final int height) {
        return draw(graphics, resource, () -> {
            try (InputStream stream = BedrockImageCache.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Bundled Bedrock image is missing");
                return stream.readAllBytes();
            }
        }, 4096, x, y, width, height);
    }

    public static boolean drawScreenshot(final GuiGraphicsExtractor graphics, final Path path, final int x,
                                         final int y, final int width, final int height) {
        return draw(graphics, "screenshot:" + path.toUri(), () -> {
            if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("Screenshot is too large");
            final BufferedImage original = ImageIO.read(path.toFile());
            if (original == null || original.getWidth() > 8192 || original.getHeight() > 8192) {
                throw new IOException("Invalid screenshot dimensions");
            }
            final int longest = Math.max(original.getWidth(), original.getHeight());
            final double scale = Math.min(1D, 512D / longest);
            final int scaledWidth = Math.max(1, (int) (original.getWidth() * scale));
            final int scaledHeight = Math.max(1, (int) (original.getHeight() * scale));
            final BufferedImage thumbnail = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D painter = thumbnail.createGraphics();
            try {
                painter.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                painter.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
            } finally {
                painter.dispose();
            }
            final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "png", encoded);
            return encoded.toByteArray();
        }, 2048, x, y, width, height);
    }

    private static boolean draw(final GuiGraphicsExtractor graphics, final String key, final ImageSource source,
                                final int sourceLimit, final int x, final int y, final int width, final int height) {
        final Image image = IMAGES.get(key);
        if (image != null) {
            int sourceWidth = image.width();
            int sourceHeight = image.height();
            if ((long) sourceWidth * height > (long) sourceHeight * width) {
                sourceWidth = Math.max(1, sourceHeight * width / height);
            } else {
                sourceHeight = Math.max(1, sourceWidth * height / width);
            }
            final float u = (image.width() - sourceWidth) / 2F;
            final float v = (image.height() - sourceHeight) / 2F;
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), x, y, u, v,
                width, height, sourceWidth, sourceHeight, image.width(), image.height());
            return true;
        }
        if (!FAILED.contains(key) && LOADING.add(key)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    final byte[] bytes = source.read();
                    if (bytes.length > MAX_BYTES) throw new IOException("Image is too large");
                    final NativeImage pixels = NativeImage.read(isPng(bytes)
                        ? bytes : convertImage(bytes, sourceLimit));
                    if (pixels.getWidth() > sourceLimit || pixels.getHeight() > sourceLimit) {
                        pixels.close();
                        throw new IOException("Image dimensions are too large");
                    }
                    if (pixels.getWidth() > 2048 || pixels.getHeight() > 2048) {
                        final int longest = Math.max(pixels.getWidth(), pixels.getHeight());
                        final int scaledWidth = Math.max(1, pixels.getWidth() * 2048 / longest);
                        final int scaledHeight = Math.max(1, pixels.getHeight() * 2048 / longest);
                        final NativeImage scaled = new NativeImage(pixels.format(), scaledWidth, scaledHeight, false);
                        try {
                            pixels.resizeSubRectTo(0, 0, pixels.getWidth(), pixels.getHeight(), scaled);
                            return scaled;
                        } catch (Exception exception) {
                            scaled.close();
                            throw exception;
                        } finally {
                            pixels.close();
                        }
                    }
                    return pixels;
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not load image", exception);
                }
            }).whenComplete((pixels, error) -> Minecraft.getInstance().execute(() -> {
                LOADING.remove(key);
                if (error != null) {
                    FAILED.add(key);
                    ViaFabricPlusBedrock.impl().logger().warn("Could not load Bedrock image", error);
                    return;
                }
                final Identifier id = Identifier.fromNamespaceAndPath("viafabricplus-bedrock",
                    "image/" + UUID.randomUUID());
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> key, pixels));
                IMAGES.put(key, new Image(id, pixels.getWidth(), pixels.getHeight()));
                if (IMAGES.size() > MAX_IMAGES) {
                    final Iterator<Image> oldest = IMAGES.values().iterator();
                    Minecraft.getInstance().getTextureManager().release(oldest.next().id());
                    oldest.remove();
                }
            }));
        }
        return false;
    }

    private static boolean isPng(final byte[] bytes) {
        return bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P'
            && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static byte[] convertImage(final byte[] bytes, final int limit) throws IOException {
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() > limit || image.getHeight() > limit) {
            throw new IOException("Invalid Bedrock image");
        }
        final int longest = Math.max(image.getWidth(), image.getHeight());
        final double scale = Math.min(1D, 2048D / longest);
        final int width = Math.max(1, (int) (image.getWidth() * scale));
        final int height = Math.max(1, (int) (image.getHeight() * scale));
        final BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D painter = converted.createGraphics();
        try {
            painter.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            painter.drawImage(image, 0, 0, width, height, null);
        } finally {
            painter.dispose();
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(converted, "png", output)) throw new IOException("Could not encode Bedrock image");
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface ImageSource {
        byte[] read() throws Exception;
    }

    private record Image(Identifier id, int width, int height) {
    }

}
