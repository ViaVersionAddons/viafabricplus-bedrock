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

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayer extends Avatar {

    @Unique
    private int viaFabricPlusBedrock$ticksSinceSwimming;

    protected MixinPlayer(final EntityType<? extends LivingEntity> type, final Level level) {
        super(type, level);
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos modifyWaterAbovePosition(final double x, final double y, final double z) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return BlockPos.containing(x, y - 0.9, z);
        } else {
            return BlockPos.containing(x, y, z);
        }
    }

    @WrapWithCondition(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private boolean preventSwimmingMotionWhenJumping(final Player instance, final Vec3 movement) {
        return !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) || !instance.isJumping();
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private void preventJumpingWhenStartedSwimming(final Vec3 input, final CallbackInfo ci) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }

        if (this.isSwimming()) {
            this.viaFabricPlusBedrock$ticksSinceSwimming++;
        } else {
            this.viaFabricPlusBedrock$ticksSinceSwimming = 0;
        }
        if (this.viaFabricPlusBedrock$ticksSinceSwimming > 0 && this.viaFabricPlusBedrock$ticksSinceSwimming < 10 && this.isJumping()) {
            this.setDeltaMovement(this.getDeltaMovement().x(), 0, this.getDeltaMovement().z());
        }
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSwimming()Z"))
    private boolean preventSwimmingResurface(final Player instance) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) || !instance.isSwimming()) {
            return instance.isSwimming();
        }

        final double lookY = this.getLookAngle().y;
        // TODO: The value used here (0.55) isn't entirely correct, however in most cases it should be fine.
        if (this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 0.4, this.getZ())).isEmpty() && lookY > 0 && lookY < 0.55) {
            instance.setDeltaMovement(instance.getDeltaMovement().x(), 0, instance.getDeltaMovement().z());
            return false;
        }

        return true;
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 1))
    private void removeFlySlipperiness(final Player instance, final Vec3 movement, @Local(argsOnly = true) final Vec3 input) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && input.horizontalDistanceSqr() == 0) {
            instance.setDeltaMovement(new Vec3(0, movement.y, 0));
        } else {
            instance.setDeltaMovement(movement);
        }
    }

}
