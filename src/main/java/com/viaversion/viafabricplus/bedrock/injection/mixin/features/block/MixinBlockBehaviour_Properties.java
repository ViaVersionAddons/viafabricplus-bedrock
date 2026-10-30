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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.Properties.class)
public abstract class MixinBlockBehaviour_Properties {

    // Bedrock random offset parameters for bamboo (uses BlockRandomOffsetDefaults::XZ)
    @Unique
    private static final float viaFabricPlusBedrock$OFFSET_MIN = -0.25F; // -4/16

    @Unique
    private static final float viaFabricPlusBedrock$OFFSET_MAX = 0.25F;  // 4/16

    @Unique
    private static final int viaFabricPlusBedrock$STEPS = 16; // Quantization steps

    @Shadow
    private BlockBehaviour.OffsetFunction offsetFunction;

    @Inject(method = "offsetType", at = @At("RETURN"))
    private void fixBlockOffsets(final BlockBehaviour.OffsetType offsetType, final CallbackInfoReturnable<BlockBehaviour.Properties> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && offsetType != BlockBehaviour.OffsetType.NONE) {
            this.offsetFunction = (state, pos) -> viaFabricPlusBedrock$randomlyModifyPosition(pos, offsetType);
        }
    }

    // Bedrock's position hash only uses the X and Z coordinates
    @Unique
    private static long viaFabricPlusBedrock$positionHash(final int x, final int z) {
        // Step 1: Initial hash from X and Z
        final long v1 = (116129781L * z) ^ ((0x2FC20F00000001L * Integer.toUnsignedLong(x)) >> 32);

        // Step 2: LCG-style mixing
        // Note: Bedrock uses cdqe instruction which sign-extends low 32 bits to 64 bits
        final long temp = (v1 * (42317861L * v1 + 11L)) >>> 16;
        return (int) temp ^ 0x6A09E667F3BCC909L;
    }

    // Same as Bedrock: (random >>> 40) * 2^-24, giving a float in [0, 1)
    @Unique
    private static float viaFabricPlusBedrock$randomToFloat(final long random) {
        return (random >>> 40) * 5.9604645e-8F;
    }

    // Bedrock quantizes the offset to discrete steps
    @Unique
    private static float viaFabricPlusBedrock$calculateOffsetValue(final float min, final float max, final float random) {
        if (min >= max) {
            return min;
        }

        if (viaFabricPlusBedrock$STEPS == 1) {
            return (min + max) * 0.5F;
        } else if (viaFabricPlusBedrock$STEPS > 1) {
            final float range = max - min;
            final float stepSize = range / (viaFabricPlusBedrock$STEPS - 1);
            final float index = (float) Math.floor(viaFabricPlusBedrock$STEPS * random);
            return min + index * stepSize;
        } else {
            return min + (max - min) * random;
        }
    }

    @Unique
    private static Vec3 viaFabricPlusBedrock$randomlyModifyPosition(final BlockPos pos, final BlockBehaviour.OffsetType type) {
        // Use Bedrock's custom position hash
        final long seed = viaFabricPlusBedrock$positionHash(pos.getX(), pos.getZ());

        // Use Minecraft's SplitMix64 (mixStafford13) to generate the PRNG state
        final long s0 = RandomSupport.mixStafford13(seed);
        final long s1 = RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64);

        // Use Minecraft's Xoroshiro128PlusPlus
        final Xoroshiro128PlusPlus prng = new Xoroshiro128PlusPlus(s0, s1);

        // Generate X offset with quantization
        final float offsetX = viaFabricPlusBedrock$calculateOffsetValue(viaFabricPlusBedrock$OFFSET_MIN, viaFabricPlusBedrock$OFFSET_MAX, viaFabricPlusBedrock$randomToFloat(prng.nextLong()));
        final float offsetY = switch (type) {
            case XZ -> {
                // Y offset - must consume random even though Y range is (0,0) in XZ mode
                // BDS advances PRNG state regardless of whether the offset is computed
                prng.nextLong();

                yield 0;
            }
            // Generate Y offset with quantization
            case XYZ -> viaFabricPlusBedrock$calculateOffsetValue(-0.2F, 0, viaFabricPlusBedrock$randomToFloat(prng.nextLong()));
            case NONE -> 0;
        };

        // Generate Z offset with quantization
        final float offsetZ = viaFabricPlusBedrock$calculateOffsetValue(viaFabricPlusBedrock$OFFSET_MIN, viaFabricPlusBedrock$OFFSET_MAX, viaFabricPlusBedrock$randomToFloat(prng.nextLong()));
        return new Vec3(offsetX, offsetY, offsetZ);
    }

}
