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

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HoneyBlock.class)
public abstract class MixinHoneyBlock extends Block {

    @Unique
    private static final VoxelShape viaFabricPlusBedrock$shape = Shapes.box(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);

    public MixinHoneyBlock(final Properties settings) {
        super(settings);
    }

    @Shadow
    protected abstract boolean isSlidingDown(final BlockPos pos, final Entity entity);

    @Shadow
    protected abstract void maybeDoSlideEffects(final Level level, final Entity entity);

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void changeCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context, final CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            cir.setReturnValue(viaFabricPlusBedrock$shape);
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void applyBedrockHoneyCollision(final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier effectApplier, final boolean isPrecise, final CallbackInfo ci) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }

        ci.cancel();
        if (this.isSlidingDown(pos, entity)) {
            this.maybeDoSlideEffects(level, entity);
        }

        final Vec3 velocity = entity.getDeltaMovement();
        entity.setDeltaMovement(new Vec3(velocity.x * 0.4F, Math.max(-0.12F, velocity.y), velocity.z * 0.4F));
    }

    @Override
    public void stepOn(final @NonNull Level world, final @NonNull BlockPos pos, final @NonNull BlockState state, final @NonNull Entity entity) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            final double absoluteY = Math.abs(entity.getDeltaMovement().y);
            if (absoluteY < 0.1 && !entity.isSteppingCarefully()) {
                final double frictionFactor = 0.4 + absoluteY * 0.2;
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(frictionFactor, 1.0F, frictionFactor));
            }
        } else {
            super.stepOn(world, pos, state, entity);
        }
    }

    @Override
    public float getFriction() {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? 0.8F : super.getFriction();
    }

    @Override
    public float getSpeedFactor() {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? 1F : super.getSpeedFactor();
    }

    @Override
    public float getJumpFactor() {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? 0.6F : super.getJumpFactor();
    }

}
