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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.player.Player;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EntityFluidInteraction.CurrentAccumulator.class)
public abstract class MixinEntityFluidInteraction_CurrentAccumulator {

    // Bedrock normalizes the accumulated current for players as well
    @Definition(id = "entity", local = @Local(type = Entity.class, argsOnly = true))
    @Definition(id = "Player", type = Player.class)
    @Expression("entity instanceof Player")
    @ModifyExpressionValue(method = "applyTo", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean normalizeInsteadScale(final boolean original) {
        return !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && original;
    }

    // Dropping the threshold skips the boost small currents would get
    @ModifyConstant(method = "applyTo", constant = @Constant(doubleValue = 0.0045000000000000005, ordinal = 0))
    private double dontScaleSmallValues(final double constant) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return -1;
        } else {
            return constant;
        }
    }

}
