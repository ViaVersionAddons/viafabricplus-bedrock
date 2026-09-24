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

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viafabricplus.bedrock.injection.access.IEventLoopGroupHolder;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.netty.RakNetPingEncapsulationCodec;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetInetSocketAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetHttpAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetJsonRpcAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetLanAddress;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.BedrockRakNetStatusProtocol;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetDiscoverySignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetXboxRpcSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetXboxSignaling;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.IOException;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.netty.PacketCodec;
import net.raphimc.viabedrock.netty.raknet.MessageCodec;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, priority = 1001) // Apply after ViaFabricPlus' own connection mixin
public abstract class MixinConnection extends SimpleChannelInboundHandler<Packet<?>> {

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void leaveFriendWorld(final ChannelHandlerContext context, final CallbackInfo ci) {
        BedrockFriendsService.leaveIfCurrent(context.channel().remoteAddress());
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        if (BedrockProtocolVersion.bedrockLatest.equals(((IConnection) this).viaFabricPlus$getTargetVersion())) { // Call channelActive manually when the channel is registered
            this.channelActive(ctx);
        }
    }

    @WrapWithCondition(method = "channelActive", at = @At(value = "INVOKE", target = "Lio/netty/channel/SimpleChannelInboundHandler;channelActive(Lio/netty/channel/ChannelHandlerContext;)V", remap = false))
    private boolean dontCallChannelActiveTwice(final SimpleChannelInboundHandler<Packet<?>> instance, final ChannelHandlerContext channelHandlerContext) {
        return !BedrockProtocolVersion.bedrockLatest.equals(((IConnection) this).viaFabricPlus$getTargetVersion());
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private static void useCompatibleEventLoopGroup(final InetSocketAddress inetSocketAddress, final EventLoopGroupHolder eventLoopGroupHolder, final Connection connection, final CallbackInfoReturnable<ChannelFuture> cir, @Local(argsOnly = true) final LocalRef<EventLoopGroupHolder> eventLoopGroupHolderRef) {
        final ProtocolVersion targetVersion = ((IConnection) connection).viaFabricPlus$getTargetVersion();
        if (BedrockProtocolVersion.bedrockLatest.equals(targetVersion) && (eventLoopGroupHolder.channelCls() == KQueueSocketChannel.class || inetSocketAddress instanceof NetherNetInetSocketAddress)) { // RakNet does not support KQueue, switch to NIO. NetherNet requires NIO
            final EventLoopGroupHolder newEventLoopGroupHolder = EventLoopGroupHolder.remote(false);
            ((IEventLoopGroupHolder) newEventLoopGroupHolder).viaFabricPlusBedrock$setConnecting(((IEventLoopGroupHolder) eventLoopGroupHolder).viaFabricPlusBedrock$isConnecting());
            eventLoopGroupHolderRef.set(newEventLoopGroupHolder);
        }
    }

    @WrapOperation(method = "connect", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;", remap = false))
    private static AbstractBootstrap<?, ?> useRakNetChannelFactory(final Bootstrap instance, Class<? extends Channel> channelTypeClass, final Operation<AbstractBootstrap<Bootstrap, Channel>> original, @Local(argsOnly = true) final InetSocketAddress address, @Local(argsOnly = true) final Connection clientConnection) {
        if (!BedrockProtocolVersion.bedrockLatest.equals(((IConnection) clientConnection).viaFabricPlus$getTargetVersion())) {
            return original.call(instance, channelTypeClass);
        }

        if (address instanceof final NetherNetInetSocketAddress netherNetAddress) {
            final SocketAddress remote = netherNetAddress.getNetherNetAddress();
            final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
            final NetherNetClientSignaling signaling;
            if (remote instanceof NetherNetHttpAddress) {
                signaling = new NetherNetHTTPClientSignaling();
            } else if (remote instanceof NetherNetLanAddress) {
                signaling = new NetherNetDiscoverySignaling();
            } else {
                if (account == null) {
                    throw new IllegalStateException("An Xbox account is required for Xbox NetherNet signaling");
                }
                final String authorizationHeader;
                try {
                    authorizationHeader = account.getMinecraftSession().refresh().getAuthorizationHeader();
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not refresh the Bedrock signaling session", exception);
                }
                signaling = remote instanceof NetherNetJsonRpcAddress
                    ? new NetherNetXboxRpcSignaling(authorizationHeader)
                    : new NetherNetXboxSignaling(authorizationHeader);
            }

            if (account != null) {
                try {
                    final MinecraftMultiplayerToken token = account.getMinecraftMultiplayerToken().refresh();
                    instance.option(NetherChannelOption.NETHER_CLIENT_IDENTITY,
                        OperatorIdentity.fromToken(account.getSessionKeyPair(), token.getToken(), "https://authorization.franchise.minecraft-services.net/"));
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not refresh the Bedrock multiplayer identity", exception);
                }
            }
            return instance.channelFactory(NetherNetChannelFactory.client(signaling));
        } else { // RakNet
            if (channelTypeClass == NioSocketChannel.class) {
                channelTypeClass = NioDatagramChannel.class;
            } else if (channelTypeClass == EpollSocketChannel.class) {
                channelTypeClass = EpollDatagramChannel.class;
            } else {
                throw new IllegalStateException("Unsupported channel type for RakNet: " + channelTypeClass);
            }
            return instance.channelFactory(RakChannelFactory.client((Class<? extends DatagramChannel>) channelTypeClass));
        }
    }

    @WrapOperation(method = "connect", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;", remap = false))
    private static ChannelFuture useRakNetPingHandlers(final Bootstrap instance, final InetAddress inetHost, final int inetPort, final Operation<ChannelFuture> original, @Local(argsOnly = true) final InetSocketAddress address, @Local(argsOnly = true) final Connection clientConnection, @Local(argsOnly = true) final EventLoopGroupHolder eventLoopGroupHolder) {
        if (BedrockProtocolVersion.bedrockLatest.equals(((IConnection) clientConnection).viaFabricPlus$getTargetVersion())) {
            if (address instanceof final NetherNetInetSocketAddress netherNetAddress) {
                final SocketAddress remote = netherNetAddress.getNetherNetAddress();
                final SocketAddress connectAddress;
                if (remote instanceof NetherNetHttpAddress httpAddress) {
                    connectAddress = new InetSocketAddress(httpAddress.host(), httpAddress.port());
                } else if (remote instanceof NetherNetLanAddress lanAddress) {
                    connectAddress = new InetSocketAddress(lanAddress.host(), lanAddress.port());
                } else {
                    connectAddress = remote;
                }
                return instance.connect(connectAddress).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, (ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        f.channel().pipeline().remove(MessageCodec.NAME);
                    }
                });
            } else if (!((IEventLoopGroupHolder) eventLoopGroupHolder).viaFabricPlusBedrock$isConnecting()) {
                // Bedrock edition / RakNet has different handlers for pinging a server
                return instance.register().syncUninterruptibly().channel().bind(new InetSocketAddress(0)).addListeners(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE, (ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        f.channel().pipeline().replace(
                            MessageCodec.NAME,
                            RakNetPingEncapsulationCodec.NAME,
                            new RakNetPingEncapsulationCodec(new InetSocketAddress(inetHost, inetPort))
                        );
                        f.channel().pipeline().remove(PacketCodec.NAME);
                        f.channel().pipeline().remove(HandlerNames.SPLITTER);

                        final UserConnection user = ((IConnection) clientConnection).viaFabricPlus$getUserConnection();
                        user.getProtocolInfo().getPipeline().add(BedrockRakNetStatusProtocol.INSTANCE);
                    }
                });
            }
        }
        return original.call(instance, inetHost, inetPort);
    }

}
