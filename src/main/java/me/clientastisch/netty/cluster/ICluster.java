package me.clientastisch.netty.cluster;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

@SuppressWarnings("all")
public interface ICluster {

    void onConnect(ChannelHandlerContext context);

    void onDisconnect(ChannelHandlerContext context);

    void receivePacket(ChannelHandlerContext context, Object object);

    void onException(ChannelHandlerContext context, Throwable cause);

    void onTimeout(Channel channel, long lastResponse);

    void onException(Throwable cause);
}
