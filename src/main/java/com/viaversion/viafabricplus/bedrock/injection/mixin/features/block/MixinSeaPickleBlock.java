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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SeaPickleBlock.class)
public abstract class MixinSeaPickleBlock extends Block {

    @Unique
    private static final VoxelShape viaFabricPlusBedrock$shape = Block.column(16.0F, 0.0F, 6.0F);

    @Shadow
    @Final
    private static VoxelShape SHAPE_ONE;

    @Shadow
    @Final
    private static VoxelShape SHAPE_TWO;

    @Shadow
    @Final
    private static VoxelShape SHAPE_THREE;

    @Shadow
    @Final
    private static VoxelShape SHAPE_FOUR;

    @Shadow
    @Final
    public static IntegerProperty PICKLES;

    public MixinSeaPickleBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context, final CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            cir.setReturnValue(viaFabricPlusBedrock$shape);
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(final @NonNull BlockState state, final @NonNull BlockGetter world, final @NonNull BlockPos pos, final @NonNull CollisionContext context) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return Shapes.empty();
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(final @NonNull BlockState state) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return switch (state.getValue(PICKLES)) {
                case 2 -> SHAPE_TWO;
                case 3 -> SHAPE_THREE;
                case 4 -> SHAPE_FOUR;
                default -> SHAPE_ONE;
            };
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
