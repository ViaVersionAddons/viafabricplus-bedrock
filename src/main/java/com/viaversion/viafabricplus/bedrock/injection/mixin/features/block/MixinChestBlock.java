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
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public abstract class MixinChestBlock {

    @Unique
    private static final VoxelShape viaFabricPlusBedrock$single_shape = Shapes.box(0.025, 0, 0.025, 0.975, 0.95, 0.975);

    @Unique
    private static final Map<Direction, VoxelShape> viaFabricPlusBedrock$double_shapes = Map.of(
        Direction.NORTH, Shapes.box(0.025, 0, 0, 0.975, 0.95, 0.975),
        Direction.SOUTH, Shapes.box(0.025, 0, 0.025, 0.975, 0.95, 1),
        Direction.WEST, Shapes.box(0, 0, 0.025, 0.975, 0.95, 0.975),
        Direction.EAST, Shapes.box(0.025, 0, 0.025, 1, 0.95, 0.975)
    );

    @Shadow
    @Final
    private static Map<Direction, VoxelShape> HALF_SHAPES;

    @Shadow
    @Final
    private static VoxelShape SHAPE;

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context, final CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            cir.setReturnValue(switch (state.getValue(ChestBlock.TYPE)) {
                case SINGLE -> viaFabricPlusBedrock$single_shape;
                case LEFT, RIGHT -> viaFabricPlusBedrock$double_shapes.get(ChestBlock.getConnectedDirection(state));
            });
        }
    }

    // The method is added to the block by ViaFabricPlus, so this addon can only inject into it
    @Inject(method = "getOcclusionShape", at = @At("HEAD"), cancellable = true)
    private void changeOcclusionShape(final BlockState state, final CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                cir.setReturnValue(SHAPE);
            } else {
                cir.setReturnValue(HALF_SHAPES.get(ChestBlock.getConnectedDirection(state)));
            }
        }
    }

}
