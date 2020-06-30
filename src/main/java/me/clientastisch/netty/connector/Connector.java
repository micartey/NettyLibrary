package me.clientastisch.netty.connector;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class Connector implements ConnectorStructure<Connector> {

    private final ExecutorService executor;

    private EventLoopGroup group;
    private IConnector processor;
    private Channel channel;

    private Type type;

    private String address, name;
    private int port;

    public Connector(String address, int port, Type type) {
        this.executor = Executors.newScheduledThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), 2));
        this.name = "Connector@" + ThreadLocalRandom.current().nextInt(100, 999);

        this.address = address;
        this.type = type;
        this.port = port;

        group = new NioEventLoopGroup();
    }

    @Override
    @SneakyThrows
    public Connector setProcessor(Class<? extends IConnector> processor) {
        this.processor = processor.newInstance();
        return this;
    }

    @Override
    public Connector setProcessor(IConnector processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public Connector addShutdownHook(Thread thread) {
        Runtime.getRuntime().addShutdownHook(thread);
        return this;
    }

    @Override
    public Connector write(String message) {
        channel.writeAndFlush(message + "\r\n");
        return this;
    }

    @Override
    public Connector setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Connector initialise() {
            try {
                Bootstrap bootstrap = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socket) throws Exception {
                                ChannelPipeline pipeline = socket.pipeline();

                                if(getType().equals(Type.BYTEBUF))
                                    pipeline.addLast(new Connector.TcpProcessor(Connector.this));
                                else {
                                    pipeline.addLast("framer", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
                                    pipeline.addLast("decoder", new StringDecoder());
                                    pipeline.addLast("encoder", new StringEncoder());

                                    pipeline.addLast("handler", new NettyProcessor(Connector.this));
                                }
                            }
                        });

                channel = bootstrap.connect(getAddress(), getPort()).sync().channel();

            } catch (InterruptedException ex) {
                processor.onException(ex);
            }
        return this;
    }

    @Override
    @Deprecated
    public Connector terminate() {
        group.shutdown();
        return this;
    }

    @Override
    public Connector terminateGracefully() {
        group.shutdownGracefully();
        return this;
    }

    private class TcpProcessor extends ChannelInboundHandlerAdapter {

        @Getter
        private Connector connector;

        public TcpProcessor(Connector connector) {
            this.connector = connector;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object label) throws Exception {
            connector.processor.receivePacket(context, label);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
            connector.processor.onException(context, cause);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) throws Exception {
            connector.processor.onDisconnect(context);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
            connector.processor.onDisconnect(channelHandlerContext);
            super.channelUnregistered(channelHandlerContext);
        }

    }

    private class NettyProcessor extends SimpleChannelInboundHandler<String> {

        @Getter
        private Connector connector;

        public NettyProcessor(Connector connector) {
            this.connector = connector;
        }

        @Deprecated
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {
            /* NOTHING */
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            connector.processor.receivePacket(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            connector.processor.onException(ctx, cause);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext channelHandlerContext) throws Exception {
            connector.processor.onDisconnect(channelHandlerContext);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext channelHandlerContext) throws Exception {
            connector.processor.onDisconnect(channelHandlerContext);
            super.channelUnregistered(channelHandlerContext);
        }
    }

    public static enum Type {
        STRING, BYTEBUF, HTTP
    }
}
