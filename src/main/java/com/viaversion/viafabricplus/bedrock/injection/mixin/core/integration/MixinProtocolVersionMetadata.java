/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/ViaVersionAddons/viafabricplus-bedrock
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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.integration;

import com.viaversion.viafabricplus.screen.impl.protocol.ProtocolVersionMetadata;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.resources.Identifier;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProtocolVersionMetadata.class, remap = false)
public abstract class MixinProtocolVersionMetadata {

    @Unique
    private static final Identifier viaFabricPlusBedrock$icon = Identifier.withDefaultNamespace("textures/block/bedrock.png");

    // The name of the Bedrock version changes with every ViaBedrock update, so it is no key of the metadata file
    @Inject(method = "icon(Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private static void bedrockIcon(final ProtocolVersion version, final CallbackInfoReturnable<Identifier> cir) {
        if (BedrockProtocolVersion.BEDROCK_LATEST.equals(version)) {
            cir.setReturnValue(viaFabricPlusBedrock$icon);
        }
    }

}
