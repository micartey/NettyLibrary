package me.clientastisch.netty.cluster;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultEventExecutor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.net.BindException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Cluster implements ClusterStructure<Cluster> {

    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ExecutorService executor;

    private final ChannelGroup channels;
    private ICluster processor;
    private Thread thread;

    private boolean autoPort;
    private int timeOut;
    private Type type;

    private String address, name;
    private AtomicInteger port;

    public Cluster(String address, int port, Type type) {
        this.name = "Cluster@" + ThreadLocalRandom.current().nextInt(100, 999);
        this.channels = new DefaultChannelGroup(new DefaultEventExecutor());
        this.executor = Executors.newCachedThreadPool();

        this.address = address;
        this.type = type;

        this.timeOut = Integer.MAX_VALUE;
        this.port = new AtomicInteger(port);
    }

    @Override
    @SneakyThrows
    public Cluster setProcessor(Class<? extends ICluster> processor) {
        this.processor = processor.newInstance();
        return this;
    }

    @Override
    public Cluster setProcessor(ICluster processor) {
        this.processor = processor;
        return this;
    }

    @Override
    public Cluster addShutdownHook(Thread thread) {
        Runtime.getRuntime().addShutdownHook(thread);
        return this;
    }

    @Override
    public Cluster autoPort(boolean autoPort) {
        this.autoPort = autoPort;
        return this;
    }

    @Override
    public Cluster setTimeOut(int timeOut) {
        this.timeOut = timeOut;
        return this;
    }

    @Override
    public Cluster setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public boolean isAlive() {
        return !bossGroup.isShutdown();
    }

    @Override
    public Cluster initialise() {
        executor.execute(() -> {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();

                            if(getType().equals(Type.BYTEBUF))
                                pipeline.addLast(new SocketProcessor(Cluster.this));
                            else if(getType().equals(Type.STRING)) {
                                pipeline.addLast("framer", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
                                pipeline.addLast("decoder", new StringDecoder());
                                pipeline.addLast("encoder", new StringEncoder());

                                pipeline.addLast("handler", new NettyProcessor(Cluster.this));
                            } else if(getType().equals(Type.HTTP)) {
                                pipeline.addLast(new HttpRequestDecoder());
                                pipeline.addLast(new HttpResponseEncoder());
                                pipeline.addLast(new HttpProcessor(Cluster.this));
                            }
                        }
                    });

            bind(bootstrap);
        });

        return this;
    }

    private void bind(ServerBootstrap bootstrap) {
        try {
            bootstrap.bind(address, port.get()).sync().channel().closeFuture().sync();
        } catch (Exception exception) {
            if (exception.getClass().equals(BindException.class)) {
                port.incrementAndGet();
                bind(bootstrap);
            }

            processor.onException(exception);
        }
    }


    @Override
    public Cluster terminate() {
        this.workerGroup.shutdownGracefully();
        this.bossGroup.shutdownGracefully();
        return this;
    }

    private class SocketProcessor extends ChannelInboundHandlerAdapter {

        @Getter
        private Cluster cluster;

        private Channel channel;
        private long lastResponse;

        public SocketProcessor(Cluster cluster) {
            this.cluster = cluster;
        }

        public void checkResponse(Channel channel) {
            Timer timeOut = new Timer(channel.remoteAddress().toString(), false);
            timeOut.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(lastResponse < (System.currentTimeMillis() - cluster.getTimeOut())) {
                        cluster.processor.onTimeout(channel, System.currentTimeMillis() - lastResponse);
                        SocketProcessor.this.channel = null;
                        channel.close();

                        timeOut.cancel();
                    }
                }
            }, cluster.getTimeOut(), cluster.getTimeOut());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext context) {
            lastResponse = System.currentTimeMillis();
            channel = context.channel();

            cluster.channels.add(context.channel());
            cluster.processor.onConnect(context);
            checkResponse(context.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) {
            cluster.channels.remove(context.channel());
            channel = null;

            cluster.processor.onDisconnect(context);
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object label) {
            lastResponse = System.currentTimeMillis();
            cluster.processor.receivePacket(context, label);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            cluster.processor.onException(context, cause);
        }
    }

    private class NettyProcessor extends SimpleChannelInboundHandler<String> {

        @Getter
        private Cluster cluster;

        private Channel channel;
        private long lastResponse;

        public NettyProcessor(Cluster cluster) {
            this.cluster = cluster;
        }

        public void checkResponse(Channel channel) {
            Timer timeOut = new Timer(channel.remoteAddress().toString(), false);
            timeOut.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(lastResponse < (System.currentTimeMillis() - cluster.getTimeOut())) {
                        cluster.processor.onTimeout(channel, System.currentTimeMillis() - lastResponse);
                        NettyProcessor.this.channel = null;
                        channel.close();

                        timeOut.cancel();
                    }
                }
            }, cluster.getTimeOut(), cluster.getTimeOut());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext context) {
            lastResponse = System.currentTimeMillis();
            channel = context.channel();

            cluster.channels.add(context.channel());
            cluster.processor.onConnect(context);
            checkResponse(context.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) {
            cluster.channels.remove(context.channel());
            channel = null;

            cluster.processor.onDisconnect(context);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, String label) {
            lastResponse = System.currentTimeMillis();
            cluster.processor.receivePacket(context, label);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            cluster.processor.onException(context, cause);
        }
    }

    private class HttpProcessor extends SimpleChannelInboundHandler<HttpObject> {

        @Getter
        private Cluster cluster;

        private Channel channel;
        private long lastResponse;

        public HttpProcessor(Cluster cluster) {
            this.cluster = cluster;
        }

        public void checkResponse(Channel channel) {
            Timer timeOut = new Timer(channel.remoteAddress().toString(), false);
            timeOut.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(lastResponse < (System.currentTimeMillis() - cluster.getTimeOut())) {
                        cluster.processor.onTimeout(channel, System.currentTimeMillis() - lastResponse);
                        HttpProcessor.this.channel = null;
                        channel.close();

                        timeOut.cancel();
                    }
                }
            }, cluster.getTimeOut(), cluster.getTimeOut());
        }

        @Override
        public void handlerAdded(ChannelHandlerContext context) {
            lastResponse = System.currentTimeMillis();
            channel = context.channel();

            cluster.channels.add(context.channel());
            cluster.processor.onConnect(context);
            checkResponse(context.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) {
            cluster.channels.remove(context.channel());
            channel = null;

            cluster.processor.onDisconnect(context);
        }

        @Override
        public void channelRead0(ChannelHandlerContext context, HttpObject httpObject) throws Exception {
            lastResponse = System.currentTimeMillis();
            cluster.processor.receivePacket(context, httpObject);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            cluster.processor.onException(context, cause);
        }
    }

    public static enum Type {
        STRING, BYTEBUF, HTTP
    }
}
