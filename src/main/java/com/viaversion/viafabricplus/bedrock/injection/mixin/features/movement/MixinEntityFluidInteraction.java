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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.AABB;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Bedrock uses the pre 1.13 fluid handling, ViaFabricPlus' own injections already claim its targets so other ones are used
@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {

    @Definition(id = "fluidTop", local = @Local(type = double.class, name = "fluidTop"))
    @Definition(id = "box", local = @Local(type = AABB.class, name = "box"))
    @Definition(id = "minY", field = "Lnet/minecraft/world/phys/AABB;minY:D")
    @Expression("fluidTop < box.minY")
    @ModifyExpressionValue(method = "update", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean removeConditional(final boolean original) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return false; // Equates to true due to the negation in the original code
        } else {
            return original;
        }
    }

    // The fluid height is measured 0.4 below the entity, which is the same as adding 0.4 to the measured height
    @ModifyVariable(method = "update", at = @At("STORE"), name = "entityY")
    private double adjustHeightCalculation(final double entityY) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return entityY - 0.4;
        } else {
            return entityY;
        }
    }

    // Dropping the threshold skips the scaling of the current in shallow fluids
    @ModifyConstant(method = "update", constant = @Constant(doubleValue = 0.4))
    private double dontScaleCurrent(final double constant) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return -1;
        } else {
            return constant;
        }
    }

}
