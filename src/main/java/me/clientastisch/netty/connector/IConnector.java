package me.clientastisch.netty.connector;

import io.netty.channel.ChannelHandlerContext;

@SuppressWarnings("all")
public interface IConnector {

    void onException(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    void receivePacket(ChannelHandlerContext context, Object object) throws Exception;

    void onDisconnect(ChannelHandlerContext context) throws Exception;

    void onException(Throwable cause);
}
