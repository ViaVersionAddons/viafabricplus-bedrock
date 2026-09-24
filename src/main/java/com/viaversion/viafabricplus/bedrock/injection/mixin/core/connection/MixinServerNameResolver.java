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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.connection;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.injection.access.IServerAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetInetSocketAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
public abstract class MixinServerNameResolver {

    @Shadow
    @Final
    private ServerAddressResolver resolver;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void resolveBedrockAddress(final ServerAddress address, final CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        final SocketAddress netherNetAddress = ((IServerAddress) (Object) address).viaFabricPlusBedrock$getNetherNetAddress();
        if (netherNetAddress != null) {
            cir.setReturnValue(Optional.of(new ResolvedServerAddress() {
                @Override
                public @NonNull String getHostName() {
                    return netherNetAddress.toString();
                }

                @Override
                public @NonNull String getHostIp() {
                    return netherNetAddress.toString();
                }

                @Override
                public int getPort() {
                    return 0;
                }

                @Override
                public @NonNull InetSocketAddress asInetSocketAddress() {
                    return new NetherNetInetSocketAddress(netherNetAddress);
                }
            }));
        } else if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            // Bedrock servers don't use SRV records, so the redirect handler has to be skipped
            cir.setReturnValue(this.resolver.resolve(address));
        }
    }

}
