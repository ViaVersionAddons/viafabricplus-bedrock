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

package com.viaversion.viafabricplus.bedrock.screen;

import com.mojang.blaze3d.Blaze3D;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.visual.BedrockImageCache;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Lists screenshots captured by this Minecraft client, including the F2 shortcut. */
public final class BedrockScreenshotGalleryScreen extends VFPScreen {

    private final Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    private List<Path> screenshots = List.of();
    private boolean loaded;
    private boolean failed;
    private GalleryList list;
    private Button openButton;

    public BedrockScreenshotGalleryScreen() {
        super(Component.translatable("screen.viafabricplus.bedrock_gallery"), true);
    }

    @Override
    protected void init() {
        this.list = this.addRenderableWidget(new GalleryList(this.minecraft, this.width, this.height, 60,
            FOOTER_HEIGHT, 54));
        this.openButton = Button.builder(Component.translatable("bedrock_gallery.viafabricplus.open"), _ -> this.openSelected()).build();
        this.addFooter(this.openButton,
            Button.builder(Component.translatable("bedrock_gallery.viafabricplus.folder"), _ -> Blaze3D.openUri(this.folder.toUri())).build(),
            Button.builder(Component.translatable("bedrock_friends.viafabricplus.refresh"), _ -> this.refresh()).build());
        super.init();
        if (!this.loaded) {
            this.refresh();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.openButton.active = this.list.getFocused() instanceof ScreenshotEntry;
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        super.renderTitle(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 32, -1);
    }

    private void refresh() {
        this.loaded = true;
        try {
            if (!Files.isDirectory(this.folder)) {
                this.screenshots = List.of();
            } else {
                try (Stream<Path> files = Files.list(this.folder)) {
                    this.screenshots = files.filter(Files::isRegularFile)
                        .filter(path -> {
                            final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                        })
                        .sorted(Comparator.comparingLong(BedrockScreenshotGalleryScreen::modified).reversed())
                        .limit(100).toList();
                }
            }
            this.failed = false;
        } catch (IOException exception) {
            this.failed = true;
            ViaFabricPlusBedrock.impl().logger().error("Failed to read Minecraft screenshots", exception);
            showToast(Component.translatable("bedrock_gallery.viafabricplus.failed"));
        }
        this.rebuildWidgets();
    }

    private static long modified(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void openSelected() {
        if (this.list.getFocused() instanceof ScreenshotEntry entry && Files.isRegularFile(entry.path)) {
            Blaze3D.openUri(entry.path.toUri());
        }
    }

    private final class GalleryList extends ActionList {

        private GalleryList(final Minecraft minecraft, final int width, final int height, final int top,
                            final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            if (BedrockScreenshotGalleryScreen.this.failed) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_gallery.viafabricplus.failed")));
            } else if (BedrockScreenshotGalleryScreen.this.screenshots.isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_gallery.viafabricplus.empty")));
            } else {
                BedrockScreenshotGalleryScreen.this.screenshots.forEach(path -> this.addEntry(new ScreenshotEntry(path)));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(352, this.width - 20);
        }

        @Override
        protected boolean activate(final VFPListEntry entry) {
            if (entry instanceof ScreenshotEntry screenshot && Files.isRegularFile(screenshot.path)) {
                BedrockScreenshotGalleryScreen.this.openSelected();
                return true;
            }
            return false;
        }

    }

    private static final class ScreenshotEntry extends VFPListEntry {

        private final Path path;

        private ScreenshotEntry(final Path path) {
            this.path = path;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.path.getFileName().toString());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int width, final int height) {
            final Font font = Minecraft.getInstance().font;
            final String name = this.path.getFileName().toString();
            final int thumbnailWidth = 70;
            BedrockImageCache.drawScreenshot(graphics, this.path, SLOT_MARGIN, SLOT_MARGIN,
                thumbnailWidth, height - SLOT_MARGIN * 2);
            final int textX = SLOT_MARGIN + thumbnailWidth + 8;
            final int available = width - textX - SLOT_MARGIN;
            final String shown = font.width(name) <= available ? name
                : font.plainSubstrByWidth(name, Math.max(0, available - font.width("…"))) + "…";
            graphics.text(font, shown, textX, SLOT_MARGIN + 6, -1);
            graphics.text(font, Component.translatable("bedrock_gallery.viafabricplus.local"), textX,
                SLOT_MARGIN + font.lineHeight + 10, 0xFFB8B8B8);
        }

    }

}
